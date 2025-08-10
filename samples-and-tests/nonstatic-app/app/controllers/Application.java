package controllers;

import com.google.inject.Inject;
import play.Logger;
import play.modules.routing.Get;
import play.mvc.PlayController;
import play.mvc.Render;
import play.mvc.Scope;
import play.mvc.results.RenderTemplate;
import play.mvc.results.RenderText;
import play.mvc.results.Result;
import play.templates.Template;
import play.templates.TemplateLoader;
import services.Counter;

import java.util.HashMap;
import java.util.Map;

public class Application implements PlayController, Render {

    @Inject
    private Counter counter;

    @Get("/")
    public Result index() {
        Logger.info("counter : %s", counter.get("counter.job.onApplicationStart"));
//        Logger.info("counter onEvery : %s", counter.get("counter.job.every"));
//        Template template = TemplateLoader.load("Application/index.html");
//        Map<String, Object> params = new HashMap<>();
//        params.putAll(Scope.RenderArgs.current().data);
//        return new RenderTemplate(template, params);
        Logger.info("request : %s", request);
        return renderTemplate("Application/index.html");
    }

    @Get("/hello")
    public Result hello() {
        Logger.info("session : %s", session);
        return renderText("Hello world!");
    }

}