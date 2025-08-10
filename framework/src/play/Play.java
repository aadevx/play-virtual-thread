package play;

import org.apache.commons.lang3.StringUtils;
import play.cache.Cache;
import play.classloading.ApplicationClasses;
import play.classloading.ApplicationClassloader;
import play.deps.DependenciesManager;
import play.exceptions.PlayException;
import play.exceptions.UnexpectedException;
import play.inject.Injector;
import play.jte.JtePlugin;
import play.libs.IO;
import play.mvc.Http;
import play.mvc.Router;
import play.plugins.PluginCollection;
import play.templates.TemplateLoader;
import play.utils.OrderSafeProperties;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main framework class
 */
public class Play implements Serializable {

    /**
     * 2 modes
     */
    public enum Mode {

        /**
         * Enable development-specific features, e.g. view the documentation at the URL {@literal "/@documentation"}.
         */
        DEV,

        /**
         * test environment
         */
        TEST,
        /**
         * Disable development-specific features.
         */
        PROD;

        public boolean isDev() {
            return this == DEV;
        }

        public boolean isProd() {
            return this == PROD || this == TEST;
        }

        public boolean isTest() {
            return this == TEST;
        }
    }
    /**
     * Is the application initialized
     */
    public static boolean initialized = false;

    /**
     * Is the application started
     */
    public static boolean started = false;
    /**
     * True when the one and only shutdown hook is enabled
     */
    private static boolean shutdownHookEnabled = false;
    /**
     * The framework ID
     */
    public static String id = System.getProperty("play.id", "");
    /**
     * The application mode
     */
    public static Mode mode = Mode.DEV;
    /**
     * The application root
     */
    public static File applicationPath = new File(System.getProperty("application.path", "."));
    /**
     * tmp dir
     */
    public static File tmpDir = null;
    /**
     * The framework root
     */
    public static File frameworkPath = null;
    /**
     * All loaded application classes
     */
    public static ApplicationClasses classes;
    /**
     * The application classLoader
     */
    public static ApplicationClassloader classloader;
    /**
     * All paths to search for files
     */
    public static final List<File> roots = new ArrayList<>(16);
    /**
     * All paths to search for Java files
     */
    public static List<File> javaPath = new CopyOnWriteArrayList<>();
    /**
     * All paths to search for templates files
     */
    public static final List<File> templatesPath = new ArrayList<>(2);
    /**
     * Main routes file
     */
    public static File routes;
    /**
     * Plugin routes files
     */
    public static final Map<String, File> modulesRoutes = new HashMap<>(16);
    /**
     * The loaded configuration files
     */
    public static Set<File> confs = new HashSet<>(1);
    /**
     * The app configuration (already resolved from the framework id)
     */
    public static Properties configuration = new Properties();
    /**
     * The last time than the application has started
     */
    public static long startedAt;
    /**
     * The list of supported locales
     */
    public static List<String> langs = new ArrayList<>(16);
    /**
     * The very secret key
     */
    public static String secretKey;
    /**
     * pluginCollection that holds all loaded plugins and all enabled plugins..
     */
    public static PluginCollection pluginCollection = new PluginCollection();
    /**
     * Readonly list containing currently enabled plugins.
     * This list is updated from pluginCollection when pluginCollection is modified
     * Play plugins
     * Use pluginCollection instead.
     */
    @Deprecated
    public static List<PlayPlugin> plugins = pluginCollection.getEnabledPlugins();
    /**
     * Modules
     */
    public static final Map<String, File> modules = new HashMap<>(16);
    /**
     * Framework version
     */
    public static String version = null;
    /**
     * Context path (when several application are deployed on the same host)
     */
    public static String ctxPath = "";
    static boolean firstStart = true;
    public static boolean usePrecompiled = false;

    /**
     * This is used as default encoding everywhere related to the web: request, response, WS
     */
    public static String defaultWebEncoding = "utf-8";

    /**
     * This flag indicates if the app is running in a standalone Play server or as a
     * WAR in an applicationServer
     */
    public static final boolean standalonePlayServer = true;

