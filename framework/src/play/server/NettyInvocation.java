package play.server;

import play.Invocation;
import play.InvocationContext;
import play.Logger;
import play.Play;
import play.data.binding.CachedBoundActionMethodArgs;
import play.mvc.Http;
import play.mvc.Scope;

public class NettyInvocation extends Invocation {
    private final Context context;

    public NettyInvocation(Context context) {
        this.context = context;
    }

    @Override
   public boolean init() {
        if (Logger.isTraceEnabled()) {
            Logger.trace("init: begin");
        }
        Thread.currentThread().setContextClassLoader(Play.classloader);
        Http.Request request = context.request();
        Http.Request.current.set(request);
        Http.Response.current.set(context.response());
        Scope.Params.current.set(request.params);
        Scope.RenderArgs.current.remove();
        Scope.Session.current.remove();
        Scope.Flash.current.remove();
        CachedBoundActionMethodArgs.init();
        super.init();
        if (Logger.isTraceEnabled()) {
            Logger.trace("init: end true");
        }
        return true;
    }

    @Override
    public InvocationContext getInvocationContext() {
        return context.getInvocationContext();
    }

    @Override
    public void run() {
        if (Logger.isTraceEnabled()) {
            Logger.trace("run: begin");
        }
        try {
            super.run();
        }catch (Exception e) {
            context.serve500(e);
        } finally {
            context.release();
        }
        if (Logger.isTraceEnabled()) {
            Logger.trace("run: end");
        }
    }

    @Override
    public void execute() throws Exception {
        context.execute();
    }

    @Override
    public void onSuccess() throws Exception {
        super.onSuccess();
        context.writeResponse();
        if (Logger.isTraceEnabled()) {
            Logger.trace("execute: end");
        }
    }
}
