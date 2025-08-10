package play.db.jdbc;

import org.apache.commons.lang3.StringUtils;
import org.sql2o.ResultSetHandler;
import org.sql2o.ResultSetIterable;
import org.sql2o.data.Table;
import play.db.DB;

import java.util.*;

/**
 * Created by Arief on 05/29/17.
 */
public final class QueryBuilder {

    private String dbname = DB.DEFAULT;
    /** The SQL query gathered so far */
    private final StringBuilder query = new StringBuilder();
    /** The arguments gathered so far */
    private final List<Object> params = new ArrayList<>();

    private boolean autocloseConnection = false;

    private int fetchSize;
    /**
     * Constructs a new empty QueryBuilder.
     */
    public QueryBuilder() {
    }

    public static QueryBuilder create(String sql, Object... params) {
        return new QueryBuilder(sql, params);
    }
    /**
     * Constructs a new empty QueryBuilder.
     */
    public QueryBuilder(String sql) {
        append(sql);
    }

    public static QueryBuilder select(String... colums) {
        return new QueryBuilder("SELECT ").append(StringUtils.join(colums, ","));
    }

    public QueryBuilder from(Class<? extends BaseTable> clazz) {
        play.db.jdbc.Table table = clazz.getAnnotation(play.db.jdbc.Table.class);
        append("FROM").append(table.name());
        return this;
    }

    public QueryBuilder from(String sql) {
        append("FROM").append(sql);
        return this;
    }

    public QueryBuilder where(String sql, Object... params) {
        append("WHERE").append(sql, params);
        return this;
    }

    public QueryBuilder orderBy(String sql, Object... params) {
        append("ORDER BY").append(sql, params);
        return this;
    }

    public QueryBuilder leftJoin(String sql) {
        append("LEFT JOIN").append(sql);
        return this;
    }

    // use left join ... on....
    public QueryBuilder on(String sql) {
        append("ON").append(sql);
        return this;
    }

    public QueryBuilder replace(String oldStr, String newStr) {
        String query_new = query().replace(oldStr, newStr);
        query.setLength(0);
        query.append(query_new);
        return this;
    }

    public QueryBuilder replaceOnce(String oldStr, String newStr) {
        String query_new = query().replaceFirst(oldStr, newStr);
        query.setLength(0);
        query.append(query_new);
        return this;
    }

    /**
     * Constructs a QueryBuilder with given initial SQL-fragment and arguments.
     */
    public QueryBuilder(String sql, Object... params) {
        append(sql, params);
    }

    // using dbname , default value : "default"
    public QueryBuilder using(String dbname) {
        this.dbname = dbname;
        return this;
    }

    /*
     * reset query to empty string
     * reset param to null
     */
    public void reset() {
        query.setLength(0);
        params.clear();
    }
    /**
     * Appends given fragment and arguments to this query.
     */
    public QueryBuilder append(String sql, Object... params) {
        query.append(' ').append(sql).append(' '); 
        if(params != null) {
            this.params.addAll(Arrays.asList(params));
        }
        return this;
    }

    public QueryBuilder append(QueryBuilder builder) {
       if(builder != null && StringUtils.isNotEmpty(builder.dbname))
           if(!dbname.equals(builder.dbname))
               return this;
        query.append(' ').append(builder.query).append(' ');
        if(builder.params != null) {
            this.params.addAll(builder.params);
        }
       return this;
    }
    /**
     * Is the query string empty?
     */
    public boolean isEmpty() {
        return query.isEmpty();
    }

    public boolean isNotEmptyParam() {
        return params != null && !params.isEmpty();
    }

    public List<Object> params() {
        return params;
    }

    public String query() {
        return query.toString();
    }

    public QueryBuilder setAutocloseConnection(boolean autocloseConnection) {
        this.autocloseConnection = autocloseConnection;
        return this;
    }

    public String getDbname() {
        return dbname;
    }

    public <T> List<T> fetch(ResultSetHandler<T> resultSetHandler) {
        Query q = createQuery();
        if (isNotEmptyParam())
            q.withParams(params());
        return q.executeAndFetch(resultSetHandler);
    }

