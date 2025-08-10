package play.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.selectors.FilenameSelector;
import org.apache.tools.ant.util.FileUtils;
import play.Logger;
import play.libs.IO;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ant task which loads settings needed by the ant from the ant configuration file.
 *
 * These include:
 * - Resolving the settings for the given play id and setting them to the ant project properties
 * - Creating classpath element for the module libraries
 *
 */
public class PlayConfigurationLoadTask {

    private Project project;
    /** Play id */
    private String playId = "";
    /** Prefix to use for the properties loaded from the configuration file */
    private String prefix = "application.conf.";
    /** Source file to read */
    private File applicationDir;
    /** Properties extracted from the conf file */
    private Map<String, String> properties = null;

    public void setProject(Project project) {
        this.project = project;
    }

    public void setPlayId(String playId) {
        this.playId = playId;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setApplicationDir(File applicationDir) {
        this.applicationDir = applicationDir;
    }

    public void execute() {
        if (applicationDir == null) {
            throw new BuildException("No applicationDir set!");
        }

        // Add the properties from application.conf as ant properties
        properties().forEach((key, v) -> {
            String value = project.replaceProperties(v);
            project.setProperty(prefix + key, value);
            project.log("Loaded property '" + prefix + key + "'='" + value + "'", Project.MSG_VERBOSE);
        });

        // Add the module classpath as an ant property
        Path path = new Path(project);
        FilenameSelector endsToJar = new FilenameSelector();
        endsToJar.setName("*.jar");

        for (File module: modules()) {
            File moduleLib = new File(module, "lib");
            if (moduleLib.exists()) {
                FileSet fileSet = new FileSet();
                fileSet.setDir(moduleLib);
                fileSet.addFilename(endsToJar);
                path.addFileset(fileSet);
                project.log("Added fileSet to path: " + fileSet, Project.MSG_VERBOSE);
            } else {
                project.log("Ignoring non existing lib dir: " + moduleLib.getAbsolutePath(), Project.MSG_VERBOSE);
            }
        }
        /** Id for the classpath element */
        String modulesClasspath = "modules.classpath";
        project.addReference(modulesClasspath, path);
        project.log("Generated classpath '" + modulesClasspath + "':" + project.getReference(modulesClasspath), Project.MSG_VERBOSE);
    }

    /**
     * Load all properties from the given conf file, resolving the id
//     * @param srcFile the conf file
//     * @param playId the current id
     * @return a Map of key, values corresponding to the entries in the conf file
     */
    private Map<String, String> properties() {
        if (properties != null) return properties;
        File srcFile = new File(applicationDir, "conf/application.conf");
        if (!srcFile.exists()) {
            throw new BuildException("No application configuration found! " + srcFile.getAbsolutePath());
        }
        BufferedReader reader = null;
        try {
            properties = new HashMap<>();
            Map<String,String> idSpecific = new HashMap<>();
            reader = new BufferedReader(new FileReader(srcFile));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("%")) {
                    if (!playId.isEmpty() && line.startsWith(playId + ".")) {
                        line = line.substring((playId + ".").length());
                        String[] sa = splitLine(line);
                        if (sa != null) {
                            idSpecific.put(sa[0], sa[1]);
                        }
                    }
                } else {
                    String[] sa = splitLine(line);
                    if (sa != null) {
                        properties.put(sa[0], sa[1]);
                    }
                }
            }
            properties.putAll(idSpecific);
            return properties;
        } catch (IOException e) {
            throw new BuildException("Failed to load configuration file: " + srcFile.getAbsolutePath(), e);
        } finally{
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                throw new BuildException("Failed to close configuration file: " + srcFile.getAbsolutePath(), e);
            }
        }
    }

    /*
     * Get the set of modules for the current project. This include old-style (using application.conf)
     * and new style, with dependencies starting at 1.2 (load everything from the modules/ dir)
     */
    private Set<File> modules() {
        Set<File> modules = new HashSet<>();

        // Old-skool
        for (Map.Entry<String,String> entry: properties().entrySet()) {
            if (!entry.getKey().startsWith("module.")) {
                continue;
            }
            String s = project.replaceProperties(entry.getValue());
            File moduleDir;
            if (!FileUtils.isAbsolutePath(s)) {
                moduleDir = new File(new File(applicationDir, "conf"), s);
            } else {
                moduleDir = new File(s);
            }
            if (!moduleDir.exists()) {
                project.log("Failed add non existing module to classpath! " + moduleDir.getAbsolutePath(), Project.MSG_WARN);
                continue;
            }
            modules.add(moduleDir);
        }

        // 1.2+ fashion
        File modulesDir = new File(applicationDir, "modules");
        if (modulesDir.exists()) {
            try (DirectoryStream<java.nio.file.Path> stream = Files.newDirectoryStream(modulesDir.toPath())){
                for (java.nio.file.Path path : stream) {
                    File child = path.toFile();
                    if (child == null) {
                        // No-op
                    } else if (child.isDirectory()) {
                        modules.add(child);
                    } else {
                        modules.add(new File(IO.readContentAsString(child)));
                    }
                }
            } catch (IOException e) {
                Logger.error(e,e.getMessage());
            }
        }
        return modules;
    }

    private static String[] splitLine(String line) {
        if (line.indexOf("=") == -1) return null;
        String[] splitted = line.split("=");
        return new String[]{splitted[0].trim(), splitted[1].trim()};
    }
}
