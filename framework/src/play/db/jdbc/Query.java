package play.db.jdbc;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;
import org.sql2o.*;
import org.sql2o.converters.Converter;
import org.sql2o.converters.ConverterException;
import org.sql2o.data.LazyTable;
import org.sql2o.data.Row;
import org.sql2o.data.Table;
import org.sql2o.data.TableResultSetIterator;
import org.sql2o.quirks.Quirks;
import org.sql2o.reflection2.PojoIntrospector;
import play.Logger;
import play.Play;
import play.db.DB;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.*;
import java.util.*;

import static org.sql2o.converters.Convert.throwIfNull;


public final class Query {

    private static final Integer DEFAULT_FETCH_SIZE = Integer.valueOf(Play.configuration.getProperty("jdbc.fetchSize", "100"));
    private final Connection connection;
    private final Quirks quirks;
    private final boolean returnGeneratedKeys;
    private final Map<String, List<Integer>> paramNameToIdxMap;
    private final PreparedStatement statement;
    private final String parsedQuery;
    private Integer fetchSize;
    private boolean autocloseConnection;
    private List<Object> keys;

    private static final Class[] primitives = new Class[] {
            Integer.class,
            Double.class,
            String.class,
            Float.class,
            Long.class,
            Boolean.class,
            BigDecimal.class
    };

    @Override
    public String toString() {
        return parsedQuery;
    }

    public static <T> ResultSetIterable<T> findLazy(QueryBuilder builder, ResultSetHandler<T> resultSetHandler) {
        return builder.fetchLazy(resultSetHandler);
    }

    public static <T> ResultSetIterable<T> findLazy(String sql, ResultSetHandler<T> resultSetHandler, Object... params) {
        return findLazy(sql, false, resultSetHandler, params);
    }

    public static <T> ResultSetIterable<T> findLazy(String sql, boolean autocloseConnection, ResultSetHandler<T> resultSetHandler, Object... params) {
        return findLazy(QueryBuilder.create(sql, params).setAutocloseConnection(autocloseConnection), resultSetHandler);
    }

    public static <T> ResultSetIterable<T> findLazy(QueryBuilder builder, Class<T> returnType) {
        return builder.fetchLazy(returnType);
    }

    public static <T> ResultSetIterable<T> findLazy(String sql, Class<T> returnType, Object... params) {
        return findLazy(sql, false, returnType, params);
    }

    public static <T> ResultSetIterable<T> findLazy(String sql, boolean autocloseConnection, Class<T> returnType, Object... params) {
        return findLazy(new QueryBuilder(sql, params).setAutocloseConnection(autocloseConnection), returnType);
    }

    public static Table findTable(QueryBuilder builder) {
        return builder.fetchTable();
    }

    public static Table findTable(String sql, Object... params) {
        return findTable(sql, false, params);
    }

    public static Table findTable(String sql, boolean autocloseConnection, Object... params) {
        return findTable(new QueryBuilder(sql, params).setAutocloseConnection(autocloseConnection));
    }

    public static <T> T findObject(QueryBuilder builder, ResultSetHandler<T> resultSetHandler) {
        return builder.fetchFirst(resultSetHandler);
    }

    public static <T> T findObject(String sql, ResultSetHandler<T> resultSetHandler, Object... params) {
        return findObject(sql, false, resultSetHandler, params);
    }

    public static <T> T findObject(String sql, boolean autocloseConnection, ResultSetHandler<T> resultSetHandler, Object... params) {
        return findObject(new QueryBuilder(sql, params).setAutocloseConnection(autocloseConnection), resultSetHandler);
    }

    public static <T> T findObject(QueryBuilder builder, Class<T> clazz) {
        return builder.fetchFirst(clazz);
    }

    public static <T> T findObject(String sql, Class<T> returnType, Object... params) {
        return findObject(sql, false, returnType, params);
    }

    public static <T> T findObject(String sql, boolean autocloseConnection, Class<T> returnType, Object... params) {
        return findObject(new QueryBuilder(sql, params).setAutocloseConnection(autocloseConnection), returnType);
    }

    public static <T> List<T> findList(QueryBuilder builder, ResultSetHandler<T> resultSetHandler) {
        return builder.fetch(resultSetHandler);
    }

