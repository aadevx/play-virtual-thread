package play.libs;

import org.apache.commons.lang3.StringUtils;
import play.Logger;
import play.libs.ws.FileParam;
import play.libs.ws.WSSSLContext;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * webclient use Java 11 HttpClient
 */

public final class WebClient {

    private static final HttpClient httpClient = HttpClient.newBuilder().sslContext(WSSSLContext.getSslContext())
            .version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofMinutes(1)).executor(Executors.newCachedThreadPool()).build();

    final HttpClient client;
    final HttpRequest.Builder builder;
    final String url;
    private HttpRequest.BodyPublisher publisher;
    private Map<String, String> params;
    private MultipartFormDataBodyPublisher formData;
    private boolean multipart = false;

    public static WebClient url(String url) {
        return new WebClient(httpClient, url);
    }

    public static WebClient url(HttpClient client, String url) {
        return new WebClient(client, url);
    }

    private WebClient(HttpClient client, String url) {
        this.client = client;
        this.url = url;
        this.builder = HttpRequest.newBuilder().setHeader("User-Agent", "Mozilla/5.0 WebClient SPSE");
        this.publisher = HttpRequest.BodyPublishers.noBody();
    }

    public WebClient setHeader(String name, String value) {
        builder.header(name, value);
        return this;
    }

    public WebClient mimeType(String mimeType) {
        builder.header("Content-Type", mimeType);
        return this;
    }

    public WebClient setParameter(String name, String value) {
        param().put(name, value);
        return this;
    }

    public WebClient setParameter(String name, Object value) {
        param().put(name, value.toString());
        return this;
    }

    public WebClient body(Object body) {
        if(body != null) {
            if (body instanceof InputStream is)
                publisher = HttpRequest.BodyPublishers.ofByteArray(IO.readContent(is));
            else
                publisher = HttpRequest.BodyPublishers.ofString(body.toString());
        }
       return this;
    }

    public WebClient file(String namae, File file) {
        form().addFile(namae, file.toPath());
        return this;
    }

    public WebClient files(File... files) {
        return files(FileParam.getFileParams(files));
    }

    public WebClient files(FileParam... files) {
        if(files != null) {
            for (FileParam entry : files) {
                form().addFile(entry.paramName(), entry.file().toPath(), entry.contentType());
            }
        }
        return this;
    }

    public WebClient authenticate(String username, String password) {
        String key = username + ":" + password;
        builder.header("Authorization", "Basic "+ Base64.getEncoder().encodeToString(key.getBytes()));
        return this;
    }

    public WebClient setTimeout(String timeout) {
        int second = Time.parseDuration(timeout);
        builder.timeout(Duration.ofSeconds(second));
        return this;
    }


    <T> Response<T> execute(String method, HttpResponse.BodyHandler<T> handler) {
        URI uri = URI.create(url);
        if(params != null && !params.isEmpty()) {
            if (multipart) {
                params.entrySet().forEach(entry -> {
                    form().add(entry.getKey(), entry.getValue());
                });
            } else {
                uri = URI.create(url + (StringUtils.isEmpty(uri.getQuery()) ? "?":"&") + ofForm(params));
            }
        }
        builder.uri(uri);
        builder.method(method, formData != null ? formData : publisher);
        HttpResponse<T> response = null;
        try {
            HttpResponse<T> httpresponse = client.sendAsync(builder.build(), handler).get();
            return new Response<T>(httpresponse);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    String ofForm(Map<String, String> data) {
        StringBuilder body = new StringBuilder();
        for (String dataKey : data.keySet()) {
            if (!body.isEmpty()) {
                body.append("&");
            }
            body.append(URLEncoder.encode(dataKey, StandardCharsets.UTF_8)).append("=").append(URLEncoder.encode(data.get(dataKey),StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private Map<String, String> param() {
        if(params == null)
            params = new HashMap<>();
        return params;
    }

    private MultipartFormDataBodyPublisher form() {
        if(formData == null) {
            formData = new MultipartFormDataBodyPublisher();
            builder.header("Content-Type", formData.contentType());
        }
        return formData;
    }

    public void setMultipart(boolean multipart) {
        this.multipart = multipart;
    }

    public <T> Response<T> get(HttpResponse.BodyHandler<T> handler) {
        return execute("GET", handler);
    }

    public <T> Response<T> post(HttpResponse.BodyHandler<T> handler) {
        return execute("POST", handler);
    }

    public <T> Response<T> put(HttpResponse.BodyHandler<T> handler) {
        return execute("PUT", handler);
    }

    public <T> Response<T> delete(HttpResponse.BodyHandler<T> handler) {
        return execute("DELETE", handler);
    }

    public <T> Response<T> patch(HttpResponse.BodyHandler<T> handler) {
        return execute("PATCH", handler);
    }

    public Response<String> get() {
        return get(HttpResponse.BodyHandlers.ofString());
    }

    public Response<String> put() {
        return put(HttpResponse.BodyHandlers.ofString());
    }

    public Response<String> delete() {
        return delete(HttpResponse.BodyHandlers.ofString());
    }

    public Response<String> patch() {
        return patch(HttpResponse.BodyHandlers.ofString());
    }

    public Response<String> post() {
        return post(HttpResponse.BodyHandlers.ofString());
    }

    public Response<InputStream> getStream(){
        return get(HttpResponse.BodyHandlers.ofInputStream());
    }

    public Response<byte[]> getBytes() {
        return get(HttpResponse.BodyHandlers.ofByteArray());
    }

    public Response<Path> getFile(Path path) {
        return get(HttpResponse.BodyHandlers.ofFile(path));
    }

    public static List<String> get(String... urls) {
        List<URI> targets = new ArrayList<>();
        for(String url : urls){
            targets.add(URI.create(url));
        }
        List<CompletableFuture<String>> result = targets.stream().map(url -> httpClient.sendAsync(
                        HttpRequest.newBuilder(url).GET().setHeader("User-Agent", "Java 11 HttpClient").build(),
                        HttpResponse.BodyHandlers.ofString()).thenApply(response -> response.body()))
                .collect(Collectors.toList());
        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> future : result) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                Logger.error(e,e.getMessage());
            }
        }
        return results;
    }

    public static Response<String> request(HttpRequest request) {
        return request(request, HttpResponse.BodyHandlers.ofString());
    }

    public static <T> Response<T> request(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
        try {
            HttpResponse<T> httpResponse = httpClient.sendAsync(request, bodyHandler).get();
            return new Response<>(httpResponse);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
