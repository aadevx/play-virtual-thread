package play.jobs;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.exceptions.PlayException;
import play.exceptions.UnexpectedException;
import play.inject.Injector;
import play.libs.CronExpression;
import play.libs.Expression;
import play.libs.Time;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;

public class JobsPlugin extends PlayPlugin {

    public static final List<Job> scheduledJobs = new ArrayList<>();
    public static ScheduledThreadPoolExecutor jobExecutor;

    @Override
    public String getStatus() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.println("<h6>Jobs execution pool</h6>");
        out.println("<table class=\"table table-sm table-striped\">");
        if (jobExecutor == null) {
            out.println("(not yet started)");
            out.println("<tr><td>(not yet started)</td></tr>");
            return sw.toString();
        }
        out.println("<tr><td>Pool size</td><td>"+jobExecutor.getPoolSize()+"</td></tr>");
        out.println("<tr><td>Active count</td><td>"+jobExecutor.getActiveCount()+"</td></tr>");
        out.println("<tr><td>Scheduled task count</td><td>"+jobExecutor.getTaskCount()+"</td></tr>");
        out.println("<tr><td>Queue size</td><td>"+jobExecutor.getQueue().size()+"</td></tr>");
        SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        if (!scheduledJobs.isEmpty()) {
            out.println("<tr><td>Scheduled jobs (" + scheduledJobs.size() + ")</td><td>");
            for (Job job : scheduledJobs) {
                out.print(job);
                if (job.getClass().isAnnotationPresent(OnApplicationStart.class)
                        && !(job.getClass().isAnnotationPresent(On.class) || job.getClass().isAnnotationPresent(Every.class))) {
                    OnApplicationStart appStartAnnotation = job.getClass().getAnnotation(OnApplicationStart.class);
                    out.print(" run at application start" + (appStartAnnotation.async() ? " (async)" : "") + ".");
                }

                if (job.getClass().isAnnotationPresent(On.class)) {

                    String cron = job.getClass().getAnnotation(On.class).value();
                    if (cron != null && cron.startsWith("cron.")) {
                        cron = Play.configuration.getProperty(cron);
                    }
                    out.print(" run with cron expression " + cron + ".");
                }
                if (job.getClass().isAnnotationPresent(Every.class)) {
                    out.print(" run every " + job.getClass().getAnnotation(Every.class).value() + ".");
                }
                if (job.lastRun > 0) {
                    out.print(" (last run at " + df.format(new Date(job.lastRun)));
                    if (job.wasError) {
                        out.print(" with error)");
                    } else {
                        out.print(")");
                    }
                } else {
                    out.print(" (has never run)");
                }
                out.println("</br>");
            }
            out.println("</td></tr>");
        }
        if (!jobExecutor.getQueue().isEmpty()) {
            out.println("<tr><td>Waiting jobs</td><td>");
            ScheduledFuture[] q = jobExecutor.getQueue().toArray(new ScheduledFuture[0]);

            for (ScheduledFuture task : q) {
                out.println(extractUnderlyingCallable((FutureTask<?>) task) + " will run in " + task.getDelay(TimeUnit.SECONDS)
                        + " seconds </br>");
            }
            out.println("</td></tr>");
        }
        out.println("</table>");
        return sw.toString();
    }

    @Override
    public void afterApplicationStart() {
        List<Class> jobs = new ArrayList<>();
        jobs.addAll(Play.classes.getAssignableClasses(Job.class));
        for (Class<?> clazz : jobs) {
            // @OnApplicationStart
            if (clazz.isAnnotationPresent(OnApplicationStart.class)) {
                // check if we're going to run the job sync or async
                OnApplicationStart appStartAnnotation = clazz.getAnnotation(OnApplicationStart.class);
                if (!appStartAnnotation.async()) {
                    // run job sync
                    try {
                        Job<?> job = createJob(clazz);
                        job.run();
                        if (job.wasError) {
                            if (job.lastException != null) {
                                throw job.lastException;
                            }
                            throw new RuntimeException("@OnApplicationStart Job has failed");
                        }
                    } catch (InstantiationException | IllegalAccessException  e) {
                        throw new UnexpectedException("Job could not be instantiated", e);
                    } catch (Throwable ex) {
                        if (ex instanceof PlayException e) {
                            throw e;
                        }
                        throw new UnexpectedException(ex);
                    }
                } else {
                    // run job async
                    try {
                        Job<?> job = createJob(clazz);
                        // start running job now in the background
                        @SuppressWarnings("unchecked")
                        Callable<Job> callable = (Callable<Job>) job;
                        jobExecutor.submit(callable);
                    } catch (InstantiationException | IllegalAccessException ex) {
                        throw new UnexpectedException("Cannot instantiate Job " + clazz.getName(), ex);
                    }
                }
            }

            // @On
            if (clazz.isAnnotationPresent(On.class)) {
                try {
                    Job<?> job = createJob(clazz);
                    scheduleForCRON(job);
                } catch (InstantiationException | IllegalAccessException ex) {
                    throw new UnexpectedException("Cannot instantiate Job " + clazz.getName(), ex);
                }
            }
            // @Every
            if (clazz.isAnnotationPresent(Every.class)) {
                try {
                    Job job = createJob(clazz);
                    String value = clazz.getAnnotation(Every.class).value();
                    if (value.startsWith("cron.")) {
                        value = Play.configuration.getProperty(value);
                    }
                    value = Expression.evaluate(value, value).toString();
                    if (!"never".equalsIgnoreCase(value)) {
                        jobExecutor.scheduleWithFixedDelay(job, Time.parseDuration(value), Time.parseDuration(value), TimeUnit.SECONDS);
                    }
                } catch (InstantiationException | IllegalAccessException ex) {
                    throw new UnexpectedException("Cannot instantiate Job " + clazz.getName(), ex);
                }
            }
        }
    }

    private Job<?> createJob(Class<?> clazz) throws InstantiationException, IllegalAccessException {
        Job<?> job = (Job<?>) Injector.getBeanOfType(clazz);
        if (!job.getClass().equals(clazz)) {
            throw new RuntimeException("Enhanced job are not allowed: " + clazz.getName() + " vs. " + job.getClass().getName());
        }
        scheduledJobs.add(job);
        Injector.scanObject(job);
        return job;
    }



    @Override
    public void onApplicationStart() {
        jobExecutor = new ScheduledThreadPoolExecutor(0, Thread.ofVirtual().name("job").factory());
        jobExecutor.setRemoveOnCancelPolicy(true);
        scheduledJobs.clear();
    }

    public static <V> void scheduleForCRON(Job<V> job) {
        if (!job.getClass().isAnnotationPresent(On.class)) {
            return;
        }
        String cron = job.getClass().getAnnotation(On.class).value();
        if (cron.startsWith("cron.")) {
            cron = Play.configuration.getProperty(cron, "");
        }
        cron = Expression.evaluate(cron, cron).toString();
        if (cron == null || cron.isEmpty() || "never".equalsIgnoreCase(cron)) {
            Logger.info("Skipping job %s, cron expression is not defined", job.getClass().getName());
            return;
        }
        try {
            Date now = new Date();
            cron = Expression.evaluate(cron, cron).toString();
            CronExpression cronExp = new CronExpression(cron);
            Date nextDate = cronExp.getNextValidTimeAfter(now);
            if (nextDate == null) {
                Logger.warn("The cron expression for job %s doesn't have any match in the future, will never be executed",
                        job.getClass().getName());
                return;
            }
            if (nextDate.equals(job.nextPlannedExecution)) {
                // Bug #13: avoid running the job twice for the same time
                // (happens when we end up running the job a few minutes before
                // the planned time)
                Date nextInvalid = cronExp.getNextInvalidTimeAfter(nextDate);
                nextDate = cronExp.getNextValidTimeAfter(nextInvalid);
            }
            job.nextPlannedExecution = nextDate;
            jobExecutor.schedule((Callable<V>) job, nextDate.getTime() - now.getTime(), TimeUnit.MILLISECONDS);
            job.executor = jobExecutor;
        } catch (Exception ex) {
            throw new UnexpectedException(ex);
        }
    }

    @Override
    public void onApplicationStop() {

        List<Class> jobs = Play.classes.getAssignableClasses(Job.class);

        for (Class clazz : jobs) {
            // @OnApplicationStop
            if (clazz.isAnnotationPresent(OnApplicationStop.class)) {
                try {
                    Job<?> job = createJob(clazz);
                    job.run();
                    if (job.wasError) {
                        if (job.lastException != null) {
                            throw job.lastException;
                        }
                        throw new RuntimeException("@OnApplicationStop Job has failed");
                    }
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new UnexpectedException("Job could not be instantiated", e);
                } catch (Throwable ex) {
                    if (ex instanceof PlayException e) {
                        throw e;
                    }
                    throw new UnexpectedException(ex);
                }
            }
        }
        jobExecutor.shutdownNow();
        jobExecutor.getQueue().clear();
    }

    /**
     * Try to discover what is hidden under a FutureTask (hack)
     */
    public static Object extractUnderlyingCallable(FutureTask<?> futureTask) {
        try {
            Object callable = null;
            // Try to search for the Filed sync first, if not present will try filed callable
            try {
                Field syncField = FutureTask.class.getDeclaredField("sync");
                syncField.setAccessible(true);
                Object sync = syncField.get(futureTask);
                if (sync != null) {
                    Field callableField = sync.getClass().getDeclaredField("callable");
                    callableField.setAccessible(true);
                    callable = callableField.get(sync);
                }
            } catch (NoSuchFieldException ex) {
                Field callableField = FutureTask.class.getDeclaredField("callable");
                callableField.setAccessible(true);
                callable = callableField.get(futureTask);
            }
            if (callable != null && callable.getClass().getSimpleName().equals("RunnableAdapter")) {
                Field taskField = callable.getClass().getDeclaredField("task");
                taskField.setAccessible(true);
                return taskField.get(callable);
            }
            return callable;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
