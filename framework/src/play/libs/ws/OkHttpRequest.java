package play.libs.ws;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import play.Logger;
import play.libs.IO;
import play.libs.Time;
import play.libs.XML;

import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class OkHttpRequest {

    OkHttpClient client;
    final String url;
    final Request.Builder requestBuilder;
    RequestBody requestBody;
    FileParam[] fileParams;
    final Map<String, Object> parameters = new HashMap<>();

    public OkHttpRequest(OkHttpClient client, String url) {
        this.client = client;
        this.url = url;
        this.requestBuilder = new Request.Builder().url(url).addHeader("User-Agent", "Mozilla/5.0 client SPSE");
    }


    /**
     * Add files to request. This will only work with POST or PUT.
     *
     * @param files
     *            list of files
     * @return the WSRequest for chaining.
     */
    public OkHttpRequest files(File... files) {
        this.fileParams = FileParam.getFileParams(files);
        return this;
    }

    /**
     * Add fileParams aka File and Name parameter to the request. This will only work with POST or PUT.
     *
     * @param fileParams
     *            The fileParams list
     * @return the WSRequest for chaining.
     */
    public OkHttpRequest files(FileParam... fileParams) {
        this.fileParams = fileParams;
        return this;
    }

    /**
     * Add the given body to the request.
     *
     * @param body
     *            The request body
     * @return the WSRequest for chaining.
     */
    public OkHttpRequest body(Object body) {
        if (body != null) {
            if (this.parameters != null && !this.parameters.isEmpty()) {
                throw new RuntimeException("POST or PUT method with parameters AND body are not supported.");
            }
            if (body instanceof InputStream is) {
                byte[] bodyBytes = IO.readContent(is);
                requestBody = RequestBody.create(bodyBytes);
            } else {
                byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
                requestBody = RequestBody.create(bodyBytes);
            }
        }
        return this;
    }

    /**
     * Add a header to the request
     *
     * @param name
     *            header name
     * @param value
     *            header value
     * @return the WSRequest for chaining.
     */
    public OkHttpRequest setHeader(String name, String value) {
        requestBuilder.addHeader(name, value);
        return this;
    }

    public OkHttpRequest mimeType(String mimeType) {
        requestBuilder.addHeader("Content-Type", mimeType);
        return this;
    }

    /**
     * Add a parameter to the request
     *
     * @param name
     *            parameter name
     * @param value
     *            parameter value
     * @return the WSRequest for chaining.
     */
    public OkHttpRequest setParameter(String name, String value) {
        this.parameters.put(name, value);
        return this;
    }

    public OkHttpRequest setParameter(String name, Object value) {
        this.parameters.put(name, value);
        return this;
    }

    /**
     * Add parameters to request. If POST or PUT, parameters are passed in body using x-www-form-urlencoded if
     * alone, or form-data if there is files too. For any other method, those params are appended to the
     * queryString.
     *
     * @param parameters
     *            The request parameters
     *
     * @return the OkHttpRequest for chaining.
     */
    public OkHttpRequest params(Map<String, Object> parameters) {
        this.parameters.putAll(parameters);
        return this;
    }

    /**
     * Add parameters to request. If POST or PUT, parameters are passed in body using x-www-form-urlencoded if
     * alone, or form-data if there is files too. For any other method, those params are appended to the
     * queryString.
     *
     * @param parameters
     *            The request parameters
     *
     * @return the WSRequest for chaining.
     */
    public OkHttpRequest setParameters(Map<String, String> parameters) {
        this.parameters.putAll(parameters);
        return this;
    }

    public OkHttpRequest authenticate(String username, String password) {
        String credential = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        requestBuilder.addHeader("Authorization", credential);
        return this;
    }

    Request prepareRequest(String method) {
        if (this.fileParams != null) {
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            // could be optimized, we know the size of this array.
            for (FileParam fileParam : fileParams) {
                builder.addFormDataPart(fileParam.paramName, fileParam.file.getName(),
                        RequestBody.create(fileParam.file, MediaType.get(fileParam.contentType)));
            }
            if (this.parameters != null) {
                parameters.forEach((key, value) -> {
                    if (value instanceof Collection<?> || value.getClass().isArray()) {
                        Collection<?> values = value.getClass().isArray() ? Arrays.asList((Object[]) value) : (Collection<?>) value;
                        for (Object v : values) {
                            MultipartBody.Part part = MultipartBody.Part.createFormData(key, v.toString());
                            builder.addPart(part);
                        }
                    } else {
                        MultipartBody.Part part = MultipartBody.Part.createFormData(key, value.toString());
                        builder.addPart(part);
                    }
                });
            }
            requestBody = builder.build();
        }
        if (this.parameters != null && !this.parameters.isEmpty()) {
            boolean isPostPut = "POST".equals(method) || ("PUT".equals(method));
            if (isPostPut) {
                FormBody.Builder builder = new FormBody.Builder();
                for (Map.Entry<String, Object> entry : this.parameters.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (value == null)
                        continue;

                    if (value instanceof Collection<?> || value.getClass().isArray()) {
                        Collection<?> values = value.getClass().isArray() ? Arrays.asList((Object[]) value) : (Collection<?>) value;
                        for (Object v : values) {
                            builder.add(key, v.toString());
                        }
                    } else {
                        builder.add(key, value.toString());
                    }
                }
                requestBody = builder.build();
            } else {
                HttpUrl.Builder builder = HttpUrl.parse(url).newBuilder();
                for (Map.Entry<String, Object> entry : this.parameters.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (value == null)
                        continue;
                    if (value instanceof Collection<?> || value.getClass().isArray()) {
                        Collection<?> values = value.getClass().isArray() ? Arrays.asList((Object[]) value) : (Collection<?>) value;
                        for (Object v : values) {
                            builder.addQueryParameter(key, v.toString());
                        }
                    } else {
                        // must encode it since AHC uses raw urls
                        builder.addQueryParameter(key, value.toString());
                    }
                }
                requestBuilder.url(builder.build());
            }
        }
        requestBuilder.method(method, requestBody);
        return requestBuilder.build();
    }

    Response execute(String method) {
        Request request = prepareRequest(method);
        CompletableFuture<Response> promise = new CompletableFuture<>();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Logger.debug(e, "[OkHttpClient] onFailure for %s : %s", request, e.getLocalizedMessage());
                promise.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                Logger.debug("[OkHttpClient] onResponse for %s : %s", request, response.code());
                promise.complete(response);
            }
        });
        return promise.join();
    }

    public void enqueue(Callback callback, String method) {
        client.newCall(prepareRequest(method)).enqueue(callback);
    }

    public Response get(){
        return execute("GET");
    }

    public HttpResponse getResponse() {
        return new HttpResponse(get());
    }

    public Response post(){
        return execute("POST");
    }

    public HttpResponse postResponse() {
        return new HttpResponse(post());
    }

    public Response put(){
        return execute("PUT");
    }

    public HttpResponse putResponse() {
        return new HttpResponse(put());
    }

    public Response delete(){
        return execute("DELETE");
    }

    public HttpResponse deleteResponse() {
        return new HttpResponse(delete());
    }

    public Response patch(){
        return execute("PATCH");
    }

    public HttpResponse pathResponse() {
        return new HttpResponse(patch());
    }

    public Document getXml() {
        try(Response response = get()){
            if(response != null) {
                InputSource source = new InputSource(response.body().charStream());
                source.setEncoding(StandardCharsets.UTF_8.toString());
                DocumentBuilder builder = XML.newDocumentBuilder(false);
                return builder.parse(source);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * get the response as a stream
     *
     * @return an inputstream
     */
    public InputStream getStream() {
        try(Response response = get()){
            return response.body().byteStream();
        }  catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * get the response as a string
     *
     * @return an string
     */
    public String getString() {
        try(Response response = get()){
            return response.body().string();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * get the response body as a {@link com.google.gson.JsonElement}
     *
     * @return the json response
     */
    public JsonElement getJson() {
        String json = null;
        try(Response response = get()){
            json = response.body().string();
            return JsonParser.parseString(json);
        } catch (Exception e) {
//            Logger.error("Bad JSON: \n%s", json);
            throw new RuntimeException("Cannot parse JSON (check logs) : %s", e.getCause());
        }
    }

    public OkHttpRequest setTimeout(String timeout) {
        int second = Time.parseDuration(timeout);
        client = client.newBuilder().connectTimeout(second, TimeUnit.SECONDS).readTimeout(second, TimeUnit.SECONDS)
                .writeTimeout(second, TimeUnit.SECONDS).callTimeout(second, TimeUnit.SECONDS).build();
        return this;
    }
//
//    public OkHttpRequest setFollowRedirects(boolean followRedirects) {
//        client  = client.newBuilder().followRedirects(followRedirects).followSslRedirects(followRedirects).build();
//        return this;
//    }

    public OkHttpRequest addInterceptor(Interceptor interceptor) {
        client  = client.newBuilder().addInterceptor(interceptor).build();
        return this;
    }

    public OkHttpRequest authenticator(Authenticator authenticator) {
        client  = client.newBuilder().authenticator(authenticator).build();
        return this;
    }
}
