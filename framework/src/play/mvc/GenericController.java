package play.mvc;

import com.google.gson.Gson;
import com.google.gson.JsonSerializer;
import org.w3c.dom.Document;
import play.Logger;
import play.Play;
import play.classloading.ApplicationClasses;
import play.classloading.enhancers.LocalvariablesNamesEnhancer.LocalVariablesNamesTracer;
import play.classloading.enhancers.LocalvariablesNamesEnhancer.LocalVariablesSupport;
import play.data.validation.Validation;
import play.exceptions.NoRouteFoundException;
import play.exceptions.PlayException;
import play.exceptions.TemplateNotFoundException;
import play.exceptions.UnexpectedException;
import play.libs.Time;
import play.mvc.results.Error;
import play.mvc.results.*;
import play.templates.Template;
import play.templates.TemplateLoader;

import java.io.File;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * controller non static action
 */
public class GenericController implements PlayController, LocalVariablesSupport {

    protected Http.Request request() {
        return Http.Request.current();
    }

    protected Http.Response response() {
        return Http.Response.current();
    }

    protected Scope.Session session() {
        return Scope.Session.current();
    }

    protected String session(String name) {
        return Scope.Session.current().get(name);
    }

    protected void session(String name, String value) {
        Scope.Session.current().put(name, value);
    }

    protected Scope.Flash flash() {
        return Scope.Flash.current();
    }

    protected String flash(String name) {
        return Scope.Flash.current().get(name);
    }

    protected void flash(String name, String value) {
        Scope.Flash.current().put(name, value);
    }

    protected Scope.Params params(){
        return Scope.Params.current();
    }

    protected String params(String name){
        return Scope.Params.current().get(name);
    }

    protected <T> T params(String name, Class<T> clazz){
        return Scope.Params.current().get(name, clazz);
    }

    protected void params(String name, String value){
        Scope.Params.current().put(name, value);
    }

    protected Scope.RenderArgs renderArgs(){
        return Scope.RenderArgs.current();
    }

    protected Object renderArgs(String name) {
        return Scope.RenderArgs.current().get(name);
    }

    protected <T> T renderArgs(String name, Class<T> clazz) {
        return Scope.RenderArgs.current().get(name, clazz);
    }

    protected void renderArgs(String name, Object value) {
        Scope.RenderArgs.current().put(name, value);
    }

    protected Validation validation(){
        return Validation.current();
    }

    protected Result renderText(Object text) {
        return new RenderText(text == null ? "" : text.toString());
    }

    /**
     * Return a 200 OK text/html response
     *
     * @param html
     *            The response content
     */
    protected Result renderHtml(Object html) {
        return new RenderHtml(html == null ? "" : html.toString());
    }

    /**
     * Return a 200 OK text/plain response
     *
     * @param pattern
     *            The response content to be formatted (with String.format)
     * @param args
     *            Args for String.format
     */
    protected Result renderText(CharSequence pattern, Object... args) {
        return new RenderText(pattern == null ? "" : String.format(pattern.toString(), args));
    }

    /**
     * Return a 200 OK text/xml response
     *
     * @param xml
     *            The XML string
     */
    protected Result renderXml(String xml) {
        return new RenderXml(xml);
    }

    /**
     * Return a 200 OK text/xml response
     *
     * @param xml
     *            The DOM document object
     */
    protected Result renderXml(Document xml) {
        return new RenderXml(xml);
    }

    /**
     * Return a 200 OK application/binary response. Content is fully loaded in
     * memory, so it should not be used with large data.
     *
     * @param is
     *            The stream to copy
     */
    protected Result renderBinary(InputStream is) {
        return new RenderBinary(is, null, true);
    }