    public static <T> List<T> findList(String sql, ResultSetHandler<T> resultSetHandler, Object... params) {
        return findList(sql, false, resultSetHandler, params);
    }

    public static <T> List<T> findList(String sql, boolean autocloseConnection, ResultSetHandler<T> resultSetHandler, Object... params) {
        return findList(new QueryBuilder(sql, params).setAutocloseConnection(autocloseConnection), resultSetHandler);
    }

    public static <T> List<T> findList(QueryBuilder builder, Class<T> clazz) {
        return builder.fetch(clazz);
    }

    public static <T> List<T> findList(String sql, Class<T> returnType, Object... params) {
        return findList(sql, false, returnType, params);
    }

    public static <T> List<T> findList(String sql, boolean autocloseConnection, Class<T> returnType, Object... params) {
        return findList(new QueryBuilder(sql, params).setAutocloseConnection(autocloseConnection), returnType);
    }

    // gunakan method jika memakai db 'default'
    public static long count(String sql, Object... params) {
        return count(sql, false, params);
    }

    public static long count(String sql, boolean autocloseConnection, Object... params) {
        return count(new QueryBuilder(sql, params).setAutocloseConnection(autocloseConnection));
    }

    public static long count(QueryBuilder builder) {
        return builder.count();
    }

    public static <V> V count(String sql, Class<V> clazz, Object... params) {
        return count(sql, false, clazz, params);
    }

    public static <V> V count(String sql, boolean autocloseConnection, Class<V> clazz, Object... params) {
        return count(new QueryBuilder(sql, params).setAutocloseConnection(autocloseConnection), clazz);
    }

    public static <V> V count(QueryBuilder builder, Class<V> clazz) {
        return builder.count(clazz);
    }

    public static int bindCount(String sql, Object object) {
        return bindCount(sql, false, object);
    }

    public static int bindCount(String sql, boolean autocloseConnection, Object object) {
        return bindCount(new QueryBuilder(sql).setAutocloseConnection(autocloseConnection), object);
    }

    public static int bindCount(QueryBuilder builder, Object object) {
        return builder.bindCount(object);
    }


    public static int update(String sql, Object... params) {
        return update(new QueryBuilder(sql, params));
    }

    public static int update(QueryBuilder builder) {
        return builder.update();
    }

    /**
     * Binding lalu update. Jika ada kolom yg autoincrement, maka method ini return nilai kolom
     * tersebut.
     */
    public static int bindUpdate(String sql, Object object) {
        return bindUpdate(sql, false, object);
    }

    public static int bindUpdate(String sql, boolean autocloseConnection, Object object) {
        return bindUpdate(new QueryBuilder(sql).setAutocloseConnection(autocloseConnection), object);
    }

    /**
     * Binding lalu update. Jika ada kolom yg autoincrement, maka method ini return nilai kolom
     * tersebut.
     */
    public static int bindUpdate(QueryBuilder builder, Object object) {
        return builder.bindUpdate(object);
    }

    public static void bindUpdateAll(String sql, List<?> list) {
        bindUpdateAll(new QueryBuilder(sql), list);
    }

    public static void bindUpdateAll(QueryBuilder builder, List<?> list) {
        builder.bindUpdateAll(list);
    }

    public static <V> List<V> bindUpdateAll(String sql, List<?> list, String[] generatedColumns, Class<V> type) {
        return bindUpdateAll(new QueryBuilder(sql), list, generatedColumns, type);
    }

    public static <V> List<V> bindUpdateAll(QueryBuilder builder, List<?> list, String[] generatedColumns, Class<V> type) {
        return builder.bindUpdateAll(list, generatedColumns, type);
    }

    private Query(Connection connection, Quirks quirks, String queryText) {
        this(connection, quirks, queryText, null);
    }

    protected Query(Connection connection, Quirks quirks, String queryText, String[] columnGenerated) {
        this.connection = connection;
        this.quirks = quirks;
        this.returnGeneratedKeys = columnGenerated != null && columnGenerated.length > 0;
        paramNameToIdxMap = new HashMap<>();
        parsedQuery = quirks.getSqlParameterParsingStrategy().parseSql(queryText, paramNameToIdxMap);
        try {
            if (returnGeneratedKeys) {
                statement = this.connection.prepareStatement(parsedQuery, columnGenerated);
            } else {
                statement = this.connection.prepareStatement(parsedQuery);
            }
        } catch (SQLException ex) {
            throw new Sql2oException(String.format("Error preparing statement - %s", ex.getMessage()), ex);
        }
    }

