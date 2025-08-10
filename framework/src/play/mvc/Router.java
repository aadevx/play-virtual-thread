package play.mvc;

import org.apache.commons.lang3.StringUtils;
import play.Logger;
import play.Play;
import play.Play.Mode;
import play.exceptions.ActionNotFoundException;
import play.exceptions.NoRouteFoundException;
import play.exceptions.UnexpectedException;
import play.libs.IO;
import play.modules.routing.RouterAnnotation;
import play.mvc.results.NotFound;
import play.mvc.results.RenderStatic;
import play.templates.TemplateLoader;
import play.utils.Default;
import play.utils.FileUtils;
import play.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The router matches HTTP requests to action invocations
 */
public class Router {

    static final Pattern routePattern = Pattern.compile(
            "^(?<method>GET|POST|PUT|PATCH|DELETE|OPTIONS|HEAD|WS|\\*)[(]?(?<headers>[^)]*)(\\))?\\s+(?<path>.*/[^\\s]*)\\s+(?<action>[^\\s(]+)(?<params>.+)?(\\s*)$");
    /**
     * Pattern used to locate a method override instruction in request.querystring
     */
    static final Pattern methodOverride = Pattern.compile("^.*x-http-method-override=(?<method>GET|PUT|POST|PATCH|DELETE).*$");
    /**
     * Timestamp the routes file was last loaded at.
     */
    public static long lastLoading = -1;
    public static boolean reload = true;
    /**
     * Parse the routes file. This is called at startup.
     *
     * @param prefix
     *            The prefix that the path of all routes in this route file start with. This prefix should not end with
     *            a '/' character.
     */
    public static void load(String prefix) {
        routes.clear();
        actionRoutesCache.clear();
        RouterAnnotation obj = new RouterAnnotation();
        obj.computeRoutes();
        // parse conf/routes app n modules
        parse(Play.routes, prefix);
        obj.defaultRouteController();
        lastLoading = System.currentTimeMillis();
        Play.pluginCollection.onRoutesLoaded();
    }

    /**
     * This one can be called to add new route. Last added is first in the route list.
     *
     * @param method
     *            The method of the route
     * @param path
     *            The path of the route
     * @param action
     *            The associated action
     * @param headers
     *            The headers
     */
    public static void prependRoute(String method, String path, String action, String headers) {
        prependRoute(method, path, action, null, headers);
    }

    /**
     * This one can be called to add new route. Last added is first in the route list.
     *
     * @param method
     *            The method of the route
     * @param path
     *            The path of the route
     * @param action
     *            The associated action
     */
    public static void prependRoute(String method, String path, String action) {
        prependRoute(method, path, action, null, null);
    }

    /**
     * Add a route at the given position
     *
     * @param position
     *            The position where to insert the route
     * @param method
     *            The method of the route
     * @param path
     *            The path of the route
     * @param action
     *            The associated action
     * @param params
     *            The parameters
     * @param headers
     *            The headers
     */
    public static void addRoute(int position, String method, String path, String action, String params, String headers) {
        if (position > routes.size()) {
            position = routes.size();
        }
        routes.add(position, getRoute(method, path, action, params, headers));
    }

    /**
     * Add a route at the given position
     *
     * @param position
     *            The position where to insert the route
     * @param method
     *            The method of the route
     * @param path
     *            The path of the route
     * @param headers
     *            The headers
     */
    public static void addRoute(int position, String method, String path, String headers) {
        addRoute(position, method, path, null, null, headers);
    }

    /**
     * Add a route at the given position
     *
     * @param position
     *            The position where to insert the route
     * @param method
     *            The method of the route
     * @param path
     *            The path of the route
     * @param action
     *            The associated action
     * @param headers
     *            The headers
     */
    public static void addRoute(int position, String method, String path, String action, String headers) {
        addRoute(position, method, path, action, null, headers);
    }

    /**
     * Add a new route. Will be first in the route list
     *
     * @param method
     *            The method of the route * @param action : The associated action
     * @param path
     *            The path of the route
     * @param action
     *            The associated action
     */
    public static void addRoute(String method, String path, String action) {
        prependRoute(method, path, action);
    }

    /**
     * Add a route at the given position
     *
     * @param method
     *            The method of the route
     * @param path
     *            The path of the route
     * @param action
     *            The associated action
     * @param headers
     *            The headers
     */
    public static void addRoute(String method, String path, String action, String headers) {
        addRoute(method, path, action, null, headers);
    }

