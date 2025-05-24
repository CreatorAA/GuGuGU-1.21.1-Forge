package online.pigeonshouse.gugugu.utils;

import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.zip.*;

/**
 * 文件工具类，提供常见的文件和目录操作，包括安全文件名、hash计算、原子拷贝、并行压缩/解压等功能。
 */
public final class FileUtil {
    private static final Pattern UNSAFE = Pattern.compile("[^a-zA-Z0-9._-]+");
    private static final int BUFFER_SIZE = 512 * 1024;
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
            while ((len = bis.read(buf)) != -1) md.update(buf, 0, len);
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

    public static void copyAtomic(@NonNull Path src, @NonNull Path dst) throws IOException {
        Path tmp = dst.resolveSibling(dst.getFileName() + ".tmp");
        createDirectory(dst.getParent());
        Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, dst, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void copyDirectoryAtomic(@NonNull Path source, @NonNull Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = source.relativize(dir);
                createDirectory(target.resolve(rel));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                copyAtomic(file, target.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void compressDirectoryParallel(
            @NonNull Path sourceDir,
            @NonNull Path zipFilePath,
            List<String> filter,
            int parallelism,
            long maxByteCapacity
    ) throws IOException, InterruptedException {
        if (filter == null) filter = List.of();
        createFile(zipFilePath);
        createDirectory(zipFilePath.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipFilePath)), StandardCharsets.UTF_8)) {
            zos.setLevel(COMPRESSION_LEVEL);

            List<Future<CompressedEntry>> futures = new ArrayList<>();
            List<Path> largeFiles = Collections.synchronizedList(new ArrayList<>());
            ExecutorService pool = Executors.newFixedThreadPool(parallelism);

            List<String> finalFilter = filter;
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attrs) {
                    String entryName = normalizeEntryName(sourceDir, file);
                    if (shouldFilter(entryName, finalFilter)) return FileVisitResult.CONTINUE;
                    if (attrs.size() > maxByteCapacity / 2) {
                        largeFiles.add(file);
                    } else {
                        futures.add(pool.submit(() -> compressFile(entryName, file)));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            pool.shutdown();

            for (Future<CompressedEntry> f : futures) {
                CompressedEntry ce;
                try {
                    ce = f.get();
                } catch (ExecutionException e) {
                    throw new IOException("压缩失败", e.getCause());
                }
                synchronized (zos) {
                    zos.putNextEntry(new ZipEntry(ce.entryName));
                    zos.write(ce.data);
                    zos.closeEntry();
                }
            }
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

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
    }

    private static CompressedEntry compressFile(String entryName, Path file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos,
                new Deflater(COMPRESSION_LEVEL, true));
             InputStream is = Files.newInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = bis.read(buf)) != -1) {
                dos.write(buf, 0, len);
            }
        }
        return new CompressedEntry(entryName, baos.toByteArray());
    }

    @Getter
    private static class CompressedEntry {
        private final String entryName;
        private final byte[] data;
        public CompressedEntry(String entryName, byte[] data) {
            this.entryName = entryName;
            this.data = data;
        }
    }

    public static Path unzipToDirectoryParallel(@NotNull Path zipFilePath, @NotNull Path destPath) throws IOException {
        int workers = Runtime.getRuntime().availableProcessors();
        try {
            return unzipToDirectoryParallel(zipFilePath, destPath, workers);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Parallel unzip interrupted", e);
        }
    }

    public static Path unzipToDirectoryParallel(
            @NotNull Path zipFilePath,
            @NotNull Path destPath,
            int parallelism
    ) throws IOException, InterruptedException {
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
                        if (!entryPath.startsWith(destPath)) throw new IOException("非法路径: " + entry.getName());
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
                                while ((read = bis.read(buffer)) != -1) {
                                    bos.write(buffer, 0, read);
                                }
                            }
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }));
            }
            for (Future<?> f : tasks) {
                f.get();
            }
        } catch (ExecutionException e) {
            throw new IOException("Failed during parallel unzip", e.getCause());
        } finally {
            pool.shutdown();
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        }
        return destPath;
    }

    public static void deleteDirectory(@NonNull Path dir) {
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

    private static String normalizeEntryName(Path base, Path file) {
        return base.relativize(file).toString().replace(File.separatorChar, '/');
    }

    private static boolean shouldFilter(String entryName, List<String> filter) {
        return filter.stream().anyMatch(entryName::contains);
    }
}
