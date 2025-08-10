package play.server.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodecFactory;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import play.server.PlayHandler;

import java.util.concurrent.ExecutorService;

/**
 * Sets up the Netty pipeline for the example server. Depending on the endpoint config, sets up the
 * pipeline for NPN or cleartext HTTP upgrade to HTTP/2.
 */
public class Http2ServerInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;
    private final ExecutorService executorService;
    private final Http2ServerUpgradeCodec upgradeCodecFactory;

    public Http2ServerInitializer(SslContext sslCtx, ExecutorService executorService) {
        this.sslCtx = sslCtx;
        this.executorService = executorService;
        this.upgradeCodecFactory = new Http2ServerUpgradeCodec(
                Http2FrameCodecBuilder.forServer().build(), new Http2MultiplexHandler(new Http2Handler(true, executorService)));
    }

    @Override
    public void initChannel(SocketChannel ch) {
        if (sslCtx != null) {
            configureSsl(ch);
        } else {
            configureClearText(ch);
        }
    }

    /**
     * Configure the pipeline for TLS NPN negotiation to HTTP/2.
     */
    private void configureSsl(SocketChannel ch) {
        boolean secure = sslCtx != null;
        ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()), new Http2OrHttpHandler(secure, executorService));
    }

    /**
     * Configure the pipeline for a cleartext upgrade from HTTP to HTTP/2.0
     */
    private void configureClearText(SocketChannel ch) {
        final ChannelPipeline p = ch.pipeline();
        boolean secure = sslCtx != null;
        final HttpServerCodec sourceCodec = new HttpServerCodec();
        p.addLast(sourceCodec);
        p.addLast(new ChunkedWriteHandler());
        p.addLast(new HttpServerUpgradeHandler(sourceCodec,  protocol -> {
            if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol))
                return upgradeCodecFactory;
            else return null;
        }));
        p.addLast(new SimpleChannelInboundHandler<HttpMessage>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, HttpMessage msg) throws Exception {
                // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
                ChannelPipeline pipeline = ctx.pipeline();
                pipeline.addAfter(ctx.name(), null, new PlayHandler(secure, executorService));
                pipeline.replace(this, null, new HttpObjectAggregator(Integer.MAX_VALUE));
                ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
            }
        });
    }
}