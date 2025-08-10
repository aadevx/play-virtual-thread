package play;

import play.classloading.enhancers.LocalvariablesNamesEnhancer;
import play.db.DB;
import play.exceptions.PlayException;
import play.exceptions.UnexpectedException;
import play.i18n.Lang;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An Invocation in something to run in a Play! context
 */
public abstract class Invocation implements Runnable {

    /**
     * If set, monitor the time the invocation waited in the queue
     */
//    Monitor waitInQueue;

    /**
     * Override this method
     * @throws java.lang.Exception
     */
    public abstract void execute() throws Exception;


    /**
     * Needs this method to do stuff *before* init() is executed.
     * The different Invocation-implementations does a lot of stuff in init()
     * and they might do it before calling super.init()
     */
    protected void preInit() {
        // clear language for this request - we're resolving it later when it is needed
        Lang.clear();
    }

    /**
     * Init the call (especially useful in DEV mode to detect changes)
     */
    public boolean init() {
        Thread.currentThread().setContextClassLoader(Play.classloader);
        Play.detectChanges();
        if (!Play.started) {
            if (Play.mode.isProd()) {
                throw new UnexpectedException("Application is not started");
            }
            Play.start();
        }
        InvocationContext.current.set(getInvocationContext());
        return true;
    }


    public abstract InvocationContext getInvocationContext();

    /**a
     * Things to do before an Invocation
     */
    public void before() {
        Thread.currentThread().setContextClassLoader(Play.classloader);
        Play.pluginCollection.beforeInvocation();
    }

    /**
     * Things to do after an Invocation.
     * (if the Invocation code has not thrown any exception)
     */
    public void after() {
        Play.pluginCollection.afterInvocation();
        LocalvariablesNamesEnhancer.LocalVariablesNamesTracer.checkEmpty(); // detect bugs ....
    }

    /**
     * Things to do when the whole invocation has succeeded (before + execute + after)
     */
    public void onSuccess() throws Exception {
        Play.pluginCollection.onInvocationSuccess();
    }

    /**
     * Things to do if the Invocation code thrown an exception
     */
    public void onException(Throwable e) {
        Play.pluginCollection.onInvocationException(e);
        if (e instanceof PlayException x) {
            throw x;
        }
        throw new UnexpectedException(e);
    }

    /**
     * Things to do in all cases after the invocation.
     */
    public void _finally() {
        Play.pluginCollection.invocationFinally();
        InvocationContext.current.remove();
    }

    /**
     * It's time to execute.
     */
    @Override
    public void run() {
//        if (waitInQueue != null) {
//            waitInQueue.stop();
//        }
        try {
            preInit();
            if (init()) {
                before();
                final AtomicBoolean executed = new AtomicBoolean(false);
                DB.withinFilter(() -> {
                    executed.set(true);
                    execute();
                    return null;
                });
                // No filter function found => we need to execute anyway( as before the use of withinFilter )
                if (!executed.get()) {
                    execute();
                }
                after();
                onSuccess();
            }
        }catch (Throwable e) {
            onException(e);
        } finally {
            _finally();
        }
    }
}