    /**
     * Add a route
     *
     * @param method
     *            The method of the route
     * @param path
     *            The path of the route
     * @param action
     *            The associated action
     * @param params
     *            The parameters
     * @param headers
     *            The headers
     */
    public static void addRoute(String method, String path, String action, String params, String headers) {
        appendRoute(method, path, action, params, headers, null, 0);
    }

    /**
     * This is used internally when reading the route file. The order the routes are added matters and we want the
     * method to append the routes to the list.
     *
     * @param method
     *            The method of the route
     * @param path
     *            The path of the route
     * @param action
     *            The associated action
     * @param params
     *            The parameters
     * @param headers
     *            The headers
     * @param sourceFile
     *            The source file
     * @param line
     *            The source line
     */
    public static void appendRoute(String method, String path, String action, String params, String headers, String sourceFile, int line) {
        routes.add(getRoute(method, path, action, params, headers, sourceFile, line));
    }

    public static Route getRoute(String method, String path, String action, String params, String headers) {
        return getRoute(method, path, action, params, headers, null, 0);
    }

    public static Route getRoute(String method, String path, String action, String params, String headers, String sourceFile, int line) {
        Route route = new Route();
        route.method = method;
        route.path = path.replace("//", "/");
        route.action = action;
        route.routesFile = sourceFile;
        route.routesFileLine = line;
        route.addFormat(headers);
        route.addParams(params);
        route.compute();
        if (Logger.isTraceEnabled()) {
            Logger.trace("Adding [" + route + "] with params [" + params + "] and headers [" + headers + "]");
        }
        return route;
    }

    /**
     * Add a new route at the beginning of the route list
     *
     * @param method
     *            The method of the route
     * @param path
     *            The path of the route
     * @param action
     *            The associated action
     * @param params
     *            The parameters
     * @param headers
     *            The headers
     */
    public static void prependRoute(String method, String path, String action, String params, String headers) {
        routes.add(0, getRoute(method, path, action, params, headers));
    }

    /**
     * Parse a route file. If an action starts with <i>"plugin:name"</i>, replace that route by the ones declared in the
     * plugin route file denoted by that <i>name</i>, if found.
     *
     * @param routeFile
     * @param prefix
     *            The prefix that the path of all routes in this route file start with. This prefix should not end with
     *            a '/' character.
     */
    static void parse(File routeFile, String prefix) {
        String fileAbsolutePath = routeFile.getAbsolutePath();
        String content = Play.usePrecompiled ? "" : IO.readContentAsString(routeFile);
        if (Play.usePrecompiled || content.indexOf("${") > -1 || content.indexOf("#{") > -1 || content.indexOf("%{") > -1) {
            // Mutable map needs to be passed in.
            content = TemplateLoader.load(routeFile).render(new HashMap<String, Object>(16));
        }
        parse(content, prefix, fileAbsolutePath);
    }

    static void parse(String content, String prefix, String fileAbsolutePath) {
        int lineNumber = 0;
        Pattern pattern = Pattern.compile("\\s+");
        for (String line : content.split("\n")) {
            lineNumber++;
            line = pattern.matcher(line.trim()).replaceAll(" ");
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher matcher = routePattern.matcher(line);
            if (matcher.matches()) {
                String action = matcher.group("action");
                // module:
                if (action.startsWith("module:")) {
                    String moduleName = action.substring("module:".length());
                    String newPrefix = prefix + matcher.group("path");
                    if (newPrefix.length() > 1 && newPrefix.endsWith("/")) {
                        newPrefix = newPrefix.substring(0, newPrefix.length() - 1);
                    }
                    if (moduleName.equals("*")) {
                        for (String p : Play.modulesRoutes.keySet()) {
                            parse(Play.modulesRoutes.get(p), newPrefix + p);
                        }
                    } else if (Play.modulesRoutes.containsKey(moduleName)) {
                        parse(Play.modulesRoutes.get(moduleName), newPrefix);
                    } else {
                        Logger.error("Cannot include routes for module %s (not found)", moduleName);
                    }
                } else {
                    String method = matcher.group("method");
                    String path = prefix + matcher.group("path");
                    String params = matcher.group("params");
                    String headers = matcher.group("headers");
                    appendRoute(method, path, action, params, headers, fileAbsolutePath, lineNumber);
                }
            } else {
                Logger.error("Invalid route definition : %s", line);
            }
        }
    }

