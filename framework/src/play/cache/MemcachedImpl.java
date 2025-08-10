package play.cache;

import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.MemcachedClientBuilder;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import net.rubyeye.xmemcached.auth.AuthInfo;
import net.rubyeye.xmemcached.command.BinaryCommandFactory;
import net.rubyeye.xmemcached.transcoders.SerializingTranscoder;
import net.rubyeye.xmemcached.utils.AddrUtil;
import org.apache.commons.lang3.NotImplementedException;
import play.Logger;
import play.Play;
import play.exceptions.ConfigurationException;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Memcached implementation (using http://code.google.com/p/spymemcached/)
 *
 * expiration is specified in seconds
 */
public class MemcachedImpl implements CacheImpl {

    private static MemcachedImpl uniqueInstance;

    MemcachedClient client;

    final SerializingTranscoder tc;

    public static MemcachedImpl getInstance() throws IOException {
        return getInstance(false);
    }

    public static MemcachedImpl getInstance(boolean forceClientInit) throws IOException {
        if (uniqueInstance == null) {
            uniqueInstance = new MemcachedImpl();
        } else if (forceClientInit) {
            // When you stop the client, it sets the interrupted state of this thread to true. If you try to reinit it with the same thread in this state,
            // Memcached client errors out. So a simple call to interrupted() will reset this flag
            Thread.interrupted();
            uniqueInstance.initClient();
        }
        return uniqueInstance;

    }

    private MemcachedImpl() throws IOException {
        tc = new SerializingTranscoder() {

            @Override
            protected Object deserialize(byte[] data) {
                try {
                    return new ObjectInputStream(new ByteArrayInputStream(data)) {

                        @Override
                        protected Class<?> resolveClass(ObjectStreamClass desc)
                                throws ClassNotFoundException {
                            return Class.forName(desc.getName(), false, Play.classloader);
                        }
                    }.readObject();
                } catch (Exception e) {
                    Logger.error(e, "Could not deserialize");
                }
                return null;
            }

            @Override
            protected byte[] serialize(Object object) {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    new ObjectOutputStream(bos).writeObject(object);
                    return bos.toByteArray();

                } catch (IOException e) {
                    Logger.error(e, "Could not serialize");
                }
                return null;
            }
        };
        initClient();
    }

    public void initClient() throws IOException {
//        System.setProperty("net.spy.log.LoggerImpl", "net.spy.memcached.compat.log.Log4JLogger");

        List<InetSocketAddress> addrs;
        if (Play.configuration.containsKey("memcached.host")) {
            addrs = AddrUtil.getAddresses(Play.configuration.getProperty("memcached.host"));
        } else if (Play.configuration.containsKey("memcached.1.host")) {
            int nb = 1;
            StringBuilder addresses = new StringBuilder();
            while (Play.configuration.containsKey("memcached." + nb + ".host")) {
                addresses.append(Play.configuration.get("memcached." + nb + ".host")).append(" ");
                nb++;
            }
            addrs = AddrUtil.getAddresses(addresses.toString());
        } else {
            throw new ConfigurationException("Bad configuration for memcached: missing host(s)");
        }
        MemcachedClientBuilder builder = new XMemcachedClientBuilder(addrs);
        if (Play.configuration.containsKey("memcached.user")) {
            String memcacheUser = Play.configuration.getProperty("memcached.user");
            String memcachePassword = Play.configuration.getProperty("memcached.password");
            if (memcachePassword == null) {
                throw new ConfigurationException("Bad configuration for memcached: missing password");
            }
            // Use plain SASL to connect to memcached
            for(InetSocketAddress addr : addrs)
                builder.addAuthInfo(addr, AuthInfo.plain(memcacheUser, memcachePassword));
            builder.setCommandFactory(new BinaryCommandFactory());
        }
        client = builder.build();
    }

    @Override
    public void add(String key, Object value, int expiration) {
        try {
            client.add(key, expiration, value, tc);
        } catch (Exception e) {
            Logger.error(e, "[MemcachedImpl] add - %s", e.getMessage());
        }
    }

    @Override
    public Object get(String key) {
        try {
            return client.get(key, tc);
        } catch (Exception e) {
            Logger.error(e, "[MemcachedImpl] get - %s", e.getMessage());
        }
        return null;
    }

    @Override
    public void clear() {
        try {
            client.flushAll();
        } catch (Exception e) {
            Logger.error(e, "[MemcachedImpl] clear - %s", e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.delete(key);
        } catch (Exception e) {
            Logger.error(e, "[MemcachedImpl] delete - %s", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> get(String[] keys) {
        try {
            return client.get(Arrays.asList(keys), tc);
        }catch (Exception e){
            Logger.error(e, "[MemcachedImpl] get - %s", e.getMessage());
        }
        return Collections.<String, Object>emptyMap();
    }

    @Override
    public long incr(String key, int by) {
        try {
            return client.incr(key, by, 0);
        } catch (Exception e) {
            Logger.error(e, "[MemcachedImpl] incr - %s", e.getMessage());
            return 0L;
        }
    }

    @Override
    public long decr(String key, int by) {
        try {
            return client.decr(key, by, 0);
        } catch (Exception e) {
            Logger.error(e, "[MemcachedImpl] decr -  %s", e.getMessage());
            return 0L;
        }
    }

    @Override
    public void replace(String key, Object value, int expiration) {
        try {
            client.replace(key, expiration, value, tc);
        } catch (Exception e) {
            Logger.error(e, "[MemcachedImpl] replace - %s", e.getMessage());
        }
    }

    @Override
    public boolean safeAdd(String key, Object value, int expiration) {
        try {
            return client.add(key, expiration, value, tc);
        } catch (Exception e) {
            Logger.error(e, "[MemcachedImpl] safeAdd - %s", e.getMessage());
        }
        return false;
    }

    @Override
    public boolean safeDelete(String key) {
        try {
            return client.delete(key);
        } catch (Exception e) {
            Logger.error(e, "[MemcachedImpl] safeDelete - %s", e.getMessage());
        }
        return false;
    }

    @Override
    public boolean safeReplace(String key, Object value, int expiration) {
        try {
            return client.replace(key, expiration, value, tc);
        } catch (Exception e) {
            Logger.error(e, "[MemcachedImpl] safeReplace - %s", e.getMessage());
        }
        return false;
    }

    @Override
    public boolean safeSet(String key, Object value, int expiration) {
        try {
            return client.set(key, expiration, value, tc);
        } catch (Exception e) {
            Logger.error(e, "[MemcachedImpl] safeSet - %s", e.getMessage());
        }
        return false;
    }

    @Override
    public void set(String key, Object value, int expiration) {
        try {
            client.set(key, expiration, value, tc);
        } catch (Exception e) {
            Logger.error(e, "[MemcachedImpl] safeSet - %s", e.getMessage());
        }
    }

    @Override
    public void stop() {
        try {
            client.shutdown();
        } catch (IOException e) {
            Logger.error(e, "[MemcachedImpl] stop - %s", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getAll() {
        throw new NotImplementedException("NOT YET IMPLEMENTED");
    }
}