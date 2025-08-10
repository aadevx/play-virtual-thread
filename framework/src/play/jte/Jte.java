package play.jte;

import gg.jte.html.escape.Escape;
import gg.jte.output.StringOutput;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import play.Play;
import play.cache.Cache;
import play.data.validation.Error;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.PlayController;
import play.mvc.Router;
import play.mvc.Scope;
import play.templates.JavaExtensions;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * support JTE template
 * ref : https://jte.gg/
 * @author arief ardiyansah
 */
public class Jte {

    public static Scope.Session session() {
        return Scope.Session.current();
    }

    public static String session(String key) {
        Scope.Session session = Scope.Session.current();
        if(session == null)
            return null;
        return session.get(key);
    }

    public static Scope.RenderArgs renderArgs(){
        return Scope.RenderArgs.current();
    }

    public static String renderArgs(String key){
        Scope.RenderArgs renderArgs = renderArgs();
        if(renderArgs == null)
            return null;
        Object o = renderArgs.get(key);
        return o != null ? o.toString():null;
    }

    public static <T> T renderArgs(String key, Class<T> clazz){
        Scope.RenderArgs renderArgs = renderArgs();
        if(renderArgs == null)
            return null;
        T o = renderArgs.get(key, clazz);
        return o;
    }

    public static Http.Request request() {
        return Http.Request.current();
    }


    public static Scope.Flash flash() {
        return Scope.Flash.current();
    }

    public static String flash(String key) {
        Scope.Flash flash = Scope.Flash.current();
        if(flash == null)
            return null;
        return flash.get(key);
    }

    public static Scope.Params params() {
        return Scope.Params.current();
    }

    public static String param(String key) {
        Scope.Params params = Scope.Params.current();
        if(params == null)
            return null;
        return params.get(key);
    }

    public static List<Error> errors() {
        return Validation.errors();
    }

    public static String error(String key) {
        return error(key, null);
    }

    public static String error(String key, String field) {
        Error error = Validation.error(key);
        if (error != null) {
            if (field == null) {
                return error.message();
            } else {
                return error.message(field);
            }
        }
        return null;
    }

    public static String url(String path) {
        if(StringUtils.isEmpty(path))
            return null;
        else if(!path.startsWith("/"))
            path = "/" +path;
//        return Router.reverse(Play.getVirtualFile(path));
        return Play.ctxPath+path;
    }

    public static String action(String action) {
        if(StringUtils.isEmpty(action))
            return null;
        return Router.reverse(action).url;
    }

    public static String action(Class<? extends PlayController> clazz, String methodName)  {
        return action(clazz, methodName, new Object[]{});
    }

    public static String action(Class<? extends PlayController> clazz, String methodName, Object... param)  {
        return Router.reverse(clazz, methodName, param).url;
    }

    public static String action(String action, Map<String, Object> args) {
        if(StringUtils.isEmpty(action))
            return null;
        return Router.reverse(action, args).url;
    }

    public static String msg(String key, Object... args) {
        return Messages.get(key, args);
    }

    public static Object cache(String key) {
        return Cache.get(key);
    }

    public static String jsAction(String action) {
        return jsAction(action, false, false, null);
    }

    public static String jsAction(Class<? extends PlayController> clazz, String methodName, Object... param) {
        return jsAction(action(clazz, methodName, param), false, false, null);
    }

    public static String jsAction(String action, boolean min, boolean encodeURI, String customScript) {
        var html = new StringBuilder();
        String minimize = "";
        if (!min) {
            minimize = "\n";
        }
        html.append("function(options) {").append(minimize);
        html.append("var pattern = '").append(action.replace("&amp;", "&")).append("';").append(minimize);
        html.append("for(key in options) {").append(minimize);
        html.append("var val = options[key];").append(minimize);
        // Encode URI script
        if (encodeURI) {
            html.append("val = encodeURIComponent(val.replace('&amp;', '&'));").append(minimize);
        }
        // Custom script
        if (!StringUtils.isEmpty(customScript)) {
            html.append("val = ").append(customScript).append(minimize);
        }
        html.append("pattern = pattern.replace(':' + encodeURIComponent(key), ( (val===undefined || val===null)?'': val));").append(minimize);
        html.append("}").append(minimize);
        html.append("return pattern;").append(minimize);
        html.append("}").append(minimize);
        return html.toString();
    }

