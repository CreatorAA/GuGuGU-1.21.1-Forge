package online.pigeonshouse.gugugu.utils;

import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class FileUtil {
    private static final Pattern UNSAFE = Pattern.compile("[^a-zA-Z0-9._-]+");

    public static String safeFileName(String name) {
        return UNSAFE.matcher(name).replaceAll("\\$\\$");
    }

    public static void createDirectory(Path path) {
        if (!path.toFile().exists()) {
            path.toFile().mkdirs();
        }
    }

    public static void createFile(Path path) {
        if (!path.toFile().exists()) {
            createDirectory(path.toAbsolutePath().getParent());
            try {
                path.toFile().createNewFile();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hashFile(Path p) {
        try {
            MessageDigest md = sha1();
            md.reset();
            md.update(Files.readAllBytes(p));
            return bytesToHex(md.digest());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte v : b) sb.append(String.format("%02x", v));
        return sb.toString();
    }

    @SneakyThrows
    public static void copyAtomic(Path src, Path dst) {
        Path tmp = dst.resolveSibling(dst.getFileName()+".tmp");
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

                if (!Files.exists(destination)) {
                    Files.createDirectories(destination);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Path destination = target.resolve(relative);

                copyAtomic(file, destination);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void compressDirectory(Path sourceDirPath, Path zipFilePath, List<String> filter) throws IOException {
        if (filter == null) filter = List.of();
        FileUtil.createFile(zipFilePath);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            List<String> finalFilter = filter;
            Files.walk(sourceDirPath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        if (finalFilter.stream().noneMatch(path.toString()::contains)) {
                            String entryName = sourceDirPath.relativize(path).toString()
                                    .replace(File.separatorChar, '/');
                            addToZipFile(zos, path, entryName);
                        }
                    });
        }
    }

    private static void addToZipFile(ZipOutputStream zos, Path filePath, String entryName) {
        try (InputStream fis = Files.newInputStream(filePath)) {
            ZipEntry zipEntry = new ZipEntry(entryName);
            zos.putNextEntry(zipEntry);

            byte[] buffer = new byte[4096];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException("Error adding file to ZIP: " + filePath, e);
        }
    }

    public static Path unzipToDirectory(Path zipFilePath, Path destPath) throws IOException {
        try (ZipInputStream zipIn = new ZipInputStream(
                Files.newInputStream(zipFilePath), StandardCharsets.UTF_8)) {

            if (!Files.exists(destPath)) {
                createDirectory(destPath);
            }

            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                Path entryPath = destPath.resolve(entry.getName()).normalize();

                if (!entryPath.startsWith(destPath)) {
                    throw new IOException(entry.getName());
                }

                if (entry.isDirectory()) {
                    createDirectory(entryPath);
                } else {
                    if (!Files.exists(entryPath.getParent())) {
                        createDirectory(entryPath.getParent());
                    }

                    try (OutputStream os = Files.newOutputStream(entryPath);
                         BufferedOutputStream bos = new BufferedOutputStream(os)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = zipIn.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                        }
                    }
                }
                zipIn.closeEntry();
            }
        }
        return destPath;
    }

    public static void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
