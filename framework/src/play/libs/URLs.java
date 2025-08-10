package play.libs;

import play.exceptions.UnexpectedException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class URLs {

    public static String addParam(String originalUrl, String name, String value) {
        return originalUrl + (originalUrl.contains("?") ? "&" : "?") + encodePart(name) + "=" + encodePart(value);
    }

    public static String encodePart(String part) {
        try {
            return URLEncoder.encode(part, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }
}