    /**
     * Init the framework
     *
     * @param root The application path
     * @param id   The framework id to use
     */
    public static void init(File root, String id) {
        // Simple things
        Play.id = id;
        Play.started = false;
        Play.applicationPath = root;

        // load all play.static of exists
        initStaticStuff();

        guessFrameworkPath();

        // Read the configuration file
        readConfiguration();

        Play.classes = new ApplicationClasses();

        // Configure logs
        Logger.init();

        //only override log-level if Logger was not configured manually
        if( !Logger.configuredManually) {
            String logLevel = configuration.getProperty("application.log", "INFO");
            Logger.setUp(logLevel);
        }
        Logger.recordCaller = Boolean.parseBoolean(configuration.getProperty("application.log.recordCaller", "false"));

        Logger.info("Starting %s", root.getAbsolutePath());

        if (configuration.getProperty("play.tmp", "tmp").equals("none")) {
            tmpDir = null;
            Logger.debug("No tmp folder will be used (play.tmp is set to none)");
        } else {
            tmpDir = new File(configuration.getProperty("play.tmp", "tmp"));
            if (!tmpDir.isAbsolute()) {
                tmpDir = new File(applicationPath, tmpDir.getPath());
            }

            if (Logger.isTraceEnabled()) {
                Logger.trace("Using %s as tmp dir", Play.tmpDir);
            }

            if (!tmpDir.exists()) {
                try {
                    tmpDir.mkdirs();
                } catch (Throwable e) {
                    tmpDir = null;
                    Logger.warn("No tmp folder will be used (cannot create the tmp dir), caused by: %s", e);
                }
            }
        }

        // Mode
        try {
            mode = Mode.valueOf(configuration.getProperty("application.mode", "DEV").toUpperCase());
        } catch (IllegalArgumentException e) {
            Logger.error("Illegal mode '%s', use either prod or dev", configuration.getProperty("application.mode"));
            fatalServerErrorOccurred();
        }
        
        // Force the Production mode if forceProd or precompile is activate
        // Set to the Prod mode must be done before loadModules call
        // as some modules (e.g. DocViewer) is only available in DEV
        if (usePrecompiled || System.getProperty("precompile") != null) {
            mode = Mode.PROD;
        }

        // Context path
        ctxPath = configuration.getProperty("http.path", ctxPath);

        // Build basic java source path
        File appRoot = applicationPath;
        roots.clear();
        roots.add(appRoot);
        javaPath.clear();
        javaPath.add(new File(appRoot, "app"));
        javaPath.add(new File(appRoot, "conf"));

        // Build basic templates path
        templatesPath.clear();
        if (new File(appRoot, "app/views").exists() || (usePrecompiled && new File(appRoot, "precompiled/templates/app/views").exists())
            || (usePrecompiled && Play.getResource("app/views") != null)) {
            templatesPath.add(new File(appRoot, "app/views"));
        }
        
        // Main route file
        routes = new File(appRoot, "conf/routes");

        // Plugin route files
        modulesRoutes.clear();

        // Load modules
        loadModules(appRoot);

        // Enable a first classloader
        classloader = new ApplicationClassloader();

        // Fix ctxPath
        if ("/".equals(Play.ctxPath)) {
            Play.ctxPath = "";
        }

        // Default cookie domain
        Http.Cookie.defaultDomain = configuration.getProperty("application.defaultCookieDomain", null);
        if (Http.Cookie.defaultDomain!=null) {
            Logger.info("Using default cookie domain: " + Http.Cookie.defaultDomain);
        }

        // Plugins
        pluginCollection.loadPlugins();

        // Done !
        if (Play.mode.isProd()) {
            if (preCompile() && System.getProperty("precompile") == null) {
                start();
            } else {
                return;
            }
        } else {
            Logger.warn("You're running Play! in DEV mode");
            start();
        }

        // Plugins
        pluginCollection.onApplicationReady();

        Play.initialized = true;
    }

    public static void guessFrameworkPath() {
        // Guess the framework path
        try {
            URL versionUrl = Play.class.getResource("/play/version");
            // Read the content of the file
            Play.version = new LineNumberReader(new InputStreamReader(versionUrl.openStream())).readLine();

            // This is used only by the embedded server (Mina, Netty, Jetty etc)
            URI uri = new URI(versionUrl.toString().replace(" ", "%20"));
            if (frameworkPath == null || !frameworkPath.exists()) {
                if (uri.getScheme().equals("jar")) {
                    String jarPath = uri.getSchemeSpecificPart().substring(5,
                            uri.getSchemeSpecificPart().lastIndexOf("!"));
                    frameworkPath = new File(jarPath).getParentFile().getParentFile().getAbsoluteFile();
                } else if (uri.getScheme().equals("file")) {
                    frameworkPath = new File(uri).getParentFile().getParentFile().getParentFile().getParentFile();
                } else {
                    throw new UnexpectedException(
                            "Cannot find the Play! framework - trying with uri: " + uri + " scheme " + uri.getScheme());
                }
            }
        } catch (Exception e) {
            throw new UnexpectedException("Where is the framework ?", e);
        }
    }

