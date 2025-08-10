package play.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;

public abstract class NettyTransport {
    private static final int BACKLOG = 8192;

    public ServerBootstrap configure(EventLoopGroup acceptor, EventLoopGroup eventloop) {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.option(ChannelOption.SO_BACKLOG, BACKLOG);
        bootstrap.option(ChannelOption.SO_REUSEADDR, true);
        return bootstrap.group(acceptor, eventloop);
    }

    public abstract EventLoopGroup createEventLoop(int threads, String threadName, int ioRatio);

    public static NettyTransport transport(ClassLoader loader) {
        return nio();
    }

    private static NettyTransport nio() {
        return new JDKTransport();
    }

    private static class JDKTransport extends NettyTransport {
        @Override public EventLoopGroup createEventLoop(int threads, String threadName, int ioRatio) {
            NioEventLoopGroup loopGroup = new NioEventLoopGroup(threads, new DefaultThreadFactory(threadName));
            loopGroup.setIoRatio(ioRatio);
            return loopGroup;
        }

        @Override public ServerBootstrap configure(EventLoopGroup acceptor, EventLoopGroup eventloop) {
            return super.configure(acceptor, eventloop).channel(NioServerSocketChannel.class);
        }
    }
}