    /**
     * <p>
     * In PROD mode and if the routes are already loaded, this does nothing.
     * </p>
     * <p>
     * In DEV mode, this checks each routes file's "last modified" time to see if the routes need updated.
     * </p>
     *
     * @param prefix
     *            The prefix that the path of all routes in this route file start with. This prefix should not end with
     *            a '/' character.
     */
    public static void detectChanges(String prefix) {
        if (Play.mode.isProd() && lastLoading > 0) {
            return;
        }
        if (Play.routes.lastModified() > lastLoading || reload) {
            load(prefix);
        } else {
            for (File file : Play.modulesRoutes.values()) {
                if (file.lastModified() > lastLoading || reload) {
                    load(prefix);
                    return;
                }
            }
        }
    }

    /**
     * All the loaded routes.
     */
    public static final List<Route> routes = new CopyOnWriteArrayList<>();

    public static void routeOnlyStatic(Http.Request request) {
        for (Route route : routes) {
            try {
                Map<String, String> args = route.matches(request.method, request.path, request.format, request.domain);
                if (args != null) {
                    request.routeArgs = args;
                    request.action = route.action;
                    if (args.containsKey("format")) {
                        request.format = args.get("format");
                    }
                    if (request.action.indexOf("{") > -1) { // more optimization ?
                        for (String arg : request.routeArgs.keySet()) {
                            request.action = request.action.replace("{" + arg + "}", request.routeArgs.get(arg));
                        }
                    }
                    if (request.action.equals("404")) {
                        throw new NotFound(route.path);
                    }
                    request.resolveFormat();
                    try {
                        Object[] ca = ActionInvoker.getActionMethod(request.action);
                        Method actionMethod = (Method) ca[1];
                        request.controller = ((Class) ca[0]).getName().substring(12).replace("$", "");
                        request.controllerClass = ((Class) ca[0]);
                        request.actionMethod = actionMethod.getName();
                        request.action = request.controller + "." + request.actionMethod;
                        request.invokedMethod = actionMethod;
                        request.resolved = true;
                        break;
                    } catch (ActionNotFoundException e) {
                        throw new NotFound(String.format("%s action not found", e.getAction()));
                    }
                }
            } catch (RenderStatic | NotFound e) {
                throw e;
            } catch (Throwable ignore) {
            }
        }
        if(!request.resolved) {
            throw new NotFound(request.method, request.path);
        }
    }


    public static Route route(Http.Request request) {
        if (Logger.isTraceEnabled()) {
            Logger.trace("Route: " + request.path + " - " + request.querystring);
        }
        // request method may be overridden if a x-http-method-override parameter
        // is given
        if (request.querystring != null && methodOverride.matcher(request.querystring).matches()) {
            Matcher matcher = methodOverride.matcher(request.querystring);
            if (matcher.matches()) {
                if (Logger.isTraceEnabled()) {
                    Logger.trace("request method %s overridden to %s ", request.method, matcher.group("method"));
                }
                request.method = matcher.group("method");
            }
        }
        for (Route route : routes) {
            Map<String, String> args = route.matches(request.method, request.path, request.format, request.domain);
            if (args != null) {
                request.routeArgs = args;
                request.action = route.action;
                if (args.containsKey("format")) {
                    request.format = args.get("format");
                }
                if (request.action.indexOf("{") > -1) { // more optimization ?
                    for (String arg : request.routeArgs.keySet()) {
                        request.action = request.action.replace("{" + arg + "}", request.routeArgs.get(arg));
                    }
                }
                if (request.action.equals("404")) {
                    throw new NotFound(route.path);
                }
                return route;
            }
        }
        // Not found - if the request was a HEAD, let's see if we can find a
        // corresponding GET
        if (request.method.equalsIgnoreCase("head")) {
            request.method = "GET";
            Route route = route(request);
            request.method = "HEAD";
            return route;
        }
        throw new NotFound(request.method, request.path);
    }

    public static Map<String, String> route(String method, String path) {
        return route(method, path, null, null);
    }

    public static Map<String, String> route(String method, String path, String headers) {
        return route(method, path, headers, null);
    }

    public static Map<String, String> route(String method, String path, String headers, String host) {
        for (Route route : routes) {
            Map<String, String> args = route.matches(method, path, headers, host);
            if (args != null) {
                args.put("action", route.action);
                return args;
            }
        }
        return new HashMap<>(16);
    }

    public static ActionDefinition reverse(String action) {
        // Note the map is not <code>Collections.EMPTY_MAP</code> because it
        // will be copied and changed.
        return reverse(action, new HashMap<String, Object>(16));
    }

