package play.modules.routing;

import play.Play;
import play.mvc.PlayController;
import play.mvc.Router;
import play.utils.Java;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class RouterAnnotation {

    final List<String> routelist = new ArrayList<>();

    private boolean routeExist(String methodName, String action, String path) {
        boolean exists = false;
        for (Router.Route route : Router.routes) {
            boolean match = route.method.equals(methodName) && route.path.equals(path) && route.action.equals(action);
            if (match) {
                exists = true;
                break;
            }
        }
        return !exists;
    }

    void append(String route) {
        if(!routelist.contains(route))
            routelist.add(route);
    }

    public void computeRoutes() {
        List<Class> controllers = getControllerClasses();
        List<Method> gets = Java.findAllAnnotatedMethods(controllers, Get.class);
        for (Method get : gets) {
            Get annotation = get.getAnnotation(Get.class);
            if (annotation != null)
                append("GET " + annotation.value() + " " + getControllerName(get) + "." + get.getName());
        }
        List<Method> posts = Java.findAllAnnotatedMethods(controllers, Post.class);
        for (Method post : posts) {
            Post annotation = post.getAnnotation(Post.class);
            if (annotation != null)
                append("POST " + annotation.value() + " " + getControllerName(post) + "." + post.getName());
        }
        List<Method> puts = Java.findAllAnnotatedMethods(controllers, Put.class);
        for (Method put : puts) {
            Put annotation = put.getAnnotation(Put.class);
            if (annotation != null)
                append("PUT " + annotation.value() + " " + getControllerName(put) + "." + put.getName());
        }
        List<Method> options = Java.findAllAnnotatedMethods(controllers, Options.class);
        for (Method option : options) {
            Options annotation = option.getAnnotation(Options.class);
            if (annotation != null)
                append("OPTIONS " + annotation.value() + " " + getControllerName(option) + "." + option.getName());

        }
        List<Method> deletes = Java.findAllAnnotatedMethods(controllers, Delete.class);
        for (Method delete : deletes) {
            Delete annotation = delete.getAnnotation(Delete.class);
            if (annotation != null)
                append("DELETE " + annotation.value() + " " + getControllerName(delete) + "." + delete.getName());
        }
        List<Method> heads = Java.findAllAnnotatedMethods(controllers, Head.class);
        for (Method head : heads) {
            Head annotation = head.getAnnotation(Head.class);
            if (annotation != null)
                append("HEAD " + annotation.value() + " " + getControllerName(head) + "." + head.getName());
        }
        List<Method> webSockets = Java.findAllAnnotatedMethods(controllers, WS.class);
        for (Method ws : webSockets) {
            WS annotation = ws.getAnnotation(WS.class);
            if (annotation != null)
                append("WS " + annotation.value() + " " + getControllerName(ws) + "." + ws.getName());
        }
        List<Method> list = Java.findAllAnnotatedMethods(controllers, Any.class);
        for (Method any : list) {
            Any annotation = any.getAnnotation(Any.class);
            if (annotation != null)
                append("* " + annotation.value() + " " + getControllerName(any) + "." + any.getName());
        }
        for (Class clazz : controllers) {
            StaticRoutes annotation = (StaticRoutes) clazz.getAnnotation(StaticRoutes.class);
            if (annotation != null) {
                ServeStatic[] serveStatics = annotation.value();
                if (serveStatics != null) {
                    for (ServeStatic serveStatic : serveStatics) {
                        append("GET " + serveStatic.value() + " staticDir:" + serveStatic.directory());
                    }
                }
            }
        }
        for (Class clazz : controllers) {
            ServeStatic annotation = (ServeStatic) clazz.getAnnotation(ServeStatic.class);
            if (annotation != null) {
                append("GET " + annotation.value() + " staticDir:" + annotation.directory());
            }
        }
        int line = 0;
        for(String route : routelist){
            String[] routesSplit = route.split("\\s+");
            if(routesSplit != null && routesSplit.length == 3){
                Router.appendRoute(routesSplit[0].trim(), Play.ctxPath+routesSplit[1].trim(), routesSplit[2].trim(), null, null, null, line);
                line++;
            }
        }
        Router.reload = false;
    }

    public List<Class> getControllerClasses() {
        List<Class> returnValues = new ArrayList<>();
        for (Class clazz : Play.classes.getAssignableClasses(PlayController.class)) {
            if (clazz != null && !clazz.isInterface() && !clazz.isAnnotation()) {
                returnValues.add(clazz);
            }
        }
        return returnValues;
    }

    private String getControllerName(Method method) {
        return method.getDeclaringClass().getName().substring(12, method.getDeclaringClass().getName().length());
    }

    public void defaultRouteController() {
        if(routeExist("GET", "staticDir:public", Play.ctxPath + "/public/"))
            Router.addRoute( "GET", Play.ctxPath + "/public/", "staticDir:public", null);
        if(routeExist("*", "{controller}.{action}", Play.ctxPath + "/{controller}/{action}"))
            Router.addRoute( "*", Play.ctxPath + "/{controller}/{action}", "{controller}.{action}", null, null);
    }
}
