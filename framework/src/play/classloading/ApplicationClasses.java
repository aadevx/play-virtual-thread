package play.classloading;

import javassist.ClassPool;
import javassist.CtClass;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.cache.Cache;
import play.classloading.enhancers.Enhancer;
import play.exceptions.UnexpectedException;
import play.inject.Injector;
import play.libs.IO;
import play.mvc.PlayController;
import play.mvc.Router;
import play.utils.FileUtils;
import play.utils.Java;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.instrument.ClassDefinition;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

/**
 * Application classes container.
 */
public class ApplicationClasses {

    /**
     * Reference to the eclipse compiler.
     */
	final ApplicationCompiler compiler = new ApplicationCompiler(this);
    /**
     * Cache of all compiled classes
     */
    Map<String, ApplicationClass> classes = new ConcurrentHashMap<>();

    /**
     * Clear the classes cache
     */
    public void clear() {
        classes = new ConcurrentHashMap<>();
    }

    /**
     * Get a class by name
     * 
     * @param name
     *            The fully qualified class name
     * @return The ApplicationClass or null
     */
    public ApplicationClass getApplicationClass(String name) {
        return classes.computeIfAbsent(name, className -> {
            File javaFile = getJava(className);
            return javaFile == null ? null : new ApplicationClass(className, javaFile);
        });
    }

    /**
     * Retrieve all application classes with a specific annotation.
     * 
     * @param clazz
     *            The annotation class.
     * @return A list of application classes.
     */
    public List<Class> getAnnotatedClasses(Class<? extends Annotation> clazz) {
        List<Class> results = new ArrayList<>();
        for (ApplicationClass applicationClass : classes.values()) {
            if (!applicationClass.isClass()) {
                continue;
            }
            loadClass(applicationClass.name);
            if (applicationClass.javaClass != null && applicationClass.javaClass.isAnnotationPresent(clazz)) {
                results.add(applicationClass.javaClass);
            }
        }
        return results;
    }

    public List<Class> getAnnotatedClasses(Class[] clazz) {
        List<Class> results = new ArrayList<>();
        for (Class<? extends Annotation> cl : clazz) {
            results.addAll(getAnnotatedClasses(cl));
        }
        return results;
    }

    /**
     * All loaded classes.
     * 
     * @return All loaded classes
     */
    public List<ApplicationClass> all() {
        return new ArrayList<>(classes.values());
    }

    /**
     * Put a new class to the cache.
     */
    public void add(ApplicationClass applicationClass) {
        classes.put(applicationClass.name, applicationClass);
    }


    /**
     * Does this class is already loaded ?
     * 
     * @param name
     *            The fully qualified class name
     */
    public boolean hasClass(String name) {
        return classes.containsKey(name);
    }

    /**
     * Represent a application class
     */
    public static class ApplicationClass {

        /**
         * The fully qualified class name
         */
        public String name;
        /**
         * A reference to the java source file
         */
        public File javaFile;
        /**
         * The compiled byteCode
         */
        public byte[] javaByteCode;
        /**
         * The enhanced byteCode
         */
        public byte[] enhancedByteCode;
        /**
         * The in JVM loaded class
         */
        public Class<?> javaClass;
        /**
         * Last time than this class was compiled
         */
        public Long timestamp = 0L;
        /**
         * Is this class compiled
         */
        boolean compiled;
        /**
         * Signatures checksum
         */
        boolean enhanced;

        public ApplicationClass() {
        }

        public ApplicationClass(String name) {
            this(name, getJava(name));
        }

        public ApplicationClass(String name, File javaFile) {
            this.name = name;
            this.javaFile = javaFile;
            this.refresh();
        }

        /**
         * Need to refresh this class !
         */
        public final void refresh() {
            this.javaByteCode = null;
            this.enhancedByteCode = null;
            this.compiled = false;
            this.timestamp = 0L;
            this.enhanced = false;
        }

        static final ClassPool enhanceChecker_classPool = Enhancer.newClassPool();
        static final CtClass ctPlayPluginClass = enhanceChecker_classPool.makeClass(PlayPlugin.class.getName());

