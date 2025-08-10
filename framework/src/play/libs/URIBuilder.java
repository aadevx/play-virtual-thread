package play.libs;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * A utility class to create and parse uri's
 * ref : https://github.com/BastiaanJansen/uri-builder-java
 * @author Bastiaan Jansen
 */
public class URIBuilder {

    private String scheme;
    private String authority;
    private String path;
    private Map<String, String> query;
    private String fragment;

    public URIBuilder() {
        query = new HashMap<>();
    }

    public static URIBuilder parse(String uri) {
        return new URIBuilder(URI.create(uri));
    }

    public URIBuilder(URI uri) {
        this.scheme = uri.getScheme();
        this.authority = uri.getAuthority();
        this.path = uri.getPath();
        this.fragment = uri.getFragment();

        if (uri.getQuery() != null)
            this.query = queryToMap(uri.getQuery());
        else
            this.query = new HashMap<>();
    }

    public URIBuilder withScheme(String scheme) {
        this.scheme = scheme;
        return this;
    }

    public URIBuilder withQueryParameter(String key, String value) {
        query.put(key, value);
        return this;
    }

    public URIBuilder withQuery(Map<String, String> query) {
        this.query = query;
        return this;
    }

    public URIBuilder withAuthority(String authority) {
        this.authority = authority;
        return this;
    }

    public URIBuilder withPath(String path) {
        this.path = path;
        return this;
    }

    public URIBuilder withFragment(String fragment) {
        this.fragment = fragment;
        return this;
    }

    public URI build()  {
        try {
            return new URI(scheme, authority, path, queryToString(query), fragment);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public String getScheme() {
        return scheme;
    }

    public String getAuthority() {
        return authority;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getQuery() {
        return query;
    }

    public String getFragment() {
        return fragment;
    }

    private Map<String, String> queryToMap(String query) {
        Map<String, String> map = new HashMap<>();
        String[] queryParameters = query.split("&");

        for (String parameter: queryParameters) {
            String[] keyValuePair = parameter.split("=");
            map.put(keyValuePair[0], keyValuePair[1]);
        }

        return map;
    }

    private String queryToString(Map<String, String> query) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> queryParameter: query.entrySet()) {
            if (!builder.toString().isEmpty())
                builder.append("&");
            builder
                    .append(queryParameter.getKey())
                    .append("=")
                    .append(queryParameter.getValue());
        }

        return builder.toString().isEmpty() ? null : builder.toString();
    }
}