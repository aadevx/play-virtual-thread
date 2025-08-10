package play.cache;

import org.junit.Before;
import org.junit.Test;
import play.Play;

import java.util.Properties;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CacheTest {

    @Before
    public void setUp() {
        Play.configuration = new Properties();
        Play.configuration.setProperty("play.cache.warmupPeriodMs", "1000");
    }

    @Test
    public void clearCallsImplClear() {
        Cache.cacheImpl = mock(CacheImpl.class);
        Cache.clear();
        verify(Cache.cacheImpl).clear();
    }

    @Test
    public void clearIsNullSafe_ifImplIsNotInitializedYet() {
        Cache.cacheImpl = null;
        Cache.clear();
    }
}