package play.server.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.codec.http2.*;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.handler.stream.ChunkedStream;
import io.netty.handler.stream.ChunkedWriteHandler;
import play.InvocationContext;
import play.Logger;
import play.Play;
import play.data.validation.Validation;
import play.exceptions.UnexpectedException;
import play.i18n.Messages;
import play.jte.JtePlugin;
import play.libs.MimeTypes;
import play.libs.Time;
import play.mvc.ActionInvoker;
import play.mvc.Http;
import play.mvc.Router;
import play.mvc.Scope;
import play.mvc.results.NotFound;
import play.mvc.results.RenderStatic;
import play.server.Context;
import play.server.LazyChunkedInput;
import play.server.NettyInvocation;
import play.templates.JavaExtensions;
import play.templates.TemplateLoader;
import play.utils.HTTP;
import play.utils.Utils;

import java.io.*;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http.HttpHeaderNames.*;

public class Http2Context implements Context {

    /**
     * If true (the default), Play will send the HTTP header
     * "Server: Play! Framework; ....". This could be a security problem (old
     * versions having publicly known security bugs), so you can disable the
     * header in application.conf: <code>http.exposePlayServer = false</code>
     */
    private static final String signature = "Play! Framework;" + Play.version + ";" + Play.mode.name().toLowerCase();
    private static final boolean exposePlayServer = !"false".equals(Play.configuration.getProperty("http.exposePlayServer", "false"));
    private static final Map<String, RenderStatic> staticPathsCache = new ConcurrentHashMap<>();
    private static final Play play = new Play();

    private final ChannelHandlerContext ctx;
    private final Http2HeadersFrame nettyRequest;
    private final Http.Request request;
    private final Http.Response response;
    private final boolean secure ;

    public Http2Context(ChannelHandlerContext ctx, Http2HeadersFrame nettyRequest, Http.Request request) {
        this.ctx = ctx;
        this.nettyRequest = nettyRequest;
        this.request = request;
        this.secure = request.secure;
        this.response = new Http.Response();
        this.response.out = new ByteArrayOutputStream();
        this.response.direct = null;
    }

    public Http.Request request(){
        return this.request;
    }

    public Http.Response response() {
        return response;
    }

    public void serve404(NotFound e) {
        if (Logger.isTraceEnabled()) {
            Logger.trace("serve404: begin");
        }
        Map<String, Object> binding = getBindingForErrors(e, false);
        String format = request.format;
        if (format == null) {
            format = "txt";
        }
        String errorHtml;
        if(JtePlugin.hasTemplate("errors/404.jte"))
            errorHtml = JtePlugin.render("errors/404.jte", binding);
        else
            errorHtml = TemplateLoader.load("errors/404." + format).render(binding);
        ByteBuf content = ctx.alloc().buffer().writeBytes(errorHtml.getBytes(Charset.forName(response.encoding)));
        Http2Headers headers = new DefaultHttp2Headers().status(HttpResponseStatus.NOT_FOUND.codeAsText());
        headers.add(CONTENT_TYPE, MimeTypes.getContentType("404." + format, "text/plain"));
        ctx.write(new DefaultHttp2HeadersFrame(headers));
        ctx.write(new DefaultHttp2DataFrame(content, true));
        if (Logger.isTraceEnabled()) {
            Logger.trace("serve404: end");
        }
    }

    private Map<String, Object> getBindingForErrors(Exception e, boolean isError) {

        Map<String, Object> binding = new HashMap<>();
        if (!isError) {
            binding.put("result", e);
        } else {
            binding.put("exception", e);
        }
        binding.put("session", Scope.Session.current());
        binding.put("request", request);
        binding.put("flash", Scope.Flash.current());
        binding.put("params", Scope.Params.current());
        binding.put("play", play);
        try {
            binding.put("errors", Validation.errors());
        } catch (Exception ex) {
             Logger.error(ex, "Error when getting Validation errors");
        }

        return binding;
    }