    public static String getFullUrl(String action, Map<String, Object> args) {
        ActionDefinition actionDefinition = reverse(action, args);
        String base = getBaseUrl();
        if (actionDefinition.method.equals("WS")) {
            return base.replaceFirst("http:", "ws:").replaceFirst("https:", "wss:") + actionDefinition;
        }
        return base + actionDefinition;
    }

    // Gets baseUrl from current request or application.baseUrl in
    // application.conf
    public static String getBaseUrl() {
        if (Http.Request.current() == null) {
            // No current request is present - must get baseUrl from config
            String appBaseUrl = Play.configuration.getProperty("application.baseUrl", "application.baseUrl");
            if (appBaseUrl.endsWith("/")) {
                // remove the trailing slash
                appBaseUrl = appBaseUrl.substring(0, appBaseUrl.length() - 1);
            }
            return appBaseUrl;

        } else {
            return Http.Request.current().getBase();
        }
    }

    public static String getFullUrl(String action) {
        // Note the map is not <code>Collections.EMPTY_MAP</code> because it
        // will be copied and changed.
        return getFullUrl(action, new HashMap<String, Object>(16));
    }

    public static String reverse(File file) {
        return reverse(file, false);
    }

    public static String reverse(File file, boolean absolute) {
        if (file == null || !file.exists()) {
            throw new NoRouteFoundException("File not found (" + file + ")");
        }
        String path = FileUtils.relativePath(file);
        path = path.substring(path.indexOf("}") + 1);
        for (Route route : routes) {
            String staticDir = route.staticDir;
            if (staticDir != null) {
                if (!staticDir.startsWith("/")) {
                    staticDir = "/" + staticDir;
                }
                if (!staticDir.equals("/") && !staticDir.endsWith("/")) {
                    staticDir = staticDir + "/";
                }
                if (path.startsWith(staticDir)) {
                    String to = route.path + path.substring(staticDir.length());
                    if (to.endsWith("/index.html")) {
                        to = to.substring(0, to.length() - "/index.html".length() + 1);
                    }
                    if (absolute) {
                        boolean isSecure = Http.Request.current() != null && Http.Request.current().secure;
                        String base = getBaseUrl();
                        if (!StringUtils.isEmpty(route.host)) {
                            // Compute the host
                            int port = Http.Request.current() == null ? 80 : Http.Request.current().get().port;
                            String host = (port != 80 && port != 443) ? route.host + ":" + port : route.host;
                            to = (isSecure ? "https://" : "http://") + host + to;
                        } else {
                            to = base + to;
                        }
                    }
                    return to;
                }
            }
        }
        throw new NoRouteFoundException(path);
    }

    public static String reverseWithCheck(String name, File file, boolean absolute) {
        if (file == null || !file.exists()) {
            Logger.error("Not Found: %s", name);
            throw new NoRouteFoundException(name + " (file not found)");
        }
        return reverse(file, absolute);
    }

