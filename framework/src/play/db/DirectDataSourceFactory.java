package play.db;

import play.Play;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Set;

/**
 * @author ariefardiyansah
 * DataSourceFactory untuk kebutuhan direct connection tanpa pool manager
 */
public class DirectDataSourceFactory implements DataSourceFactory {
    @Override
    public DataSource createDataSource(Configuration dbConfig) throws SQLException {
        String driver = dbConfig.getProperty("db.driver");
        String jdbcUrl = dbConfig.getProperty("db.url");
        String user = dbConfig.getProperty("db.user");
        String pass = dbConfig.getProperty("db.pass");
        return new GenericDataSource(jdbcUrl, driver, user, pass);
    }

    @Override
    public String getStatus() throws SQLException {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        Set<String> dbNames = Configuration.getDbNames();
        for (String dbName : dbNames) {
            DataSource ds = DB.getDataSource(dbName);
            out.println("<h6>Datasource (" + dbName + ")</h6>");
            if (!(ds instanceof GenericDataSource datasource)) {
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
            out.println("</table></p>");
        }
        return sw.toString();
    }

    @Override
    public String getDriverClass(DataSource ds) {
        return ((GenericDataSource)ds).getDriverClass();
    }

    @Override
    public String getJdbcUrl(DataSource ds) {
        return ((GenericDataSource)ds).getJdbcUrl();
    }

    @Override
    public String getUser(DataSource ds) {
        return ((GenericDataSource)ds).getUser();
    }
}
