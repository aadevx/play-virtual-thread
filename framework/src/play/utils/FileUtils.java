package play.utils;

import play.Logger;
import play.Play;
import play.exceptions.UnexpectedException;
import play.libs.IO;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileUtils {

    static final Pattern pattern = Pattern.compile("^(\\{(.+?)\\})?(.*)$");
    public static File fromRelativePath(String relativePath) {
        if (relativePath == null) { // avoid NPE in pattern.matcher(relativePath)
            return null;
        }
        Matcher matcher = pattern.matcher(relativePath);
        if (matcher.matches()) {
            String path = matcher.group(3);
            String module = matcher.group(2);
            if (module == null || module.equals("?") || module.isEmpty()) {
                return new File(Play.applicationPath, path);
            } else {
                if (module.equals("play")) {
                    return new File(Play.frameworkPath, path);
                }
                if (module.startsWith("module:")) {
                    module = module.substring("module:".length());
                    for (Map.Entry<String, File> entry : Play.modules.entrySet()) {
                        if (entry.getKey().equals(module))
                            return new File(entry.getValue(), path);
                    }
                }
            }
        }
        return null;
    }

    public static String relativePath(File f) {
        List<String> path = new ArrayList<>();
        String prefix = "{?}";
        while (true) {
            path.add(f.getName());
            f = f.getParentFile();
            if (f == null) {
                break; // ??
            }
            if (f.equals(Play.applicationPath) || f.equals(Play.frameworkPath)) {
                prefix = "";
                break;
            }
            String module = isRoot(f);
            if (module != null) {
                prefix = module;
                break;
            }
        }
        Collections.reverse(path);
        StringBuilder builder = new StringBuilder(prefix);
        for (String p : path) {
            builder.append('/').append(p);
        }
        return builder.toString();
    }

    static String isRoot(File f) {
        for (File vf : Play.roots) {
            if (vf.getAbsolutePath().equals(f.getAbsolutePath())) {
                String modulePathName = vf.getName();
                String moduleName = modulePathName.contains("-") ? modulePathName.substring(0, modulePathName.lastIndexOf("-"))
                        : modulePathName;
                return "{module:" + moduleName + "}";
            }
        }
        return null;
    }

    public static List<File> list(File file) {
        List<File> res = new ArrayList<>();
        if (file != null && file.exists()) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(file.toPath())){
                for (Path path : stream) {
                    res.add(path.toFile());
                }
            } catch (IOException e) {
                Logger.error(e,e.getMessage());
            }
        }
        return res;
    }

    public static URL toURl(File file) {
        if(file.exists()) {
            try {
                return file.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            String relatifPath = relativePath(file);
            URL resource = Play.getResource(relatifPath);
            if (resource == null) {// find in modules
                if(relatifPath.startsWith("{module:")) {
                    for (String name : Play.modules.keySet()) {
                        String modulekey = "{module:"+name+"}";
                        if (relatifPath.startsWith(modulekey)) {
                            File vf = Play.modules.get(name);
                            relatifPath = relatifPath.replace(modulekey, "modules/"+vf.getName());
                            break;
                        }
                    }
                    resource = Play.getResource(relatifPath);
                } else {// find resource in play.jar
                    resource = Play.getResource(relatifPath.replace("/app/views", ""));
                }
            }
            return resource;
        }
    }

    public static boolean matchName(File file, String fileName) {
        // we need to check the name case to be sure we is not conflict with a file with the same name
        String canonicalName = null;
        try {
            canonicalName = file.getCanonicalFile().getName();
        } catch (IOException e) {
        }
        // Name case match
        return fileName != null && canonicalName != null && fileName.endsWith(canonicalName);
    }

    public static byte[] content(File file) {
        try (InputStream is = new FileInputStream(file)){
            return IO.readContent(is);
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    public static String contentAsString(File file) {
        try (InputStream is = file.exists() ? new FileInputStream(file) : toURl(file).openStream()) {
            return IO.readContentAsString(is);
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    public static void delete(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }
}
