package play.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class HttpServerInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;
    private final ExecutorService executorService;

    public HttpServerInitializer(SslContext sslCtx, ExecutorService executorService) {
        this.sslCtx = sslCtx;
        this.executorService = executorService;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        if (sslCtx != null) {
            p.addLast(sslCtx.newHandler(ch.alloc()));
        }
        boolean secure = sslCtx != null;
        p.addLast(new HttpServerCodec());
        p.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
        p.addLast(new ChunkedWriteHandler());
        p.addLast(new PlayHandler(secure, executorService));
    }

}

