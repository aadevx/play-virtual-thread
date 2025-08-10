package play.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.websocketx.*;
import org.apache.commons.lang3.StringUtils;
import play.Logger;
import play.Play;
import play.mvc.Http;
import play.mvc.Router;
import play.mvc.results.NotFound;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.*;

public class PlayHandler extends ChannelInboundHandlerAdapter {
    private static final Pattern PATTERN_REMOTEADDR = Pattern.compile("/[0-9]+[.][0-9]+[.][0-9]+[.][0-9]+[:][0-9]+");
    private static final Pattern PATTERN_LOOPBACK = Pattern.compile("^127\\.0\\.0\\.1:?[0-9]*$");
    private static final Pattern PATTERN_REMOTEADDR2 = Pattern.compile(".*[%].*");
    private static final String FORBIDDEN_MSG = "Failure: "+ HttpResponseStatus.FORBIDDEN+ "\r\n";

    private WebSocketServerHandshaker handshaker;
    private final boolean secure;
    private final ExecutorService executor;

    public static final Map<ChannelHandlerContext, Http.Inbound> channels = new ConcurrentHashMap<>();

    public PlayHandler(boolean secure, ExecutorService executor) {
        this.secure = secure;
        this.executor = executor;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest nettyRequest) {
            try {
                if (nettyRequest.method().equals(HttpMethod.TRACE)) {
                    ByteBuf content = ctx.alloc().buffer().writeBytes(FORBIDDEN_MSG.getBytes(StandardCharsets.UTF_8));
                    FullHttpResponse nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN, content);
                    nettyResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                    ctx.writeAndFlush(nettyResponse).addListener(ChannelFutureListener.CLOSE);
                } else if (isStaticFile(nettyRequest)) {
                    NettyStaticContext context = new NettyStaticContext(ctx, nettyRequest);
                    context.serveStatic();
                } else {
                    final Http.Request request = parseRequest(ctx, nettyRequest, secure);
                    if (HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(nettyRequest.headers().get(HttpHeaderNames.UPGRADE))) {
                        websocketHandshake(ctx, nettyRequest, request);
                    } else {
                        NettyContext context = new NettyContext(ctx, nettyRequest, request);
                        try {
                            context.response().onWriteChunk(obj -> context.writeChunk(obj));
                            // Raw invocation
                            boolean raw = Play.pluginCollection.rawInvocation(request, context.response());
                            if (raw) {
                                context.copyResponse();
                            } else if (context.resolve()) {
                                nettyRequest.retain();
                                executor.submit(new NettyInvocation(context));
                            }
                        } catch (Exception ex) {
                            Logger.warn(ex, "Exception on request. serving 500 back");
                            context.serve500(ex);
                        }
                    }
                }
            }finally {
                if(nettyRequest.refCnt() > 0) {
                    nettyRequest.release();
                }
            }
        }
        else if (msg instanceof WebSocketFrame frame) {
            websocketFrameReceived(ctx, frame);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)  {
        if(cause != null && cause.getMessage() != null && !cause.getMessage().toLowerCase().contains("connection reset")) {
            Logger.error(cause, cause.getMessage());
        }
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx)  {
        Http.Inbound inbound = channels.get(ctx);
        if (inbound != null) {
            inbound.close();
        }
        channels.remove(ctx);
    }

    void websocketFrameReceived(ChannelHandlerContext ctx, WebSocketFrame webSocketFrame) {
        try {
            Http.Inbound inbound = channels.get(ctx);
            // Check for closing frame
            if (webSocketFrame instanceof CloseWebSocketFrame closeWebSocketFrame && handshaker != null) {
                this.handshaker.close(ctx.channel(), closeWebSocketFrame);
            } else if (webSocketFrame instanceof PingWebSocketFrame pingWebSocketFrame) {
                ctx.write(new PongWebSocketFrame(pingWebSocketFrame.content()));
            } else if (webSocketFrame instanceof BinaryWebSocketFrame binaryWebSocketFrame) {
                inbound._received(new Http.WebSocketFrame(binaryWebSocketFrame.content().array()));
            } else if (webSocketFrame instanceof TextWebSocketFrame textWebSocketFrame) {
                inbound._received(new Http.WebSocketFrame(textWebSocketFrame.text()));
            }
        }finally {
            if(webSocketFrame.refCnt() > 0)
                webSocketFrame.release();
        }
    }

    void websocketHandshake(ChannelHandlerContext ctx, FullHttpRequest nettyRequest, Http.Request request) {
        String wsLocation = "ws://" + nettyRequest.headers().get(HttpHeaderNames.HOST) + nettyRequest.uri();
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(wsLocation, null, false);
        this.handshaker = wsFactory.newHandshaker(nettyRequest);
        if (this.handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            this.handshaker.handshake(ctx.channel(), nettyRequest);
        }
        try {
            request.method = "WS";
            Router.routeOnlyStatic(request);
        } catch (NotFound e) {
            ctx.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND));
            return;
        }

        // Inbound
        Http.Inbound inbound = new Http.Inbound() {

            @Override
            public boolean isOpen() {
                return ctx.channel().isActive();
            }
        };
        channels.put(ctx, inbound);

        // Outbound
        Http.Outbound outbound = new Http.Outbound() {

            final List<ChannelFuture> writeFutures = new CopyOnWriteArrayList<>();

            void writeAndClose(ChannelFuture writeFuture) {
                if (!writeFuture.isDone()) {
                    writeFutures.add(writeFuture);
                    writeFuture.addListener((ChannelFutureListener) cf -> {
                        writeFutures.remove(cf);
                    });
                }
            }

            @Override
            public void send(String data) {
                if (!isOpen()) {
                    throw new IllegalStateException("The outbound channel is closed");
                }
                writeAndClose(ctx.writeAndFlush(new TextWebSocketFrame(data)));
            }

            @Override
            public void send(byte opcode, byte[] data, int offset, int length) {
                if (!isOpen()) {
                    throw new IllegalStateException("The outbound channel is closed");
                }
                writeAndClose(ctx.writeAndFlush(new BinaryWebSocketFrame(wrappedBuffer(data, offset, length))));
            }

            @Override
            public boolean isOpen() {
                return ctx.channel().isActive();
            }

            @Override
            public void close() {
                writeFutures.clear();
                ctx.channel().disconnect();
            }
        };
        Logger.trace("invoking websocket handshake %s", wsLocation);
        executor.submit(new WebSocketInvocation(inbound, outbound, request, ctx));
    }

    Http.Request parseRequest(ChannelHandlerContext ctx, FullHttpRequest nettyRequest, boolean secure) {
        if (Logger.isTraceEnabled()) {
            Logger.trace("parseRequest: begin");
            Logger.trace("parseRequest: URI = " + nettyRequest.uri());
        }
        String uri = nettyRequest.uri();
        // Remove domain and port from URI if it's present.
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            // Begins searching / after 9th character (last / of https://)
            int index = uri.indexOf("/", 9);
            // prevent the IndexOutOfBoundsException that was occurring
            if (index >= 0) {
                uri = uri.substring(index);
            } else {
                uri = "/";
            }
        }
        String contentType = nettyRequest.headers().get(CONTENT_TYPE);

        int i = uri.indexOf("?");
        String querystring = "";
        String path = uri;
        if (i != -1) {
            path = uri.substring(0, i);
            querystring = uri.substring(i + 1);
        }
        InetAddress remoteSocketAddress = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();
        String remoteAddress = remoteSocketAddress.getHostAddress();
        if (PATTERN_REMOTEADDR.matcher(remoteAddress).matches()) {
            remoteAddress = remoteAddress.substring(1);
            remoteAddress = remoteAddress.substring(0, remoteAddress.indexOf(":"));
        } else if (PATTERN_REMOTEADDR2.matcher(remoteAddress).matches()) {
            remoteAddress = remoteAddress.substring(0, remoteAddress.indexOf("%"));
        }
        String method = nettyRequest.method().name();

        InputStream body = new ByteBufInputStream(nettyRequest.content());
        String host = nettyRequest.headers().get(HOST);
        boolean isLoopback = remoteSocketAddress.isLoopbackAddress() && PATTERN_LOOPBACK.matcher(host).matches();
        int port = 0;
        String domain = null;
        if (host == null) {
            host = "";
            port = 80;
            domain = "";
        }
        // Check for IPv6 address
        else if (host.startsWith("[")) {
            // There is no port
            if (host.endsWith("]")) {
                domain = host;
                port = 80;
            } else {
                // There is a port so take from the last colon
                int portStart = host.lastIndexOf(':');
                if (portStart > 0 && (portStart + 1) < host.length()) {
                    domain = host.substring(0, portStart);
                    port = Integer.parseInt(host.substring(portStart + 1));
                }
            }
        }
        // Non IPv6 but has port
        else if (host.contains(":")) {
            String[] hosts = StringUtils.split(host, ":");
            port = Integer.parseInt(hosts[1]);
            domain = hosts[0];
        } else {
            port = 80;
            domain = host;
        }

        Map<String, Http.Header> headers = new HashMap<>(16);
        for (String key : nettyRequest.headers().names()) {
            Http.Header hd = new Http.Header(key.toLowerCase(), new ArrayList<>());
            hd.values.addAll(nettyRequest.headers().getAll(key));
            headers.put(hd.name, hd);
        }

        Map<String, Http.Cookie> cookies = new HashMap<>(16);
        String value = nettyRequest.headers().get(COOKIE);
        if (value != null) {
            Set<io.netty.handler.codec.http.cookie.Cookie> cookieSet = ServerCookieDecoder.STRICT.decode(value);
            for (io.netty.handler.codec.http.cookie.Cookie cookie : cookieSet) {
                io.netty.handler.codec.http.cookie.DefaultCookie defaultCookie = (DefaultCookie) cookie;
                Http.Cookie playCookie = new Http.Cookie(cookie.name(), cookie.value());
                playCookie.path = defaultCookie.path();
                playCookie.domain = defaultCookie.domain();
                playCookie.secure = defaultCookie.isSecure();
                playCookie.httpOnly = defaultCookie.isHttpOnly();
                playCookie.sameSite = defaultCookie.sameSite() != null ? defaultCookie.sameSite().name() : CookieHeaderNames.SameSite.Lax.name();
                cookies.put(playCookie.name, playCookie);
            }
        }

        return Http.Request.createRequest(remoteAddress, method, path, querystring, contentType, body, uri, host, isLoopback,
                port, domain, secure, headers, cookies);
    }

    public boolean isStaticFile(FullHttpRequest nettyRequest) {
        return nettyRequest.uri().startsWith(Play.ctxPath+"/public/");
    }

}
