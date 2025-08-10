package play.db.jdbc;

import org.sql2o.DefaultResultSetHandlerFactoryBuilder;
import org.sql2o.ResultSetHandlerFactory;
import org.sql2o.quirks.Quirks;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultResultSetBuilder extends DefaultResultSetHandlerFactoryBuilder {

    private static DefaultResultSetBuilder instance = null;
    private static final Map<Class, ResultSetHandlerFactory> resultSetHandlerFactoryMap = new ConcurrentHashMap<>(1);

    public DefaultResultSetBuilder() {
        setAutoDeriveColumnNames(false);
        setCaseSensitive(false);
        setColumnMappings(Collections.emptyMap());
    }

    public static DefaultResultSetBuilder getInstance() {
        if(instance == null)
            instance = new DefaultResultSetBuilder();
        return instance;
    }

    public static <T>  ResultSetHandlerFactory<T> newResultSetHandlerFactory(Class<T> clazz, Quirks quirks) {
        ResultSetHandlerFactory obj = resultSetHandlerFactoryMap.get(clazz);
        if(obj == null) {
            DefaultResultSetBuilder defaultBuilder = getInstance();
            defaultBuilder.setQuirks(quirks);
            obj = defaultBuilder.newFactory(clazz);
            resultSetHandlerFactoryMap.put(clazz, obj);
        }
        return obj;
    }

    public static void clear() {
        resultSetHandlerFactoryMap.clear();
        instance = null;
    }
}
