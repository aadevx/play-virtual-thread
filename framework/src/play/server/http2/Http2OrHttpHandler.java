package play.server.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import play.server.PlayHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Negotiates with the browser if HTTP2 or HTTP is going to be used. Once decided, the Netty
 * pipeline is setup with the correct handlers for the selected protocol.
 */
public class Http2OrHttpHandler extends ApplicationProtocolNegotiationHandler {

//    private static final int MAX_CONTENT_LENGTH = 1024 * 100;
    private final boolean secure;
    private final ExecutorService executorService;

    protected Http2OrHttpHandler(boolean secure, ExecutorService executorService) {
        super(ApplicationProtocolNames.HTTP_1_1);
        this.secure = secure;
        this.executorService = executorService;
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
            ctx.pipeline().addLast(Http2FrameCodecBuilder.forServer().build());
            ctx.pipeline().addLast(new ChunkedWriteHandler(), new Http2MultiplexHandler(new Http2Handler(secure, executorService)));
            return;
        }

        if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
            ctx.pipeline().addLast(new HttpServerCodec(), new HttpObjectAggregator(Integer.MAX_VALUE), new ChunkedWriteHandler(), new PlayHandler(secure, executorService));
            return;
        }

        throw new IllegalStateException("unknown protocol: " + protocol);
    }
}