    public static String jsRoute(String action) {
        var result = new StringBuilder("{");
        if(StringUtils.isEmpty(action)) {
            result.append("url: function() { return '").append(action.replace( "&amp;", "&")).append("'; },");
        } else {
            result.append("url: function(args) { var pattern = '").append(action.replace( "&amp;", "&")).append("'; for (var key in args) { pattern = pattern.replace(':'+key, args[key] || ''); } return pattern; },");
        }
        result.append("method: '").append(action).append("'");
        result.append("}");
        return result.toString();
    }

    public static String jsRoute(Class<? extends PlayController> clazz, String methodName, Object... param) {
        return jsRoute(action(clazz, methodName, param));
    }

    public static String authenticityToken() {
        return "<input type=\"hidden\" name=\"authenticityToken\" value=\"" + Scope.Session.current().getAuthenticityToken() + "\"/>";
    }

    public static boolean isValid(String field) {
        return !Validation.hasError(field);
    }

    public static boolean isInvalid(String field) {
        return Validation.hasError(field);
    }

    public static String tag404(Exception exception) {
        StringBuilder content = new StringBuilder();
        content.append("<style type=\"text/css\">");
        content.append("html, body, pre {font-family: Monaco, 'Lucida Console';background: #ECECEC;}h1 {margin: 0;background: #AD632A;padding: 20px 45px;color: #fff;text-shadow: 1px 1px 1px rgba(0,0,0,.3);border-bottom: 1px solid #9F5805;font-size: 28px;}p#detail {margin: 0;padding: 15px 45px;background: #F6A960;border-top: 4px solid #D29052;color: #733512;text-shadow: 1px 1px 1px rgba(255,255,255,.3);font-size: 14px;border-bottom: 1px solid #BA7F5B;}h2 {margin: 0;padding: 5px 45px;font-size: 12px;background: #333;color: #fff;text-shadow: 1px 1px 1px rgba(0,0,0,.3);border-top: 4px solid #2a2a2a;}pre {margin: 0;border-bottom: 1px solid #DDD;text-shadow: 1px 1px 1px rgba(255,255,255,.5);position: relative;font-size: 12px;overflow: hidden;}pre span.line {text-align: right;display: inline-block;padding: 5px 5px;width: 30px;background: #D6D6D6;color: #8B8B8B;text-shadow: 1px 1px 1px rgba(255,255,255,.5);font-weight: bold;}pre span.route {padding: 5px 5px;position: absolute;right: 0;left: 40px;}pre span.route span.verb {display: inline-block;width: 5%;min-width: 50px;overflow: hidden;margin-right: 10px;}pre span.route span.path {display: inline-block;width: 30%;min-width: 200px;overflow: hidden;margin-right: 10px;}pre span.route span.call {display: inline-block;width: 50%;overflow: hidden;margin-right: 10px;}pre:first-child span.route {border-top: 4px solid #CDCDCD;}pre:first-child span.line {border-top: 4px solid #B6B6B6;}pre.error span.line {background: #A31012;color: #fff;text-shadow: 1px 1px 1px rgba(0,0,0,.3);}");
        content.append("</style>");
        content.append("<h1>Action not found</h1>");
        content.append("<p id=\"detail\">").append(exception.getMessage()).append("</p>");
        content.append("<h2>These routes have been tried, in this order :</h2>");
        int i=0;
        for(Router.Route route : Router.routes) {
            content.append("<pre><span class=\"line\">").append(i).append("</span><span class=\"route\"><span class=\"verb\">")
                    .append(JavaExtensions.pad(route.method, 10)).append("</span><span class=\"path\">")
                    .append(JavaExtensions.pad(route.path, 50)).append("</span><span class=\"call\">")
                    .append(route.action).append("</span></span></pre>");
            i++;
        }
        return content.toString();
    }

