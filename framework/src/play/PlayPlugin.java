package play;

import com.google.gson.JsonObject;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.data.binding.RootParamNode;
import play.db.Model;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.results.Result;
import play.templates.Template;
import play.test.BaseTest;
import play.test.TestEngine.TestResults;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;

import static java.util.Collections.emptyList;

/**
 * A framework plugin
 */
public abstract class PlayPlugin implements Comparable<PlayPlugin> {

    /**
     * Plugin priority (0 for highest priority)
     */
    public int index;

    /**
     * Called at plugin loading
     */
    public void onLoad() {
    }

    public boolean compileSources() {
        return false;
    }

    /**
     * Run a test class
     */
    public TestResults runTest(Class<BaseTest> clazz) {
        return null;
    }

    /**
     * Called when play need to bind a Java object from HTTP params.
     *
     * When overriding this method, do not call super impl.. super impl is calling old bind method
     * to be backward compatible.
     */
    public Object bind( RootParamNode rootParamNode, String name, Class<?> clazz, Type type, Annotation[] annotations) {
        // call old method to be backward compatible
        return null;
    }

    /**
     * Translate the given key for the given locale and arguments.
     * If null is returned, Play's normal message translation mechanism will be
     * used.
     */
    public String getMessage(String locale, Object key, Object... args) {
        return null;
    }

    /**
     * Return the plugin status
     */
    public String getStatus() {
        return null;
    }

    /**
     * Return the plugin status in JSON format
     */
    public JsonObject getJsonStatus() {
        return null;
    }

    /**
     * Enhance this class
     * @param applicationClass
     * @throws java.lang.Exception
     */
    public void enhance(ApplicationClass applicationClass) throws Exception {
    }


    /**
     * Give a chance to this plugin to fully manage this request
     * @param request The Play request
     * @param response The Play response
     * @return true if this plugin has managed this request
     */
    public boolean rawInvocation(Request request, Response response) throws Exception {
        return false;
    }

    public Template loadTemplate(File file) {
        return null;
    }

    /**
     * It's time for the plugin to detect changes.
     * Throw an exception is the application must be reloaded.
     */
    public void detectChange() {
    }


    /**
     * Called at application start (and at each reloading)
     * Time to start stateful things.
     */
    public void onApplicationStart() {
    }

    /**
     * Called after the application start.
     */
    public void afterApplicationStart() {
    }

    /**
     * Called at application stop (and before each reloading)
     * Time to shutdown stateful things.
     */
    public void onApplicationStop() {
    }

    /**
     * Called before a Play! invocation.
     * Time to prepare request specific things.
     */
    public void beforeInvocation() {
    }

    /**
     * Called after an invocation.
     * (unless an exception has been thrown).
     * Time to close request specific things.
     */
    public void afterInvocation() {
    }

    /**
     * Called if an exception occurred during the invocation.
     * @param e The caught exception.
     */
    public void onInvocationException(Throwable e) {
    }

    /**
     * Called at the end of the invocation.
     * (even if an exception occurred).
     * Time to close request specific things.
     */
    public void invocationFinally() {
    }

    /**
     * Called before an 'action' invocation,
     * ie an HTTP request processing.
     */
    public void beforeActionInvocation(Method actionMethod) {
    }

    /**
     * Called when the action method has thrown a result.
     * @param result The result object for the request.
     */
    public void onActionInvocationResult(Result result) {
    }

    public void onInvocationSuccess() {
    }

    /**
     * Called at the end of the action invocation.
     */
    public void afterActionInvocation() {
    }

    /**
     * Called at the end of the action invocation (either in case of success or any failure).
     */
    public void onActionInvocationFinally() {
    }

    /**
     * Called when the application.conf has been read.
     */
    public void onConfigurationRead() {
    }

    /**
     * Called after routes loading.
     */
    public void onRoutesLoaded() {
    }


    /**
     * Let some plugins route themself
     * @param request
     */
    public void routeRequest(Request request) {
    }

    public Model.Factory modelFactory(Class<? extends Model> modelClass) {
        return null;
    }

    public void afterFixtureLoad() {
    }

    public void onApplicationReady() {
    }

    @Override
    public int compareTo(PlayPlugin o) {
        int res = index < o.index ? -1 : (index == o.index ? 0 : 1);
        if (res != 0) {
            return res;
        }

        // index is equal in both plugins.
        // Sort on class type to get consistent order
        res = this.getClass().getName().compareTo(o.getClass().getName());
        if (res != 0) {
            // classnames where different
            return res;
        }

        // Identical classnames.
        // Sort on instance to get consistent order.
        // We only return 0 (equal) if both identityHashCode are identical
        // which is only the case if both this and other are the same object instance.
        // This is consistent with equals() when no special equals-method is implemented.
        int thisHashCode = System.identityHashCode(this);
        int otherHashCode = System.identityHashCode(o);
        return (thisHashCode < otherHashCode ? -1 : (thisHashCode == otherHashCode ? 0 : 1));
    }


    /**
     * Implement to add some classes that should be considered unit tests but do not extend
     * {@link org.junit.Assert} to tests that can be executed by test runner (will be visible in test UI).
     * <p>
     * <strong>Note:</strong>You probably will also need to override {@link PlayPlugin#runTest(java.lang.Class)} method
     * to handle unsupported tests execution properly.
     * <p>
     * Keep in mind that this method can only add tests to currently loaded ones.
     * You cannot disable tests this way. You should also make sure you do not duplicate already loaded tests.
     * 
     * @return list of plugin supported unit test classes (empty list in default implementation)
     */
    public Collection<Class> getUnitTests() {
        return emptyList();
    }

    /**
     * Implement to add some classes that should be considered functional tests but do not extend
     * {@link play.test.FunctionalTest} to tests that can be executed by test runner (will be visible in test UI).
     * <p>
     * <strong>Note:</strong>You probably will also need to override {@link PlayPlugin#runTest(java.lang.Class)} method
     * to handle unsupported tests execution properly.
     * <p>
     * Keep in mind that this method can only add tests to currently loaded ones.
     * You cannot disable tests this way. You should also make sure you do not duplicate already loaded tests.
     *
     * @return list of plugin supported functional test classes (empty list in default implementation)
     */
    public Collection<Class> getFunctionalTests() {
        return emptyList();
    }


}
