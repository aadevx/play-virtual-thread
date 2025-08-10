package play.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import org.apache.commons.lang3.time.DateUtils;
import play.Play;
import play.jte.JtePlugin;
import play.libs.MimeTypes;
import play.templates.TemplateLoader;
import play.utils.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Netty handle static content on public folder
 * @author ariefardiyansah
 */
public class NettyStaticContext {

//    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
//    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    private static final int maxAge = Integer.parseInt(Play.configuration.getProperty("http.cacheControl", "31536000"));
    private final ChannelHandlerContext ctx;
    private final HttpRequest nettyRequest;
    private final boolean keepAlive;
    private static final Play play = new Play();

    public NettyStaticContext(ChannelHandlerContext ctx, HttpRequest nettyRequest) {
        this.ctx = ctx;
        this.nettyRequest = nettyRequest;
        this.keepAlive =  HttpUtil.isKeepAlive(nettyRequest);
    }

    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");
    public void serveStatic() throws IOException {
        if (!GET.equals(nettyRequest.method())) {
            sendError(METHOD_NOT_ALLOWED);
            return;
        }
        String uri = nettyRequest.uri();
        int i = uri.indexOf("?");
        if (i != -1)
            uri = uri.substring(0, i);
        String path = sanitizeUri(uri);
        if (path == null) {
            sendError404();
            return;
        }
        String url = Utils.urlDecodePath(path.replaceFirst(Play.ctxPath, ""));
        File vf = Play.getVirtualFile(url);
        if (vf == null || !vf.exists()) {
            sendError404();
            return;
        }
        // Cache Validation
        String ifModifiedSince = nettyRequest.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE);
        if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
            Date ifModifiedSinceDate = DateFormatter.parseHttpDate(ifModifiedSince);
            // Only compare up to the second because the datetime format we send to the client
            // does not have milliseconds
            long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
            long fileLastModifiedSeconds = vf.lastModified() / 1000;
            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                sendNotModified();
                return;
            }
        }
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(vf, "r");
        } catch (FileNotFoundException ignore) {
            sendError404();
            return;
        }
        long fileLength = raf.length();
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        HttpUtil.setContentLength(response, fileLength);
        String contentType = MimeTypes.getContentType(vf.getName(), "text/plain");
        response.headers().set(CONTENT_TYPE, contentType);
        setDateAndCacheHeaders(response, vf);

        if (!keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        } else if (nettyRequest.protocolVersion().equals(HTTP_1_0)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        // Write the initial line and the header.
        ctx.write(response);
        // Write the content.
        ChannelFuture sendFileFuture;
        ChannelFuture lastContentFuture;
        if (ctx.pipeline().get(SslHandler.class) == null) {
            sendFileFuture = ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength));
            // Write the end marker.
            lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        } else {
            sendFileFuture = ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192)));
            // HttpChunkedInput will write the end marker (LastHttpContent) for us.
            lastContentFuture = sendFileFuture;
        }

        // Decide whether to close the connection or not.
        if (!keepAlive) {
            // Close the connection when the whole content is written out.
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static String sanitizeUri(String uri) {
        // Decode the path.
        uri = URLDecoder.decode(uri, StandardCharsets.UTF_8);

        if (uri.isEmpty() || uri.charAt(0) != '/') {
            return null;
        }

        // Convert file separators.
        uri = uri.replace('/', File.separatorChar);

        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if (uri.contains(File.separator + '.') ||
                uri.contains('.' + File.separator) ||
                uri.charAt(0) == '.' || uri.charAt(uri.length() - 1) == '.' ||
                INSECURE_URI.matcher(uri).matches()) {
            return null;
        }

        // Convert to absolute path.
        return uri;
    }

    /**
     * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
     */
    private void sendNotModified() {
        ByteBuf content = ctx.alloc().buffer();
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED,content);
        response.headers().set(HttpHeaderNames.DATE, DateFormatter.format(new Date()));
        sendAndCleanupConnection(response);
    }

    private void sendError(HttpResponseStatus status) {
        Map<String, Object> binding = new HashMap<>();
        binding.put("result", new Exception("Failure: " + status + "\r\n"));
        binding.put("play", play);
        binding.put("_response_encoding", Play.defaultWebEncoding);
        String errorHtml = JtePlugin.hasTemplate("errors/400.jte") ? JtePlugin.render("errors/400.jte", binding) : TemplateLoader.load("errors/400.html").render(binding);
        ByteBuf content = ctx.alloc().buffer().writeBytes(errorHtml.getBytes(StandardCharsets.UTF_8));
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        sendAndCleanupConnection(response);
    }

    private void sendError404() {
        Map<String, Object> binding = new HashMap<>();
        binding.put("result", new Exception("The file " + nettyRequest.uri() + " does not exist"));
        binding.put("play", play);
        binding.put("_response_encoding", Play.defaultWebEncoding);
        String errorHtml = JtePlugin.hasTemplate("errors/404.jte") ? JtePlugin.render("errors/404.jte", binding) : TemplateLoader.load("errors/404.html").render(binding);
        ByteBuf content = ctx.alloc().buffer().writeBytes(errorHtml.getBytes(StandardCharsets.UTF_8));
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(CONTENT_TYPE, (MimeTypes.getContentType("404.html", "text/plain")));
        sendAndCleanupConnection(response);
    }

    private void sendAndCleanupConnection(FullHttpResponse response) {
        HttpUtil.setContentLength(response, response.content().readableBytes());
        if (!keepAlive) {
            // We're going to close the connection as soon as the response is sent,
            // so we should also make it clear for the client.
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        } else if (nettyRequest.protocolVersion().equals(HTTP_1_0)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        ChannelFuture flushPromise = ctx.writeAndFlush(response);

        if (!keepAlive) {
            // Close the connection as soon as the response is sent.
            flushPromise.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param response
     *            HTTP response
     * @param fileToCache
     *            file to extract content type
     */
    private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        // Date header
        Date date = new Date();
        response.headers().set(HttpHeaderNames.DATE, DateFormatter.format(date));
        // Add cache headers
        Date expiryDate = DateUtils.addSeconds(date, Play.mode.isDev() ? 0:maxAge);
        response.headers().set(HttpHeaderNames.EXPIRES, DateFormatter.format(expiryDate));
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, Play.mode.isDev() ? HttpHeaderValues.NO_CACHE:"max-age=" + maxAge);
        response.headers().set(HttpHeaderNames.LAST_MODIFIED, DateFormatter.format(new Date(fileToCache.lastModified())));
    }


}