    public static ActionDefinition reverse(String action, Map<String, Object> args) {

        String encoding = Http.Response.current() == null ? Play.defaultWebEncoding : Http.Response.current().encoding;

        if (action.startsWith("controllers.")) {
            action = action.substring(12);
        }
        Map<String, Object> argsbackup = new HashMap<>(args);

        Http.Request request = Http.Request.current();
        String requestFormat = request == null || request.format == null ? "" : request.format;

        List<ActionRoute> matchingRoutes = getActionRoutes(action);
        for (ActionRoute actionRoute : matchingRoutes) {
            Route route = actionRoute.route;
            args.putAll(actionRoute.args);

            List<String> inPathArgs = new ArrayList<>(16);
            boolean allRequiredArgsAreHere = true;
            // les noms de parametres matchent ils ?
            for (Route.Arg arg : route.args) {
                inPathArgs.add(arg.name);
                Object value = args.get(arg.name);
                if (value == null) {
                    // This is a hack for reverting on hostname that are
                    // a regex expression.
                    // See [#344] for more into. This is not optimal and
                    // should retough. However,
                    // it allows us to do things like {(.*}}.domain.com
                    String host = route.host.replaceAll("\\{", "").replaceAll("\\}", "");
                    if (host.equals(arg.name) || host.matches(arg.name)) {
                        args.remove(arg.name);
                        route.host = request == null ? "" : request.domain;
                        break;
                    } else {
                        allRequiredArgsAreHere = false;
                        break;
                    }
                } else {
                    if (value instanceof List<?> l) {
                        value = l.get(0);
                    }
                    if (!value.toString().startsWith(":") && !arg.constraint.matcher(Utils.urlEncodePath(value.toString())).matches()) {
                        allRequiredArgsAreHere = false;
                        break;
                    }
                }
            }
            // les parametres codes en dur dans la route matchent-ils ?
            for (String staticKey : route.staticArgs.keySet()) {
                if (staticKey.equals("format")) {
                    if (!requestFormat.equals(route.staticArgs.get("format"))) {
                        allRequiredArgsAreHere = false;
                        break;
                    }
                    continue; // format is a special key
                }
                if (!args.containsKey(staticKey) || (args.get(staticKey) == null)
                        || !args.get(staticKey).toString().equals(route.staticArgs.get(staticKey))) {
                    allRequiredArgsAreHere = false;
                    break;
                }
            }
            if (allRequiredArgsAreHere) {
                StringBuilder queryString = new StringBuilder();
                String path = route.path;
                String host = route.host;
                if (path.endsWith("/?")) {
                    path = path.substring(0, path.length() - 2);
                }
                for (Map.Entry<String, Object> entry : args.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (inPathArgs.contains(key) && value != null) {
                        if (List.class.isAssignableFrom(value.getClass())) {
                            @SuppressWarnings("unchecked")
                            List<Object> vals = (List<Object>) value;
                            path = path.replaceAll("\\{(<[^>]+>)?" + key + "\\}", vals.get(0).toString()).replace("$", "\\$");
                        } else {
                            try {
                                path = path.replaceAll("\\{(<[^>]+>)?" + key + "\\}", URLEncoder.encode(value.toString(), encoding)
                                        .replace("$", "\\$").replace("%3A", ":").replace("%40", "@").replace("+", "%20"));
                            } catch (UnsupportedEncodingException e) {
                                path = path.replaceAll("\\{(<[^>]+>)?" + key + "\\}",
                                        value.toString().replace("$", "\\$").replace("%3A", ":").replace("%40", "@").replace("+", "%20"));
                            }
                            try {
                                host = host.replaceAll("\\{(<[^>]+>)?" + key + "\\}", URLEncoder.encode(value.toString(), encoding)
                                        .replace("$", "\\$").replace("%3A", ":").replace("%40", "@").replace("+", "%20"));
                            } catch (UnsupportedEncodingException e) {
                                host = host.replaceAll("\\{(<[^>]+>)?" + key + "\\}",
                                        value.toString().replace("$", "\\$").replace("%3A", ":").replace("%40", "@").replace("+", "%20"));
                            }
                        }
                    } else if (route.staticArgs.containsKey(key)) {
                        // Do nothing -> The key is static
                    } else if (!argsbackup.containsKey(key)) {
                        // Do nothing -> The key is provided in
                        // RouteArgs and not used (see #447)
                    } else if (value != null) {
                        if (List.class.isAssignableFrom(value.getClass())) {
                            @SuppressWarnings("unchecked")
                            List<Object> vals = (List<Object>) value;
                            for (Object object : vals) {
                                try {
                                    queryString.append(URLEncoder.encode(key, encoding));
                                    queryString.append("=");
                                    String objStr = object.toString();
                                    // Special case to handle jsAction
                                    // tag
                                    if (objStr.startsWith(":") && objStr.length() > 1) {
                                        queryString.append(':');
                                        objStr = objStr.substring(1);
                                    }
                                    queryString.append(URLEncoder.encode(objStr, encoding));
                                    queryString.append("&");
                                } catch (UnsupportedEncodingException ex) {
                                }
                            }
                        } else if (value.getClass().equals(Default.class)) {
                            // Skip defaults in queryString
                        } else {
                            try {
                                queryString.append(URLEncoder.encode(key, encoding));
                                queryString.append("=");
                                String objStr = value.toString();
                                // Special case to handle jsAction tag
                                if (objStr.startsWith(":") && objStr.length() > 1) {
                                    queryString.append(':');
                                    objStr = objStr.substring(1);
                                }
                                queryString.append(URLEncoder.encode(objStr, encoding));
                                queryString.append("&");
                            } catch (UnsupportedEncodingException ex) {
                            }
                        }
                    }
                }
                String qs = queryString.toString();
                if (qs.endsWith("&")) {
                    qs = qs.substring(0, qs.length() - 1);
                }
                ActionDefinition actionDefinition = new ActionDefinition();
                actionDefinition.url = qs.isEmpty() ? path : path + "?" + qs;
                actionDefinition.method = route.method == null || route.method.equals("*") ? "GET" : route.method.toUpperCase();
                actionDefinition.star = "*".equals(route.method);
                actionDefinition.action = action;
                actionDefinition.args = argsbackup;
                actionDefinition.host = host;
                if (Boolean.parseBoolean(Play.configuration.getProperty("application.forceSecureReverseRoutes", "false"))) {
                    actionDefinition.secure();
                }
                return actionDefinition;
            }
        }

        throw new NoRouteFoundException(action, args);
    }

