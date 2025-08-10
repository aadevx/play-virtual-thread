package play.db;

import org.sql2o.quirks.Quirks;
import play.InvocationContext;
import play.Logger;
import play.db.jdbc.BaseTable;
import play.db.jdbc.BaseTableDao;
import play.db.jpa.NoTransaction;
import play.exceptions.DatabaseException;
import play.libs.SupplierWithException;

import javax.persistence.PersistenceUnit;
import javax.sql.DataSource;
import javax.sql.RowSet;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database connection utilities.
 */
public class DB {

	public static final Map<String, Quirks> quirks = new ConcurrentHashMap<>(1);

    /**
     * The loaded datasource.
     * @see ExtendedDatasource
     */
    protected static final Map<String, ExtendedDatasource> datasources = new ConcurrentHashMap<>();

    public static class ExtendedDatasource {

        /**
         * Connection to the physical data source
         */
        private final DataSource datasource;

        /**
         * The method used to destroy the data source
         */
        private final String destroyMethod;

        public ExtendedDatasource(DataSource ds, String destroyMethod) {
            this.datasource = ds;
            this.destroyMethod = destroyMethod;
        }

        public String getDestroyMethod() {
            return destroyMethod;
        }

        public DataSource getDataSource() {
            return datasource;
        }

    }

    public static final String DEFAULT = "default";

    static final ThreadLocal<Map<String, Connection>> localConnection = new ThreadLocal<>();

    public static DataSource getDataSource(String name) {
        ExtendedDatasource datasource = datasources.get(name);
        return datasource == null ? null : datasource.getDataSource();
    }

    public static DataSource getDataSource() {
        return getDataSource(DEFAULT);
    }

    public static Connection getConnection(String name, boolean autocommit) {
        try {
            Connection connection = getDataSource(name).getConnection();
            connection.setAutoCommit(autocommit);
            return connection;
        } catch (Exception e) {
            // Exception
            throw new DatabaseException(e.getMessage());
        }
    }

    private static Connection getLocalConnection(String name) throws SQLException {
        Map<String, Connection> map = localConnection.get();
        if (map != null) {
            Connection connection = map.get(name);
            if(connection != null && !connection.isClosed()) {
                if (Logger.isTraceEnabled())
                    Logger.trace("getLocalConnection :%s", connection);
                return connection;
            }
            else {
                map.remove(name);
                localConnection.set(map);
            }
        }
        return null;
    }

    private static void registerLocalConnection(String name, Connection connection) {
        Map<String, Connection> map = localConnection.get();
        if (map == null) {
            map = new ConcurrentHashMap<>();
        }
        map.put(name, connection);
        localConnection.set(map);
    }

    /**
     * Close all the open connections for the current thread.
     */
    public static void closeAll() {
        Map<String, Connection> map = localConnection.get();
        if (map != null) {
            for (String name : map.keySet()) {
                close(name);
            }
        }
    }

    /**
     * Close all the open connections for the current thread.
     */
    public static void close() {
        close(DEFAULT);
    }

    /**
     * Close an given open connections for the current thread
     */
    public static void close(String name) {
        Map<String, Connection> map = localConnection.get();
        if (map != null) {
            Connection connection = map.get(name);
            if (connection != null) {
                map.remove(name);
                localConnection.set(map);
                try {
                    connection.close();
                    if(Logger.isTraceEnabled())
                        Logger.trace("close connecition :%s", connection);
                } catch (Exception e) {
                    throw new DatabaseException("It's possible than the connection '" + name + "'was not properly closed !", e);
                }
            }
        }
    }

    /**
     * Open a connection for the current thread.
     * 
     * @return A valid SQL connection
     */
    public static Connection getConnection(String name) {
        try {
            Connection localConnection = getLocalConnection(name);
            if (localConnection != null) {
                return localConnection;
            }
            // We have no connection
            Connection connection = getDataSource(name).getConnection();
            InvocationContext context = InvocationContext.current();
            if (context == null || context.getAnnotation(NoTransaction.class) == null) {
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            }
            registerLocalConnection(name, connection);
            return connection;
        } catch (NullPointerException e) {
            if (getDataSource(name) == null) {
                throw new DatabaseException("No database found. Check the configuration of your application.", e);
            }
            throw e;
        } catch (Exception e) {
            // Exception
            throw new DatabaseException(e.getMessage());
        }
    }

