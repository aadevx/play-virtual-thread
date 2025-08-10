package play.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
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

import static io.netty.handler.codec.http.HttpHeaderNames.*;


public class NettyContext implements Context {
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
    private final FullHttpRequest nettyRequest;
    private final Http.Request request;
    private final Http.Response response;
    private final boolean secure ;
    private final boolean keepAlive;

    public NettyContext(ChannelHandlerContext ctx, FullHttpRequest nettyRequest, Http.Request request) {
        this.ctx = ctx;
        this.nettyRequest = nettyRequest;
        this.request = request;
        this.secure = request.secure;
        this.keepAlive =  HttpUtil.isKeepAlive(nettyRequest);
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
        FullHttpResponse nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, content);
        nettyResponse.headers().set(CONTENT_TYPE, (MimeTypes.getContentType("404." + format, "text/plain")));
        write(nettyResponse);
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
            // Logger.error(ex, "Error when getting Validation errors");
        }

        return binding;
    }

    private boolean isModified(String etag, long last) {
        String browserEtag = nettyRequest.headers().get(IF_NONE_MATCH);
        String ifModifiedSince = nettyRequest.headers().get(IF_MODIFIED_SINCE);
        return HTTP.isModified(etag, last, browserEtag, ifModifiedSince);
    }

    private void addEtag(HttpResponse httpResponse, String etag) {
        if (Play.mode == Play.Mode.DEV) {
            httpResponse.headers().set(CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        } else {
            // Check if Cache-Control header is not set
            if (httpResponse.headers().get(CACHE_CONTROL) == null) {
                String maxAge = Play.configuration.getProperty("http.cacheControl", "31536000");
                if (maxAge.equals("0")) {
                    httpResponse.headers().set(CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
                } else {
                    httpResponse.headers().set(CACHE_CONTROL, "max-age=" + maxAge);
                }
            }
        }
        boolean useEtag = Play.configuration.getProperty("http.useETag", "true").equals("true");
        if (useEtag) {
            httpResponse.headers().set(ETAG, etag);
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
            ByteBuf content = ctx.alloc().buffer().writeBytes(errorHtml.getBytes(Charset.forName(encoding)));
            FullHttpResponse nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, content);
            nettyResponse.headers().set(CONTENT_TYPE, (MimeTypes.getContentType("500." + format, "text/plain")));
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
                nettyResponse.headers().add(SET_COOKIE, ServerCookieEncoder.STRICT.encode(c));
            }
            write(nettyResponse);
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
            FullHttpResponse errorResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, content);
            write(errorResponse);
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
                ChunkedInput stream = (obj instanceof InputStream is) ? new ChunkedStream(is) : (ChunkedInput) obj;
                DefaultHttpResponse nettyResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(response.status));
                addToResponse(nettyResponse);
                ctx.write(nettyResponse); // Headers
                ChannelFuture f = ctx.write(new HttpChunkedInput(stream)); // Body
                if (f != null && !keepAlive) {
                    f.addListener(ChannelFutureListener.CLOSE);
                }
            }
        } else {
            // write response
            ByteBuf content = ctx.alloc().buffer();
            if (!nettyRequest.method().equals(HttpMethod.HEAD)) {
                content.writeBytes(response.out.toByteArray());
            }
            FullHttpResponse nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(response.status),content);
            addToResponse(nettyResponse);
            write(nettyResponse);
        }
    }

    private void addToResponse(HttpResponse nettyResponse) {
        if (exposePlayServer) {
            nettyResponse.headers().set(SERVER, signature);
        }
        if (response.contentType != null) {
            nettyResponse.headers().set(CONTENT_TYPE, response.contentType + (response.contentType.startsWith("text/") && !response.contentType.contains("charset")
                            ? "; charset=" + response.encoding : ""));
        } else {
            nettyResponse.headers().set(CONTENT_TYPE, "text/plain; charset=" + response.encoding);
        }
        Map<String, Http.Header> headers = response.headers;
        headers.forEach((k, v) -> {
            for (String value : v.values) {
                nettyResponse.headers().add(k, value);
            }
        });

        nettyResponse.headers().set(DATE, Utils.httpFormatter.format(LocalDateTime.now()));

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
            nettyResponse.headers().add(SET_COOKIE, ServerCookieEncoder.STRICT.encode(c));
        }
        if (!response.headers.containsKey(CACHE_CONTROL.toString()) && !response.headers.containsKey(EXPIRES.toString()) && !(response.direct instanceof File)) {
            nettyResponse.headers().set(CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
        }
    }

    // serve file static
    void serveFile(File localFile) {
        if(Logger.isTraceEnabled()) {
            Logger.trace("keep alive %s", keepAlive);
            Logger.trace("content type %s", (response.contentType != null ? response.contentType : MimeTypes.getContentType(localFile.getName(), "text/plain")));
        }

        long last = localFile.lastModified();
        String etag = "\"" + last + "-" + localFile.hashCode() + "\"";
        if (!isModified(etag, last) && nettyRequest.method().equals(HttpMethod.GET)) {
            FullHttpResponse nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
            addToResponse(nettyResponse);
            write(nettyResponse);
        } else {
            HttpResponse nettyResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(response.status));
            nettyResponse.headers().set(LAST_MODIFIED, Utils.httpFormatter.format(Time.toLocalDateTime(last)));
            addToResponse(nettyResponse);
            addEtag(nettyResponse, etag);
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
            if (!nettyResponse.status().equals(HttpResponseStatus.NOT_MODIFIED)) {
                // Add 'Content-Length' header only for a keep-alive connection.
                nettyResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(fileLength));
            }
            String contentType = MimeTypes.getContentType(localFile.getName(), "text/plain");
            nettyResponse.headers().set(CONTENT_TYPE, response.contentType != null? response.contentType : contentType);
            nettyResponse.headers().set(HttpHeaderNames.ACCEPT_RANGES, HttpHeaderValues.BYTES);
            long start=0, end;
            long contentLength = fileLength;
            // Write the content.
            if(nettyRequest.headers().contains(HttpHeaderNames.RANGE.toLowerCase())) { // resumable proses
                String rangeValue = nettyRequest.headers().get(HttpHeaderNames.RANGE).trim().substring("bytes=".length());
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
                nettyResponse.headers().add(ACCEPT_RANGES, "bytes");
                if (start <= end) {
                    contentLength = end - start + 1;
                    nettyResponse.headers().set(CONTENT_RANGE, "bytes " + start + "-"+ end + "/" + fileLength);
                    nettyResponse.headers().set(CONTENT_TYPE, contentType);
                }else {
                    nettyResponse.setStatus(HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
                    nettyResponse.headers().set(CONTENT_RANGE, "bytes " + 0 + "-" + (fileLength-1) + "/" + fileLength);
                }
            }
            writeFile(nettyResponse, fileChannel, start, contentLength);
        }
    }

    private void writeFile(HttpResponse nettyResponse, FileChannel fileChannel, long start, long contentLength) {
        if(fileChannel == null)
            return;
        if (!keepAlive) {
            nettyResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        } else if (nettyRequest.protocolVersion().equals(HttpVersion.HTTP_1_0)) {
            nettyResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        HttpUtil.setContentLength(nettyResponse, contentLength);
        ctx.write(nettyResponse); 
        ChannelFuture f = null;
        if (secure) {
            try {            	           
            	f = ctx.write(new HttpChunkedInput(new ChunkedNioFile(fileChannel, start, contentLength, 8192)));
            } catch (IOException e) {
                serve500(e);
            }
        } else {        	
        	 ctx.write(new DefaultFileRegion(fileChannel, start, contentLength));// Body
        	 f = ctx.write(LastHttpContent.EMPTY_LAST_CONTENT);
        }
        if (f != null && !keepAlive) {
            f.addListener(ChannelFutureListener.CLOSE);
        }  
    }

    void write(FullHttpResponse nettyResponse) {
        if (!keepAlive) {
            nettyResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        } else if (nettyRequest.protocolVersion().isKeepAliveDefault()) {
            nettyResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        if (!nettyResponse.status().equals(HttpResponseStatus.NOT_MODIFIED)) {
            HttpUtil.setContentLength(nettyResponse, nettyResponse.content().readableBytes());
        }
        ChannelFuture f = ctx.write(nettyResponse);
        if (f != null && !keepAlive) {
            // Close the connection when the whole content is written out.
            f.addListener(ChannelFutureListener.CLOSE);
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
        String warning = nettyRequest.headers().get(HttpHeaderNames.WARNING);
        String length = nettyRequest.headers().get(HttpHeaderNames.CONTENT_LENGTH);
        if (warning != null) {
            if (Logger.isTraceEnabled()) {
                Logger.trace("saveExceededSizeError: begin");
            }

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
                    size = JavaExtensions.formatSize(Long.parseLong(length));
                } catch (Exception e) {
                    size = length + " bytes";
                }
                error.append(Messages.get(warning, size));
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
        nettyRequest.release();
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
        File vf = Play.getVirtualFile(nettyRequest.uri().replaceFirst(Play.ctxPath, ""));
        if (vf == null || !vf.exists()) {
            serve404(new NotFound("The file " + nettyRequest.uri() + " does not exist"));
        } else {
            serveFile(vf);
        }
    }
}
