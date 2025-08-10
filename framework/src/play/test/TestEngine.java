package play.test;

import org.junit.Assert;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import play.Play;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.Router;
import play.mvc.Scope.RenderArgs;
import play.utils.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Run application tests
 */
public class TestEngine {

    private static final class ClassNameComparator implements Comparator<Class> {
        @Override
        public int compare(Class aClass, Class bClass) {
            return aClass.getName().compareTo(bClass.getName());
        }
    }

    private static final ClassNameComparator classNameComparator = new ClassNameComparator();

    public static final ExecutorService functionalTestsExecutor = Executors.newSingleThreadExecutor();

    public static List<Class> allUnitTests() {
        List<Class> classes = new ArrayList<>();
        classes.addAll(Play.classes.getAssignableClasses(Assert.class));
        classes.addAll(Play.pluginCollection.getUnitTests());
        for (ListIterator<Class> it = classes.listIterator(); it.hasNext();) {
            Class c = it.next();
            if (Modifier.isAbstract(c.getModifiers())) {
                it.remove();
            } else {
                if (FunctionalTest.class.isAssignableFrom(c)) {
                    it.remove();
                }
            }
        }
        Collections.sort(classes, classNameComparator);
        return classes;
    }

    public static List<Class> allFunctionalTests() {
        List<Class> classes = new ArrayList<>();
        classes.addAll(Play.classes.getAssignableClasses(FunctionalTest.class));
        classes.addAll(Play.pluginCollection.getFunctionalTests());

        for (ListIterator<Class> it = classes.listIterator(); it.hasNext();) {
            if (Modifier.isAbstract(it.next().getModifiers())) {
                it.remove();
            }
        }
        Collections.sort(classes, classNameComparator);
        return classes;
    }

    public static List<String> seleniumTests(String testPath, List<String> results) {
        File testDir = Play.getFile(testPath);
        if (testDir.exists()) {
            scanForSeleniumTests(testDir, results);
        }
        return results;
    }

    public static List<String> allSeleniumTests() {
        List<String> results = new ArrayList<>();
        seleniumTests("test", results);
        for (File root : Play.roots) {
            seleniumTests(FileUtils.relativePath(root) + "/test", results);
        }
        Collections.sort(results);
        return results;
    }

