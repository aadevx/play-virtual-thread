package play.server.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import org.apache.commons.lang3.StringUtils;
import play.Logger;
import play.Play;
import play.mvc.Http;
import play.server.NettyInvocation;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaderNames.*;

public class Http2Handler extends ChannelDuplexHandler {
    private static final Pattern PATTERN_REMOTEADDR = Pattern.compile("/[0-9]+[.][0-9]+[.][0-9]+[.][0-9]+[:][0-9]+");
    private static final Pattern PATTERN_LOOPBACK = Pattern.compile("^127\\.0\\.0\\.1:?[0-9]*$");
    private static final Pattern PATTERN_REMOTEADDR2 = Pattern.compile(".*[%].*");
    private static final String FORBIDDEN_MSG = "Failure: " + HttpResponseStatus.FORBIDDEN + "\r\n";

    private InputStream body;
    private final boolean secure;
    private final ExecutorService executor;

    public Http2Handler(boolean secure, ExecutorService executor) {
        this.secure = secure;
        this.executor = executor;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        if (cause != null && cause.getMessage() != null && !cause.getMessage().toLowerCase().contains("connection reset")) {
            Logger.error(cause, cause.getMessage());
        }
        ctx.close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http2HeadersFrame headersFrame) {
            if (headersFrame.headers().method().equals(HttpMethod.TRACE)) {
                ByteBuf content = ctx.alloc().buffer().writeBytes(FORBIDDEN_MSG.getBytes(StandardCharsets.UTF_8));
                FullHttpResponse nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN, content);
                nettyResponse.headers().set(CONNECTION, HttpHeaderValues.CLOSE);
                ctx.writeAndFlush(nettyResponse).addListener(ChannelFutureListener.CLOSE);
            }
            if(headersFrame.isEndStream()) {
                if (isStaticFile(headersFrame) && headersFrame.headers().method().equals(HttpMethod.GET.asciiName())) {
                    Http2StaticContext context = new Http2StaticContext(ctx, headersFrame);
                    context.serveStatic();
                } else {
                    Http.Request request = parseRequest(ctx, headersFrame.headers(), body, secure);
                    Http2Context context = new Http2Context(ctx, headersFrame, request);
                    try {
                        context.response().onWriteChunk(obj -> context.writeChunk(obj));
                        // Raw invocation
                        boolean raw = Play.pluginCollection.rawInvocation(request, context.response());
                        if (raw) {
                            context.copyResponse();
                        } else if (context.resolve()) {
                            executor.submit(new NettyInvocation(context));
                        }
                    } catch (Exception ex) {
                        Logger.warn(ex, "Exception on request. serving 500 back");
                        context.serve500(ex);
                    }
                }
            }
        } else if (msg instanceof Http2DataFrame frame) {
            if(frame.isEndStream())
                this.body = new ByteBufInputStream(frame.content());
            else
                frame.release();
        } else {
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    /**
     * If receive a frame with end-of-stream set, send a pre-canned response.
     */
//    private void onDataRead(ChannelHandlerContext ctx, Http2DataFrame data) throws Exception {
//        if (data.isEndStream()) {
//            this.content = data.content();
////            sendResponse(ctx, data.content());
//        } else {
//            // We do not send back the response to the remote-peer, so we need to release it.
//            data.release();
//        }
//    }

    /**
     * If receive a frame with end-of-stream set, send a pre-canned response.
     */
//    private static void onHeadersRead(ChannelHandlerContext ctx, Http2HeadersFrame headers) throws Exception {
//        Logger.info("onHeadersRead...."+headers);
//        if (headers.isEndStream()) {
//            ByteBuf content = ctx.alloc().buffer();
//            content.writeBytes(RESPONSE_BYTES.duplicate());
//            ByteBufUtil.writeAscii(content, " - via HTTP/2");
//            sendResponse(ctx, content);
//        }
//    }

//    /**
//     * Sends a "Hello World" DATA frame to the client.
//     */
//    private static void sendResponse(ChannelHandlerContext ctx, ByteBuf payload) {
//        // Send a frame for the response status
//        Http2Headers headers = new DefaultHttp2Headers().status(OK.codeAsText());
//        ctx.write(new DefaultHttp2HeadersFrame(headers));
//        ctx.write(new DefaultHttp2DataFrame(payload, true));
//    }

    private boolean isStaticFile(Http2HeadersFrame headersFrame) {
        return headersFrame.headers().path().toString().startsWith(Play.ctxPath + "/public/");
    }

    Http.Request parseRequest(ChannelHandlerContext ctx, Http2Headers headers, InputStream body, boolean secure) {
        if (Logger.isTraceEnabled()) {
            Logger.trace("parseRequest: begin");
            Logger.trace("parseRequest: URI = " + headers.path());
        }
        String uri = headers.path().toString();
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
        CharSequence contentType = headers.get(CONTENT_TYPE);

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
        CharSequence method = headers.method();
        CharSequence host = headers.get(HOST);
        boolean isLoopback = false;
        int port = 80;
        String domain = "";
       if(host != null && !host.isEmpty()) {
           isLoopback = remoteSocketAddress.isLoopbackAddress() && PATTERN_LOOPBACK.matcher(host).matches();
           // Check for IPv6 address
            if (host.toString().startsWith("[")) {
               // There is no port
               if (host.toString().endsWith("]")) {
                   domain = host.toString();
                   port = 80;
               } else {
                   // There is a port so take from the last colon
                   int portStart = host.toString().lastIndexOf(':');
                   if (portStart > 0 && (portStart + 1) < host.length()) {
                       domain = host.toString().substring(0, portStart);
                       port = Integer.parseInt(host.toString().substring(portStart + 1));
                   }
               }
           }
           // Non IPv6 but has port
           else if (host.toString().contains(":")) {
               String[] hosts = StringUtils.split(host.toString(), ":");
               port = Integer.parseInt(hosts[1]);
               domain = hosts[0];
           } else {
               port = 80;
               domain = host.toString();
           }
       }

        Map<String, Http.Header> headerHttps = new HashMap<>(16);
        for (CharSequence key : headers.names()) {
            Http.Header hd = new Http.Header(key.toString().toLowerCase(), new ArrayList<>());
            for(CharSequence value : headers.getAll(key))
                hd.values.add(value.toString());
            headerHttps.put(hd.name, hd);
        }

        Map<String, Http.Cookie> cookies = new HashMap<>(16);
        CharSequence value = headers.get(COOKIE);
        if (value != null) {
            Set<io.netty.handler.codec.http.cookie.Cookie> cookieSet = ServerCookieDecoder.STRICT.decode(value.toString());
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

        return Http.Request.createRequest(remoteAddress, method.toString(), path, querystring, (contentType != null? contentType.toString():null), body, uri,
                (host != null ? host.toString():null), isLoopback,
                port, domain, secure, headerHttps, cookies);
    }
}
