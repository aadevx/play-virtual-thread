package play.jte;

import gg.jte.CodeResolver;
import gg.jte.runtime.Constants;
import play.Play;
import play.libs.IO;
import play.utils.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TemplateResolver implements CodeResolver {

    private final List<File> root = new ArrayList<>();
//    private static final List<String> errorTemplateList = Arrays.asList("errors/400.jte", "errors/403.jte", "errors/404.jte", "errors/500.jte");

    public TemplateResolver() {
        this.root.add(new File(Play.applicationPath,  "app/jte"));
        for(File vf : Play.modules.values())
            this.root.add(new File(vf, "app/jte"));
    }

    @Override
    public String resolve(String name) {
        for(File vf : root) {
            File tf = new File(vf, name);
            if (tf.exists())
                return IO.readContentAsString(tf);
        }
        return null;
    }

    @Override
    public long getLastModified(String name) {
        for(File vf : root) {
            File tf = new File(vf, name);
            if (tf.exists())
                return tf.lastModified();
        }
       return 0L;
    }

    public boolean isUseJte() {
        return !root.isEmpty() || Play.getResource(Constants.PACKAGE_NAME_PRECOMPILED.replace(".", "/")) != null;
    }

    @Override
    public List<String> resolveAllTemplateNames() {
        List<String> result = new ArrayList<>();
        for(File vf : root) {
            scan(result, vf, vf);
        }
//        result.addAll(errorTemplateList);
        return result;
    }

    void scan(List<String> templates,  File root, File current) {
        if (!current.isDirectory() && current.getName().endsWith(".jte")) {
            String name = root.toPath().relativize(current.toPath()).toString().replace('\\', '/');
            templates.add(name);
        } else if (current.isDirectory() && !current.getName().startsWith(".")) {
            for (File virtualFile : FileUtils.list(current)) {
                scan(templates, root, virtualFile);
            }
        }
    }

    public void scan(List<Path> templates, File current, FileFilter filter) {
        if (filter.accept(current)) {
            templates.add(current.toPath());
        } else if (current.isDirectory() && !current.getName().startsWith(".")) {
            for (File virtualFile : FileUtils.list(current)) {
                scan(templates, virtualFile, filter);
            }
        }
    }
}
