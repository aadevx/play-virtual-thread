package play.libs.ws;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import okhttp3.Headers;
import okhttp3.Response;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import play.Logger;
import play.Play;
import play.libs.XML;
import play.mvc.Http;
import play.utils.HTTP;

import javax.xml.parsers.DocumentBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An HTTP response wrapper
 */
public class HttpResponse {

    private String _encoding = null;
    private final Integer code;
    private final String message;
    private final Headers headers;
    private final String body;
    private final InputStream stream;

    public HttpResponse(Response response) {
        this.code = response.code();
        this.message = response.message();
        this.headers = response.headers();
        this.stream = response.body().byteStream();
        try {
            this.body = response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * the HTTP status code
     *
     * @return the status code of the http response
     */
    public Integer getStatus() {
        return code;
    }

    /**
     * The HTTP status text
     *
     * @return the status text of the http response
     */
    public String getStatusText() {
        return message;
    }

    /**
     * @return true if the status code is 20x, false otherwise
     */
    public boolean success() {
        return Http.StatusCode.success(this.getStatus());
    }

    /**
     * The http response content type
     *
     * @return the content type of the http response
     */
    public String getContentType() {
        return getHeader("content-type") != null ? getHeader("content-type") : getHeader("Content-Type");
    }

    public String getEncoding() {
        // Have we already parsed it?
        if (_encoding != null) {
            return _encoding;
        }

        // no! must parse it and remember
        String contentType = getContentType();
        if (contentType == null) {
            _encoding = Play.defaultWebEncoding;
        } else {
            HTTP.ContentTypeWithEncoding contentTypeEncoding = HTTP.parseContentType(contentType);
            if (contentTypeEncoding.encoding == null) {
                _encoding = Play.defaultWebEncoding;
            } else {
                _encoding = contentTypeEncoding.encoding;
            }
        }
        return _encoding;

    }

    public String getHeader(String key) {
        return headers.get(key);
    }

    public List<Http.Header> getHeaders(){
        List<Http.Header> result = new ArrayList<>();
        for (String key : headers.names()) {
            List<String> values = headers.values(key);
            if(values != null && !values.isEmpty()) {
                for(String value : values)
                    result.add(new Http.Header(key, value));
            }
        }
        return result;
    }

    /**
     * Parse and get the response body as a {@link Document DOM document}
     *
     * @return a DOM document
     */
    public Document getXml() {
        return getXml(false);
    }

    /**
     * Parse and get the response body as a {@link Document DOM document}
     *
     * @param namespaceAware
     *            whether to output XML namespace information in the returned document
     * @return a DOM document
     */
    public Document getXml(boolean namespaceAware) {
        return getXml(getEncoding(), namespaceAware);
    }

    /**
     * parse and get the response body as a {@link Document DOM document}
     *
     * @param encoding
     *            xml charset encoding
     * @return a DOM document
     */
    public Document getXml(String encoding) {
        return getXml(encoding, false);
    }

    /**
     * parse and get the response body as a {@link Document DOM document}
     *
     * @param encoding
     *            xml charset encoding
     * @param namespaceAware
     *           whether to output XML namespace information in the returned document
     * @return a DOM document
     */
    public Document getXml(String encoding, boolean namespaceAware) {
        try {
            InputSource source = new InputSource(new StringReader(getString()));
            source.setEncoding(encoding);
            DocumentBuilder builder = XML.newDocumentBuilder(namespaceAware);
            return builder.parse(source);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * get the response body as a string
     *
     * @return the body of the http response
     */
    public String getString() {
        return body;
    }

    /**
     * Parse the response string as a query string.
     *
     * @return The parameters as a Map. Return an empty map if the response is not formed as a query string.
     */
    public Map<String, String> getQueryString() {
        Map<String, String> result = new HashMap<>();
        String body = getString();
        for (String entry : body.split("&")) {
            int pos = entry.indexOf("=");
            if (pos > -1) {
                result.put(entry.substring(0, pos), entry.substring(pos + 1));
            } else {
                result.put(entry, "");
            }
        }
        return result;
    }

    /**
     * get the response as a stream
     * <p>
     * + this method can only be called onced because async implementation does not allow it to be called + multiple
     * times +
     * </p>
     *
     * @return an inputstream
     */
    public InputStream getStream() {
        return stream;
    }

    /**
     * get the response body as a {@link com.google.gson.JsonElement}
     *
     * @return the json response
     */
    public JsonElement getJson() {
        String json = getString();
        try {
            return JsonParser.parseString(json);
        } catch (Exception e) {
            Logger.error("Bad JSON: \n%s", json);
            throw new RuntimeException("Cannot parse JSON (check logs)", e);
        }
    }

}
