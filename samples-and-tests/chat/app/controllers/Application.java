package controllers;

import play.data.validation.Required;
import play.mvc.Controller;

public class Application extends Controller {

    public static void index() {
        render();
    }
    
    public static void enterDemo(@Required String user, @Required String demo) {        
        if(validation().hasErrors()) {
            flash().error("Please choose a nick name and the demonstration type…");
            redirect(Application.class, "index");
        }
        
        // Dispatch to the demonstration        
        if(demo.equals("refresh")) {
            redirect(Refresh.class, "index", user);
        }
        if(demo.equals("longpolling")) {
            redirect(LongPolling.class, "room", user);
        }
        if(demo.equals("websocket")) {
            redirect(WebSocket.class, "room", user);
        }        
    }

}