    public static ActionDefinition reverse(Class<? extends PlayController> clazz, String methodName, Object... param)  {
        try {
            Map<String, Object> r = new HashMap<>();
            String action = clazz.getName()+"."+methodName;
            Method actionMethod = null;
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equalsIgnoreCase(methodName) && Modifier.isPublic(m.getModifiers())) {
                    actionMethod = m;
                    break;
                }
            }
            if (actionMethod != null && Modifier.isPublic(actionMethod.getModifiers()) && param != null) {
                Parameter[] names = actionMethod.getParameters();
                if (names.length < param.length) {
                    throw new NoRouteFoundException(action, null);
                }
                for (int i = 0; i < param.length; i++) {
                    r.put(names[i].getName(), param[i]);
                }
            }
            return reverse(action, r);
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    private static final Map<String, List<ActionRoute>> actionRoutesCache = new ConcurrentHashMap<>();

    private static List<ActionRoute> getActionRoutes(String action) {
        List<ActionRoute> matchingRoutes = actionRoutesCache.get(action);
        if (matchingRoutes == null) {
            matchingRoutes = findActionRoutes(action);
            actionRoutesCache.put(action, matchingRoutes);
        }
        return matchingRoutes;
    }

    private static List<ActionRoute> findActionRoutes(String action) {
        List<ActionRoute> matchingRoutes = new ArrayList<>(2);
        for (Router.Route route : routes) {
            if (route.actionPattern != null) {
                Matcher matcher = route.actionPattern.matcher(action);
                if (matcher.matches()) {
                    ActionRoute matchingRoute = new ActionRoute(route);
                    for (String group : route.actionArgs) {
                        String v = matcher.group(group);
                        if (v == null) {
                            continue;
                        }
                        matchingRoute.args.put(group, v.toLowerCase());
                    }
                    matchingRoutes.add(matchingRoute);
                }
            }
        }
        return matchingRoutes;
    }

    private static final class ActionRoute {
        private final Route route;
        private final Map<String, String> args = new HashMap<>(2);

        public ActionRoute(Route route){
            this.route = route;
        }
    }

    public static class ActionDefinition {

        /**
         * The domain/host name.
         */
        public String host;
        /**
         * The HTTP method, e.g. "GET".
         */
        public String method;
        /**
         * FIXME - what is this? does it include the domain?
         */
        public String url;
        /**
         * Whether the route contains an astericks *.
         */
        public boolean star;
        /**
         * FIXME - what is this? does it include the class and package?
         */
        public String action;
        /**
         * FIXME - are these the required args in the routing file, or the query string in a request?
         */
        public Map<String, Object> args;

        public ActionDefinition add(String key, Object value) {
            args.put(key, value);
            return reverse(action, args);
        }

        public ActionDefinition remove(String key) {
            args.remove(key);
            return reverse(action, args);
        }

        public ActionDefinition addRef(String fragment) {
            url += "#" + fragment;
            return this;
        }

        @Override
        public String toString() {
            return url;
        }

        public void absolute() {
            boolean isSecure = Http.Request.current() != null && Http.Request.current().secure;
            String base = getBaseUrl();
            String hostPart = host;
            String domain = Http.Request.current() == null ? "" : Http.Request.current().get().domain;
            int port = Http.Request.current() == null ? 80 : Http.Request.current().get().port;
            if (port != 80 && port != 443) {
                hostPart += ":" + port;
            }
            // ~
            if (!url.startsWith("http")) {
                if (StringUtils.isEmpty(host)) {
                    url = base + url;
                } else if (host.contains("{_}")) {
                    Matcher matcher = Pattern.compile("([-_a-z0-9A-Z]+([.][-_a-z0-9A-Z]+)?)$").matcher(domain);
                    if (matcher.find()) {
                        url = (isSecure ? "https://" : "http://") + hostPart.replace("{_}", matcher.group(1)) + url;
                    } else {
                        url = (isSecure ? "https://" : "http://") + hostPart + url;
                    }
                } else {
                    url = (isSecure ? "https://" : "http://") + hostPart + url;
                }
                if (method.equals("WS")) {
                    url = isSecure ? url.replaceFirst("https:", "wss:") : url.replaceFirst("http:", "ws:");
                }
            }
        }

