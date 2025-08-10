package play.libs;


import org.apache.commons.io.FileUtils;
import play.Logger;
import play.exceptions.UnexpectedException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Files utils
 */
public class Files {

    /**
     * Characters that are invalid in Windows OS file names (Unix only forbids '/' character)
     */
    public static final char[] ILLEGAL_FILENAME_CHARS = { 34, 60, 62, 124, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
            19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 58, 42, 63, 92, 47 };

    public static final char ILLEGAL_FILENAME_CHARS_REPLACE = '_';

    /**
     * Indicate if two file refers to the same one
     * 
     * @param a
     *            First file to compare
     * @param b
     *            Second file to compare
     * @return true is file are the same
     */
    public static boolean isSameFile(File a, File b) {
        if (a != null && b != null) {
            Path aPath = null;
            Path bPath = null;
            try {
                aPath = Paths.get(a.getCanonicalPath());
                bPath = Paths.get(b.getCanonicalPath());
                return java.nio.file.Files.isSameFile(aPath, bPath);
            } catch (NoSuchFileException e) {
                // As the file may not exist, we only compare path
                return 0 == aPath.compareTo(bPath);
            } catch (Exception e) {
                Logger.error(e, "Cannot get canonical path from files");
            }
        }
        return false;
    }

    /**
     * Just copy a file
     * 
     * @param from
     *            source of the file
     * @param to
     *            destination file
     */
    public static void copy(File from, File to) {
        if (isSameFile(from, to)) {
            return;
        }
        try {
            FileUtils.copyFile(from, to);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void copyDir(File from, File to) {
        IO.copyDirectory(from, to);
    }

    public static void unzip(File from, File to) {
        try {
            String outDir = to.getCanonicalPath();
            try (ZipFile zipFile = new ZipFile(from)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        new File(to, entry.getName()).mkdir();
                        continue;
                    }
                    File f = new File(to, entry.getName());
                    if (!f.getCanonicalPath().startsWith(outDir)) {
                        throw new IOException("Corrupted zip file");
                    }
                    f.getParentFile().mkdirs();
                    IO.write(zipFile.getInputStream(entry), f);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void zip(File directory, File zipFile) {
        try {
            try (FileOutputStream os = new FileOutputStream(zipFile)) {
                try (ZipOutputStream zos = new ZipOutputStream(os)) {
                    zipDirectory(directory, directory, zos);
                }
            }
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    /**
     * Replace all characters that are invalid in file names on Windows or Unix operating systems with
     * {@link Files#ILLEGAL_FILENAME_CHARS_REPLACE} character.
     * <p>
     * This method makes sure your file name can successfully be used to write new file to disk. Invalid characters are
     * listed in {@link Files#ILLEGAL_FILENAME_CHARS} array.
     *
     * @param fileName
     *            File name to sanitize
     * @return Sanitized file name (new String object) if found invalid characters or same string if not
     */
    public static String sanitizeFileName(String fileName) {
        return sanitizeFileName(fileName, ILLEGAL_FILENAME_CHARS_REPLACE);
    }

    /**
     * Replace all characters that are invalid in file names on Windows or Unix operating systems with passed in
     * character.
     * <p>
     * This method makes sure your file name can successfully be used to write new file to disk. Invalid characters are
     * listed in {@link Files#ILLEGAL_FILENAME_CHARS} array.
     *
     * @param fileName
     *            File name to sanitize
     * @param replacement
     *            character to use as replacement for invalid chars
     * @return Sanitized file name (new String object) if found invalid characters or same string if not
     */
    public static String sanitizeFileName(String fileName, char replacement) {
        if (fileName == null || fileName.isEmpty()) {
            return fileName;
        }

        char[] chars = fileName.toCharArray();
        boolean changed = false;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c < 128) {
                for (char illegal : ILLEGAL_FILENAME_CHARS) {
                    if (c == illegal) {
                        chars[i] = replacement;
                        changed = true;
                        break;
                    }
                }
            }
        }

        return changed ? new String(chars) : fileName;
    }

    static void zipDirectory(File root, File directory, ZipOutputStream zos) throws Exception {
        try (DirectoryStream<Path> stream = java.nio.file.Files.newDirectoryStream(directory.toPath())){
            for (Path path : stream) {
                File item = path.toFile();
                if (item.isDirectory()) {
                    zipDirectory(root, item, zos);
                } else {
                    try (FileInputStream fis = new FileInputStream(item)) {
                        String fpath = item.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
                        ZipEntry anEntry = new ZipEntry(fpath);
                        zos.putNextEntry(anEntry);
                        IO.copy(fis, zos);
                    }
                }
            }
        } catch (IOException e) {
            Logger.error(e,e.getMessage());
        }
    }

    public static void delete(File file) {
        if(file == null)
            return;
        try {
            Path dir = file.toPath();
            java.nio.file.Files.walkFileTree(dir, new SimpleFileVisitor<Path>()
            {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    java.nio.file.Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc == null) {
                        java.nio.file.Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else {
                        throw exc;
                    }
                }
            });
        } catch (IOException e) {
            Logger.error(e,e.getMessage());
        }
    }
}