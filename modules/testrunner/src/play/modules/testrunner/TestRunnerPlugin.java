package play.modules.testrunner;

import java.io.File;

import play.Play;
import play.PlayPlugin;
import play.mvc.Router;

public class TestRunnerPlugin extends PlayPlugin {

    @Override
    public void onLoad() {
        File appRoot = Play.applicationPath;
        Play.javaPath.add(new File(appRoot, "test"));
        for (File module : Play.modules.values()) {
            File modulePath = module;
            if (!modulePath.getAbsolutePath().startsWith(Play.frameworkPath.getAbsolutePath()) && !Play.javaPath.contains(new File(module, "test"))) {
                Play.javaPath.add(new File(module, "test"));
            }
        }
    }

    @Override
    public void onRoutesLoaded() {
        Router.addRoute("GET", "/@tests", "TestRunner.index");
        Router.addRoute("GET", "/@tests.list", "TestRunner.list");
        Router.addRoute("GET", "/@tests/{<.*>test}", "TestRunner.run");
        Router.addRoute("POST", "/@tests/{<.*>test}", "TestRunner.saveResult");
        Router.addRoute("GET", "/@tests/emails", "TestRunner.mockEmail");
        Router.addRoute("GET", "/@tests/cache", "TestRunner.cacheEntry");
    }

    @Override
    public void onApplicationReady() {
        String protocol = "http";
        String port = "9000";
        if(Play.configuration.getProperty("https.port") != null) {
            port = Play.configuration.getProperty("https.port");
            protocol = "https";
        } else if(Play.configuration.getProperty("http.port") != null) {
          port = Play.configuration.getProperty("http.port");
        }
        System.out.println("~");
        System.out.println("~ Go to "+protocol+"://localhost:" + port + "/@tests to run the tests");
        System.out.println("~");
    }
    
}
