package play.jobs;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;
import play.Invocation;
import play.InvocationContext;
import play.Logger;
import play.Play;
import play.db.DB;
import play.exceptions.JavaExecutionException;
import play.exceptions.PlayException;
import play.exceptions.UnexpectedException;
import play.inject.Injector;
import play.libs.Time;

import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A job is an asynchronously executed unit of work
 *
 * @param <V>
 *            The job result type (if any)
 */
public class Job<V> extends Invocation implements Callable<V> {

    public static final String invocationType = "Job";

    protected ExecutorService executor;
    protected long lastRun = 0;
    protected boolean wasError = false;
    protected Throwable lastException = null;

    Date nextPlannedExecution = null;

    @Override
    public InvocationContext getInvocationContext() {
        return new InvocationContext(invocationType, this.getClass().getAnnotations());
    }

    /**
     * Here you do the job
     *
     * @throws Exception
     *             if problems occurred
     */
    public void doJob() throws Exception {
    }

    /**
     * Here you do the job and return a result
     *
     * @return The job result
     * @throws Exception
     *             if problems occurred
     */
    public V doJobWithResult() throws Exception {
        doJob();
        return null;
    }

    @Override
    public void execute() throws Exception {

    }

    /**
     * Start this job now (well ASAP)
     *
     * @return the job completion
     */
    public CompletableFuture<V> now() {
        CompletableFuture<V> smartFuture = new CompletableFuture<>();
        Injector.scanObject(this);
        JobsPlugin.jobExecutor.submit(getJobCallingCallable(smartFuture));
        return smartFuture;
    }

    /**
     * Start this job in several seconds
     *
     * @param delay
     *            time in seconds
     * @return the job completion
     */
    public CompletableFuture<V> in(String delay) {
        return in(Time.parseDuration(delay));
    }

    /**
     * Start this job in several seconds
     *
     * @param seconds
     *            time in seconds
     * @return the job completion
     */
    public CompletableFuture<V> in(int seconds) {
        CompletableFuture<V> smartFuture = new CompletableFuture<>();
        Injector.scanObject(this);
        JobsPlugin.jobExecutor.schedule(getJobCallingCallable(smartFuture), seconds, TimeUnit.SECONDS);
        return smartFuture;
    }

    private Callable<V> getJobCallingCallable(final CompletableFuture<V> smartFuture) {
        return () -> {
            try {
                V result = Job.this.call();
                if (smartFuture != null) {
                    smartFuture.complete(result);
                }
                return result;
            } catch (Exception e) {
                if (smartFuture != null) {
                    smartFuture.completeExceptionally(e);
                }
                return null;
            }
        };
    }

    /**
     * Run this job every n seconds
     *
     * @param delay
     *            time in seconds
     */
    public void every(String delay) {
        every(Time.parseDuration(delay));
    }

    /**
     * Run this job every n seconds
     *
     * @param seconds
     *            time in seconds
     */
    public void every(int seconds) {
        Injector.scanObject(this);
        JobsPlugin.jobExecutor.scheduleWithFixedDelay(this, seconds, seconds, TimeUnit.SECONDS);
        JobsPlugin.scheduledJobs.add(this);
    }

    // Customize Invocation
    @Override
    public void onException(Throwable e) {
        wasError = true;
        lastException = e;
        try {
            super.onException(e);
        } catch (Throwable ex) {
            Logger.error(ex, "Error during job execution (%s)", this);
            throw new UnexpectedException(unwrap(e));
        }
    }

    private Throwable unwrap(Throwable e) {
        while ((e instanceof PlayException) && e.getCause() != null) {
            e = e.getCause();
        }
        return e;
    }

    @Override
    public void run() {
        call();
    }

    @Override
    public V call() {
        Logger.debug("Job %s started at %s", getClass(), new Date());
        Monitor monitor = null;
        try {
            if (init()) {
                before();
                V result = null;

                try {
                    lastException = null;
                    lastRun = System.currentTimeMillis();
                    monitor = MonitorFactory.start(this + ".doJob()");

                    // If we have a plugin, get him to execute the job within the filter.
                    final AtomicBoolean executed = new AtomicBoolean(false);
                    result = DB.withinFilter(() -> {
                        executed.set(true);
                        return doJobWithResult();
                    });
                    // No filter function found => we need to execute anyway( as before the use of withinFilter )
                    if (!executed.get()) {
                        result = doJobWithResult();
                    }

                    monitor.stop();
                    monitor = null;
                    wasError = false;
                } catch (PlayException e) {
                    throw e;
                } catch (Exception e) {
                    StackTraceElement element = PlayException.getInterestingStackTraceElement(e);
                    if (element != null) {
                        throw new JavaExecutionException(Play.classes.getApplicationClass(element.getClassName()), element.getLineNumber(),
                                e);
                    }
                    throw e;
                }
                after();
                return result;
            }
        } catch (Throwable e) {
            onException(e);
        } finally {
            if (monitor != null) {
                monitor.stop();
            }
            _finally();
        }
        return null;
    }

    @Override
    public void _finally() {
        super._finally();
        if (executor == JobsPlugin.jobExecutor) {
            JobsPlugin.scheduleForCRON(this);
        }
    }

    @Override
    public String toString() {
        return this.getClass().getName();
    }

}