    /**
     * Read application.conf and resolve overridden key using the play id mechanism.
     */
    public static void readConfiguration() {
        confs = new HashSet<>();
        configuration = readOneConfigurationFile("application.conf");
//        extractHttpPort();
        // Plugins
        pluginCollection.onConfigurationRead();
     }


    private static Properties readOneConfigurationFile(String filename) {
        Properties propsFromFile=null;
        File appRoot = applicationPath;
        File conf = new File(appRoot, "conf/" + filename);
        if (confs.contains(conf)) {
            throw new RuntimeException("Detected recursive @include usage. Have seen the file " + filename + " before");
        }
        
        try {
            propsFromFile = IO.readUtf8Properties(new FileInputStream(conf));
        } catch (Exception e) {
            if (e.getCause() instanceof IOException) {
                Logger.fatal("Cannot read "+filename);
                fatalServerErrorOccurred();
            } else {
                Logger.error(e, "readOneConfigurationFile : %s", e.getMessage());
            }
        }
        confs.add(conf);
        
        // OK, check for instance specifics configuration
        Properties newConfiguration = new OrderSafeProperties();
        Pattern pattern = Pattern.compile("^%([a-zA-Z0-9_\\-]+)\\.(.*)$");
        if(propsFromFile != null) {
            for (Map.Entry<Object, Object> entry : propsFromFile.entrySet()) {
                Object key = entry.getKey();
                Matcher matcher = pattern.matcher(String.valueOf(key));
                if (!matcher.matches()) {
                    newConfiguration.put(key, entry.getValue().toString().trim());
                }
            }
            for (Map.Entry<Object, Object> entry : propsFromFile.entrySet()) {
                Matcher matcher = pattern.matcher(String.valueOf(entry.getKey()));
                if (matcher.matches()) {
                    String instance = matcher.group(1);
                    if (instance.equals(id)) {
                        newConfiguration.put(matcher.group(2), entry.getValue().toString().trim());
                    }
                }
            }
        }
        propsFromFile = newConfiguration;
        // Resolve ${..}
        pattern = Pattern.compile("\\$\\{([^}]+)}");
        Pattern replacement = Pattern.compile("\\\\");
        for (Object key : propsFromFile.keySet()) {
            String value = propsFromFile.getProperty(key.toString());
            Matcher matcher = pattern.matcher(value);
            StringBuilder newValue = new StringBuilder(100);
            while (matcher.find()) {
                String jp = matcher.group(1);
                String r;
                if (jp.equals("application.path")) {
                    r = Play.applicationPath.getAbsolutePath();
                } else if (jp.equals("play.path")) {
                    r = Play.frameworkPath.getAbsolutePath();
                } else {
                    r = System.getProperty(jp);
                    if (r == null) {
                        r = System.getenv(jp);
                    }
                    if (r == null) {
                        Logger.warn("Cannot replace %s in configuration (%s=%s)", jp, key, value);
                        continue;
                    }
                }
                matcher.appendReplacement(newValue, replacement.matcher(r).replaceAll("\\\\\\\\"));
            }
            matcher.appendTail(newValue);
            propsFromFile.setProperty(key.toString(), newValue.toString());
        }
        // Include
        Map<Object, Object> toInclude = new HashMap<>(16);
        for (Object key : propsFromFile.keySet()) {
            if (key.toString().startsWith("@include.")) {
                try {
                    String filenameToInclude = propsFromFile.getProperty(key.toString());
                    toInclude.putAll( readOneConfigurationFile(filenameToInclude) );
                } catch (Exception ex) {
                    Logger.warn(ex, "Missing include: %s", key);
                }
            }
        }
        propsFromFile.putAll(toInclude);

        return propsFromFile;
    }

