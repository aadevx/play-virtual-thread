package play.server;

import io.netty.channel.ChannelHandlerContext;
import play.Invocation;
import play.InvocationContext;
import play.Logger;
import play.mvc.Http;
import play.mvc.WebSocketInvoker;

public class WebSocketInvocation extends Invocation {

    final Http.Inbound inbound;
    final Http.Outbound outbound;
    final Http.Request request;
    final ChannelHandlerContext ctx;

    public WebSocketInvocation(Http.Inbound inbound, Http.Outbound outbound, Http.Request request, ChannelHandlerContext ctx) {
        this.inbound = inbound;
        this.outbound = outbound;
        this.request = request;
        this.ctx = ctx;
    }

    @Override
    public boolean init() {
        Http.Request.current.set(request);
        Http.Inbound.current.set(inbound);
        Http.Outbound.current.set(outbound);
        return super.init();
    }

    @Override
    public InvocationContext getInvocationContext() {
        return new InvocationContext(Http.invocationType, request.invokedMethod.getAnnotations(),
                request.invokedMethod.getDeclaringClass().getAnnotations());
    }

    @Override
    public void execute() {
        WebSocketInvoker.invoke(request, inbound, outbound);
    }

    @Override
    public void onException(Throwable e) {
        Logger.error(e, "Internal Server Error in WebSocket (closing the socket) for request %s", request.method + " " + request.url);
        ctx.channel().close();
        super.onException(e);
    }

    @Override
    public void onSuccess() throws Exception {
        outbound.close();
        super.onSuccess();
    }
}
