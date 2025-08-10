package play.db;

import org.apache.commons.lang3.StringUtils;
import org.sql2o.converters.Convert;
import org.sql2o.quirks.Quirks;
import org.sql2o.quirks.QuirksDetector;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.classloading.ApplicationClasses;
import play.classloading.enhancers.Enhancer;
import play.db.DB.ExtendedDatasource;
import play.db.jdbc.DefaultEnumConverterFactory;
import play.db.jdbc.DefaultResultSetBuilder;
import play.db.jdbc.JdbcEnhancer;
import play.exceptions.DatabaseException;
import play.libs.Crypto;
import play.mvc.Http.Response;
import play.mvc.Scope;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DBPlugin extends PlayPlugin {

    public static String url = "";

    public final Pattern MYSQL_PATTERN = Pattern.compile("^mysql:(//)?((?<user>[a-zA-Z0-9_]+)(:(?<pwd>[^@]+))?@)?((?<host>[^/]+)/)?(?<name>[a-zA-Z0-9_]+)(\\?)?(?<parameters>[^\\s]+)?$");
    public final Pattern MARIADB_PATTERN = Pattern.compile("^maridb:(//)?((?<user>[a-zA-Z0-9_]+)(:(?<pwd>[^@]+))?@)?((?<host>[^/]+)/)?(?<name>[a-zA-Z0-9_]+)(\\?)?(?<parameters>[^\\s]+)?$");
    public final Pattern POSTGRES_PATTERN= Pattern.compile("^postgres:(//)?((?<user>[a-zA-Z0-9_]+)(:(?<pwd>[^@]+))?@)?((?<host>[^/]+)/)?(?<name>[^\\s]+)$");
    
    private final DataSourceFactory factory = Play.configuration.getProperty("application.directdb", "false").equalsIgnoreCase("true") ? new DirectDataSourceFactory()  : new HikariDataSourceFactory() ;
    @Override
    public void onApplicationStart() {
        decryptUrl();
        if (changed()) {
            String dbName = "";
            try {
                // Destroy all connections
                if (!DB.datasources.isEmpty()) {
                    DB.destroyAll();
                }                
                
                Set<String> dbNames = Configuration.getDbNames();
                for (String name : dbNames) {
                	dbName = name;
                    Configuration dbConfig = new Configuration(dbName);                    
                    boolean isJndiDatasource = false;
                    String datasourceName = dbConfig.getProperty("db", "");

                    // Identify datasource JNDI lookup name by 'jndi:' or 'java:' prefix 
                    if (datasourceName.startsWith("jndi:")) {
                        datasourceName = datasourceName.substring("jndi:".length());
                        isJndiDatasource = true;
                    }

                    if (isJndiDatasource || datasourceName.startsWith("java:")) {
                        Context ctx = new InitialContext();
                        DataSource ds =  (DataSource) ctx.lookup(datasourceName);
                        DB.ExtendedDatasource extDs = new DB.ExtendedDatasource(ds, "");
                        DB.datasources.put(dbName, extDs);  
                    } else {

                        // Try the driver
                        String driver = dbConfig.getProperty("db.driver");
                        try {
                            Driver d = (Driver) Class.forName(driver, true, Play.classloader).getDeclaredConstructor().newInstance();
                            DriverManager.registerDriver(new ProxyDriver(d));
                        } catch (Exception e) {
                            throw new Exception("Database [" + dbName + "] Driver not found (" + driver + ")", e);
                        }

                        // Try the connection
                        Connection fake = null;
                        try {
                            if (dbConfig.getProperty("db.user") == null) {
                                fake = DriverManager.getConnection(dbConfig.getProperty("db.url"));
                            } else {
                                fake = DriverManager.getConnection(dbConfig.getProperty("db.url"), dbConfig.getProperty("db.user"), dbConfig.getProperty("db.pass"));
                            }
                            // checking requirement : checking minimum versi RDBMS
                            String databaseName = fake.getMetaData().getDatabaseProductName();
                            String databaseVersi = fake.getMetaData().getDatabaseProductVersion();
                            //mengambil versi mysql,sampe char ke 3
                            float versi = Float.parseFloat(databaseVersi.substring(0, 3));
                            Logger.info("using database DBMS %s versi %s ", databaseName, databaseVersi);
                            //databaseVersi.indexOf(".")
                            if(databaseName.equals("PostgreSQL") && versi < Float.valueOf(11)) {
                                throw new InterruptedException("Database Postgres Minimum Versi 11");
                            }else if(databaseName.equals("MySQL") && versi < Float.valueOf("5.5")) {
                                throw new InterruptedException("Database MySQL Minimum Versi 5.5");
                            }
                        } finally {
                            if (fake != null) {
                                fake.close();
                            }
                        }
                        
                        DataSource ds = factory.createDataSource(dbConfig);
                        // Current datasource. This is actually deprecated. 
                        String destroyMethod = dbConfig.getProperty("db.destroyMethod", "");

                        DB.ExtendedDatasource extDs = new DB.ExtendedDatasource(ds, destroyMethod);

                        url = testDataSource(ds);
                        Logger.info("Connected to %s for %s", url, dbName);
                        DB.datasources.put(dbName, extDs);
                        // custome set for sql2o
                        Convert.registerEnumConverter(new DefaultEnumConverterFactory());
                        if(!DB.quirks.containsKey(dbName)) {
                        	Quirks quirk = QuirksDetector.forObject(ds);
                        	DB.quirks.put(dbName, quirk);
                        }
                        DefaultResultSetBuilder.clear();
                    }
                }

            } catch (Exception e) {
                Logger.error(e, "Database [%s] Cannot connected to the database : %s", dbName, e.getMessage());
                if (e.getCause() instanceof InterruptedException) {
                    throw new DatabaseException("Cannot connected to the database["+ dbName + "]. Check the configuration.", e);
                }
                throw new DatabaseException("Cannot connected to the database["+ dbName + "], " + e.getMessage(), e);
            }
        }
    }
    
    protected String testDataSource(DataSource ds) throws SQLException {
        try (Connection connection = ds.getConnection()) {
            return connection.getMetaData().getURL();
        }
    }

    @Override
    public void onApplicationStop() {
        DB.closeAll();
        if (Play.mode.isProd()) {
            DB.destroyAll();
        }
    }

    @Override
    public void invocationFinally() {
        DB.closeAll();
    }
    
    // Method ini pernah dihilangkan oleh AA tapi menyebabkan error
    // Jadinya dipakai lagi (by AY)
  	@Override
   	public void onInvocationException(Throwable e) {
  		DB.closeTx(true);
   	}

   	@Override
   	public void afterInvocation() {
   		String flash_error = Response.current() != null ? Response.current().getHeader(Scope.HEADER_FLASH_ERROR):null;
        DB.closeTx(flash_error != null && flash_error.equals("true"));
   	}

    private static void check(Configuration config, String mode, String property) {
        if (!StringUtils.isEmpty(config.getProperty(property))) {
            Logger.warn("Ignoring " + property + " because running the in " + mode + " db.");
        }
    }

    private boolean changed() {
        Set<String> dbNames = Configuration.getDbNames();
        
        for (String dbName : dbNames) {
            Configuration dbConfig = new Configuration(dbName);
            
            if ("mem".equals(dbConfig.getProperty("db")) && dbConfig.getProperty("db.url") == null) {
                dbConfig.put("db.driver", "org.h2.Driver");
                dbConfig.put("db.url", "jdbc:h2:mem:play;MODE=MYSQL");
                dbConfig.put("db.user", "sa");
                dbConfig.put("db.pass", "");
            }

            if ("fs".equals(dbConfig.getProperty("db")) && dbConfig.getProperty("db.url") == null) {
                dbConfig.put("db.driver", "org.h2.Driver");
                dbConfig.put("db.url", "jdbc:h2:" + (new File(Play.applicationPath, "db/h2/play").getAbsolutePath()) + ";MODE=MYSQL");
                dbConfig.put("db.user", "sa");
                dbConfig.put("db.pass", "");
            }
            String datasourceName = dbConfig.getProperty("db", "");
            DataSource ds = DB.getDataSource(dbName);
                     
            if ((datasourceName.startsWith("java:") || datasourceName.startsWith("jndi:")) && dbConfig.getProperty("db.url") == null) {
                if (ds == null) {
                    return true;
                }
            } else {
                // Internal pool is c3p0, we should call the close() method to destroy it.
                check(dbConfig, "internal pool", "db.destroyMethod");

                dbConfig.put("db.destroyMethod", "close");
            }

            Matcher m = MYSQL_PATTERN.matcher(dbConfig.getProperty("db", ""));
            if (m.matches()) {
                String user = m.group("user");
                String password = m.group("pwd");
                String name = m.group("name");
                String host = m.group("host");
                String parameters = m.group("parameters");
                dbConfig.put("db.driver", "org.mariadb.jdbc.Driver");
                dbConfig.put("db.url", "jdbc:mysql://" + (host == null ? "localhost" : host) + "/" + name + "?" + parameters);
                if (user != null) {
                    dbConfig.put("db.user", user);
                }
                if (password != null) {
                    dbConfig.put("db.pass", password);
                }
            }

            m = MARIADB_PATTERN.matcher(dbConfig.getProperty("db", ""));
            if (m.matches()) {
                String user = m.group("user");
                String password = m.group("pwd");
                String name = m.group("name");
                String host = m.group("host");
                String parameters = m.group("parameters");
                dbConfig.put("db.driver", "org.mariadb.jdbc.Driver");
                dbConfig.put("db.url", "jdbc:mariadb://" + (host == null ? "localhost" : host) + "/" + name + "?" + parameters);
                if (user != null) {
                    dbConfig.put("db.user", user);
                }
                if (password != null) {
                    dbConfig.put("db.pass", password);
                }
            }
            
            m = POSTGRES_PATTERN.matcher(dbConfig.getProperty("db", ""));
            if (m.matches()) {
                String user = m.group("user");
                String password = m.group("pwd");
                String name = m.group("name");
                String host = m.group("host");
                dbConfig.put("db.driver", "org.postgresql.Driver");
                dbConfig.put("db.url", "jdbc:postgresql://" + (host == null ? "localhost" : host) + "/" + name);
                if (user != null) {
                    dbConfig.put("db.user", user);
                }
                if (password != null) {
                    dbConfig.put("db.pass", password);
                }
            }

            if(dbConfig.getProperty("db.url") != null && dbConfig.getProperty("db.url").startsWith("jdbc:h2:mem:")) {
                dbConfig.put("db.driver", "org.h2.Driver");
                dbConfig.put("db.user", "sa");
                dbConfig.put("db.pass", "");
            }

            if ((dbConfig.getProperty("db.driver") == null) || (dbConfig.getProperty("db.url") == null)) {
                return false;
            }
            
            if (ds == null) {
                return true;
            } else {
                if (!dbConfig.getProperty("db.driver").equals(factory.getDriverClass(ds))) {
                    return true;
                }
                if (!dbConfig.getProperty("db.url").equals(factory.getJdbcUrl(ds))) {
                    return true;
                }
                if (!dbConfig.getProperty("db.user", "").equals(factory.getUser(ds))) {
                    return true;
                }
            }

            ExtendedDatasource extDataSource = DB.datasources.get(dbName);

            if (extDataSource != null && !dbConfig.getProperty("db.destroyMethod", "").equals(extDataSource.getDestroyMethod())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Needed because DriverManager will not load a driver ouside of the system classloader
     */
    public static class ProxyDriver implements Driver {

        private final Driver driver;

        ProxyDriver(Driver d) {
            this.driver = d;
        }

        @Override
        public boolean acceptsURL(String u) throws SQLException {
            return this.driver.acceptsURL(u);
        }

        @Override
        public Connection connect(String u, Properties p) throws SQLException {
            return this.driver.connect(u, p);
        }

        @Override
        public int getMajorVersion() {
            return this.driver.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return this.driver.getMinorVersion();
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
            return this.driver.getPropertyInfo(u, p);
        }

        @Override
        public boolean jdbcCompliant() {
            return this.driver.jdbcCompliant();
        }
      
        // Method not annotated with @Override since getParentLogger() is a new method
        // in the CommonDataSource interface starting with JDK7 and this annotation
        // would cause compilation errors with JDK6.
        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {        
            return this.driver.getParentLogger();
        }       
    }

    private final Enhancer enhancer = new JdbcEnhancer();
    @Override
    public void enhance(ApplicationClasses.ApplicationClass applicationClass) throws Exception {
  		try {
  		      enhancer.enhanceThisClass(applicationClass);
  		} catch (Throwable t) {
  		     Logger.error(t, "DBPlugin enhancement error");
  		}
  	}

//    private TransactionalFilter txFilter = new TransactionalFilter("DBTransactionalFilter");
//
//    @Override
//    public Filter<Object> getFilter() {
//        return txFilter;
//    }
//
//    public static class TransactionalFilter extends Filter<Object> {
//        public TransactionalFilter(String name) {
//            super(name);
//        }
//        @Override
//        public Object withinFilter(play.libs.F.Function0<Object> fct) throws Throwable {
//            return DB.withinFilter(fct);
//        }
//    }

    @Override
    public String getStatus() {
        try {
            return factory.getStatus();
        }catch (SQLException e) {

        }
        return "";
    }



    /**
     * fungsi decrypt url
     * format decrypt url postgres "postgres://user:pwd@host/database"
     * format decrypt url mysql "mysql://user:pwd@host/database"
     */
    private void decryptUrl() {
        String encryptUrl = Play.configuration.getProperty("securedb");
        if(StringUtils.isEmpty(encryptUrl))
            return;
        String decryptUrl = Crypto.decryptAES(encryptUrl);
        if(StringUtils.isNotEmpty(decryptUrl))
            Play.configuration.put("db", decryptUrl);
    }

    public static void main(String[] args) throws Exception {
        Play.readConfiguration();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Masukkan IP Database [default:localhost] : ");
        String host = br.readLine();
        System.out.print("Masukkan Port Database [default:5432] : ");
        String port = br.readLine();
        System.out.print("Masukkan Nama Database [default:epns_prod] : ");
        String dbname = br.readLine();
        System.out.print("Masukkan User Database [default:epns] : ");
        String username = br.readLine();
        System.out.print("Masukkan Password Database [default:epns] : ");
        String pass = br.readLine();
        host = StringUtils.isEmpty(host) ? "localhost":host;
        port = StringUtils.isEmpty(port) ? "5432":port;
        dbname = StringUtils.isEmpty(dbname) ? "epns_prod":dbname;
        username = StringUtils.isEmpty(username) ? "epns":username;
        pass = StringUtils.isEmpty(pass) ? "epns":pass;
        String dburl = String.format("postgres://%s:%s@%s:%s/%s", username, pass, host, port, dbname);
        System.out.print("\n\n===========SILAHKAN MASUKAN SETING DI BAWAH INI KE APPLICATION.CONF===========\n\n");
        System.out.printf("db=%s\n\n", dburl);
        System.out.print("  ATAU    \n\n");
        System.out.printf("securedb=%s\n\n", Crypto.encryptAES(dburl));
        System.out.print("================================================================================\n\n");
    }

}