        public ActionDefinition secure() {
            if (!url.contains("http://") && !url.contains("https://")) {
                absolute();
            }
            url = url.replace("http:", "https:");
            return this;
        }
    }

    public static class Route {

        /**
         * HTTP method, e.g. "GET".
         */
        public String method;
        public String path;
        public String action;
        Pattern actionPattern;
        final List<String> actionArgs = new ArrayList<>(3);
        String staticDir;
        boolean staticFile;
        Pattern pattern;
        Pattern hostPattern;
        final List<Arg> args = new ArrayList<>(3);
        final Map<String, String> staticArgs = new HashMap<>(3);
        final List<String> formats = new ArrayList<>(1);
        String host;
        Arg hostArg = null;
        public int routesFileLine;
        public String routesFile;
        static final Pattern customRegexPattern = Pattern.compile("\\{([a-zA-Z_][a-zA-Z_0-9]*)\\}");
        static final Pattern argsPattern = Pattern.compile("\\{<([^>]+)>([a-zA-Z_0-9]+)\\}");
        static final Pattern paramPattern = Pattern.compile("([a-zA-Z_0-9]+):'(.*)'");

        public void compute() {
            this.host = "";
            this.hostPattern = Pattern.compile(".*");
            if (action.startsWith("staticDir:") || action.startsWith("staticFile:")) {
                // Is there is a host argument, append it.
                if (!path.startsWith("/")) {
                    String p = this.path;
                    this.path = p.substring(p.indexOf("/"));
                    this.host = p.substring(0, p.indexOf("/"));
                    if (this.host.contains("{")) {
                        Logger.warn("Static route cannot have a dynamic host name");
                        return;
                    }
                    this.hostPattern = Pattern.compile(host.replaceAll("\\.", "\\\\."));
                }
                if (!method.equalsIgnoreCase("*") && !method.equalsIgnoreCase("GET")) {
                    Logger.warn("Static route only support GET method");
                    return;
                }
            }
            // staticDir
            if (action.startsWith("staticDir:")) {
                if (!this.path.endsWith("/")) {
                    Logger.warn("The path for a staticDir route must end with / (%s)", this);
                    this.path += "/";
                }
                this.pattern = Pattern.compile("^" + path + "(?<resource>.*)$");
                this.staticDir = action.substring("staticDir:".length());
            } else if (action.startsWith("staticFile:")) {
                this.pattern = Pattern.compile("^" + path + "$");
                this.staticFile = true;
                this.staticDir = action.substring("staticFile:".length());
            } else {
                // URL pattern
                // Is there is a host argument, append it.
                if (!path.startsWith("/")) {
                    String p = this.path;
                    this.path = p.substring(p.indexOf("/"));
                    this.host = p.substring(0, p.indexOf("/"));
                    String pattern = host.replaceAll("\\.", "\\\\.").replaceAll("\\{.*\\}", "(.*)");

                    if (Logger.isTraceEnabled()) {
                        Logger.trace("pattern [" + pattern + "]");
                        Logger.trace("host [" + host + "]");
                    }

                    Matcher m = Pattern.compile(pattern).matcher(host);
                    this.hostPattern = Pattern.compile(pattern);

                    if (m.matches()) {
                        if (this.host.contains("{")) {
                            String name = m.group(1).replace("{", "").replace("}", "");
                            if (!name.equals("_")) {
                                hostArg = new Arg(name, Pattern.compile(".*"));
                                if (Logger.isTraceEnabled()) {
                                    Logger.trace("hostArg name [" + name + "]");
                                }
                                hostArg.defaultValue = host;
                                if (Logger.isTraceEnabled()) {
                                    Logger.trace("adding hostArg [" + hostArg + "]");
                                }

                                args.add(hostArg);
                            }
                        }
                    }

                }
                String patternString = path;
                patternString = customRegexPattern.matcher(patternString).replaceAll("\\{<[^/]+>$1\\}");
                Matcher matcher = argsPattern.matcher(patternString);
                while (matcher.find()) {
                    Arg arg = new Arg(matcher.group(2), Pattern.compile(matcher.group(1)));
                    args.add(arg);
                }
                patternString = argsPattern.matcher(patternString).replaceAll("(?<$2>$1)");
                this.pattern = Pattern.compile(patternString);
                // Action pattern
                patternString = action;
                patternString = patternString.replace(".", "[.]");
                for (Arg arg : args) {
                    if (patternString.contains("{" + arg.name + "}")) {
//                        patternString = patternString.replace("{" + arg.name + "}",
//                                "({" + arg.name + "}" + arg.constraint.toString() + ")");
                        patternString = patternString.replace("{" + arg.name + "}",
                                "(?<" + arg.name + ">" + arg.constraint + ")");
                        actionArgs.add(arg.name);
                    }
                }
                actionPattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
            }
        }

