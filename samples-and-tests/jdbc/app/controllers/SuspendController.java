package controllers;

import jobs.TestCronJob;
import play.Logger;
import play.modules.routing.Any;
import play.modules.routing.Get;
import play.mvc.Controller;

@Any("/suspend/{action}")
public class SuspendController extends Controller {


    @Get("/test")
    public static void test() {
        await(new TestCronJob().now());
        Logger.info("berhasil");
        renderText("berhasil");
    }
}
