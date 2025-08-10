package play.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import play.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Cache implementasi menggunakan Caffeine
 * Ref : https://github.com/ben-manes/caffeine
 */
public class CaffeineImpl implements CacheImpl {

    private static CaffeineImpl uniqueInstance;

    final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>(16);


    public static CaffeineImpl newInstance() {
        if(uniqueInstance == null)
            uniqueInstance = new CaffeineImpl();
        return uniqueInstance;
    }

    @Override
    public void add(String key, Object value, int expiration) {
        if (get(key) != null || value == null) {
            return;
        }
        Cache newCache = Caffeine.newBuilder().expireAfterWrite(expiration, TimeUnit.SECONDS).build();
        newCache.put(key, value);
        cacheMap.put(key, newCache);
    }

    @Override
    public void clear() {
        cacheMap.clear();
    }

    @Override
    public long decr(String key, int by) {
        Object obj = get(key);
        if (obj == null) {
            return -1;
        }
        long nv = ((Number)obj).longValue() - by;
        Cache newCache = Caffeine.newBuilder().expireAfterWrite(nv, TimeUnit.SECONDS).build();
        newCache.put(key, nv);
        cacheMap.put(key, newCache);
        return nv;
    }

    @Override
    public void delete(String key) {
        Cache cache = cacheMap.get(key);
        if (cache != null && cache.getIfPresent(key) != null) {
            cache.cleanUp();
        }
        cacheMap.remove(key);
    }

    @Override
    public Object get(String key) {
        Cache cache = cacheMap.get(key);
        return cache != null ? cache.getIfPresent(key):null;
    }

    @Override
    public Map<String, Object> get(String[] keys) {
        Map<String, Object> result = new HashMap<>(keys.length);
        for (String key : keys) {
            result.put(key, get(key));
        }
        return result;
    }

    @Override
    public long incr(String key, int by) {
        Object obj = get(key);
        if (obj == null) {
            return -1;
        }
        long nv = ((Number) obj).longValue() + by;
        Cache newCache = Caffeine.newBuilder().expireAfterWrite(nv, TimeUnit.SECONDS).build();
        newCache.put(key, nv);
        cacheMap.put(key, newCache);
        return nv;

    }

    @Override
    public void replace(String key, Object value, int expiration) {
        if (get(key) == null || value == null) {
            return;
        }
        Cache newCcache = Caffeine.newBuilder().expireAfterWrite(expiration, TimeUnit.SECONDS).build();
        newCcache.put(key, value);
        cacheMap.put(key, newCcache);
    }

    @Override
    public boolean safeAdd(String key, Object value, int expiration) {
        try {
            add(key, value, expiration);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean safeDelete(String key) {
        try {
            delete(key);
            return true;
        } catch (Exception e) {
            Logger.error(e.toString());
            return false;
        }
    }

    @Override
    public boolean safeReplace(String key, Object value, int expiration) {
        try {
            replace(key, value, expiration);
            return true;
        } catch (Exception e) {
            Logger.error(e.toString());
            return false;
        }
    }

    @Override
    public boolean safeSet(String key, Object value, int expiration) {
        try {
            set(key, value, expiration);
            return true;
        } catch (Exception e) {
            Logger.error(e.toString());
            return false;
        }
    }

    @Override
    public void set(String key, Object value, int expiration) {
        if(value == null)
            return;
        Cache cache = Caffeine.newBuilder().expireAfterWrite(expiration, TimeUnit.SECONDS).build();
        cache.put(key, value);
        cacheMap.put(key, cache);
    }

    @Override
    public void stop() {
        cacheMap.clear();
    }

    @Override
    public Map<String, Object> getAll() {
        Map<String, Object> result = new HashMap<>(cacheMap.size());
        result.putAll(cacheMap);
        return result;
    }
}