    public <T> List<T> fetch(Class<T> clazz) {
        Query q = createQuery();
        if (isNotEmptyParam())
            q.withParams(params());
        return q.executeAndFetch(clazz);
    }


    public <T> T fetchFirst(ResultSetHandler<T> resultSetHandler) {
        Query q = createQuery();
        if (isNotEmptyParam())
            q.withParams(params());
        return q.executeAndFetchFirst(resultSetHandler);
    }

    public <T> T fetchFirst(Class<T> clazz) {
        Query q = createQuery();
        if (isNotEmptyParam())
            q.withParams(params());
        return q.executeAndFetchFirst(clazz);
    }

    public <T>ResultSetIterable<T> fetchLazy(ResultSetHandler<T> resultSetHandler){
        Query q = createQuery();
        if (isNotEmptyParam())
            q.withParams(params());
        return q.executeAndFetchLazy(resultSetHandler);
    }

    public <T>ResultSetIterable<T> fetchLazy(Class<T> returnType){
        Query q = createQuery();
        if (isNotEmptyParam())
            q.withParams(params());
        return q.executeAndFetchLazy(returnType);
    }

    public Table fetchTable() {
        Query q = createQuery();
        if (isNotEmptyParam())
            q.withParams(params());
        return q.executeAndFetchTable();
    }

    public long count() {
        Query q = createQuery();
        if (isNotEmptyParam())
            q.withParams(params());
        return q.executeScalar(Long.class);
    }

    public <V> V count(Class<V> clazz) {
        Query q = createQuery();
        if (isNotEmptyParam())
            q.withParams(params());
        return q.executeScalar(clazz);
    }

    public int bindCount(Object object) {
        Query q = createQuery();
        if (object != null)
            q.bind(object);
        return q.executeScalar(Integer.class);
    }

    public int update() {
        Query q = createQuery();
        if (isNotEmptyParam())
            q.withParams(params());
        return q.executeUpdate();
    }

    public int bindUpdate(Object object) {
        Query q = createQuery();
        if (object != null) {
            if (object instanceof Map map && !map.isEmpty()){
                map.forEach((key, value) -> {
                    q.addParameter(key.toString(), value);
                });
            } else {
                q.bind(object);
            }
        }
        return q.executeUpdate();
    }

    public void bindUpdateAll(List<?> list) {
        Query q = createQuery();
        for (Object obj : list) {
            if (obj != null)
                q.bind(obj).addToBatch();
        }
        q.executeBatch(); // executes entire batch
    }

    public <V> List<V> bindUpdateAll(List<?> list, String[] generatedColumns, Class<V> type) {
        Query q = createQuery(generatedColumns);
        for (Object obj : list) {
            if (obj != null)
                q.bind(obj).addToBatch();
        }
        q.executeBatch(); // executes entire batch
        return q.getKeys(type);
    }

    public Query createQuery() {
        return createQuery(null).setAutocloseConnection(autocloseConnection).setFetchSize(fetchSize);
    }

    public Query createQuery(String[] generatedColumn) {
        return new Query(DB.getConnection(dbname), DB.quirks.get(dbname), query(), generatedColumn).setAutocloseConnection(autocloseConnection).setFetchSize(fetchSize);
    }

    public QueryBuilder setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
        return this;
    }

    public QueryBuilder offset(int position) {
        append(" OFFSET ?", position);
        return this;
    }

    public QueryBuilder limit(int max) {
        append(" LIMIT ?", max);
        return this;
    }

    public QueryBuilder paging(int page, int length) {
        if (page < 1) {
            page = 1;
        }
        offset((page - 1) * length);
        limit(length);
        return this;
    }

    public <T> Optional<T> one(Class<T> clazz) {
        return Optional.of(fetchFirst(clazz));
    }

    public <T> Optional<List<T>> list(Class<T> clazz) {
        return Optional.of(fetch(clazz));
    }

}
