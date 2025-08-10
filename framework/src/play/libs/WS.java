package play.libs;

import okhttp3.OkHttpClient;
import play.Logger;
import play.PlayPlugin;
import play.libs.ws.OkHttpRequest;
import play.libs.ws.WSSSLContext;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Simple HTTP client to make webservices requests.
 *
 * <p>
 * Get latest BBC World news as a RSS content
 *
 * <pre>
 * HttpResponse response = WS.url("http://newsrss.bbc.co.uk/rss/newsonline_world_edition/front_page/rss.xml").get();
 * Document xmldoc = response.getXml();
 * // the real pain begins here...
 * </pre>
 * <p>
 *
 * Search what Yahoo! thinks of google (starting from the 30th result).
 *
 * <pre>
 * HttpResponse response = WS.url("http://search.yahoo.com/search?p=<em>%s</em>&amp;pstart=1&amp;b=<em>%s</em>", "Google killed me", "30").get();
 * if (response.getStatus() == 200) {
 *     html = response.getString();
 * }
 * </pre>
 */
public class WS extends PlayPlugin {

    /**
     * Singleton configured with default encoding - this one is used when calling static method on WS.
     */
    private static OkHttpClient client;

    @Override
    public void onApplicationStop() {
        client = null;
    }

    /**
     * Build a WebService Request with the given URL. This object support chaining style programming for adding params,
     * file, headers to requests.
     *
     * @param url
     *            of the request
     * @return a WSRequest on which you can add params, file headers using a chaining style programming.
     */
    public static OkHttpRequest url(String url) {
        return new OkHttpRequest(okHttpClient(), url);
    }

    /**
     * Build a WebService Request with the given URL. This object support chaining style programming for adding params,
     * file, headers to requests.
     * Use OKHttpClient
     * ref : https://square.github.io/okhttp
     * @param url
     *            of the request
     * @return a WSRequest on which you can add params, file headers using a chaining style programming.
     */
    public static OkHttpRequest okUrl(String url) {
        return new OkHttpRequest(okHttpClient(), url);
    }

    public static OkHttpClient okHttpClient() {
        if(client == null) {
            try {
                final SSLSocketFactory sslSocketFactory = WSSSLContext.getSslContext().getSocketFactory();
                client = new OkHttpClient.Builder().sslSocketFactory(sslSocketFactory, (X509TrustManager)WSSSLContext.trustAllCerts[0])
                        .hostnameVerifier((hostname, session) -> true).build();
            }catch (Exception e) {
                Logger.error(e,"initial okhttpclient failed");
            }
        }
        return client;
    }

}