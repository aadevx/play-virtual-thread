package play.plugins;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;
import com.jamonapi.utils.Misc;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.libs.Json;
import play.libs.Time;
import play.mvc.Http.Header;
import play.mvc.Http.Request;
import play.mvc.Http.Response;

import java.io.*;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Arrays.asList;

public class PlayStatusPlugin extends PlayPlugin {

    /**
     * Get the application status
     *
     * @param json
     *            true if the status should be return in JSON
     * @return application status
     */
    public String computeApplicationStatus(boolean json) {
        if (json) {
            JsonObject o = new JsonObject();
            for (PlayPlugin plugin : Play.pluginCollection.getEnabledPlugins()) {
                try {
                    JsonObject status = plugin.getJsonStatus();
                    if (status != null) {
                        o.add(plugin.getClass().getName(), status);
                    }
                } catch (Throwable e) {
                    JsonObject error = new JsonObject();
                    error.add("error", new JsonPrimitive(e.getMessage()));
                    o.add(plugin.getClass().getName(), error);
                }
            }
            return o.toString();
        }
        StringBuilder dump = new StringBuilder(16);
        dump.append("<!doctype html>");
        dump.append("<html lang=\"en\">");
        dump.append("<head>");
        dump.append("<meta charset=\"UTF-8\" />");
        dump.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
        dump.append("<title>Application status</title>");
        dump.append("<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css\" rel=\"stylesheet\">");
        dump.append("</head>");
        dump.append("<body>");
        dump.append("<div class=\"container-fluid\">");
        for (PlayPlugin plugin : Play.pluginCollection.getEnabledPlugins()) {
            try {
                String status = plugin.getStatus();
                if (status != null) {
                    dump.append(status);
                    dump.append("</br >");
                }
            } catch (Throwable e) {
                dump.append(plugin.getClass().getName()).append(".getStatus() has failed (").append(e.getMessage()).append(")");
            }
        }
        dump.append("</div>");
        dump.append("</body>");
        dump.append("</html>");
        return dump.toString();
    }