        /**
         * Enhance this class
         */
        public void enhance() {
            this.enhancedByteCode = this.javaByteCode;
            if (isClass()) {

                // before we can start enhancing this class we must make sure it is not a PlayPlugin.
                // PlayPlugins can be included as regular java files in a Play-application.
                // If a PlayPlugin is present in the application, it is loaded when other plugins are loaded.
                // All plugins must be loaded before we can start enhancing.
                // This is a problem when loading PlayPlugins bundled as regular app-class since it uses the same
                // classloader
                // as the other (soon to be) enhanced play-app-classes.
                boolean shouldEnhance = true;
                try {
                    CtClass ctClass = enhanceChecker_classPool.makeClass(new ByteArrayInputStream(this.enhancedByteCode));
                    if (ctClass.subclassOf(ctPlayPluginClass)) {
                        shouldEnhance = false;
                    }
                } catch (Exception e) {
                    // nop
                }

                if (shouldEnhance) {
                    Play.pluginCollection.enhance(this);
                }
                this.enhanced = shouldEnhance;
            }
            if (System.getProperty("precompile") != null) {
                // emit bytecode to standard class layout as well
                File f = Play.classes.getClassFile(name);
                try {
                    f.getParentFile().mkdirs();
                    IO.write(this.enhancedByteCode, f);
                } catch (Exception e) {
                    Logger.error(e, "Failed to write precompiled class %s to disk", name);
                }
            }
        }

        /**
         * Is this class already compiled but not defined ?
         * 
         * @return if the class is compiled but not defined
         */
        public boolean isDefinable() {
            return compiled && javaClass != null;
        }

        public boolean isClass() {
            return isClass(this.name);
        }

        public static boolean isClass(String name) {
            return !name.endsWith("package-info");
        }

        public String getPackage() {
            int dot = name.lastIndexOf('.');
            return dot > -1 ? name.substring(0, dot) : "";
        }

        public boolean isShouldEnhance() {
            return enhancedByteCode != null && !enhanced;
        }
        /**
         * Compile the class from Java source
         * 
         * @return the bytes that comprise the class file
         */
        public byte[] compile() {
            long start = System.currentTimeMillis();
            Play.classes.compiler.compile(new String[] { this.name });

            if (Logger.isTraceEnabled()) {
                Logger.trace("%sms to compile class %s", System.currentTimeMillis() - start, name);
            }

            return this.javaByteCode;
        }

        /**
         * Unload the class
         */
        public void uncompile() {
            this.javaClass = null;
        }

        /**
         * Call back when a class is compiled.
         * 
         * @param code
         *            The bytecode.
         */
        public void compiled(byte[] code) {
            javaByteCode = code;
            enhancedByteCode = code;
            compiled = true;
            this.timestamp = this.javaFile.lastModified();
        }

        @Override
        public String toString() {
            return name + " (compiled:" + compiled + ")";
        }

        public String source() {
            if (this.javaFile != null) {
                return IO.readContentAsString(this.javaFile);
            }
            return "";
        }
    }

