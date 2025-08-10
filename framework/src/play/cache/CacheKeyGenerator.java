package play.cache;

import play.mvc.Http;

/**
 * Allow custom cache key to be used by applications.
 */

public interface CacheKeyGenerator {

    String generate(Http.Request request);

}
