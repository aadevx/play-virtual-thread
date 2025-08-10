package play.server.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
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

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

/**
 * Netty handle static content on public folder
 * @author ariefardiyansah
 */
public class Http2StaticContext {

    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    private static final int maxAge = Integer.parseInt(Play.configuration.getProperty("http.cacheControl", "31536000"));

    private final ChannelHandlerContext ctx;
    private final Http2HeadersFrame nettyRequest;
    private Http2FrameStream stream;
    private static final Play play = new Play();

    public Http2StaticContext(ChannelHandlerContext ctx, Http2HeadersFrame nettyRequest) {
        this.ctx = ctx;
        this.nettyRequest = nettyRequest;
        this.stream = nettyRequest.stream();
    }

    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");

    public void serveStatic() throws IOException {
        if (!GET.toString().equals(nettyRequest.headers().method().toString())) {
            sendError(METHOD_NOT_ALLOWED);
            return;
        }
        String uri = nettyRequest.headers().path().toString();
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
        String ifModifiedSince = nettyRequest.headers().contains(HttpHeaderNames.IF_MODIFIED_SINCE) ? nettyRequest.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE).toString():"";
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
        Http2Headers headers = new DefaultHttp2Headers();
        headers.status("200");
        headers.setLong(HttpHeaderNames.CONTENT_LENGTH, fileLength);
        String contentType = MimeTypes.getContentType(vf.getName(), "text/plain");
        headers.set(HttpHeaderNames.CONTENT_TYPE, contentType);
        setDateAndCacheHeaders(headers, vf);
        // Write the initial line and the header.
        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(headers).stream(stream));
        // Write the content.
        ctx.writeAndFlush(new Http2DataChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192), stream));
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
        Http2Headers headers = new DefaultHttp2Headers();
        headers.status(NOT_MODIFIED.toString());
        headers.set(HttpHeaderNames.DATE, DateFormatter.format(new Date()));
        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(headers, true).stream(stream));
    }


    private void sendError(HttpResponseStatus status) {
        Map<String, Object> binding = new HashMap<>();
        binding.put("result", new Exception("Failure: " + status + "\r\n"));
        binding.put("play", play);
        binding.put("_response_encoding", Play.defaultWebEncoding);
        String errorHtml = JtePlugin.hasTemplate("errors/500.jte") ? JtePlugin.render("errors/500.jte", binding) : TemplateLoader.load("errors/500.html").render(binding);
        Http2Headers headers = new DefaultHttp2Headers();
        headers.status(status.toString());
        headers.add(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(headers);
        headersFrame.stream(stream);
        ByteBuf content = ctx.alloc().buffer();
        content.writeBytes(errorHtml.getBytes(StandardCharsets.UTF_8));
        Http2DataFrame dataFrame = new DefaultHttp2DataFrame(content, true);
        dataFrame.stream(stream);
        ctx.write(headersFrame);
        ctx.writeAndFlush(dataFrame);
    }

    private void sendError404() {
        Map<String, Object> binding = new HashMap<>();
        binding.put("result", new Exception("The file " + nettyRequest.headers().path() + " does not exist"));
        binding.put("play", play);
        binding.put("_response_encoding", Play.defaultWebEncoding);
        String errorHtml = JtePlugin.hasTemplate("errors/404.jte") ? JtePlugin.render("errors/404.jte", binding) : TemplateLoader.load("errors/404.html").render(binding);
        Http2Headers headers = new DefaultHttp2Headers();
        headers.status(NOT_FOUND.codeAsText());
        headers.add(HttpHeaderNames.CONTENT_TYPE, MimeTypes.getContentType("404.html", "text/plain"));
        Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(headers);
        headersFrame.stream(stream);
        ByteBuf content = ctx.alloc().buffer();
        content.writeBytes(errorHtml.getBytes(CharsetUtil.UTF_8));
        Http2DataFrame dataFrame = new DefaultHttp2DataFrame(content, true);
        dataFrame.stream(stream);
        ctx.write(headersFrame);
        ctx.writeAndFlush(dataFrame);
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param headers     Http2 Headers
     * @param fileToCache file to extract content type
     */
    private static void setDateAndCacheHeaders(Http2Headers headers, File fileToCache) {
        // Date header
        Date date = new Date();
        headers.set(HttpHeaderNames.DATE, DateFormatter.format(date));
        // Add cache headers
        Date expiryDate = DateUtils.addSeconds(date, Play.mode.isDev() ? 0:maxAge);
        headers.set(HttpHeaderNames.EXPIRES, DateFormatter.format(expiryDate));
        headers.set(HttpHeaderNames.CACHE_CONTROL, Play.mode.isDev() ? HttpHeaderValues.NO_CACHE:"max-age=" + maxAge);
        headers.set(HttpHeaderNames.LAST_MODIFIED, DateFormatter.format(new Date(fileToCache.lastModified())));
    }
}
