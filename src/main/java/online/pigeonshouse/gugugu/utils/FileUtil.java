package online.pigeonshouse.gugugu.utils;

import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class FileUtil {
    private static final Pattern UNSAFE = Pattern.compile("[^a-zA-Z0-9._-]+");
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int COMPRESSION_LEVEL = Deflater.BEST_COMPRESSION;

    private static final ThreadLocal<MessageDigest> SHA1_CTX = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unsupported", e);
        }
    });

    public static String safeFileName(@NonNull String name) {
        return UNSAFE.matcher(name).replaceAll("\\$\\$");
    }

    public static void createDirectory(@NonNull Path path) {
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void createFile(@NonNull Path path) {
        try {
            if (Files.notExists(path)) {
                createDirectory(path.getParent());
                Files.createFile(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String hashFile(@NonNull Path filePath) {
        MessageDigest md = SHA1_CTX.get();
        md.reset();

        try (InputStream is = Files.newInputStream(filePath);
             BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE)) {

            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = bis.read(buf)) != -1) {
                md.update(buf, 0, len);
            }
            return bytesToHex(md.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @SneakyThrows
    public static void copyAtomic(Path src, Path dst) {
        Path tmp = dst.resolveSibling(dst.getFileName() + ".tmp");
        Files.createDirectories(dst.getParent());
        Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, dst, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void copyDirectoryAtomic(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path destination = target.resolve(relative);
                Files.createDirectories(destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                copyAtomic(file, target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void compressDirectoryParallel(
            @NonNull Path sourceDirPath,
            @NonNull Path zipFilePath,
            List<String> filter,
            int parallelism,
            long maxByteCapacity
    ) throws IOException, InterruptedException {
        if (filter == null) filter = List.of();
        createFile(zipFilePath);
        try (ZipOutputStream zos = createZipStream(zipFilePath)) {
            ByteCapacityController controller = new ByteCapacityController(maxByteCapacity);
            BlockingQueue<ZipTask> taskQueue = new LinkedBlockingQueue<>();
            List<Path> largeFiles = Collections.synchronizedList(new ArrayList<>());

            ExecutorService consumers = startConsumers(zos, taskQueue, parallelism, controller);
            Thread producer = startProducer(sourceDirPath, filter, maxByteCapacity, controller, taskQueue, largeFiles, parallelism);

            producer.join();
            shutdownAndAwait(consumers);

            writeLargeFiles(sourceDirPath, largeFiles, zos);
        }
    }

    private static ZipOutputStream createZipStream(Path zipFilePath) throws IOException {
        createDirectory(zipFilePath.getParent());
        OutputStream os = Files.newOutputStream(zipFilePath);
        BufferedOutputStream bos = new BufferedOutputStream(os);
        ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8);
        zos.setLevel(COMPRESSION_LEVEL);
        return zos;
    }

    private static ExecutorService startConsumers(
            ZipOutputStream zos,
            BlockingQueue<ZipTask> queue,
            int parallelism,
            ByteCapacityController controller
    ) {
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        for (int i = 0; i < parallelism; i++) {
            pool.submit(() -> consumeQueue(zos, queue, controller));
        }
        return pool;
    }

    private static void consumeQueue(
            ZipOutputStream zos,
            BlockingQueue<ZipTask> queue,
            ByteCapacityController controller
    ) {
        try {
            while (true) {
                ZipTask task = queue.take();
                if (task == ZipTask.POISON_TASK) {
                    queue.put(task);
                    break;
                }
                writeTask(zos, task, controller);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeTask(
            ZipOutputStream zos,
            ZipTask task,
            ByteCapacityController controller
    ) throws IOException, InterruptedException {
        synchronized (zos) {
            zos.putNextEntry(new ZipEntry(task.getEntryName()));
            ChunkData chunk;
            while ((chunk = task.chunks.take()) != ChunkData.POISON_CHUNK) {
                zos.write(chunk.data);
                controller.release(chunk.data.length);
            }
            zos.closeEntry();
        }
    }

    private static Thread startProducer(
            Path sourceDir,
            List<String> filter,
            long maxBytes,
            ByteCapacityController controller,
            BlockingQueue<ZipTask> queue,
            List<Path> largeFiles,
            int parallelism
    ) {
        Thread producer = new Thread(() -> {
            try {
                Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String entryName = normalizeEntryName(sourceDir, file);
                        if (shouldFilter(entryName, filter)) {
                            return FileVisitResult.CONTINUE;
                        }
                        long size = attrs.size();
                        if (size > maxBytes / 2) {
                            largeFiles.add(file);
                        } else {
                            try {
                                enqueueFile(queue, controller, entryName, file);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                for (int i = 0; i < parallelism; i++) {
                    try {
                        queue.put(ZipTask.POISON_TASK);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        producer.start();
        return producer;
    }

    private static String normalizeEntryName(Path base, Path file) {
        return base.relativize(file).toString().replace(File.separatorChar, '/');
    }

    private static boolean shouldFilter(String entryName, List<String> filter) {
        return filter.stream().anyMatch(entryName::contains);
    }

    private static void enqueueFile(
            BlockingQueue<ZipTask> queue,
            ByteCapacityController controller,
            String entryName,
            Path file
    ) throws InterruptedException, IOException {
        ZipTask task = new ZipTask(entryName, controller);
        queue.put(task);
        try (InputStream is = Files.newInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = bis.read(buf)) != -1) {
                task.write(Arrays.copyOf(buf, len));
            }
            task.complete();
        }
    }

    private static void shutdownAndAwait(ExecutorService pool) throws InterruptedException {
        pool.shutdown();
        if (!pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
            pool.shutdownNow();
        }
    }

    private static void writeLargeFiles(
            Path sourceDir,
            List<Path> largeFiles,
            ZipOutputStream zos
    ) throws IOException {
        for (Path file : largeFiles) {
            String entryName = normalizeEntryName(sourceDir, file);
            zos.putNextEntry(new ZipEntry(entryName));
            try (InputStream is = Files.newInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE)) {
                byte[] buf = new byte[BUFFER_SIZE];
                int len;
                while ((len = bis.read(buf)) != -1) {
                    zos.write(buf, 0, len);
                }
            }
            zos.closeEntry();
        }
    }

    public static Path unzipToDirectoryParallel(Path zipFilePath, Path destPath) throws IOException {
        int workers = Runtime.getRuntime().availableProcessors();
        try {
            return unzipToDirectoryParallel(zipFilePath, destPath, workers);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Parallel unzip interrupted", e);
        }
    }

    public static Path unzipToDirectoryParallel(Path zipFilePath, Path destPath, int parallelism) throws IOException, InterruptedException {
        createDirectory(destPath);
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try (ZipFile zipFile = new ZipFile(zipFilePath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            List<Future<?>> tasks = new ArrayList<>();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                tasks.add(pool.submit(() -> {
                    try {
                        Path entryPath = destPath.resolve(entry.getName()).normalize();
                        if (!entryPath.startsWith(destPath)) throw new IOException(entry.getName());
                        if (entry.isDirectory()) {
                            createDirectory(entryPath);
                        } else {
                            createDirectory(entryPath.getParent());
                            try (InputStream is = zipFile.getInputStream(entry);
                                 BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE);
                                 OutputStream os = Files.newOutputStream(entryPath);
                                 BufferedOutputStream bos = new BufferedOutputStream(os, BUFFER_SIZE)) {
                                byte[] buffer = new byte[BUFFER_SIZE];
                                int read;
                                while ((read = bis.read(buffer)) != -1) bos.write(buffer, 0, read);
                            }
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }));
            }

            for (Future<?> f : tasks) f.get();
        } catch (ExecutionException e) {
            throw new IOException("Failed during parallel unzip", e.getCause());
        } finally {
            pool.shutdown();
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        }
        return destPath;
    }

    public static void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class ByteCapacityController {
        private final long maxBytes;
        private long currentBytes = 0;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();

        public ByteCapacityController(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        public void acquire(long bytes) throws InterruptedException {
            lock.lock();
            try {
                while (currentBytes + bytes > maxBytes) {
                    notFull.await();
                }
                currentBytes += bytes;
            } finally {
                lock.unlock();
            }
        }

        public void release(long bytes) {
            lock.lock();
            try {
                currentBytes -= bytes;
                notFull.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    private static class ZipTask {
        @Getter
        private final String entryName;
        private final ByteCapacityController controller;
        private final BlockingQueue<ChunkData> chunks = new LinkedBlockingQueue<>();
        static final ZipTask POISON_TASK = new ZipTask(null, null);

        public ZipTask(String entryName, ByteCapacityController controller) {
            this.entryName = entryName;
            this.controller = controller;
        }

        public void write(byte[] data) throws InterruptedException {
            controller.acquire(data.length);
            chunks.put(new ChunkData(data));
        }

        public void complete() throws InterruptedException {
            chunks.put(ChunkData.POISON_CHUNK);
        }
    }

    private record ChunkData(byte[] data) {
        static final ChunkData POISON_CHUNK = new ChunkData(null);
    }
}