    private boolean isModified(String etag, long last) {
        String browserEtag = nettyRequest.headers().contains(IF_NONE_MATCH) ? nettyRequest.headers().get(IF_NONE_MATCH).toString() :"";
        String ifModifiedSince = nettyRequest.headers().contains(IF_MODIFIED_SINCE) ? nettyRequest.headers().get(IF_MODIFIED_SINCE).toString():"";
        return HTTP.isModified(etag, last, browserEtag, ifModifiedSince);
    }

    private void addEtag(Http2Headers http2Headers, String etag) {
        if (Play.mode == Play.Mode.DEV) {
            http2Headers.set(CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        } else {
            // Check if Cache-Control header is not set
            if (http2Headers.get(CACHE_CONTROL) == null) {
                String maxAge = Play.configuration.getProperty("http.cacheControl", "31536000");
                if (maxAge.equals("0")) {
                    http2Headers.set(CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
                } else {
                    http2Headers.set(CACHE_CONTROL, "max-age=" + maxAge);
                }
            }
        }
        boolean useEtag = Play.configuration.getProperty("http.useETag", "true").equals("true");
        if (useEtag) {
            http2Headers.set(ETAG, etag);
        }
    }


    public void serve500(Exception e) {
        if (Logger.isTraceEnabled()) {
            Logger.trace("serve500: begin");
        }
        String encoding = response.encoding;
        Map<String, Object> binding = getBindingForErrors(e, true);

        String format = request.format;
        if (format == null) {
            format = "txt";
        }
        Logger.error(e, "Internal Server Error (500) for request %s", request.method + " " + request.url);
        try {
            String errorHtml;
            if(JtePlugin.hasTemplate("errors/500.jte"))
                errorHtml = JtePlugin.render("errors/500.jte", binding);
            else
                errorHtml = TemplateLoader.load("errors/500." + format).render(binding);
            ByteBuf content = ctx.alloc().buffer().writeBytes(errorHtml.getBytes(StandardCharsets.UTF_8));
            Http2Headers headers = new DefaultHttp2Headers().status(HttpResponseStatus.INTERNAL_SERVER_ERROR.codeAsText());
            headers.add(CONTENT_TYPE, (MimeTypes.getContentType("500." + format, "text/plain")));
//            if (!(e instanceof PlayException)) {
//                e = new play.exceptions.UnexpectedException(e);
//            }
            // Flush some cookies
            Map<String, Http.Cookie> cookies = response.cookies;
            for (Http.Cookie cookie : cookies.values()) {
                DefaultCookie c = new DefaultCookie(cookie.name, cookie.value);
                c.setSecure(cookie.secure);
                c.setPath(cookie.path);
                if (cookie.domain != null) {
                    c.setDomain(cookie.domain);
                }
                if (cookie.maxAge != null) {
                    c.setMaxAge(cookie.maxAge);
                }
                c.setHttpOnly(cookie.httpOnly);
                c.setSameSite(CookieHeaderNames.SameSite.valueOf(cookie.sameSite));
                headers.add(SET_COOKIE, ServerCookieEncoder.STRICT.encode(c));
            }
            ctx.write(new DefaultHttp2HeadersFrame(headers));
            ctx.write(new DefaultHttp2DataFrame(content, true));
        } catch (Throwable exx) {
            Logger.error(exx, "Error during the 500 response generation");
            serveInternalError(exx);
        }
        if (Logger.isTraceEnabled()) {
            Logger.trace("serve500: end");
        }
    }

    private void serveInternalError(Throwable throwable) {
        try {
            ByteBuf content = ctx.alloc().buffer().writeBytes(throwable.getMessage().getBytes(StandardCharsets.UTF_8));
            Http2Headers headers = new DefaultHttp2Headers().status(HttpResponseStatus.INTERNAL_SERVER_ERROR.codeAsText());
            ctx.write(new DefaultHttp2HeadersFrame(headers));
            ctx.write(new DefaultHttp2DataFrame(content, true));
        } catch (Exception ex) {
            Logger.error(ex, "serving Internal Error %s", ex.getMessage());
        }
    }

    void copyResponse() throws Exception {
        Object obj = response.direct;
        if (obj != null) {
            if (obj instanceof File file) {
                serveFile(file);
            } else {
                // write chunked
                Logger.info("write chunked http 2 ...");
                ChunkedInput stream = (obj instanceof InputStream is) ? new ChunkedStream(is) : (ChunkedInput) obj;
                Http2Headers headers = new DefaultHttp2Headers().status(HttpResponseStatus.valueOf(response.status).codeAsText());
                Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(headers).stream(nettyRequest.stream());
                ctx.write(headersFrame);
                ctx.write(new Http2DataChunkedInput(stream, nettyRequest.stream()));
            }
        } else {
            // write response
            ByteBuf content = ctx.alloc().buffer();
            if (!nettyRequest.headers().method().equals(HttpMethod.HEAD)) {
                content.writeBytes(response.out.toByteArray());
            }
            Http2Headers headers = new DefaultHttp2Headers().status(HttpResponseStatus.OK.codeAsText());
            ctx.write(new DefaultHttp2HeadersFrame(headers));
            ctx.write(new DefaultHttp2DataFrame(content, true));
        }
    }

    private void addToResponse(Http2Headers http2Headers) {
        if (exposePlayServer) {
            http2Headers.set(SERVER, signature);
        }
        if (response.contentType != null) {
            http2Headers.set(CONTENT_TYPE, response.contentType + (response.contentType.startsWith("text/") && !response.contentType.contains("charset")
                    ? "; charset=" + response.encoding : ""));
        } else {
            http2Headers.set(CONTENT_TYPE, "text/plain; charset=" + response.encoding);
        }
        Map<String, Http.Header> headers = response.headers;
        headers.forEach((k, v) -> {
            for (String value : v.values) {
                http2Headers.add(k, value);
            }
        });

        http2Headers.set(DATE, Utils.httpFormatter.format(LocalDateTime.now()));

        Map<String, Http.Cookie> cookies = response.cookies;

        for (Http.Cookie cookie : cookies.values()) {
            DefaultCookie c = new DefaultCookie(cookie.name, cookie.value);
            c.setSecure(cookie.secure);
            c.setPath(cookie.path);
            if (cookie.domain != null) {
                c.setDomain(cookie.domain);
            }
            if (cookie.maxAge != null) {
                c.setMaxAge(cookie.maxAge);
            }
            c.setHttpOnly(cookie.httpOnly);
            c.setSameSite(CookieHeaderNames.SameSite.valueOf(cookie.sameSite));
            http2Headers.add(SET_COOKIE, ServerCookieEncoder.STRICT.encode(c));
        }
        if (!response.headers.containsKey(CACHE_CONTROL.toString()) && !response.headers.containsKey(EXPIRES.toString()) && !(response.direct instanceof File)) {
            http2Headers.set(CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        }
    }

    // serve file static
    void serveFile(File localFile) {
        if(Logger.isTraceEnabled()) {
            Logger.trace("content type %s", (response.contentType != null ? response.contentType : MimeTypes.getContentType(localFile.getName(), "text/plain")));
        }

        long last = localFile.lastModified();
        String etag = "\"" + last + "-" + localFile.hashCode() + "\"";
        if (!isModified(etag, last) && nettyRequest.headers().method().equals(HttpMethod.GET)) {
            Http2Headers headers = new DefaultHttp2Headers().status(HttpResponseStatus.NOT_MODIFIED.codeAsText());
            ctx.write(new DefaultHttp2HeadersFrame(headers));
            ctx.write(new DefaultHttp2DataFrame(EMPTY_BUFFER, true));
        } else {
            Http2Headers headers = new DefaultHttp2Headers().status(HttpResponseStatus.valueOf(response.status).codeAsText());
            headers.set(LAST_MODIFIED, Utils.httpFormatter.format(Time.toLocalDateTime(last)));
            addToResponse(headers);
            addEtag(headers, etag);
            FileChannel fileChannel = null;
            long fileLength = 0L;
            try {
                RandomAccessFile raf = new RandomAccessFile(localFile, "r");
                fileChannel = raf.getChannel();
                fileLength = raf.length();
            } catch (FileNotFoundException e) {
                serve404(new NotFound("File "+localFile+" not found"));
            } catch (IOException e) {
                serve500(e);
            }
            if (!headers.status().toString().equals(HttpResponseStatus.NOT_MODIFIED)) {
                // Add 'Content-Length' header only for a keep-alive connection.
                headers.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(fileLength));
            }
            String contentType = MimeTypes.getContentType(localFile.getName(), "text/plain");
            headers.set(CONTENT_TYPE, response.contentType != null? response.contentType : contentType);
            headers.set(HttpHeaderNames.ACCEPT_RANGES, HttpHeaderValues.BYTES);
            long start=0, end;
            long contentLength = fileLength;
            // Write the content.
            if(nettyRequest.headers().contains(HttpHeaderNames.RANGE.toLowerCase())) { // resumable proses
                String rangeValue = nettyRequest.headers().get(HttpHeaderNames.RANGE).toString().trim().substring("bytes=".length());
                if (rangeValue.startsWith("-")) {
                    end = fileLength - 1;
                    start = fileLength - 1 - Long.parseLong(rangeValue.substring("-".length()));
                } else {
                    String[] range = rangeValue.split("-");
                    start = Long.parseLong(range[0]);
                    end = range.length > 1 ? Long.parseLong(range[1]) : fileLength - 1;
                }
                if (end > fileLength - 1) {
                    end = fileLength - 1;
                }
                headers.add(ACCEPT_RANGES, "bytes");
                if (start <= end) {
                    contentLength = end - start + 1;
                    headers.set(CONTENT_RANGE, "bytes " + start + "-"+ end + "/" + fileLength);
                    headers.set(CONTENT_TYPE, contentType);
                }else {
                    headers.status(HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE.codeAsText());
                    headers.set(CONTENT_RANGE, "bytes " + 0 + "-" + (fileLength-1) + "/" + fileLength);
                }
            }
            writeFile(headers, fileChannel, start, contentLength);
        }
    }

    private void writeFile(Http2Headers headersFrame, FileChannel fileChannel, long start, long contentLength) {
        if(fileChannel == null)
            return;
        headersFrame.setLong(CONTENT_LENGTH, contentLength);
        Http2HeadersFrame frame = new DefaultHttp2HeadersFrame(headersFrame);
        ctx.write(frame);
        if (secure) {
            try {
                ctx.write(new Http2DataChunkedInput(new ChunkedNioFile(fileChannel, start, contentLength, 8192), frame.stream()));
            } catch (IOException e) {
                serve500(e);
            }
        } else {
            ctx.write(new DefaultFileRegion(fileChannel, start, contentLength));// Body
        }
    }


    private void closeChunked() {
        try {
            ((LazyChunkedInput) response.direct).close();
            if (ctx.pipeline().get(ChunkedWriteHandler.class) != null) {
                ctx.pipeline().get(ChunkedWriteHandler.class).resumeTransfer();
            }
            if (ctx.pipeline().get("SslChunkedWriteHandler") != null) {
                ((ChunkedWriteHandler) ctx.pipeline().get("SslChunkedWriteHandler")).resumeTransfer();
            }
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    void serveStatic(RenderStatic renderStatic) {
        if (Logger.isTraceEnabled()) {
            Logger.trace("serveStatic: begin");
        }
        try {
            File file = renderStatic.resolvedFile;
            if (file == null || !file.exists()) {
                serve404(new NotFound("The file " + renderStatic.file + " does not exist"));
            } else {
                serveFile(file);
            }

        } catch (Throwable ez) {
            Logger.error(ez, "serveStatic for request %s", request.method + " " + request.url);
            serveInternalError(ez);
        }
        if (Logger.isTraceEnabled()) {
            Logger.trace("serveStatic: end");
        }
    }

    void saveExceededSizeError() {
        CharSequence warning = nettyRequest.headers().get(HttpHeaderNames.WARNING);
        if (warning != null) {
            if (Logger.isTraceEnabled()) {
                Logger.trace("saveExceededSizeError: begin");
            }
            long length = nettyRequest.headers().getLong(HttpHeaderNames.CONTENT_LENGTH);
            try {
                StringBuilder error = new StringBuilder();
                error.append("\u0000");
                // Cannot put warning which is
                // play.netty.content.length.exceeded
                // as Key as it will result error when printing error
                error.append("play.netty.maxContentLength");
                error.append(":");
                String size;
                try {
                    size = JavaExtensions.formatSize(length);
                } catch (Exception e) {
                    size = length + " bytes";
                }
                error.append(Messages.get(warning.toString(), size));
                error.append("\u0001");
                error.append(size);
                error.append("\u0000");
                if (request.cookies.get(Scope.COOKIE_PREFIX + "_ERRORS") != null
                        && request.cookies.get(Scope.COOKIE_PREFIX + "_ERRORS").value != null) {
                    error.append(request.cookies.get(Scope.COOKIE_PREFIX + "_ERRORS").value);
                }
                String errorData = URLEncoder.encode(error.toString(), StandardCharsets.UTF_8);
                Http.Cookie c = new Http.Cookie(Scope.COOKIE_PREFIX + "_ERRORS", errorData);
                request.cookies.put(Scope.COOKIE_PREFIX + "_ERRORS", c);
                if (Logger.isTraceEnabled()) {
                    Logger.trace("saveExceededSizeError: end");
                }
            } catch (Exception e) {
                throw new UnexpectedException("Error serialization problem", e);
            }
        }
    }

    void writeChunk(Object chunk) {
        try {
            if (response.direct == null) {
                response.setHeader("Transfer-Encoding", "chunked");
                response.direct = new LazyChunkedInput();
                copyResponse();
            }
            ((LazyChunkedInput) response.direct).writeChunk(chunk, response.encoding);
            if (ctx.pipeline().get(ChunkedWriteHandler.class) != null) {
                ctx.pipeline().get(ChunkedWriteHandler.class).resumeTransfer();
            }
            if (ctx.pipeline().get("SslChunkedWriteHandler") != null) {
                ((ChunkedWriteHandler) ctx.pipeline().get("SslChunkedWriteHandler")).resumeTransfer();
            }
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    public void writeResponse() throws Exception {
        if (response.chunked) {
            closeChunked();
        } else {
            copyResponse();
        }
    }

    public void execute() {
        if (!ctx.channel().isActive()) {
            try {
                ctx.channel().close();
            } catch (Throwable e) {
                // Ignore
            }
            return;
        }
        // Check the exceeded size before re rendering; so we can render the
        // error if the size is exceeded
        saveExceededSizeError();
        ActionInvoker.invoke(request, response);
    }

    public void release() {
//        if(nettyRequest.refCnt() > 0)
//            nettyRequest.release();
        ctx.flush();
    }

    boolean resolve() {
        String staticKey = request.domain + " " + request.method + " " + request.path;
        try {
            if (Play.mode == Play.Mode.DEV) {
                Router.detectChanges(Play.ctxPath);
            }
            if (Play.mode.isProd() && staticPathsCache.containsKey(staticKey)) {
                RenderStatic rs = staticPathsCache.get(staticKey);
                serveStatic(rs);
                return false;
            } else {
                Router.routeOnlyStatic(request);
                return true;
            }
        } catch (RenderStatic rs) {
            if (Play.mode.isProd()) {
                staticPathsCache.put(staticKey, rs);
            }
            serveStatic(rs);
            return false;
        } catch (NotFound nf) {
            serve404(nf);
            return false;
        }
    }

    public InvocationContext getInvocationContext() {
        return new InvocationContext(Http.invocationType, request.invokedMethod.getAnnotations(),
                request.invokedMethod.getDeclaringClass().getAnnotations());
    }

    public void serveStatic() {
        File vf = Play.getVirtualFile(nettyRequest.headers().path().toString().replaceFirst(Play.ctxPath, ""));
        if (vf == null || !vf.exists()) {
            serve404(new NotFound("The file " + nettyRequest.headers().path() + " does not exist"));
        } else {
            serveFile(vf);
        }
    }
}