    public static Connection getConnection() {
        return getConnection(DEFAULT);
    }

    /**
     * Execute an SQL update
     * 
     * @param SQL
     * @return true if the next result is a ResultSet object; false if it is an
     *         update count or there are no more results
     */
    public static boolean execute(String name, String SQL) {
        try (Statement statement = getConnection(name).createStatement()){
            if (statement != null) {
                return statement.execute(SQL);
            }
        } catch (SQLException ex) {
            throw new DatabaseException(ex.getMessage(), ex);
        }
        return false;
    }

    public static boolean execute(String SQL) {
        return execute(DEFAULT, SQL);
    }

    public static RowSet executeQuery(String SQL) {
        return executeQuery(DEFAULT, SQL);
    }

    /**
     * Execute an SQL query
     * 
     * @param SQL
     * @return The rowSet of the query
     */
    public static RowSet executeQuery(String name, String SQL) {
        try (Statement statement = getConnection(name).createStatement()){
            if (statement != null) {
                try(ResultSet rs = statement.executeQuery(SQL)){
                    // Need to use a CachedRowSet that caches its rows in memory, which
                    // makes it possible to operate without always being connected to
                    // its data source
                    CachedRowSet rowset = RowSetProvider.newFactory().createCachedRowSet();
                    rowset.populate(rs);
                    return rowset;
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException(ex.getMessage(), ex);
        }
        return null;
    }

    public static void safeCloseResultSet(ResultSet resultSet) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException ex) {
                throw new DatabaseException(ex.getMessage(), ex);
            }
        }
    }

    /**
     * Destroy the datasource
     */
    public static void destroy(String name) {
        try {
            ExtendedDatasource extDatasource = datasources.get(name);
            if (extDatasource != null && extDatasource.getDestroyMethod() != null) {
                Method close = extDatasource.datasource.getClass().getMethod(extDatasource.getDestroyMethod());
                close.invoke(extDatasource.getDataSource());
                datasources.remove(name);
                Logger.trace("Datasource destroyed");
            }
        } catch (Throwable t) {
            Logger.error(t,"Couldn't destroy the datasource");
        }
    }

    /**
     * Destroy the datasource
     */
    public static void destroy() {
        destroy(DEFAULT);
    }

    /**
     * Destroy all datasources
     */
    public static void destroyAll() {
        for (String name : datasources.keySet()) {
            destroy(name);
        }
    }
    
    public static void closeTx(boolean rollback) {
        for (String name : datasources.keySet()) {
            try (Connection connection = getLocalConnection(name)){
                if (connection != null && !connection.getAutoCommit()) {
                    if (rollback) {
                        connection.rollback();
                        if(Logger.isTraceEnabled())
                            Logger.trace("rollback connection :%s", connection);
                    } else {
                        connection.commit();
                        if(Logger.isTraceEnabled())
                            Logger.trace("commit connection :%s", connection);
                    }
                }
            }catch (SQLException e) {
                Logger.error(e, "DB: cannot closeTx : %s", e.getMessage());
            }
        }
 	}

    public static String getDBName(Class<?> clazz) {
        String name = DEFAULT;
        if (clazz != null) {
            PersistenceUnit pu = clazz.getAnnotation(PersistenceUnit.class);
            if (pu != null) {
                name = pu.name();
            }
        }
        return name;
    }

    public static <T> T withinFilter(SupplierWithException<T> block) throws Exception {
        return block.get();
    }

    public static final Map<Class, BaseTableDao> daoMap = new ConcurrentHashMap<>(1);

    public static <T extends BaseTable> BaseTableDao<T> model(Class<T> clazz) {
        return daoMap.computeIfAbsent(clazz, c -> BaseTableDao.getInstance(c));
    }

}