    private static final Lock readLock = new ReentrantLock();
    /**
     * Start the application.
     * Recall to restart !
     */
    public static void start() {
        try {
            readLock.lock();
            if (started) {
                stop();
            }

            if (standalonePlayServer) {
                // Can only register shutdown-hook if running as standalone server
                if (!shutdownHookEnabled) {
                    // registers shutdown hook - Now there's a good chance that we can notify
                    // our plugins that we're going down when some calls ctrl+c or just kills our
                    // process..
                    shutdownHookEnabled = true;
                    Thread hook = new Thread(Play::stop);
                    hook.setContextClassLoader(ClassLoader.getSystemClassLoader());
                    Runtime.getRuntime().addShutdownHook(hook);
                }
            }

            if (mode == Mode.DEV) {
                // Need a new classloader
                classloader = new ApplicationClassloader();
                // Put it in the current context for any code that relies on having it there
                Thread.currentThread().setContextClassLoader(classloader);
                // Reload plugins
                pluginCollection.reloadApplicationPlugins();

            }

            // Reload configuration
            readConfiguration();

            // Configure logs
            String logLevel = configuration.getProperty("application.log", "INFO");
            // only override log-level if Logger was not configured manually
            if (!Logger.configuredManually) {
                Logger.setUp(logLevel);
            }
            Logger.recordCaller = Boolean.parseBoolean(configuration.getProperty("application.log.recordCaller", "false"));

            // Locales
            langs = new ArrayList<>(Arrays.asList(configuration.getProperty("application.langs", "").split(",")));
            if (langs.size() == 1 && langs.get(0).isBlank()) {
                langs = new ArrayList<>(16);
            }

            // Clean templates
            TemplateLoader.cleanCompiledCache();

            // SecretKey
            secretKey = configuration.getProperty("application.secret", "").trim();
            if (secretKey.isEmpty()) {
                Logger.warn("No secret key defined. Sessions will not be encrypted");
            }

            // Default web encoding
            String _defaultWebEncoding = configuration.getProperty("application.web_encoding");
            if (_defaultWebEncoding != null) {
                Logger.info("Using custom default web encoding: " + _defaultWebEncoding);
                defaultWebEncoding = _defaultWebEncoding;
                // Must update current response also, since the request/response triggering
                // this configuration-loading in dev-mode have already been
                // set up with the previous encoding
                if (Http.Response.current() != null) {
                    Http.Response.current().encoding = _defaultWebEncoding;
                }
            }

            // Try to load all classes
            Play.classes.getAllClasses();
            // Routes
            Router.detectChanges(ctxPath);
            // injector init
            Injector.init();
            // Cache
            Cache.init();
            // jte check
            JtePlugin.load();
            // Plugins
            try {
                pluginCollection.onApplicationStart();
            } catch (Exception e) {
                if (Play.mode.isProd()) {
                    Logger.error(e, "Can't start in PROD mode with errors");
                }
                if (e instanceof RuntimeException x) {
                    throw x;
                }
                throw new UnexpectedException(e);
            }

            if (firstStart) {
                Logger.info("Application '%s' is now started !", configuration.getProperty("application.name", ""));
                firstStart = false;
            }

            // We made it
            started = true;
            startedAt = System.currentTimeMillis();

            // Plugins
            pluginCollection.afterApplicationStart();

        } catch (PlayException e) {
            started = false;
            try {
                Cache.stop();
            } catch (Exception ignored) {
            }
            throw e;
        } catch (Exception e) {
            started = false;
            try {
                Cache.stop();
            } catch (Exception ignored) {
            }
            throw new UnexpectedException(e);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Stop the application
     */
    public static void stop() {
        try {
            readLock.lock();
            if (started) {
                Logger.trace("Stopping the play application");
                pluginCollection.onApplicationStop();
                started = false;
                Cache.stop();
                Router.lastLoading = 0L;
            }
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Force all java source and template compilation.
     *
     * @return success ?
     */
    static boolean preCompile() {
        if (usePrecompiled) {
            if (Play.getFile("precompiled").exists() || Play.getResource("controllers") != null) {
                classes.getAllClasses();
                Logger.info("Application is precompiled");
                return true;
            }
            Logger.error("Precompiled classes are missing!!");
            fatalServerErrorOccurred();
            return false;
        }
        try {
            Logger.info("Precompiling ...");
            Thread.currentThread().setContextClassLoader(Play.classloader);
            long start = System.currentTimeMillis();
            classes.getAllClasses();
            if (Logger.isTraceEnabled()) {
                Logger.trace("%sms to precompile the Java stuff", System.currentTimeMillis() - start);
            }
            Router.load(Play.ctxPath);
            JtePlugin.precompile();
            start = System.currentTimeMillis();
            TemplateLoader.getAllTemplate();
            if (Logger.isTraceEnabled()) {
                Logger.trace("%sms to precompile the templates", System.currentTimeMillis() - start);
            }

            return true;
        } catch (Throwable e) {
            Logger.error(e, "Cannot start in PROD mode with errors");
            fatalServerErrorOccurred();
            return false;
        }
    }

    /**
     * Detect sources modifications
     */
    public static void detectChanges() {
        try {
            readLock.lock();
            if (Play.mode.isProd()) {
                return;
            }
            try {
                classes.detectChanges();
                pluginCollection.detectChange();
                if (!Play.started) {
                    throw new RuntimeException("Not started");
                }
            } catch (PlayException e) {
                throw e;
            } catch (Exception e) {
                Logger.error(e, "Restart: " + e.getMessage());
                start();
            }
        }finally {
            readLock.unlock();
        }
    }

    public static <T extends PlayPlugin> T plugin(Class<T> clazz) {
        return pluginCollection.getPluginInstance(clazz);
    }



    /**
     * Allow some code to run very early in Play - Use with caution !
     */
    public static void initStaticStuff() {
        // Play! plugings
        Enumeration<URL> urls = null;
        try {
            urls = Play.class.getClassLoader().getResources("play.static");
        } catch (Exception e) {
        }
        while (urls != null && urls.hasMoreElements()) {
            URL url = urls.nextElement();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    try {
                        Class.forName(line);
                    } catch (Exception e) {
                        Logger.warn(e, "! Cannot init static: " + line);
                    }
                }
            } catch (Exception ex) {
                Logger.error(ex, "Cannot load %s", url);
            }
        }
    }

    /**
     * Load all modules. You can even specify the list using the MODULES
     * environment variable.
     */
    public static void loadModules() {
        loadModules(applicationPath);
    }

    /**
     * Load all modules.
     * You can even specify the list using the MODULES environment variable.
     * @param appRoot : the application path virtual file
     */
    public static void loadModules(File appRoot) {
        if (System.getenv("MODULES") != null) {
            // Modules path is prepended with a env property
            if (System.getenv("MODULES") != null && !System.getenv("MODULES").trim().isEmpty()) {
            	for (String m : System.getenv("MODULES").split(File.pathSeparator)) {
                    File modulePath = new File(m);
                    if (!modulePath.exists() || !modulePath.isDirectory()) {
                        Logger.error("Module %s will not be loaded because %s does not exist", modulePath.getName(), modulePath.getAbsolutePath());
                    } else {
                        String modulePathName = modulePath.getName();
                        String moduleName = modulePathName.contains("-") ?
                                modulePathName.substring(0, modulePathName.lastIndexOf("-")) :
                                modulePathName;
                        addModule(appRoot, moduleName, modulePath);
                    }
                }
            }
        }

        // Load modules from modules/ directory, but get the order from the dependencies.yml file
        // See #781
        // the yaml parser wants play.version as an environment variable
        System.setProperty("play.version", Play.version);
        System.setProperty("application.path", applicationPath.getAbsolutePath());

        File localModules = Play.getFile("modules");
        Set<String> modules = new LinkedHashSet<>();
        if (localModules != null && localModules.exists() && localModules.isDirectory()) {
            try {
                File userHome = new File(System.getProperty("user.home"));
                DependenciesManager dm = new DependenciesManager(applicationPath, frameworkPath, userHome);
                modules = dm.retrieveModules();
            } catch (Exception e) {
                Logger.error("There was a problem parsing dependencies.yml (module will not be loaded in order of the dependencies.yml)", e);
                // Load module without considering the dependencies.yml order
                modules.addAll(Arrays.asList(localModules.list()));
            }

            for (Iterator<String> iter = modules.iterator(); iter.hasNext(); ) {
                String moduleName = iter.next();

                File module = new File(localModules, moduleName);

                if (moduleName.contains("-")) {
                    moduleName = moduleName.substring(0, moduleName.lastIndexOf("-"));
                }

                if (module == null || !module.exists()) {
                    Logger.error("Module %s will not be loaded because %s does not exist", moduleName, module.getAbsolutePath());
                } else if (module.isDirectory()) {
                    addModule(appRoot, moduleName, module);
                } else {
                    File modulePath = new File(IO.readContentAsString(module).trim());
                    if (!modulePath.exists() || !modulePath.isDirectory()) {
                        Logger.error("Module %s will not be loaded because %s does not exist", moduleName, modulePath.getAbsolutePath());
                    } else {
                        addModule(appRoot, moduleName, modulePath);
                    }
                }
            }
        }

        // Auto add special modules
        if (Play.runningInTestMode()) {
            addModule(appRoot, "_testrunner", new File(Play.frameworkPath, "modules/testrunner"));
        }

        if (Play.mode == Mode.DEV) {
            addModule(appRoot, "_docviewer", new File(Play.frameworkPath, "modules/docviewer"));
        }
    }
    
    /**
     * Add a play application (as plugin)
     *
     * @param appRoot
     *            : the application path virtual file
     * @param name
     *            : the module name
     * @param path
     *            The application path
     */
    private static void addModule(File appRoot, String name, File path) {
        modules.put(name, path);
        if (new File(path, "app").exists()) {
            javaPath.add(new File(path, "app"));
        }
        if (new File(path,"app/views").exists() || (usePrecompiled && new File(appRoot, "precompiled/templates/from_module_" + name + "/app/views").exists())
         || usePrecompiled && new File(path, "app/views").toURI() != null) {
            templatesPath.add(new File(path,"app/views"));
        }
        if (new File(path, "conf/routes").exists() || (usePrecompiled && new File(appRoot, "precompiled/templates/from_module_" + name + "/conf/routes").exists())) {
            modulesRoutes.put(name, new File(path, "conf/routes"));
        }
        roots.add(path);
        if (!name.startsWith("_")) {
            Logger.info("Module %s is available (%s)", name, path.getAbsolutePath());
        }
    }

    /**
     * Search a VirtualFile in all loaded applications and plugins
     *
     * @param path Relative path from the applications root
     * @return The virtualFile or null
     */
    public static File getVirtualFile(String path) {
        for (File file : roots) {
            File vf = new File(file, path);
            if (vf.exists()) {
                return vf;
            }
        }
        return null;
    }

    /**
     * Search a Inputstream in the current application
     *
     * @param path Relative path from the application root
     * @return The Inputstream even if it doesn't exist
     */
    public static InputStream getResourceAsStream(String path) {
        if(StringUtils.isEmpty(path))
            return null;
        if(!path.startsWith("/"))
            path = "/"+path;
        return Play.class.getResourceAsStream(path);
    }

    public static URL getResource(String path) {
        if(StringUtils.isEmpty(path))
            return null;
        if(!path.startsWith("/"))
            path = "/"+path;
        return Play.class.getResource(path);
    }

    /**
     * Search a Inputstream in the current application
     *
     * @param path Relative path from the application root
     * @return The Inputstream even if it doesn't exist
     */

    /**
     * Search a File in the current application
     *
     * @param path Relative path from the application root
     * @return The file even if it doesn't exist
     */
    public static File getFile(String path) {
        return new File(applicationPath, path);
    }

    /**
     * Returns true if application is runing in test-mode.
     * Test-mode is resolved from the framework id.
     *
     * Your app is running in test-mode if the framwork id (Play.id)
     * is 'test' or 'test-?.*'
     * @return true if testmode
     */
    public static boolean runningInTestMode(){
        return id.matches("test|test-?.*");
    }
    

    /**
     * Call this method when there has been a fatal error that Play cannot recover from
     */
    public static void fatalServerErrorOccurred() {
        if (standalonePlayServer) {
            // Just quit the process
            System.exit(-1);
        } else {
            // Cannot quit the process while running inside an applicationServer
            String msg = "A fatal server error occurred";
            Logger.error(msg);
            throw new Error(msg);
        }
    }


    public static boolean useDefaultMockMailSystem() {
        return configuration.getProperty("mail.smtp", "").equals("mock") && mode == Mode.DEV;
    }

    // modifed by Arief Ardiyansah
    public static File classPath() {
        if (System.getProperty("precompile") != null || Play.usePrecompiled) {
            return Play.getFile("precompiled/java");
        }
        return new File(Play.tmpDir, "classes");
    }

    public static File getCompiledTemplate(String path) {
        path = path.replaceAll("\\{(.*)\\}", "from_$1").replace(':', '_').replace("..", "parent");
        if (System.getProperty("precompile") != null) {
            return Play.getFile("precompiled/templates/" + path);
        }
        return new File(Play.tmpDir, "templates/" + path);
    }
}
