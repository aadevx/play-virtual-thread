package controllers;

import play.modules.routing.Any;
import play.modules.routing.Get;
import play.mvc.GenericController;
import play.mvc.results.Redirect;
import play.mvc.results.RenderText;
import play.mvc.results.Result;
import services.BeritaService;

import javax.inject.Inject;

@Any("/nonstatic/{action}")
public class NonStatic extends GenericController {

    @Inject
    private static BeritaService service;

    public Result index() {
        String message = "LKPP 2022";
        String lable = "renderer";
        long time = System.currentTimeMillis();
        //        Map<String, Object> param = new HashMap<>();
//        param.put("message", "LKPP 2022");
//        param.put("label", "renderer");
//        param.put("time", System.currentTimeMillis());
        service.saveBerita();
        return render("hello.jte", message, lable, time);
    }

    public Result find(Long id) {
        notFoundIfNull(id);
        return new RenderText("find id : "+id);
    }

    public Result redirect() {
        return new Redirect(NonStatic.class, "index");
    }
    
    @Get("/json")
    public Result json() {
//        Sub_tag sub_tag = Sub_tag.find("stg_id='BAP'").first();
//        renderJSON(sub_tag);
        return renderText("{\"message\": \"Hello World nonstatic\"}");
    }
}