    /**
     * Return a 200 OK application/binary response. Content is streamed.
     *
     * @param is
     *            The stream to copy
     * @param length
     *            Stream's size in bytes.
     */
    protected Result renderBinary(InputStream is, long length) {
        return new RenderBinary(is, null, length, true);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition
     * attachment. Content is fully loaded in memory, so it should not be used
     * with large data.
     *
     * @param is
     *            The stream to copy
     * @param name
     *            Name of file user is downloading.
     */
    protected Result renderBinary(InputStream is, String name) {
        return new RenderBinary(is, name, false);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition
     * attachment.
     *
     * @param is
     *            The stream to copy. Content is streamed.
     * @param name
     *            Name of file user is downloading.
     * @param length
     *            Stream's size in bytes.
     */
    protected Result renderBinary(InputStream is, String name, long length) {
        return new RenderBinary(is, name, length, false);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition
     * attachment. Content is fully loaded in memory, so it should not be used
     * with large data.
     *
     * @param is
     *            The stream to copy
     * @param name
     *            Name of file user is downloading.
     * @param inline
     *            true to set the response Content-Disposition to inline
     */
    protected Result renderBinary(InputStream is, String name, boolean inline) {
        return new RenderBinary(is, name, inline);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition
     * attachment.
     *
     * @param is
     *            The stream to copy
     * @param name
     *            The attachment name
     * @param length
     *            Stream's size in bytes.
     * @param inline
     *            true to set the response Content-Disposition to inline
     */
    protected Result renderBinary(InputStream is, String name, long length, boolean inline) {
        return new RenderBinary(is, name, length, inline);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition
     * attachment. Content is fully loaded in memory, so it should not be used
     * with large data.
     *
     * @param is
     *            The stream to copy
     * @param name
     *            The attachment name
     * @param contentType
     *            The content type of the attachment
     * @param inline
     *            true to set the response Content-Disposition to inline
     */
    protected Result renderBinary(InputStream is, String name, String contentType, boolean inline) {
        return new RenderBinary(is, name, contentType, inline);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition
     * attachment.
     *
     * @param is
     *            The stream to copy
     * @param name
     *            The attachment name
     * @param length
     *            Content's byte size.
     * @param contentType
     *            The content type of the attachment
     * @param inline
     *            true to set the response Content-Disposition to inline
     */
    protected Result renderBinary(InputStream is, String name, long length, String contentType, boolean inline) {
        return new RenderBinary(is, name, length, contentType, inline);
    }

    /**
     * Return a 200 OK application/binary response
     *
     * @param file
     *            The file to copy
     */
    protected Result renderBinary(File file) {
        return new RenderBinary(file);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition
     * attachment
     *
     * @param file
     *            The file to copy
     * @param name
     *            The attachment name
     */
    protected Result renderBinary(File file, String name) {
        return new RenderBinary(file, name);
    }

    /**
     * Render a 200 OK application/json response
     *
     * @param jsonString
     *            The JSON string
     */
    protected Result renderJSON(String jsonString) {
        return new RenderJson(jsonString);
    }

    /**
     * Render a 200 OK application/json response
     *
     * @param o
     *            The Java object to serialize
     */
    protected Result renderJSON(Object o) {
        return new RenderJson(o);
    }

    /**
     * Render a 200 OK application/json response
     *
     * @param o
     *            The Java object to serialize
     * @param type
     *            The Type information for complex generic types
     */
    protected Result renderJSON(Object o, Type type) {
        return new RenderJson(o, type);
    }


    /**
     * Render a 200 OK application/json response.
     *
     * @param o
     *            The Java object to serialize
     * @param gson
     *            The GSON serializer object use
     */
    protected Result renderJSON(Object o, Gson gson) {
        return new RenderJson(o, gson);
    }

    /**
     * Render a 200 OK application/json response.
     *
     * @param o
     *            The Java object to serialize
     * @param adapters
     *            A set of GSON serializers/deserializers/instance creator to
     *            use
     */
    protected Result renderJSON(Object o, JsonSerializer<?>... adapters) {
        return new RenderJson(o, adapters);
    }

    /**
     * Send a 304 Not Modified response
     */
    protected void notModified() {
        throw new NotModified();
    }

    /**
     * Send a 400 Bad request
     */
    protected void badRequest(String msg) {
        throw new BadRequest(msg);
    }

    /**
     * Send a 400 Bad request
     */
    protected void badrequest() {
        throw new BadRequest("Bad request");
    }

    /**
     * Send a 401 Unauthorized response
     *
     * @param realm
     *            The realm name
     */
    protected void unauthorized(String realm) {
        throw new Unauthorized(realm);
    }

    /**
     * Send a 401 Unauthorized response
     */
    protected void unauthorized() {
        throw new Unauthorized("Unauthorized");
    }

    /**
     * Send a 404 Not Found response
     *
     * @param what
     *            The Not Found resource name
     */
    protected void notFound(String what) {
        throw new NotFound(what);
    }

    /**
     * Send a 200 OK response
     */
    protected Result ok() {
        return new Ok();
    }

    /**
     * Send a 404 Not Found response if object is null
     *
     * @param o
     *            The object to check
     */
    protected void notFoundIfNull(Object o) {
        if (o == null) {
            throw new NotFound("");
        }
    }

    /**
     * Send a 404 Not Found response if object is null
     *
     * @param o
     *            The object to check
     * @param what
     *            The Not Found resource name
     */
    protected void notFoundIfNull(Object o, String what) {
        if (o == null) {
            throw new NotFound(what);
        }
    }

    /**
     * Send a 404 Not Found response
     */
    protected void notFound() {
        throw new NotFound("");
    }

    /**
     * Check that the token submitted from a form is valid.
     */
    protected void checkAuthenticity() {
        if (params().get("authenticityToken") == null || !params().get("authenticityToken").equals(session().getAuthenticityToken())) {
            throw new Forbidden("Bad authenticity token");
        }
    }

    /**
     * Send a 403 Forbidden response
     *
     * @param reason
     *            The reason
     */
    protected void forbidden(String reason) {
        throw new Forbidden(reason);
    }

    /**
     * Send a 403 Forbidden response
     */
    protected void forbidden() {
        throw new Forbidden("Access denied");
    }

    /**
     * Send a 5xx Error response
     *
     * @param status
     *            The exact status code
     * @param reason
     *            The reason
     */
    protected Result error(int status, String reason) {
        return new Error(status, reason);
    }

    /**
     * Send a 500 Error response
     *
     * @param reason
     *            The reason
     */
    protected Result error(String reason) {
        return new Error(reason);
    }

    /**
     * Send a 500 Error response
     *
     * @param reason
     *            The reason
     */
    protected Result error(Exception reason) {
        Logger.error(reason, "error()");
        return new Error(reason.toString());
    }

    /**
     * Send a 500 Error response
     */
    protected Result error() {
        return new Error("Internal Error");
    }

    /**
     * Add a value to the flash scope
     *
     * @param key
     *            The key
     * @param value
     *            The value
     */
    protected void flash(String key, Object value) {
        flash().put(key, value);
    }


    /**
     * Send a 302 redirect response.
     *
     * @param file
     *            The Location to redirect
     */
    protected void redirectToStatic(String file) {
        try {
            File vf = Play.getVirtualFile(file);
            if (vf == null || !vf.exists()) {
                throw new NoRouteFoundException(file);
            }
            throw  new RedirectToStatic(Router.reverse(Play.getVirtualFile(file)));
        } catch (NoRouteFoundException e) {
            StackTraceElement element = PlayException.getInterestingStackTraceElement(e);
            if (element != null) {
                throw new NoRouteFoundException(file, Play.classes.getApplicationClass(element.getClassName()), element.getLineNumber());
            } else {
                throw e;
            }
        }
    }

    protected void redirect(String url) {
        redirect(url, false);
    }

    protected void redirect(String url, boolean permanent) {
        if (url.indexOf("/") == -1) { // fix Java !
            Router.ActionDefinition actionDefinition = Router.reverse(url);
            throw new Redirect(actionDefinition.toString(), false);
        }
        throw new Redirect(url, permanent);
    }

    protected boolean templateExists(String templateName) {
        try {
            TemplateLoader.load(template(templateName));
            return true;
        } catch (TemplateNotFoundException ex) {
            return false;
        }
    }

    /**
     * Render the template corresponding to the action's package-class-method
     * name (@see <code>template()</code>).
     */
    protected Result renderTemplate() {
        return renderTemplate(template(), Collections.emptyMap());
    }

    /**
     * Render a specific template
     *
     * @param templateName
     *            The template name
     * @param args
     *            The template data
     */
    protected Result renderTemplate(String templateName, Object... args) {
        // Template datas
        Map<String, Object> templateBinding = new HashMap<>(16);
        for (Object o : args) {
            List<String> names = LocalVariablesNamesTracer.getAllLocalVariableNames(o);
            for (String name : names) {
                templateBinding.put(name, o);
            }
        }
        return renderTemplate(templateName, templateBinding);
    }

    /**
     * Render a specific template.
     *
     * @param templateName
     *            The template name.
     * @param args
     *            The template data.
     */
    protected Result renderTemplate(String templateName, Map<String, Object> args) {
        // Template datas
        Scope.RenderArgs templateBinding = renderArgs();
        templateName = template(templateName);
        templateBinding.data.putAll(args);
        templateBinding.put("session", session());
        templateBinding.put("request",request());
        templateBinding.put("flash", flash());
        templateBinding.put("params", params());
        templateBinding.put("errors", Validation.errors());
        try {
            if(templateName.endsWith(".jte")) {
                return new RenderJte(templateName, templateBinding.data);
            } else {
                Template template = TemplateLoader.load(templateName);
                return new RenderTemplate(template, templateBinding.data);
            }
        } catch (TemplateNotFoundException ex) {
            if (ex.isSourceAvailable()) {
                throw ex;
            }
            StackTraceElement element = PlayException.getInterestingStackTraceElement(ex);
            if (element != null) {
                ApplicationClasses.ApplicationClass applicationClass = Play.classes.getApplicationClass(element.getClassName());
                if (applicationClass != null) {
                    throw new TemplateNotFoundException(templateName, applicationClass, element.getLineNumber());
                }
            }
            throw ex;
        }
    }

    /**
     * Render the template corresponding to the action's package-class-method
     * name (@see <code>template()</code>).
     *
     * @param args
     *            The template data.
     */
    protected Result renderTemplate(Map<String, Object> args) {
        return renderTemplate(template(), args);
    }

    /**
     * Render the corresponding template (@see <code>template()</code>).
     *
     * @param args
     *            The template data
     */
    protected Result render(Object... args) {
        String templateName = null;
        if (args.length > 0 && args[0] instanceof String string && LocalVariablesNamesTracer.getAllLocalVariableNames(string).isEmpty()) {
            templateName = string;
        } else {
            templateName = template();
        }
        return renderTemplate(templateName, args);
    }

    /**
     * Work out the default template to load for the invoked action. E.g.
     * "controllers.Pages.index" returns "views/Pages/index.html".
     */
    protected String template() {
        String format = request().format;
        String templateName = request().action.replace('.', '/') + "." + (format == null ? "html" : format);
        if (templateName.startsWith("@")) {
            templateName = templateName.substring(1);
            if (!templateName.contains(".")) {
                templateName = request().controller + "." + templateName;
            }
            templateName = templateName.replace( ".", "/") + "." + (format == null ? "html" : format);
        }
        return templateName;
    }

    /**
     * Work out the default template to load for the action. E.g.
     * "controllers.Pages.index" returns "views/Pages/index.html".
     */
    protected String template(String templateName) {
        String format = request().format;
        if (templateName.startsWith("@")) {
            templateName = templateName.substring(1);
            if (!templateName.contains(".")) {
                templateName = request().controller + "." + templateName;
            }
            templateName = templateName.replace('.', '/') + "." + (format == null ? "html" : format);
        }
        return templateName;
    }

    protected void await(String timeout) {
        await(1000 * Time.parseDuration(timeout));
    }

    protected void await(int millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void verifyContinuationsEnhancement() {
        if (Play.mode.isProd()) {
            return;
        }

        try {
            throw new Exception();

        } catch (Exception e) {
            boolean verificationStarted = false;

            for (StackTraceElement ste : e.getStackTrace()) {
                String className = ste.getClassName();

                if (!verificationStarted) {
                    // Look for first application class to mark start of verification
                    verificationStarted = Play.classes.getApplicationClass(className) != null;
                }

                if(verificationStarted) {
                    // When we next see a class that should not be enhanced, the verification is complete
                    return;
                }
            }
        }
    }

    protected <T> T await(CompletableFuture<T> future) {
        try {
            return (T) CompletableFuture.anyOf(future).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieve annotation for the action method
     *
     * @param clazz
     *            The annotation class
     * @return Annotation object or null if not found
     */
    protected <T extends Annotation> T getActionAnnotation(Class<T> clazz) {
        Method m = (Method) ActionInvoker.getActionMethod(request().action)[1];
        if (m.isAnnotationPresent(clazz)) {
            return m.getAnnotation(clazz);
        }
        return null;
    }

    /**
     * Retrieve annotation for the controller class
     *
     * @param clazz
     *            The annotation class
     * @return Annotation object or null if not found
     */
    protected <T extends Annotation> T getControllerAnnotation(Class<T> clazz) {
        if (getControllerClass().isAnnotationPresent(clazz)) {
            return getControllerClass().getAnnotation(clazz);
        }
        return null;
    }

    /**
     * Retrieve annotation for the controller class
     *
     * @param clazz
     *            The annotation class
     * @return Annotation object or null if not found
     */
    protected <T extends Annotation> T getControllerInheritedAnnotation(Class<T> clazz) {
        Class<?> c = getControllerClass();
        while (!c.equals(Object.class)) {
            if (c.isAnnotationPresent(clazz)) {
                return c.getAnnotation(clazz);
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * Retrieve the controller class
     *
     * @return Annotation object or null if not found
     */
    protected Class<? extends GenericController> getControllerClass() {
        return (Class<? extends GenericController>) request().controllerClass;
    }

    protected void redirect(Class<? extends PlayController> clazz, String methodName, Object... param) {
        try {
            try {
                Router.ActionDefinition actionDefinition = Router.reverse(clazz, methodName, param);
                throw new Redirect(actionDefinition.toString(), false);
            } catch (NoRouteFoundException e) {
                StackTraceElement element = PlayException.getInterestingStackTraceElement(e);
                if (element != null) {
                    throw new NoRouteFoundException(clazz.getName()+"."+methodName, Map.of("params", param), Play.classes.getApplicationClass(element.getClassName()),
                            element.getLineNumber());
                } else {
                    throw e;
                }
            }
        }catch (Exception e) {
            if (e instanceof Redirect r) {
                throw r;
            }
            if (e instanceof PlayException ex) {
                throw ex;
            }
            throw new UnexpectedException(e);
        }
    }
}
