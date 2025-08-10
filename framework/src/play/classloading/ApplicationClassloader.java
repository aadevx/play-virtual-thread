package play.classloading;

import play.Logger;
import play.Play;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.exceptions.UnexpectedException;
import play.libs.IO;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The application classLoader. Load the classes from the application Java sources files.
 */
public class ApplicationClassloader extends ClassLoader {

    /**
     * This protection domain applies to all loaded classes.
     */
    public ProtectionDomain protectionDomain;

    public ApplicationClassloader() {
        super(ApplicationClassloader.class.getClassLoader());
        // Clean the existing classes
        for (ApplicationClass applicationClass : Play.classes.all()) {
            applicationClass.uncompile();
        }
        try {
            CodeSource codeSource = new CodeSource(new URL("file:" + Play.applicationPath.getAbsolutePath()), (Certificate[]) null);
            Permissions permissions = new Permissions();
            permissions.add(new AllPermission());
            protectionDomain = new ProtectionDomain(codeSource, permissions);
        } catch (MalformedURLException e) {
            throw new UnexpectedException(e);
        }
    }

    private final Lock readLock = new ReentrantLock();

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Look up our cache
        Class<?> c = findLoadedClass(name);
        if (c != null) {
            return c;
        }

        try {
            readLock.lock();
            // First check if it's an application Class
            Class<?> applicationClass = loadApplicationClass(name);
            if (applicationClass != null) {
                if (resolve) {
                    resolveClass(applicationClass);
                }
                return applicationClass;
            }
        } finally {
            readLock.unlock();
        }
        // Delegate to the classic classloader
        return super.loadClass(name, resolve);
    }

    public Class<?> loadApplicationClass(String name) {

        if (ApplicationClass.isClass(name)) {
            Class maybeAlreadyLoaded = findLoadedClass(name);
            if (maybeAlreadyLoaded != null) {
                return maybeAlreadyLoaded;
            }
        }

        if (Play.usePrecompiled) {
            try {
                File file = Play.classes.getClassFile(name);
                if (!file.exists()) {
                    return null;
                }
                Class<?> clazz = findLoadedClass(name);
                if (clazz == null) {
                    if (name.endsWith("package-info")) {
                        definePackage(getPackageName(name), null, null, null, null, null, null, null);
                    } else {
                        loadPackage(name);
                    }
                    byte[] code = IO.readContent(file);
                    clazz = defineClass(name, code, 0, code.length, protectionDomain);
                }
                ApplicationClass applicationClass = Play.classes.getApplicationClass(name);
                if (applicationClass != null) {
                    applicationClass.javaClass = clazz;
                }
                return clazz;
            } catch (Exception e) {
                throw new RuntimeException("Cannot find precompiled class file for " + name, e);
            }
        }

        long start = System.currentTimeMillis();
        ApplicationClass applicationClass = Play.classes.getApplicationClass(name);
        if (applicationClass != null) {
            if (applicationClass.isDefinable()) {
                return applicationClass.javaClass;
            }
            if (Logger.isTraceEnabled()) {
                Logger.trace("Compiling code for %s", name);
            }
            if (!applicationClass.isClass()) {
                definePackage(applicationClass.getPackage(), null, null, null, null, null, null, null);
            } else {
                loadPackage(name);
            }
            if (applicationClass.javaByteCode != null || applicationClass.compile() != null) {
                applicationClass.enhance();
                applicationClass.javaClass = defineClass(applicationClass.name, applicationClass.enhancedByteCode, 0,
                        applicationClass.enhancedByteCode.length, protectionDomain);
                resolveClass(applicationClass.javaClass);
                if (Logger.isTraceEnabled()) {
                    Logger.trace("%sms to load class %s", System.currentTimeMillis() - start, name);
                }

                return applicationClass.javaClass;
            }
            Play.classes.classes.remove(name);
        }
        return null;
    }

    private String getPackageName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > -1 ? name.substring(0, dot) : "";
    }

    private void loadPackage(String className) {
        // find the package class name
        int symbol = className.indexOf("$");
        if (symbol > -1) {
            className = className.substring(0, symbol);
        }
        symbol = className.lastIndexOf(".");
        if (symbol > -1) {
            className = className.substring(0, symbol) + ".package-info";
        } else {
            className = "package-info";
        }
        if (this.findLoadedClass(className) == null) {
            this.loadApplicationClass(className);
        }
    }

    /**
     * Search for the byte code of the given class.
     */
    byte[] getClassDefinition(String name) {
        name = name.replace('.', '/') + ".class";
        try (InputStream is = this.getResourceAsStream(name)){
            if (is == null) {
                return null;
            }
            return IO.readContent(is);
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        for (File vf : Play.javaPath) {
            File res = new File(vf, name);
            if (res != null && res.exists()) {
                try {
                    return new FileInputStream(res);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }
//        URL url = getResource(name);
        return super.getResourceAsStream(name);
    }

    @Override
    public URL getResource(String name) {
        URL url = null;
        for (File vf : Play.javaPath) {
            File res = new File(vf, name);
            if (res != null && res.exists()) {
                try {
                    url = res.toURI().toURL();
                    break;
                } catch (MalformedURLException ex) {
                    throw new UnexpectedException(ex);
                }
            }
        }
        if (url == null) {
            url = super.getResource(name);
            if (url != null) {
                try {
                    File file = new File(url.toURI());
                    String fileName = file.getCanonicalFile().getName();
                    if (!name.endsWith(fileName)) {
                        url = null;
                    }
                } catch (Exception ignore) {
                }
            }
        }
        return url;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> urls = new ArrayList<>();
        for (File vf : Play.javaPath) {
            File res = new File(vf, name);
            if (res != null && res.exists()) {
                try {
                    urls.add(res.toURI().toURL());
                } catch (MalformedURLException ex) {
                    throw new UnexpectedException(ex);
                }
            }
        }
        Enumeration<URL> parent = super.getResources(name);
        while (parent.hasMoreElements()) {
            URL next = parent.nextElement();
            if (!urls.contains(next)) {
                urls.add(next);
            }
        }
        final Iterator<URL> it = urls.iterator();
        return new Enumeration<>() {

            @Override
            public boolean hasMoreElements() {
                return it.hasNext();
            }

            @Override
            public URL nextElement() {
                return it.next();
            }
        };
    }

    public List<Class> getAssignableClasses(Class clazz) {
        return Play.classes.getAssignableClasses(clazz);
    }
}