    public Query setAutocloseConnection(boolean autocloseConnection) {
        this.autocloseConnection = autocloseConnection;
        return this;
    }

    // ------------------------------------------------
    // ------------- Add Parameters -------------------
    // ------------------------------------------------

    private void addParameterInternal(String name, ParameterSetter parameterSetter) {
        for (int paramIdx : this.paramNameToIdxMap.get(name)) {
            try {
                parameterSetter.setParameter(paramIdx);
            } catch (SQLException e) {
                throw new RuntimeException(String.format("Error adding parameter '%s' - %s", name, e.getMessage()), e);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertParameter(Object value) {
        if (value == null) {
            return null;
        }
        Converter converter = quirks.converterOf(value.getClass());
        if (converter == null) {
            // let's try to add parameter AS IS
            return value;
        }
        return converter.toDatabaseParam(value);
    }

    private <T> Query addParameter(String name, Class<T> parameterClass, T value) {
        //TODO: must cover most of types: BigDecimal,Boolean,SmallInt,Double,Float,byte[]
        if (InputStream.class.isAssignableFrom(parameterClass))
            return addParameter(name, (InputStream) value);
        else if (Integer.class == parameterClass)
            return addParameter(name, (Integer) value);
        else if (Long.class == parameterClass)
            return addParameter(name, (Long) value);
        else if (String.class == parameterClass)
            return addParameter(name, (String) value);
        else if (Timestamp.class == parameterClass)
            return addParameter(name, (Timestamp) value);
        else if (Time.class == parameterClass)
            return addParameter(name, (Time) value);


        final Object convertedValue = convertParameter(value);

        addParameterInternal(name, paramIdx -> quirks.setParameter(statement, paramIdx, convertedValue));

        return this;
    }

    public Query withParams(List<Object> params) {
        try {
            Object param = null;
            for (int i = 0; i < params.size(); i++) {
                param = params.get(i);
                if (param == null) // jika param null tidak perlu dimasukkan kedalam parameter quirks
                    continue;
                if (param instanceof InputStream inputStream)
                    quirks.setParameter(statement, i+1, inputStream);
                else if (param instanceof Integer integer)
                    quirks.setParameter(statement, i+1, integer);
                else if (param instanceof Long longValue)
                    quirks.setParameter(statement, i+1, longValue);
                else if (param instanceof String string)
                    quirks.setParameter(statement, i+1, string);
                else if (param instanceof Timestamp timestamp)
                    quirks.setParameter(statement, i+1, timestamp);
                else if (param instanceof Time time)
                    quirks.setParameter(statement, i+1, time);
                else {
                    final Object convertedValue = convertParameter(param);
                    quirks.setParameter(statement, i+1, convertedValue);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Error adding parameter '%s' - %s", parsedQuery, e.getMessage()), e);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public Query addParameter(String name, Object value) {
        return value == null
                ? addParameter(name, Object.class, null)
                : addParameter(name,
                (Class<Object>) value.getClass(),
                value);
    }

    private Query addParameter(String name, final InputStream value) {
        addParameterInternal(name, paramIdx -> quirks.setParameter(statement, paramIdx, value));

        return this;
    }

    public Query addParameter(String name, final int value) {
        addParameterInternal(name, paramIdx -> quirks.setParameter(statement, paramIdx, value));

        return this;
    }

    private Query addParameter(String name, final Integer value) {
        addParameterInternal(name, paramIdx -> quirks.setParameter(statement, paramIdx, value));

        return this;
    }

    public Query addParameter(String name, final long value) {
        addParameterInternal(name, paramIdx -> quirks.setParameter(statement, paramIdx, value));

        return this;
    }

    private Query addParameter(String name, final Long value) {
        addParameterInternal(name, paramIdx -> quirks.setParameter(statement, paramIdx, value));

        return this;
    }

    private Query addParameter(String name, final String value) {
        addParameterInternal(name, paramIdx -> quirks.setParameter(statement, paramIdx, value));

        return this;
    }

    private Query addParameter(String name, final Timestamp value) {
        addParameterInternal(name, paramIdx -> quirks.setParameter(statement, paramIdx, value));

        return this;
    }

    private Query addParameter(String name, final Time value) {
        addParameterInternal(name, paramIdx -> quirks.setParameter(statement, paramIdx, value));

        return this;
    }

    public Query setParameter(String name, Object param) {
        addParameter(name, param);
        return this;
    }

    @SuppressWarnings("rawtypes")
    public Query bind(final Object pojo) {
        Class clazz = pojo.getClass();
        Map<String, PojoIntrospector.ReadableProperty> propertyMap = PojoIntrospector.readableProperties(clazz);
        for (PojoIntrospector.ReadableProperty property : propertyMap.values()) {
            try {
                if( this.paramNameToIdxMap.containsKey(property.name)) {

                    @SuppressWarnings("unchecked")
                    final Class<Object> type = (Class<Object>) property.type;
                    this.addParameter(property.name, type, property.get(pojo));
                }
            }
            catch(IllegalArgumentException ex) {
                Logger.debug("Ignoring Illegal Arguments", ex);
            }
            catch(IllegalAccessException | InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        }
        return this;
    }

    // ------------------------------------------------
    // -------------------- Execute -------------------
    // ------------------------------------------------

    /**
     * Iterable {@link java.sql.ResultSet} that wraps {@link PojoResultSetIterator}.
     */
    private abstract class ResultSetIterableBase<T> implements ResultSetIterable<T> {
        protected ResultSet rs;
        boolean autoCloseConnection = false;

        public ResultSetIterableBase() {
            Monitor monitor = MonitorFactory.start(parsedQuery);
            try {
            	statement.setFetchSize(fetchSize != null ? fetchSize : DEFAULT_FETCH_SIZE);
                rs = statement.executeQuery();
                monitor.stop();
                monitor = null;
            } catch (SQLException ex) {
                throw new Sql2oException("Database error: " + ex.getMessage(), ex);
            }finally {
                if(monitor != null)
                    monitor.stop();
            }
        }

        @Override
        public void close() {
            try {
                if (rs != null) {
                    rs.close();  
                    rs = null;
                }              
            } catch (SQLException ex) {               
                throw new Sql2oException("Error closing ResultSet.", ex);
            } finally {
                closeStatement();
            }
        }

        @Override
        public boolean isAutoCloseConnection() {
            return this.autoCloseConnection;
        }

        @Override
        public void setAutoCloseConnection(boolean autoCloseConnection) {
            this.autoCloseConnection = autoCloseConnection;
        }
    }

    /**
     * Read a collection lazily. Generally speaking, this should only be used if you are reading MANY
     * results and keeping them all in a Collection would cause memory issues. You MUST call
     * {@link org.sql2o.ResultSetIterable#close()} when you are done iterating.
     *
     * @param resultSetHandlerFactory factory to provide ResultSetHandler
     * @return iterable results
     */
    <T> ResultSetIterable<T> executeAndFetchLazy(final ResultSetHandlerFactory<T> resultSetHandlerFactory) {
        return new ResultSetIterableBase<>() {
            public Iterator<T> iterator() {
                return new PojoResultSetIterator<>(rs, false, quirks, resultSetHandlerFactory);
            }
        };
    }

    <T> ResultSetIterable<T> executeAndFetchLazy(final Class<T> returnType) {
        final ResultSetHandlerFactory<T> resultSetHandlerFactory = newResultSetHandlerFactory(returnType);
        return executeAndFetchLazy(resultSetHandlerFactory);
    }
    <T> ResultSetIterable<T> executeAndFetchLazy(final ResultSetHandler<T> resultSetHandler) {
        final ResultSetHandlerFactory<T> factory = newResultSetHandlerFactory(resultSetHandler);
        return executeAndFetchLazy(factory);
    }

    private <T>  ResultSetHandlerFactory<T> newResultSetHandlerFactory(Class<T> clazz) {
        return DefaultResultSetBuilder.newResultSetHandlerFactory(clazz, quirks);
    }


    static <T> ResultSetHandlerFactory<T> newResultSetHandlerFactory(final ResultSetHandler<T> resultSetHandler) {
        return resultSetMetaData -> resultSetHandler;
    }

    private <T>  ResultSetHandlerFactory<T> resultSetHandlerFactory(ResultSetHandler<T> resultSetHandler) {
        return Query.newResultSetHandlerFactory(resultSetHandler);
    }

    <T> List<T> executeAndFetch(ResultSetHandler<T> factory) {
        return executeAndFetch(resultSetHandlerFactory(factory));
    }

    private <T> List<T> executeAndFetch(ResultSetHandlerFactory<T> factory) {
        List<T> list = new ArrayList<>();
        try (ResultSetIterable<T> iterable = executeAndFetchLazy(factory)) {
            for (T item : iterable) {
                if(item == null)
                    continue;
                if (item instanceof BaseTable baseTable) { // jika object extends dari baseTable , perlu execute postLoad method
                    baseTable.postLoad();
                } else if(item instanceof GenericModel genericModel){
                    genericModel.postLoad();
                }
                list.add(item);
            }
        }
        closeStatement();
        return list;
    }

    <T> T executeAndFetchFirst(Class<T> returnType){
        if (returnType.isPrimitive() || returnType.isEnum() || Arrays.stream(primitives).anyMatch(returnType::equals))
        {
            return executeScalar(returnType);
        }
        return executeAndFetchFirst(newResultSetHandlerFactory(returnType));
    }

    <T> T executeAndFetchFirst(ResultSetHandler<T> resultSetHandler){
        return executeAndFetchFirst(newResultSetHandlerFactory(resultSetHandler));
    }

    private <T> T executeAndFetchFirst(ResultSetHandlerFactory<T> resultSetHandlerFactory) {
        try (ResultSetIterable<T> iterable = executeAndFetchLazy(resultSetHandlerFactory)) {
            Iterator<T> iterator = iterable.iterator();
            T obj = iterator.hasNext() ? iterator.next() : null;
            if(obj != null) {
                if (obj instanceof BaseTable baseTable) { // jika object extends dari baseTable , perlu execute postLoad method
                    baseTable.postLoad();
                } else if (obj instanceof GenericModel genericModel) {
                    genericModel.postLoad();
                }
            }
            closeStatement();
            return obj;
        }
    }

    private LazyTable executeAndFetchTableLazy() {
        final LazyTable lt = new LazyTable();

        lt.setRows(new ResultSetIterableBase<>() {
            public Iterator<Row> iterator() {
                return new TableResultSetIterator(rs, false, quirks, lt);
            }
        });

        return lt;
    }

    Table executeAndFetchTable() {
        LazyTable lt = executeAndFetchTableLazy();
        List<Row> rows = new ArrayList<>();
        try {
            for (Row item : lt.rows()) {
                rows.add(item);
            }
        } finally {
            lt.close();
        }
        // lt==null is always false
        return new Table(lt.getName(), rows, lt.columns());
    }

    /**
     * Jika ada kolom yg autoincrement, maka method ini return nilai kolom
     * tersebut.
     */
    int executeUpdate() {    	
        int result;
        Monitor monitor = MonitorFactory.start(parsedQuery);
        try {
            result = statement.executeUpdate();
            setKeys(this.returnGeneratedKeys ? statement.getGeneratedKeys() : null);
        } catch (SQLException ex) {
            throw new Sql2oException("Error in executeUpdate, " + ex.getMessage(), ex);
        }finally {
        	closeStatement();
            if (monitor != null) {
                monitor.stop();
            }
        }        
        return result;
    }


    private Object executeScalar() {        
        Object o = null;
        Monitor monitor = MonitorFactory.start(parsedQuery);
        try {
            ResultSet rs = this.statement.executeQuery();
            if (rs.next()) {
                o = quirks.getRSVal(rs, 1);
            }
        } catch (SQLException e) {           
            throw new Sql2oException("Database error occurred while running executeScalar: " + e.getMessage(), e);
        } finally {
        	closeStatement();
            if (monitor != null) {
                monitor.stop();
            }
        }
        return o;
    }

    <V> V executeScalar(Class<V> returnType) {
        try {
            Converter<V> converter = throwIfNull(returnType, quirks.converterOf(returnType));
            return executeScalar(converter);
        } catch (ConverterException e) {
            throw new Sql2oException("Error occured while converting value from database to type " + returnType, e);
        }
    }

    private <V> V executeScalar(Converter<V> converter) {
        try {
            return converter.convert(executeScalar());
        } catch (ConverterException e) {
            throw new Sql2oException("Error occured while converting value from database", e);
        }
    }

    public <V> V getKey(Class returnType){
        if (this.keys != null) {
            Object key = keys.get(0);
            try {
                Converter<V> converter = throwIfNull(returnType, quirks.converterOf(returnType));
                return converter.convert(key);
            } catch (ConverterException e) {
                throw new Sql2oException("Exception occurred while converting value from database to type " + returnType.toString(), e);
            }
        }
        return null;
    }

    public <V> List<V> getKeys(Class<V> returnType) {
        if (this.keys != null) {
            try {
                Converter<V> converter = throwIfNull(returnType, quirks.converterOf(returnType));
                List<V> convertedKeys = new ArrayList<>(this.keys.size());
                for (Object key : this.keys) {
                    convertedKeys.add(converter.convert(key));
                }
                return convertedKeys;
            }
            catch (ConverterException e) {
                throw new Sql2oException("Exception occurred while converting value from database to type " + returnType.toString(), e);
            }
        }
        return null;
    }

    /************** batch stuff *******************/

    Query addToBatch() {
        try {
            statement.addBatch();
        } catch (SQLException e) {
            throw new Sql2oException("Error while adding statement to batch", e);
        }

        return this;
    }

    void setKeys(ResultSet rs) throws SQLException {
        if (rs == null){
            this.keys = null;
            return;
        }
        this.keys = new ArrayList<Object>();
        while(rs.next()){
            this.keys.add(rs.getObject(1));
        }
    }

    int[] executeBatch() throws Sql2oException {    	
        int[] result;
        Monitor monitor = MonitorFactory.start(parsedQuery);
        try {
            result = statement.executeBatch();
            setKeys(this.returnGeneratedKeys ? statement.getGeneratedKeys() : null);
        } catch (Throwable e) {
            throw new Sql2oException("Error while executing batch operation: " + e.getMessage(), e);
        }finally {
            if (monitor != null) {
                monitor.stop();
            }
        }
        return result;
    }

    private interface ParameterSetter {
        void setParameter(int paramIdx) throws SQLException;
    }


    public static Query create(String sql, boolean autocloseConnection) {
        return create(DB.DEFAULT, sql, autocloseConnection);
    }

    public static Query create(String dbname, String sql, boolean autocloseConnection) {
        return new QueryBuilder(sql).using(dbname).setAutocloseConnection(autocloseConnection).createQuery();
    }

    public static <T> T getNextSequenceValue(String dbname, String sequenceName, String function, Class<T> clazz) {
        return create(dbname, "SELECT "+function+"('"+sequenceName+"')", false).executeScalar(clazz);
    }

    public void closeStatement() {
        try {
            if(statement != null) {
                quirks.closeStatement(statement);
            }
            if(autocloseConnection) {
                if (connection != null && !connection.isClosed()) {
                    if(!connection.getAutoCommit()) {
                        try {
                            connection.commit();
                        }catch (SQLException e){
                            connection.rollback();
                        }
                    }
                    connection.close();
                }
            }
        }catch (SQLException e) {
            Logger.warn(e, "[Query] Could not close : %s", e.getMessage());
        }
    }

    public Integer getFetchSize() {
        return fetchSize;
    }

    public Query setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
        return this;
    }

    <T> List<T> executeAndFetch(Class<T> returnType){
        if (returnType.isPrimitive() || returnType.isEnum() || Arrays.stream(primitives).anyMatch(returnType::equals))
        {
            return executeScalarList(returnType);
        }
        return executeAndFetch(newResultSetHandlerFactory(returnType));
    }

    public <T> List<T> executeScalarList(final Class<T> returnType){
        return executeAndFetch(newScalarResultSetHandler(returnType));
    }

    private <T> ResultSetHandler<T> newScalarResultSetHandler(final Class<T> returnType) {
        try {
            final Converter<T> converter = throwIfNull(returnType, quirks.converterOf(returnType));
            return resultSet -> {
                Object value = quirks.getRSVal(resultSet, 1);
                try {
                    return (converter.convert(value));
                } catch (ConverterException e) {
                    throw new Sql2oException("Error occurred while converting value from database to type " + returnType, e);
                }
            };
        } catch (ConverterException e) {
            throw new Sql2oException("Can't get converter for type " + returnType, e);
        }
    }
}