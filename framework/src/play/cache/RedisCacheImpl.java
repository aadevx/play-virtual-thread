package play.cache;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import play.Logger;
import play.Play;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisDataException;

import java.io.*;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Play cache implementation using Redis.
 * https://github.com/tkral/play-redis
 * @author Arief Ardiyansah
 */
public class RedisCacheImpl implements CacheImpl {

    private static RedisCacheImpl uniqueInstance;
    final JedisPool jedisPool;

    private RedisCacheImpl(JedisPool jedisPool) {
    	this.jedisPool = jedisPool;
    }

    static RedisCacheImpl getInstance() throws Exception {
        if(uniqueInstance == null) {
            URI uri = new URI(Play.configuration.getProperty("redis.cache.url", "redist://localhost:6379"));
            GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
            poolConfig.setMaxTotal(Integer.valueOf(Play.configuration.getProperty("redis.cache.maxSize", "20")));
            int timeout = Integer.valueOf(Play.configuration.getProperty("redis.cache.timeout", "6000"));
            JedisPool pool = new JedisPool(poolConfig, uri.getHost(), uri.getPort(), timeout);
            uniqueInstance = new RedisCacheImpl(pool);
        }
        return uniqueInstance;
    }

    @Override
    public void add(String key, Object value, int expiration) {
    	try(Jedis client = jedisPool.getResource()){
    		if(!client.exists(key)) {
    			set(client, key, value, expiration);
    		}
    	}
    }

    @Override
    public boolean safeAdd(String key, Object value, int expiration) {
        try(Jedis client = jedisPool.getResource()){
        	if(!client.exists(key)) {
        		set(client, key, value, expiration);
    			return true;
    		}            
        } catch (Exception e) {
          Logger.error(e, "RedisCache - safeAdd : %s", e.getMessage());
        }
        return false;
    }

    @Override
    public void set(String key, Object value, int expiration) {
        // Serialize to a byte array
        try(Jedis client = jedisPool.getResource()){
        	set(client, key, value, expiration);
        }
    }

    private static byte[] toByteArray(Object o) {
        ObjectOutputStream out = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream() ;
            out = new ObjectOutputStream(bos) ;
            out.writeObject(o);
            return bos.toByteArray();
        } catch (IOException e) {
            Logger.error(e, e.getMessage());
            return null;
        } finally {
            try {
                if (out != null) out.close();
            } catch (IOException e2) {
                throw new RuntimeException(e2);
            }
        }
    }

    @Override
    public boolean safeSet(String key, Object value, int expiration) {
        try (Jedis client = jedisPool.getResource()){
        	set(client, key, value, expiration);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void replace(String key, Object value, int expiration) {
    	try(Jedis client = jedisPool.getResource()){
    		if(client.exists(key)) {
    			set(client, key, value, expiration);
    		}
        }
    }

    @Override
    public boolean safeReplace(String key, Object value, int expiration) {
    	try(Jedis client = jedisPool.getResource()){
    		if(client.exists(key)) {
    			set(client, key, value, expiration);
				return true;
    		}
    	}
    	return false;
    }

    @Override
    public Object get(String key) {
    	try(Jedis client = jedisPool.getResource()){
    		byte[] bytes = client.get(key.getBytes());
    		if (bytes == null) 
    			return null;
    		return fromByteArray(bytes);
    	}
    }
    
    private void set(Jedis client, String key, Object value, int expiration) {
    	byte[] bytes = toByteArray(value);
    	if(bytes != null) {
			client.set(key.getBytes(), bytes);
			client.expire(key, expiration);
		}
    }

    private static Object fromByteArray(byte[] bytes) {
        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream(new ByteArrayInputStream(bytes)) {
                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc)
                        throws IOException, ClassNotFoundException {
                    return Class.forName(desc.getName(), false, Play.classloader);
                }
            };
            return in.readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (in != null) in.close();
            } catch (IOException e2) {
                throw new RuntimeException(e2);
            }
        }
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
        try(Jedis client = jedisPool.getResource()){
        	 Object cacheValue = client.get(key);
             long sum = 0L;
        	if (cacheValue == null) {
    			Long newCacheValueLong = Long.valueOf((long) by);
    			client.set(key.getBytes(), toByteArray(newCacheValueLong));
    			sum = newCacheValueLong.longValue();
    		} else if (cacheValue instanceof Integer value) {
    			Integer newCacheValueInteger = value + by;
    			client.set(key.getBytes(), toByteArray(newCacheValueInteger));
    			sum = newCacheValueInteger.longValue();
    		} else if (cacheValue instanceof Long value) {
    			Long newCacheValueLong = value + by;
    			client.set(key.getBytes(), toByteArray(newCacheValueLong));
    			sum = newCacheValueLong.longValue();
    		} else {
    			throw new JedisDataException("Cannot incr on non-integer value (key: " + key + ")");
    		}    		
    		return sum;
        }
    }

    @Override
    public long decr(String key, int by) {
        try(Jedis client = jedisPool.getResource()){
        	 Object cacheValue = client.get(key);
             long difference = 0L;
        	if (cacheValue == null) {
    			Long newCacheValueLong = Long.valueOf((long) -by);
    			client.set(key.getBytes(), toByteArray(newCacheValueLong));
    			difference = newCacheValueLong.longValue();
    		} else if (cacheValue instanceof Integer value) {
    			Integer newCacheValueInteger = value - by;
    			client.set(key.getBytes(), toByteArray(newCacheValueInteger));
    			difference = newCacheValueInteger.longValue();
    		} else if (cacheValue instanceof Long value) {
    			Long newCacheValueLong = value - by;
    			client.set(key.getBytes(), toByteArray(newCacheValueLong));
    			difference = newCacheValueLong.longValue();
    		} else {
    			throw new JedisDataException("Cannot decr on non-integer value (key: " + key + ")");
    		}
    		
    		return difference;
        }
    }

    @Override
    public void clear() {
    	try(Jedis client = jedisPool.getResource()){
    		client.flushDB();
    	}
    }

    @Override
    public void delete(String key) {
    	try(Jedis client = jedisPool.getResource()){
    		client.del(key);
    	}
    }

    @Override
    public boolean safeDelete(String key) {
        try (Jedis client = jedisPool.getResource()) {
        	client.del(key);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void stop() {
    	try(Jedis client = jedisPool.getResource()){
    		client.flushAll();
    	}
    	jedisPool.destroy();
    }

    @Override
    public Map<String, Object> getAll() {
        throw new NotImplementedException("NOT YET IMPLEMENTED");
    }

}