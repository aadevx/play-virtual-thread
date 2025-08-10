package play.libs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class Response<T> {

    private final HttpResponse<T> httpResponse;
    private final String message;

    public Response(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        this.message = message(httpResponse.statusCode());
    }

    public HttpRequest request() {
        return httpResponse.request();
    }


    public HttpHeaders headers() {
        return httpResponse.headers();
    }

    public T body() {
        return httpResponse.body();
    }

    public URI uri() {
        return httpResponse.uri();
    }

    public HttpClient.Version version() {
        return httpResponse.version();
    }

    public String message() {
        return message;
    }

    public int code() {
        return httpResponse.statusCode();
    }

    public String header(String key) {
        return httpResponse.headers().firstValue(key).orElse(null);
    }

    public String contentType() {
        String contentType = header("content-type");
        if(contentType == null)
            contentType = header( "Content-Type");
        return contentType;
    }

    private String message(int code) {
        String message = null;
        switch (code) {
            case 200: message = "OK"; break;
            case 201: message = "CREATED"; break;
            case 202: message = "ACCEPTED"; break;
            case 203: message = "PARTIAL_INFO"; break;
            case 204: message = "NO_RESPONSE"; break;
            case 206: message = "PARTIAL_CONTENT"; break;
            case 301: message = "MOVED"; break;
            case 302: message = "FOUND"; break;
            case 303: message = "METHOD"; break;
            case 304: message = "NOT_MODIFIED"; break;
            case 400: message = "BAD_REQUEST"; break;
            case 401: message = "UNAUTHORIZED"; break;
            case 402: message = "PAYMENT_REQUIRED"; break;
            case 403: message = "FORBIDDEN"; break;
            case 404: message = "NOT_FOUND"; break;
            case 500: message = "INTERNAL_ERROR"; break;
            case 501: message = "NOT_IMPLEMENTED"; break;
            case 502: message = "OVERLOADED"; break;
            case 503: message = "GATEWAY_TIMEOUT"; break;
        }
        return message;
    }

    public boolean isSuccess() {
        return httpResponse.statusCode() / 100 == 2;
    }

    public boolean isRedirect() {
        return httpResponse.statusCode() / 100 == 3;
    }

    public boolean isError() {
        return httpResponse.statusCode() / 100 == 4 || httpResponse.statusCode() / 100 == 5;
    }
}