    private final List<String> ippromotheus = new CopyOnWriteArrayList<>(Arrays.asList("180.233.156.10", "103.55.160.11", "103.170.105.24"));
    /**
     * Intercept /@status and check that the Authorization header is valid. Then ask each plugin for a status dump and
     * send it over the HTTP response.
     *
     * You can ask the /@status using the authorization header and putting your status secret key in it. Prior to that
     * you would be required to start play with a -DstatusKey=yourkey
     */
    @Override
    public boolean rawInvocation(Request request, Response response) throws Exception {
        String httpPath = Play.configuration.getProperty("http.path", "");
        if(httpPath.endsWith("/"))
            httpPath = StringUtils.removeEnd(httpPath, "/");
        if (request.path.equals(httpPath+"/@status") || request.path.equals(httpPath+"/@status.json")) {
            if (!Play.started) {
                response.print("Application is not started");
                response.status = 503;
                return true;
            }
            response.contentType = request.path.contains(".json") ? "application/json" : "text/html";
            Header authorization = request.headers.get("authorization");
            String statusKey = Play.configuration.getProperty("application.statusKey", System.getProperty("statusKey", Play.secretKey));
            if (authorization != null && statusKey != null && statusKey.equals(authorization.value())) {
                response.print(computeApplicationStatus(request.path.contains(".json")));
                response.status = 200;
                return true;
            }
            response.status = 401;
            if (response.contentType.equals("application/json")) {
                response.print("{\"error\": \"Not authorized\"}");
            } else {
                response.print("Not authorized");
            }
            return true;
        }
        else if (request.path.equals(httpPath+"/metrics")) {
            if (!Play.started) {
                response.print("Application is not started");
                response.status = 503;
                return true;
            }
            String allowed_ip = Play.configuration.getProperty("promotheus.remoteaddr");
            if(!StringUtils.isEmpty(allowed_ip)) {
                for(String ip: allowed_ip.split(";")) {
                    if(!ippromotheus.contains(ip))
                        ippromotheus.add(ip);
                }
            }
            if(!ippromotheus.contains(request.remoteAddress)){
                response.print("Akses Ditolak");
                response.status = 403;
                return true;
            }
            StringWriter writer = new StringWriter();
            CollectorRegistry registry = CollectorRegistry.defaultRegistry;
            TextFormat.write004(writer, registry.metricFamilySamples());
            response.contentType = TextFormat.CONTENT_TYPE_004;
            response.print(writer.toString());
            writer.close();
            return true;
        }
        else if (request.path.equals(httpPath+"/@query") || request.path.equals(httpPath+"/@query.json")) {
            if (!Play.started) {
                response.print("Application is not started");
                response.status = 503;
                return true;
            }
            response.contentType = request.path.contains(".json") ? "application/json" : "text/html";
            Header authorization = request.headers.get("authorization");
            String statusKey = Play.configuration.getProperty("application.statusKey", System.getProperty("statusKey", Play.secretKey));
            if (authorization != null && statusKey != null && statusKey.equals(authorization.value())) {
                response.print(computesQuery(request.path.contains(".json")));
                response.status = 200;
                return true;
            }
            response.status = 401;
            if (response.contentType.equals("application/json")) {
                response.print("{\"error\": \"Not authorized\"}");
            } else {
                response.print("Not authorized");
            }
            return true;
        }
        else if (request.path.equals(httpPath+"/@pages") || request.path.equals(httpPath+"/@pages.json")) {
            if (!Play.started) {
                response.print("Application is not started");
                response.status = 503;
                return true;
            }
            response.contentType = request.path.contains(".json") ? "application/json" : "text/html";
            Header authorization = request.headers.get("authorization");
            String statusKey = Play.configuration.getProperty("application.statusKey", System.getProperty("statusKey", Play.secretKey));
            if (authorization != null && statusKey != null && statusKey.equals(authorization.value())) {
                response.print(computesPage(request.path.contains(".json")));
                response.status = 200;
                return true;
            }
            response.status = 401;
            if (response.contentType.equals("application/json")) {
                response.print("{\"error\": \"Not authorized\"}");
            } else {
                response.print("Not authorized");
            }
            return true;
        }
        else if (request.path.equals(httpPath+"/@jobs") || request.path.equals(httpPath+"/@jobs.json")) {
            if (!Play.started) {
                response.print("Application is not started");
                response.status = 503;
                return true;
            }
            response.contentType = request.path.contains(".json") ? "application/json" : "text/html";
            Header authorization = request.headers.get("authorization");
            String statusKey = Play.configuration.getProperty("application.statusKey", System.getProperty("statusKey", Play.secretKey));
            if (authorization != null && statusKey != null && statusKey.equals(authorization.value())) {
                response.print(computesJobAndController(request.path.contains(".json")));
                response.status = 200;
                return true;
            }
            response.status = 401;
            if (response.contentType.equals("application/json")) {
                response.print("{\"error\": \"Not authorized\"}");
            } else {
                response.print("Not authorized");
            }
            return true;
        }
        else if (request.path.equals(httpPath+"/@checksum") || request.path.equals(httpPath+"/@checksum.json")) {
            if (!Play.started) {
                response.print("Application is not started");
                response.status = 503;
                return true;
            }
            response.contentType = request.path.contains(".json") ? "application/json" : "text/html";
            Header authorization = request.headers.get("authorization");
            String statusKey = Play.configuration.getProperty("application.statusKey", System.getProperty("statusKey", Play.secretKey));
            if (authorization != null && statusKey != null && statusKey.equals(authorization.value())) {
                response.print(computesChecksum(request.path.contains(".json")));
                response.status = 200;
                return true;
            }
            response.status = 401;
            if (response.contentType.equals("application/json")) {
                response.print("{\"error\": \"Not authorized\"}");
            } else {
                response.print("Not authorized");
            }
            return true;
        }
        return super.rawInvocation(request, response);
    }