    // ~~ Utils
    /**
     * Retrieve the corresponding source file for a given class name. It handles innerClass too !
     * 
     * @param name
     *            The fully qualified class name
     * @return The virtualFile if found
     */
    public static File getJava(String name) {
        String fileName = name;
        if (fileName.contains("$")) {
            fileName = fileName.substring(0, fileName.indexOf("$"));
        }
        // the local variable fileOrDir is important!
        String fileOrDir = fileName.replace('.', '/');
        fileName = fileOrDir + ".java";
        for (File path : Play.javaPath) {
            // 1. check if there is a folder (without extension)
            File javaFile = new File(path, fileOrDir);

            if (javaFile.exists() && javaFile.isDirectory() && FileUtils.matchName(javaFile, fileOrDir)) {
                // we found a directory (package)
                return null;
            }
            // 2. check if there is a file
            javaFile = new File(path, fileName);
            if (javaFile.exists() && FileUtils.matchName(javaFile, fileName)) {
                return javaFile;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return classes.toString();
    }

    // modifed by Arief Ardiyansah
    public File getClassFile(String path) {
        return new File(Play.classPath(), path.replace('.', '/') + ".class");
    }

    /**
     * Detect Java changes
     *             Thrown if the application need to be restarted
     */
    public void detectChanges() throws Exception {
        // Now check for file modification
        List<ApplicationClass> modifieds = new ArrayList<>();
        for (ApplicationClass applicationClass : Play.classes.all()) {
            if (applicationClass.timestamp < applicationClass.javaFile.lastModified()) {
                applicationClass.refresh();
                modifieds.add(applicationClass);
            }
        }
        Set<ApplicationClass> modifiedWithDependencies = new HashSet<>(modifieds);
        List<ClassDefinition> newDefinitions = new ArrayList<>();
        for (ApplicationClass applicationClass : modifiedWithDependencies) {
            if (applicationClass.compile() == null) {
                Play.classes.classes.remove(applicationClass.name);
            } else {
                applicationClass.enhance();
                newDefinitions.add(new ClassDefinition(applicationClass.javaClass, applicationClass.enhancedByteCode));
            }
            if(applicationClass.javaClass != null && PlayController.class.isAssignableFrom(applicationClass.javaClass)) {
                Router.reload = true;
            }
        }
        if (!newDefinitions.isEmpty()) {
            Cache.clear();
            Java.reload();
            Injector.init();
            if (HotswapAgent.enabled) {
                try {
                    HotswapAgent.reload(newDefinitions.toArray(new ClassDefinition[0]));
                } catch (Throwable e) {
                    throw new RuntimeException(newDefinitions.size() + " classes changed", e);
                }
            } else {
                throw new RuntimeException(newDefinitions.size() + " classes changed (and HotSwap is not enabled)");
            }
        }
    }

    /**
     * Find a class in a case insensitive way
     *
     * @param name
     *            The class name.
     * @return a class
     */
    public Class getClassIgnoreCase(String name) {
        String nameLowerCased = name.toLowerCase();
        ApplicationClass c = allClassesByNormalizedName.get(nameLowerCased);
        if (c != null) {
            if (Play.usePrecompiled) {
                return c.javaClass;
            }
            return loadClass(c.name);
        }
        return null;
    }

    /**
     * Try to load all .java files found.
     *
     * @return The list of well defined Class
     */
    public void getAllClasses() {
        if (!Play.started) {
            List<Class> result = new ArrayList<>();
            if (Play.usePrecompiled) {
                Play.classes.clear();
                URL resource = Play.getResource("controllers");
                if(resource != null) {
                    try {
                        URI uri = new URI(resource.toString().replace(" ", "%20"));
                        String jarPath = uri.getSchemeSpecificPart().substring(5, uri.getSchemeSpecificPart().lastIndexOf("!"));
                        try (JarFile jarFile = new JarFile(jarPath)){
                            Enumeration<JarEntry> jarEnum = jarFile.entries();
                            while (jarEnum.hasMoreElements()) {
                                JarEntry entry = jarEnum.nextElement();
                                if (entry.getName().endsWith(".class") && !entry.getName().startsWith(".")) {
                                    String classname = entry.getName().substring(0, entry.getName().length() - 6).replace(File.separatorChar, '.');
                                    ApplicationClass applicationClass  = new ApplicationClass(classname);
                                    Class clazz = loadClass(applicationClass.name);
                                    Play.classes.add(applicationClass);
                                    applicationClass.javaClass = clazz;
                                    applicationClass.compiled = true;
                                    result.add(clazz);
                                }
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    List<ApplicationClass> applicationClasses = new ArrayList<>();
                    scanPrecompiled(applicationClasses, "", Play.getVirtualFile("precompiled/java"));
                    for (ApplicationClass applicationClass : applicationClasses) {
                        Play.classes.add(applicationClass);
                        Class clazz = loadClass(applicationClass.name);
                        applicationClass.javaClass = clazz;
                        applicationClass.compiled = true;
                        result.add(clazz);
                    }
                }
            } else {
                if (!Play.pluginCollection.compileSources()) {
                    List<ApplicationClass> all = new ArrayList<>();
                    for (File virtualFile : Play.javaPath) {
                        all.addAll(getAllClasses(virtualFile));
                    }
                    List<String> classNames = new ArrayList<>();
                    for (ApplicationClass applicationClass : all) {
                        if (applicationClass != null && !applicationClass.compiled && applicationClass.isClass()) {
                            classNames.add(applicationClass.name);
                        }
                    }
                    compiler.compile(classNames.toArray(new String[0]));
                }
                for (ApplicationClass applicationClass : Play.classes.all()) {
                    Class clazz = loadClass(applicationClass.name);
                    if (clazz != null) {
                        result.add(clazz);
                    }
                }
            }
            Map<String, ApplicationClass> byNormalizedName = new HashMap<>(result.size());
            for (ApplicationClass clazz : Play.classes.all()) {
                byNormalizedName.put(clazz.name.toLowerCase(), clazz);
                if (clazz.name.contains("$")) {
                    byNormalizedName.put(clazz.name.toLowerCase().replace("$", "."), clazz);
                }
            }

            allClassesByNormalizedName = unmodifiableMap(byNormalizedName);
        }
    }

    private List<ApplicationClass> getAllClasses(File path) {
        return getAllClasses(path, "");
    }

    private List<ApplicationClass> getAllClasses(File path, String basePackage) {
        if (!basePackage.isEmpty() && !basePackage.endsWith(".")) {
            basePackage += ".";
        }
        List<ApplicationClass> res = new ArrayList<>();
        for (File virtualFile : FileUtils.list(path)) {
            scan(res, basePackage, virtualFile);
        }
        return res;
    }

    private void scan(List<ApplicationClass> classes, String packageName, File current) {
        if (!current.isDirectory()) {
            if (current.getName().endsWith(".java") && !current.getName().startsWith(".")) {
                String classname = packageName + current.getName().substring(0, current.getName().length() - 5);
                classes.add(Play.classes.getApplicationClass(classname));
            }
        } else {
            for (File virtualFile : FileUtils.list(current)) {
                scan(classes, packageName + current.getName() + ".", virtualFile);
            }
        }
    }

    private void scanPrecompiled(List<ApplicationClass> classes, String packageName, File current) {
        if (!current.isDirectory()) {
            if (current.getName().endsWith(".class") && !current.getName().startsWith(".")) {
                String classname = packageName.substring(5) + current.getName().substring(0, current.getName().length() - 6);
                classes.add(new ApplicationClass(classname));
            }
        } else {
            for (File virtualFile : FileUtils.list(current)) {
                scanPrecompiled(classes, packageName + current.getName() + ".", virtualFile);
            }
        }
    }

    /**
     * Retrieve all application classes assignable to this class.
     *
     * @param clazz
     *            The superclass, or the interface.
     * @return A list of class
     */
    public List<Class> getAssignableClasses(Class clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        return assignableClassesByName.computeIfAbsent(clazz.getName(), className -> {
            List<Class> results = new ArrayList<>();
            if (clazz != null) {
                for (ApplicationClass applicationClass : new ArrayList<>(classes.values())) {
                    if (!applicationClass.isClass()) {
                        continue;
                    }
                    loadClass(applicationClass.name);
                    try {
                        if (clazz.isAssignableFrom(applicationClass.javaClass)
                                && !applicationClass.javaClass.getName().equals(clazz.getName())) {
                            results.add(applicationClass.javaClass);
                        }
                    } catch (Exception e) {
                    }
                }
            }
            return unmodifiableList(results);
        });
    }

    // assignable classes cache
    private final Map<String, List<Class>> assignableClassesByName = new HashMap<>(100);
    private Map<String, ApplicationClass> allClassesByNormalizedName;

    public Class<?> loadClass(String name) {
        try {
            return Class.forName(name, false, Play.classloader);
        } catch (ClassNotFoundException e) {
            throw new UnexpectedException(e);
        }
    }
}