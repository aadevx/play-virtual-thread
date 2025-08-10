package controllers;

import models.ChatRoom;
import play.mvc.Controller;

import java.util.List;

public class Refresh extends Controller {

    public static void index(String user) {
        ChatRoom.get().join(user);
        redirect(Refresh.class, "room", user);
    }
    
    public static void room(String user) {
        List events = ChatRoom.get().archive();
        render(user, events);
    }
    
    public static void say(String user, String message) {
        ChatRoom.get().say(user, message);
        redirect(Refresh.class, "room", user);
    }
    
    public static void leave(String user) {
        ChatRoom.get().leave(user);
        redirect(Application.class, "index");
    }
    
}