    /**
     * Retrieve status about play core.
     */
    @Override
    public String getStatus() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.println("<h6>Java</h6>");
        out.println("<table class=\"table table-sm table-striped\">");
        out.println("<tr><td>Version</td><td>"+System.getProperty("java.version")+"</td></tr>");
        out.println("<tr><td>Home</td><td>"+System.getProperty("java.home")+"</td></tr>");
        out.println("<tr><td>Operating System</td><td>"+System.getProperty("os.name")+"</td></tr>");
        out.println("<tr><td>Max memory</td><td>"+getSizeFormatter(Runtime.getRuntime().maxMemory())+"</td></tr>");
        out.println("<tr><td>Free memory</td><td>"+getSizeFormatter(Runtime.getRuntime().freeMemory())+"</td></tr>");
        out.println("<tr><td>Total memory</td><td>"+getSizeFormatter(Runtime.getRuntime().totalMemory())+"</td></tr>");
        out.println("<tr><td>Available processors</td><td>"+Runtime.getRuntime().availableProcessors()+"</td></tr>");
        out.println("</table>");
        out.println();
        out.println("<h6>Play framework</h6>");
        out.println("<table class=\"table table-sm table-striped\">");
        out.println("<tr><td>Version</td><td>"+Play.version+"</td></tr>");
        out.println("<tr><td>Path</td><td>"+Play.frameworkPath+"</td></tr>");
        out.println("<tr><td>ID</td><td>"+(StringUtils.isEmpty(Play.id) ? "(not set)" : Play.id)+"</td></tr>");
        out.println("<tr><td>Mode</td><td>"+Play.mode+"</td></tr>");
        out.println("<tr><td>Tmp dir</td><td>"+(Play.tmpDir == null ? "(no tmp dir)" : Play.tmpDir)+"</td></tr>");
        out.println("</table>");
        out.println();
        out.println("<h6>Application</h6>");
        out.println("<table class=\"table table-sm table-striped\">");
        out.println("<tr><td>Path</td><td>"+Play.applicationPath+"</td></tr>");
        out.println("<tr><td>Name</td><td>"+Play.configuration.getProperty("application.name", "(not set)")+"</td></tr>");
        out.println("<tr><td>Started at</td><td>"+(Play.started ? DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm").format(Time.toLocalDateTime(Play.startedAt)) : "Not yet started")+"</td></tr>");
        out.println("</table>");
        out.println();
        out.println("<h6>Loaded modules:</h6>");
        out.println("<table class=\"table table-sm table-striped\">");
        Play.modules.forEach((k, v) -> {
            out.println("<tr><td>"+k + " at " + v+"</td></tr>");
        });

        out.println("</table>");
        out.println();
        out.println("<h6>Loaded plugins:</h6>");
        out.println("<table class=\"table table-sm table-striped\">");
        for (PlayPlugin plugin : Play.pluginCollection.getAllPlugins()) {
            out.println("<tr><td>"+plugin.index + ":" + plugin.getClass().getName() + " ["
                    + (Play.pluginCollection.isEnabled(plugin) ? "enabled" : "disabled") + "]</td></tr>");
        }
        out.println("</table>");
        out.println();
        out.println("<h6>Threads:</h6>");
        out.println("<table class=\"table table-sm table-striped\">");
        try {
            visit(out, getRootThread(), 0);
        } catch (Throwable e) {
        	out.print("<tr><td>");
            out.println("Oops; " + e.getMessage());
            out.print("</td></tr>");
        }
        out.println("</table>");
        out.println();
        return sw.toString();
    }

    @Override
    public JsonObject getJsonStatus() {
        JsonObject status = new JsonObject();

        {
            JsonObject java = new JsonObject();
            java.addProperty("version", System.getProperty("java.version"));
            status.add("java", java);
        }

        {
            JsonObject memory = new JsonObject();
            memory.addProperty("max", Runtime.getRuntime().maxMemory());
            memory.addProperty("free", Runtime.getRuntime().freeMemory());
            memory.addProperty("total", Runtime.getRuntime().totalMemory());
            status.add("memory", memory);
        }

        {
            JsonObject application = new JsonObject();
            application.addProperty("uptime", Play.started ? System.currentTimeMillis() - Play.startedAt : -1);
            application.addProperty("path", Play.applicationPath.getAbsolutePath());
            status.add("application", application);
        }

        {
            JsonArray monitors = new JsonArray();
            try {
                Object[][] data = Misc.sort(MonitorFactory.getRootMonitor().getBasicData(), 3, "desc");
                for (Object[] row : data) {
                    if (((Double) row[1]) > 0) {
                        JsonObject o = new JsonObject();
                        o.addProperty("name", row[0].toString());
                        o.addProperty("hits", (Double) row[1]);
                        o.addProperty("avg", (Double) row[2]);
                        o.addProperty("min", (Double) row[6]);
                        o.addProperty("max", (Double) row[7]);
                        monitors.add(o);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            status.add("monitors", monitors);
        }

        return status;
    }

    /**
     * Recursively visit all JVM threads
     */
    private void visit(PrintWriter out, ThreadGroup group, int level) {
        // Get threads in `group'
        int numThreads = group.activeCount();
        Thread[] threads = new Thread[numThreads * 2];
        numThreads = group.enumerate(threads, false);

        // Enumerate each thread in `group'
        for (int i = 0; i < numThreads; i++) {
            // Get thread
            Thread thread = threads[i];            
            out.print("<tr><td>");
            out.println(thread + " " + thread.getState());
            out.print("</td></tr>");
        }

        // Get thread subgroups of `group'
        int numGroups = group.activeGroupCount();
        ThreadGroup[] groups = new ThreadGroup[numGroups * 2];
        numGroups = group.enumerate(groups, false);

        // Recursively visit each subgroup
        for (int i = 0; i < numGroups; i++) {
            visit(out, groups[i], level + 1);
        }
    }

    /**
     * Retrieve the JVM root thread group.
     */
    private ThreadGroup getRootThread() {
        ThreadGroup root = Thread.currentThread().getThreadGroup().getParent();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        return root;
    }

    private String getSizeFormatter(long size) {
        return size + " ("+FileUtils.byteCountToDisplaySize(size)+")";
    }

    private String computesPage(boolean json) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.println("<!doctype html>");
        out.println("<html lang=\"en\">");
        out.println("<head>");
        out.println("<meta charset=\"UTF-8\" />");
        out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
        out.println("<title>Application status</title>");
        out.println("<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css\" rel=\"stylesheet\">");
        out.println("</head>");
        out.println("<body>");
        out.println("<div class=\"container-fluid\">");
        try {
            out.println("<h6>Monitor page:</h6>");
            out.println("<table class=\"table table-sm\" width=\"700\">");
            out.println(" <thead><tr><th>Page</th><th>hit</th><th>avg</th><th>min</th><th>max</th></tr></thead>");
            List<Monitor> monitors = new ArrayList<>(asList(MonitorFactory.getRootMonitor().getMonitors()));
            monitors.sort((m1, m2) -> Double.compare(m2.getTotal(), m1.getTotal()));
            out.println("<tbody>");
            for (Monitor monitor : monitors) {
                if(isPage(monitor.getLabel())) {
                    if (monitor.getHits() > 0) {
                        if (monitor.getAvg() > 50 && monitor.getAvg() < 100)
                            out.println("<tr class=\"table-warning\">");
                        else if (monitor.getAvg() > 100)
                            out.println("<tr class=\"table-danger\">");
                        else
                            out.println("<tr>");
                        out.print(String.format("<td>%s</td><td>%8.0f</td><td>%8.1f</td><td>%8.1f</td><td>%8.1f</td>",
                                WordUtils.wrap(monitor.getLabel(), 200),
                                monitor.getHits(), monitor.getAvg(), monitor.getMin(), monitor.getMax()));
                        out.println("</tr>");
                    }
                }
            }
            out.println("</tbody>");
            out.println("</table>");
        } catch (Exception e) {
            out.println("No monitors found: " + e);
        }
        out.println("</div>");
        out.println("</body>");
        out.println("</html>");
        return sw.toString();
    }

    private String computesQuery(boolean json) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.println("<!doctype html>");
        out.println("<html lang=\"en\">");
        out.println("<head>");
        out.println("<meta charset=\"UTF-8\" />");
        out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
        out.println("<title>Application status</title>");
        out.println("<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css\" rel=\"stylesheet\">");
        out.println("<style>\n table {\n table-layout: fixed;\n width: 650px;\n }\n</style>");
        out.println("</head>");
        out.println("<body>");
        out.println("<div class=\"container-fluid\">");
        try {
            out.println("<h6>Monitor Slow Query:</h6>");
            out.println("<table class=\"table table-sm\">");
            out.println(" <thead><tr><th>Slow Query</th><th width=\"5%\">hit</th><th width=\"5%\">avg</th><th width=\"5%\">min</th><th width=\"5%\">max</th></tr></thead>");
            List<Monitor> monitors = new ArrayList<>(asList(MonitorFactory.getRootMonitor().getMonitors()));
            monitors.sort((m1, m2) -> Double.compare(m2.getTotal(), m1.getTotal()));
            out.println("<tbody>");
            for (Monitor monitor : monitors) {
                if(isQuery(monitor.getLabel())) {
                    if (monitor.getHits() > 0) {
                        if (monitor.getAvg() > 200) {
                            out.println("<tr class=\"table-danger\">");
                            out.print(String.format("<td>%s</td><td>%8.0f</td><td>%8.1f</td><td>%8.1f</td><td>%8.1f</td>",
                                    WordUtils.wrap(monitor.getLabel(), 200),
                                    monitor.getHits(), monitor.getAvg(), monitor.getMin(), monitor.getMax()));
                            out.println("</tr>");
                        }
                    }
                }
            }
            out.println("</tbody>");
            out.println("</table>");
        } catch (Exception e) {
            out.println("No monitors found: " + e);
        }
        out.println("</div>");
        out.println("</body>");
        out.println("</html>");
        return sw.toString();
    }

    private String computesJobAndController(boolean json) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.println("<!doctype html>");
        out.println("<html lang=\"en\">");
        out.println("<head>");
        out.println("<meta charset=\"UTF-8\" />");
        out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
        out.println("<title>Application status</title>");
        out.println("<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css\" rel=\"stylesheet\">");
        out.println("</head>");
        out.println("<body>");
        out.println("<div class=\"container-fluid\">");
        try {
            out.println("<h6>Monitors:</h6>");
            out.println("<table class=\"table table-sm\" width=\"700\">");
            out.println(" <thead><tr><th>Job/Controller</th><th>hit</th><th>avg</th><th>min</th><th>max</th></tr></thead>");
            List<Monitor> monitors = new ArrayList<>(asList(MonitorFactory.getRootMonitor().getMonitors()));
            monitors.sort((m1, m2) -> Double.compare(m2.getTotal(), m1.getTotal()));
            out.println("<tbody>");
            for (Monitor monitor : monitors) {
                if (!isPage(monitor.getLabel()) && !isQuery(monitor.getLabel())) {
                    if (monitor.getHits() > 0) {
                        if (monitor.getAvg() > 50 && monitor.getAvg() < 100)
                            out.println("<tr class=\"table-warning\">");
                        else if (monitor.getAvg() > 100)
                            out.println("<tr class=\"table-danger\">");
                        else
                            out.println("<tr>");
                        out.print(String.format("<td>%s</td><td>%8.0f</td><td>%8.1f</td><td>%8.1f</td><td>%8.1f</td>",
                                WordUtils.wrap(monitor.getLabel(), 200),
                                monitor.getHits(), monitor.getAvg(), monitor.getMin(), monitor.getMax()));
                        out.println("</tr>");
                    }
                }
            }
            out.println("</tbody>");
            out.println("</table>");
        } catch (Exception e) {
            out.println("No monitors found: " + e);
        }
        out.println("</div>");
        out.println("</body>");
        out.println("</html>");
        return sw.toString();
    }

    private boolean isQuery(String label) {
        label = label.toUpperCase().trim();
        return label.startsWith("SELECT ") || label.startsWith("UPDATE ") || label.startsWith("INSERT ") || label.startsWith("DELETE ") || label.startsWith("WITH ");
    }

    private boolean isPage(String label) {
        return label.endsWith(".html") || label.endsWith(".jte");
    }

    private String computesChecksum(boolean json) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        File baseDir=Play.applicationPath;
        Collection<File> files = FileUtils.listFiles(baseDir, new String[]{"jar"}, true);
        Map<String, String> checksumFile = new HashMap<>();
        for (File file : files) {
            try {
                URI relativePath = baseDir.toURI().relativize(file.toURI());
                String hashFile = DigestUtils.md5Hex(new FileInputStream(file));
                checksumFile.put(relativePath.getPath(), hashFile);
            } catch (IOException e) {
                Logger.error(e, e.getMessage());
            }
        }
        if(json) {
            out.println(Json.toJson(checksumFile));
        } else {
            out.println("<!doctype html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("<meta charset=\"UTF-8\" />");
            out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("<title>Application status</title>");
            out.println("<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css\" rel=\"stylesheet\">");
            out.println("</head>");
            out.println("<body>");
            out.println("<div class=\"container-fluid\">");
            try {
                out.println("<h6>Monitors:</h6>");
                out.println("<table class=\"table table-sm\" width=\"700\">");
                out.println(" <thead><tr><th>Filename</th><th>Hash</th></tr></thead>");
                out.println("<tbody>");
                checksumFile.forEach((key, value) -> {
                    out.print(String.format("<tr><td>%s</td><td>%s</td></tr>", key, value));
                });
                out.println("</tbody>");
                out.println("</table>");
            } catch (Exception e) {
                Logger.error(e, "computesChecksum error : %s", e.getMessage());
                out.println("No checksum found: " + e);
            }
            out.println("</div>");
            out.println("</body>");
            out.println("</html>");
        }
        return sw.toString();
    }
}