    public static String tag500(Exception exception) {
        StringBuilder content = new StringBuilder();
        content.append("<style type=\"text/css\">");
        content.append(" html, body, pre {;font-family: Monaco, 'Lucida Console';background: #ECECEC;}h1 {margin: 0;background: #A31012;padding: 20px 45px;color: #fff;text-shadow: 1px 1px 1px rgba(0,0,0,.3);border-bottom: 1px solid #690000;font-size: 28px;}p#detail {margin: 0;padding: 15px 45px;background: #F5A0A0;border-top: 4px solid #D36D6D;color: #730000;text-shadow: 1px 1px 1px rgba(255,255,255,.3);font-size: 14px;border-bottom: 1px solid #BA7A7A;}p#detail input {background: -webkit-gradient(linear, 0% 0%, 0% 100%, from(#AE1113), to(#A31012));border: 1px solid #790000;padding: 3px 10px;text-shadow: 1px 1px 0 rgba(0, 0, 0, .5);color: white;border-radius: 3px;cursor: pointer;font-family: Monaco, 'Lucida Console';font-size: 12px;margin: 0 10px;display: inline-block;position: relative;top: -1px;}h2 {margin: 0;padding: 5px 45px;font-size: 12px;background: #333;color: #fff;text-shadow: 1px 1px 1px rgba(0,0,0,.3);border-top: 4px solid #2a2a2a;}pre {margin: 0;border-bottom: 1px solid #DDD;text-shadow: 1px 1px 1px rgba(255,255,255,.5);position: relative;font-size: 12px;overflow: hidden;}pre span.line {text-align: right;display: inline-block;padding: 5px 5px;width: 30px;background: #D6D6D6;color: #8B8B8B;text-shadow: 1px 1px 1px rgba(255,255,255,.5);font-weight: bold;}pre span.code {padding: 5px 5px;position: absolute;right: 0;left: 40px;}pre:first-child span.code {border-top: 4px solid #CDCDCD;}pre:first-child span.line {border-top: 4px solid #B6B6B6;}pre.error span.line {background: #A31012;color: #fff;text-shadow: 1px 1px 1px rgba(0,0,0,.3);}pre.error span.code {font-weight: bold;}pre.error {color: #A31012;}pre.error span.marker {background: #A31012;color: #fff;text-shadow: 1px 1px 1px rgba(0,0,0,.3);}#more {padding: 8px;font-size: 12px;}pre.error:hover {cursor: pointer;}pre.error:hover span.code {background-color: #D38989;}");
        content.append("</style>");
        content.append("<div id=\"header\" class=\"block\">\n");
        content.append(" <h1>").append(exception.getMessage()).append("</h1>\n");
        content.append("</div>");
        return content.toString();
    }

    public static boolean isEmpty(Collection<?> coll) {
        return coll == null || coll.isEmpty();
    }

    public static boolean isEmpty(Map map) {
        return map == null || map.isEmpty();
    }

    public static boolean isEmpty(String obj){
        return obj == null || obj.isEmpty() || obj.isBlank();
    }

    public static boolean isEmpty(Object[] obj){
        return obj == null || obj.length == 0;
    }

    public static String json(Object obj) {
        return Json.toJson(obj);
    }

    public static boolean isJson(String content) {
        return Json.isJson(content);
    }

    public static String actionUrl(String action) {
        if(StringUtils.isEmpty(action))
            return null;
        return Router.getFullUrl(action);
    }

    public static String actionUrl(Class<? extends PlayController> clazz, String methodName)  {
        return actionUrl(clazz, methodName, new Object[]{});
    }

    public static String actionUrl(Class<? extends PlayController> clazz, String methodName, Object... param)  {
        Router.ActionDefinition actionDefinition = Router.reverse(clazz, methodName, param);
        String base = Router.getBaseUrl();
        if (actionDefinition.method.equals("WS")) {
            return base.replaceFirst("http:", "ws:").replaceFirst("https:", "wss:") + actionDefinition;
        }
        return base + actionDefinition;
    }

    public static String actionUrl(String action, Map<String, Object> args) {
        if(StringUtils.isEmpty(action))
            return null;
        return Router.getFullUrl(action, args);
    }

    public static String escapeHtml(String value) {
        StringOutput output = new StringOutput();
        Escape.htmlContent(value, output);
        return output.toString();
    }

    public static String escapeJS(String value) {
        StringOutput output = new StringOutput();
        Escape.javaScriptBlock(value, output);
        return output.toString();
    }


    public static <T> List<T> emptyListIfNull(final List<T> list) {
        return list == null ? Collections.<T>emptyList() : list;
    }


    public static Object[] emptyArrayIfNull(Object[] array) {
        return ArrayUtils.nullToEmpty(array);
    }
}
