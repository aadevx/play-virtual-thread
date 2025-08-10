package play.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Enumeration;
import java.util.Properties;

/**
 * @author ariefardiyansah
 * Datasurce untuk kebutuhan direct connection tanpa pool manager
 */
public final class GenericDataSource implements DataSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericDataSource.class);
    private final String jdbcUrl;
    private final Properties driverProperties;
    private Driver driver;

    public GenericDataSource(String jdbcUrl, String driverClassName,  String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.driverProperties = new Properties();
        if (username != null) {
            this.driverProperties.put("user", this.driverProperties.getProperty("user", username));
        }

        if (password != null) {
            this.driverProperties.put("password", this.driverProperties.getProperty("password", password));
        }

        if (driverClassName != null) {
            Enumeration<Driver> drivers = DriverManager.getDrivers();

            while(drivers.hasMoreElements()) {
                Driver d = (Driver)drivers.nextElement();
                if (d.getClass().getName().equals(driverClassName)) {
                    this.driver = d;
                    break;
                }
            }

            if (this.driver == null) {
                LOGGER.warn("Registered driver with driverClassName={} was not found, trying direct instantiation.", driverClassName);
                Class<?> driverClass = null;
                ClassLoader threadContextClassLoader = Thread.currentThread().getContextClassLoader();
                try {
                    if (threadContextClassLoader != null) {
                        try {
                            driverClass = threadContextClassLoader.loadClass(driverClassName);
                            LOGGER.debug("Driver class {} found in Thread context class loader {}", driverClassName, threadContextClassLoader);
                        } catch (ClassNotFoundException var12) {
                            LOGGER.debug("Driver class {} not found in Thread context class loader {}, trying classloader {}", new Object[]{driverClassName, threadContextClassLoader, this.getClass().getClassLoader()});
                        }
                    }

                    if (driverClass == null) {
                        driverClass = this.getClass().getClassLoader().loadClass(driverClassName);
                        LOGGER.debug("Driver class {} found in the HikariConfig class classloader {}", driverClassName, this.getClass().getClassLoader());
                    }
                } catch (ClassNotFoundException var13) {
                    LOGGER.debug("Failed to load driver class {} from HikariConfig class classloader {}", driverClassName, this.getClass().getClassLoader());
                }

                if (driverClass != null) {
                    try {
                        this.driver = (Driver)driverClass.getDeclaredConstructor().newInstance();
                    } catch (Exception var11) {
                        LOGGER.warn("Failed to create instance of driver class {}, trying jdbcUrl resolution", driverClassName, var11);
                    }
                }
            }
        }

        String sanitizedUrl = jdbcUrl.replaceAll("([?&;]password=)[^&#;]*(.*)", "$1<masked>$2");

        try {
            if (this.driver == null) {
                this.driver = DriverManager.getDriver(jdbcUrl);
                LOGGER.debug("Loaded driver with class name {} for jdbcUrl={}", this.driver.getClass().getName(), sanitizedUrl);
            } else if (!this.driver.acceptsURL(jdbcUrl)) {
                throw new RuntimeException("Driver " + driverClassName + " claims to not accept jdbcUrl, " + sanitizedUrl);
            }

        } catch (SQLException var10) {
            throw new RuntimeException("Failed to get driver instance for jdbcUrl=" + sanitizedUrl, var10);
        }
    }

    public Connection getConnection() throws SQLException {
        return this.driver.connect(this.jdbcUrl, this.driverProperties);
    }

    public Connection getConnection(String username, String password) throws SQLException {
        Properties cloned = (Properties)this.driverProperties.clone();
        if (username != null) {
            cloned.put("user", username);
            if (cloned.containsKey("username")) {
                cloned.put("username", username);
            }
        }

        if (password != null) {
            cloned.put("password", password);
        }

        return this.driver.connect(this.jdbcUrl, cloned);
    }

    public PrintWriter getLogWriter() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void setLogWriter(PrintWriter logWriter) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public void setLoginTimeout(int seconds) throws SQLException {
        DriverManager.setLoginTimeout(seconds);
    }

    public int getLoginTimeout() throws SQLException {
        return DriverManager.getLoginTimeout();
    }

    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return this.driver.getParentLogger();
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getDriverClass() {
        return driver.getClass().getName();
    }

    public String getUser() {
        return driverProperties.getProperty("user");
    }

    public String getPassword() {
        return driverProperties.getProperty("password");
    }
}