    private static void scanForSeleniumTests(File dir, List<String> tests) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                scanForSeleniumTests(f, tests);
            } else if (f.getName().endsWith(".test.html")) {
                StringBuilder test = new StringBuilder(f.getName());
                while (!f.getParentFile().getName().equals("test")) {
                    test.insert(0, f.getParentFile().getName() + "/");
                    f = f.getParentFile();
                }
                tests.add(test.toString());
            }
        }
    }

    public static void initTest(Class<?> testClass) {
        CleanTest cleanTestAnnot = null;
        if(testClass != null ){
            cleanTestAnnot = testClass.getAnnotation(CleanTest.class) ;
        }
        if(cleanTestAnnot != null && cleanTestAnnot.removeCurrent()){
            if(Request.current != null){
                Request.current.remove();
            }
            if(Response.current != null){
                Response.current.remove();
            }
            if(RenderArgs.current != null){
                RenderArgs.current.remove();
            }
        }
        if (cleanTestAnnot == null || (cleanTestAnnot != null && cleanTestAnnot.createDefault())) {
            if (Request.current() == null) {
                // Use base URL to create a request for this host
                // host => with port
                // domain => without port
                String host = Router.getBaseUrl();
                String domain = null;
                int port = 80;
                boolean isSecure = false;
                if (host == null || host.equals("application.baseUrl")) {
                    host = "localhost:" + port;
                    domain = "localhost";
                } else if (host.contains("http://")) {
                    host = host.replaceAll("http://", "");
                } else if (host.contains("https://")) {
                    host = host.replaceAll("https://", "");
                    port = 443;
                    isSecure = true;
                }
                int colonPos =  host.indexOf(':');
                if(colonPos > -1){
                    domain = host.substring(0, colonPos);
                    port = Integer.parseInt(host.substring(colonPos+1));
                }else{
                    domain = host;
                }
                Request request = Request.createRequest(null, "GET", "/", "", null,
                        null, null, host, false, port, domain, isSecure, null, null);
                request.body = new ByteArrayInputStream(new byte[0]);
                Request.current.set(request);
            }

            if (Response.current() == null) {
                Response response = new Response();
                response.out = new ByteArrayOutputStream();
                response.direct = null;
                Response.current.set(response);
            }

            if (RenderArgs.current() == null) {
                RenderArgs renderArgs = new RenderArgs();
                RenderArgs.current.set(renderArgs);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static TestResults run(String name) {
        TestResults testResults = new TestResults();

            // Load test class
            Class testClass = Play.classes.loadClass(name);

            initTest(testClass);

            TestResults pluginTestResults = Play.pluginCollection.runTest(testClass);
            if (pluginTestResults != null) {
                return pluginTestResults;
            }

            JUnitCore junit = new JUnitCore();
            junit.addListener(new Listener(testClass.getName(), testResults));
            junit.run(testClass);

        return testResults;
    }

    // ~~~~~~ Run listener
    static class Listener extends RunListener {

        final TestResults results;
        TestResult current;
        final String className;

        public Listener(String className, TestResults results) {
            this.results = results;
            this.className = className;
        }

        @Override
        public void testStarted(Description description) throws Exception {
            current = new TestResult();
            current.name = description.getDisplayName().substring(0, description.getDisplayName().indexOf('('));
            current.time = System.currentTimeMillis();
        }

        @Override
        public void testFailure(Failure failure) throws Exception {

            if (current == null) {
                // The test probably failed before it could start, ie in @BeforeClass
                current = new TestResult();
                results.add(current); // must add it here since testFinished() never was called.
                current.name = "Before any test started, maybe in @BeforeClass?";
                current.time = System.currentTimeMillis();
            }

            if (failure.getException() instanceof AssertionError) {
                current.error = "Failure, " + failure.getMessage();
            } else {
                current.error = "A " + failure.getException().getClass().getName() + " has been caught, " + failure.getMessage();
            }
            current.trace = failure.getTrace();
            for (StackTraceElement stackTraceElement : failure.getException().getStackTrace()) {
                if (stackTraceElement.getClassName().equals(className)) {
                    current.sourceInfos = "In " + FileUtils.relativePath(Play.classes.getApplicationClass(className).javaFile) + ", line " + stackTraceElement.getLineNumber();
                    current.sourceCode = Play.classes.getApplicationClass(className).source().split("\n")[stackTraceElement.getLineNumber() - 1];
                    current.sourceFile = FileUtils.relativePath(Play.classes.getApplicationClass(className).javaFile);
                    current.sourceLine = stackTraceElement.getLineNumber();
                }
            }
            current.passed = false;
            results.passed = false;
        }

        @Override
        public void testFinished(Description description) throws Exception {
            current.time = System.currentTimeMillis() - current.time;
            results.add(current);
        }
    }

    public static class TestResults {

        public final List<TestResult> results = new ArrayList<>();
        public boolean passed = true;
        public int success = 0;
        public int errors = 0;
        public int failures = 0;
        public long time = 0;

        public void add(TestResult result) {
            time = result.time + time;
            this.results.add(result);
            if (result.passed) {
                success++;
            } else {
                if (result.error.startsWith("Failure")) {
                    failures++;
                } else {
                    errors++;
                }
            }
        }
    }

    public static class TestResult {

        public String name;
        public String error;
        public boolean passed = true;
        public long time;
        public String trace;
        public String sourceInfos;
        public String sourceCode;
        public String sourceFile;
        public int sourceLine;
    }
}