        public void addParams(String params) {
            if (params == null || params.isEmpty()) {
                return;
            }
            params = params.substring(1, params.length() - 1);
            for (String param : params.split(",")) {
                Matcher matcher = paramPattern.matcher(param.trim());
                if (matcher.matches()) {
                    staticArgs.put(matcher.group(1), matcher.group(2));
                } else {
                    Logger.warn("Ignoring %s (static params must be specified as key:'value',...)", params);
                }
            }
        }

        // TODO: Add args names
        public void addFormat(String params) {
            if (params == null || params.isEmpty()) {
                return;
            }
            params = params.trim();
            formats.addAll(Arrays.asList(params.split(",")));
        }

        private boolean contains(String accept) {
            boolean contains = (accept == null);
            if (accept != null) {
                if (this.formats.isEmpty()) {
                    return true;
                }
                for (String format : this.formats) {
                    contains = format.startsWith(accept);
                    if (contains) {
                        break;
                    }
                }
            }
            return contains;
        }

        public Map<String, String> matches(String method, String path) {
            return matches(method, path, null, null);
        }

        public Map<String, String> matches(String method, String path, String accept) {
            return matches(method, path, accept, null);
        }

        /**
         * Check if the parts of a HTTP request equal this Route.
         *
         * @param method
         *            GET/POST/etc.
         * @param path
         *            Part after domain and before query-string. Starts with a "/".
         * @param accept
         *            Format, e.g. html.
         * @param domain
         *            The domain (host without port).
         * @return ???
         */
        public Map<String, String> matches(String method, String path, String accept, String domain) {
            // Normalize
            if (path.equals(Play.ctxPath)) {
                path = path + "/";
            }
            // If method is HEAD and we have a GET
            if (method == null || this.method.equals("*") || method.equalsIgnoreCase(this.method)
                    || (method.equalsIgnoreCase("head") && ("get").equalsIgnoreCase(this.method))) {

                Matcher matcher = pattern.matcher(path);

                boolean hostMatches = (domain == null);
                if (domain != null) {

                    Matcher hostMatcher = hostPattern.matcher(domain);
                    hostMatches = hostMatcher.matches();
                }
                // Extract the host variable
                if (matcher.matches() && contains(accept) && hostMatches) {
                    // 404
                    if (action.equals("404")) {
                        throw new NotFound(method, path);
                    }
                    // Static dir
                    if (staticDir != null) {
                        String resource = null;
                        if (!staticFile) {
                            resource = matcher.group("resource");
                        }
                        try {
                            String root = new File(staticDir).getCanonicalPath();
                            String urlDecodedResource = Utils.urlDecodePath(resource);
                            String childResourceName = staticDir + (staticFile ? "" : "/" + urlDecodedResource);
                            String child = new File(childResourceName).getCanonicalPath();
                            if (child.startsWith(root)) {
                                throw new RenderStatic(childResourceName);
                            }
                        } catch (IOException e) {
                        }
                        throw new NotFound(resource);
                    } else {
                        Map<String, String> localArgs = new HashMap<>();
                        for (Arg arg : args) {
                            // FIXME: Careful with the arguments that are not
                            // matching as they are part of the hostname
                            // Defaultvalue indicates it is a one of these urls.
                            // This is a trick and should be changed.
                            if (arg.defaultValue == null) {
                                localArgs.put(arg.name, Utils.urlDecodePath(matcher.group(arg.name)));
                            }
                        }
                        if (hostArg != null && domain != null) {
                            // Parse the hostname and get only the part we are
                            // interested in
                            String routeValue = hostArg.defaultValue.replaceAll("\\{.*}", "");
                            domain = domain.replace(routeValue, "");
                            localArgs.put(hostArg.name, domain);
                        }
                        localArgs.putAll(staticArgs);
                        return localArgs;
                    }
                }
            }
            return null;
        }

        static class Arg {
            final String name;
            final Pattern constraint;
            String defaultValue;

            public Arg(String name, Pattern constraint){
                this.name = name;
                this.constraint = constraint;
            }
        }

        @Override
        public String toString() {
            return method + " " + path + " -> " + action;
        }
    }
}