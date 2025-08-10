package play.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory;
import play.Logger;
import play.Play;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Set;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;

public class HikariDataSourceFactory implements DataSourceFactory {

	@Override
	public DataSource createDataSource(Configuration dbConfig) throws SQLException {
		HikariDataSource ds = new HikariDataSource();
		ds.setDriverClassName(dbConfig.getProperty("db.driver"));
		ds.setJdbcUrl(dbConfig.getProperty("db.url"));
		ds.setUsername(dbConfig.getProperty("db.user"));
		ds.setPassword(dbConfig.getProperty("db.pass"));
		ds.setConnectionTimeout(parseLong(dbConfig.getProperty("db.pool.timeout", "30000")));//Default: 30000 (30 seconds)
		ds.setMinimumIdle(parseInt(dbConfig.getProperty("db.pool.minSize", "1")));
		ds.setIdleTimeout(parseLong(dbConfig.getProperty("db.pool.maxIdleTime", "600000"))); // Default: 600000 (10 minutes)
		ds.setMaximumPoolSize(Integer.parseInt(dbConfig.getProperty("db.pool.maxSize", "100")));
		Logger.debug("set Minimum Connection : %s", ds.getMinimumIdle());
		Logger.debug("set Max Connection : %s", ds.getMaximumPoolSize());
//		ds.setLeakDetectionThreshold(parseLong(dbConfig.getProperty("db.pool.leakDetectionThreshold", "5000")));
		ds.setValidationTimeout(parseLong(dbConfig.getProperty("db.pool.validationTimeout", "5000")));//Default: 5000
		ds.setLoginTimeout(parseInt(dbConfig.getProperty("db.pool.loginTimeout", "0"))); // in seconds
		ds.setMaxLifetime(parseLong(dbConfig.getProperty("db.pool.maxConnectionAge", "1800000"))); //  Default: 1800000 (30 minutes)
		if (dbConfig.getProperty("db.pool.connectionInitSql") != null) {
			ds.setConnectionInitSql(dbConfig.getProperty("db.pool.connectionInitSql"));
		}
		if (dbConfig.getProperty("db.testquery") != null) {
			ds.setConnectionTestQuery(dbConfig.getProperty("db.testquery"));
		} else {
			String driverClass = dbConfig.getProperty("db.driver");
			// https://mariadb.com/kb/en/about-mariadb-connector-j/
			if (driverClass.equals("org.mariadb.jdbc.Driver")) {
				ds.setConnectionTestQuery("/* ping */ SELECT 1");
			}
		}
		ds.setMetricsTrackerFactory(new PrometheusMetricsTrackerFactory());
		// This check is not required, but here to make it clear that nothing changes
		// for people
		// that don't set this configuration property. It may be safely removed.
//		if (dbConfig.getProperty("db.isolation") != null) {
			// ds.setConnectionCustomizerClassName(PlayConnectionCustomizer.class.getName());
//		}
		return ds;
	}

	@Override
	public String getStatus() throws SQLException {
		StringWriter sw = new StringWriter();
		PrintWriter out = new PrintWriter(sw);
		Set<String> dbNames = Configuration.getDbNames();		
		for (String dbName : dbNames) {
			DataSource ds = DB.getDataSource(dbName);
			out.println("<h6>Datasource ("+dbName+")</h6>");
			if (!(ds instanceof HikariDataSource datasource)) {
				out.println("(not yet connected)");
				return sw.toString();
			}
			out.println("<table class=\"table table-sm table-striped\">");
			out.println("<tr><td width=\"200\">Jdbc url</td><td>"+ getJdbcUrl(datasource)+"</td></tr>");
			out.println("<tr><td>Jdbc driver</td><td>"+ getDriverClass(datasource)+"</td></tr>");
			out.println("<tr><td>Jdbc user</td><td>"+getUser(datasource)+"</td></tr>");
			if (Play.mode.isDev()) {
				out.println("<tr><td>Jdbc password</td><td>"+ datasource.getPassword()+"</td></tr>");
			}
			out.println("<tr><td>Min idle</td><td>"+datasource.getMinimumIdle()+"</td></tr>");
			out.println("<tr><td>Max pool size</td><td>"+datasource.getMaximumPoolSize()+"</td></tr>");
			out.println("<tr><td>Active connection</td><td>"+datasource.getHikariPoolMXBean().getActiveConnections()+"</td></tr>");
			out.println("<tr><td>Max lifetime</td><td>"+datasource.getMaxLifetime()+"</td></tr>");
			out.println("<tr><td>Leak detection threshold</td><td>"+datasource.getLeakDetectionThreshold()+"</td></tr>");
			out.println("<tr><td>Initialization fail timeout</td><td>"+datasource.getInitializationFailTimeout()+"</td></tr>");	
			out.println("<tr><td>Validation timeout</td><td>"+datasource.getValidationTimeout()+"</td></tr>");	
			out.println("<tr><td>Idle timeout</td><td>"+datasource.getIdleTimeout()+"</td></tr>");	
			out.println("<tr><td>Login timeout</td><td>"+datasource.getLoginTimeout()+"</td></tr>");	
			out.println("<tr><td>Connection timeout</td><td>"+datasource.getConnectionTimeout()+"</td></tr>");	
			out.println("<tr><td>Test query</td><td>"+datasource.getConnectionTestQuery()+"</td></tr>");	
			out.println("</table></p>");
		}
		return sw.toString();
	}

	@Override
	public String getDriverClass(DataSource ds) {
		return ((HikariConfig) ds).getDriverClassName();
	}

	@Override
	public String getJdbcUrl(DataSource ds) {
		return ((HikariConfig) ds).getJdbcUrl();
	}

	@Override
	public String getUser(DataSource ds) {
		return ((HikariConfig) ds).getUsername();
	}
}
