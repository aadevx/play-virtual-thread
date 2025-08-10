package controllers;

import jobs.TestCronJob;
import play.Logger;
import play.Play;
import play.cache.Cache;
import play.data.binding.As;
import play.data.binding.types.DateBinder;
import play.i18n.Lang;
import play.i18n.Messages;
import play.libs.Images;
import play.libs.WS;
import play.libs.ws.HttpResponse;
import play.modules.routing.Any;
import play.modules.routing.Get;
import play.mvc.Controller;
import play.mvc.Router;

import java.io.File;
import java.io.FileInputStream;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class Application extends Controller {


//    @CacheFor("1mn")
    @Get("/")
    public static void index(){
        String message = "Pesan LKPP 2045";
        long time = System.currentTimeMillis();
        String label = "render ajah";
//        Berita berita = Cache.get("berita");
//        if(berita == null) {
//            berita = Berita.findById(884999);
//            Cache.add("berita", berita);
//            Logger.info("add to cache berita : %s", berita);
//        }
//        Logger.info("berita : %s", berita);
        Logger.info(Messages.get("header"));
        renderArgs().put("message", message);
        renderArgs().put("label", label);
        renderArgs().put("time", time);
        renderTemplate("hello.jte");
    }

    @Get("/hello/{spid}")
    public static void hello(Long spid) {
       Logger.info("spid: %s", spid);
//        render();
        response().writeChunk("Hallo ....");
//
    }

    @Get("/ws")
    public static void ws() throws Exception {
        String content =  WS.url("https://www.google.com").setTimeout("10mn").getString();
        renderText(content);
    }

    @Get("/error")
    public static void error() {
        // render("errors/500.pebble.html");
    }

    @Get("/json")
    public static void json() {
//        Sub_tag sub_tag = Sub_tag.find("stg_id='BAP'").first();
//        renderJSON(sub_tag);
        renderText("{\"message\": \"Hello World\"}");
    }


    @Any("/file")
    public static void file(File file, @As(binder = DateBinder.class) Date coba) {
        checkAuthenticity();
        if(file == null) {
            Logger.info("file is null");
        } else {
            Logger.info("file is not null");
        }
        Logger.info("date is %s", coba);

//        if(S3Plugin.exists("tender", file.getName()))
//            Logger.info("file %s exist", file);
//        else {
//            S3Plugin.upload("tender", file);
//            Logger.info("send file to s3");
//        }
        renderText("<a href=\""+ Router.reverse("Application.downloads").add("filename", file.getName()).url +"\">download</a>");

    }

    @Get("/download")
    public static void downloads(String filename){
        Logger.info("download file %s", filename);
//        renderBinary(file);
    }

    public static void delete(String filename){
        Logger.info("hapus file %s", filename);
        renderText("file "+filename+" telah terhapus");
    }

    public static void deletebucket(){
        Logger.info("hapus bucket");
//        S3Plugin.deleteBucket("tender");
        renderText("bucket telah terhapus");
    }

    @Get("/form")
    public static void form() {
        renderArgs().put("booking", 1);
        renderTemplate();
    }
//
//    public static void upload () {
//        try  {
//            File file = Play.getFile("public/images/favicon.png");
//            if (!file.exists()) {
//                Logger.info("file null or not exist");
//                return;
//            } else {
//                Logger.info("file :%s", file);
//                Logger.info("Ukuran :%s", file.length());
//            }
//            WS.HttpResponse response = WS.url("http://localhost:9000/application/file").files(file).postAsync().get();
//            /*com.ning.http.client.Request builder = new RequestBuilder()
//                    .setMethod("POST")
//                    .setUrl("http://localhost:9000/application/file")
//                    .addBodyPart(new FilePart("testFile", file))
//                    .build();
//            Logger.info("Builder %s", builder);
//            asyncHttpClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
//                    .setConnectTimeout(35000)
//                    .setReadTimeout(35000)
//                    .setAcceptAnyCertificate(true)
//                    .build());
//            Response response = asyncHttpClient.prepareRequest(builder).execute().get();*/
//            if (response != null) {
//                Logger.info("response : %s", response.getString());
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        renderText("DONE");
//    }

    public static void content(String url){
        try {
//            String content = WS.url(url).get().body().string();
            HttpResponse response = WS.url(url).getResponse();
            renderText(response.getString());
        }catch (Exception e){
            Logger.error(e, "Can't get content from "+url);
            renderText("Can't get content from "+url);
        }
    }

    public static void download(String id) {
        Logger.info("id : %s", id);
        File file = Play.getFile("public/images/favicon.png");
        response().setHeader("Content-disposition","attachment; filename=tradeLogTest.xlsx");
        renderBinary(file);
    }

    public static void stream() throws Exception {
        FileInputStream file = new FileInputStream(Play.getFile("public/images/favicon.png"));
        renderBinary(file);
    }

    public static void contoh() {
        response().contentType="application/json";
        Map<String,Object> map=new LinkedHashMap<>();
        map.put("msg", "DONE");
        String result="DONE";
        renderJSON(result);
    }

    public static void test() throws Exception {
        await(new TestCronJob().now());
        Logger.info("berhasil");
        response().writeChunk("test berhasil");
    }

    public static void captcha() {
        Images.Captcha captcha = Images.captcha();
        captcha.addNoise();
        //        String code = captcha.getText("#073642"); // #based02 solarized color captcha
        String code = captcha.getText(6, "abcdefghijkmnopqrstuvwxyz23456789");
        //		Logger.info("GET %s", code);
//        Cache.set(id, code.toLowerCase(), "10mn"); // store this unique id for 10mn to validate captcha
        renderBinary(captcha);
    }

    public static void lang(String locale) {
        Logger.info("locale set %s", locale);
        Lang.change(locale);
        index();
    }
}