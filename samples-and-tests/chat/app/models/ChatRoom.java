package models;

import play.libs.F;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ChatRoom {
    
    // ~~~~~~~~~ Let's chat! 
    
    final F.ArchivedEventStream<Event> chatEvents = new F.ArchivedEventStream<>(100);
    
    /**
     * For WebSocket, when a user join the room we return a continuous event stream
     * of ChatEvent
     */
    public F.EventStream<Event> join(String user) {
        chatEvents.publish(new Join(user));
        return chatEvents.eventStream();
    }
    
    /**
     * A user leave the room
     */
    public void leave(String user) {
        chatEvents.publish(new Leave(user));
    }
    
    /**
     * A user say something on the room
     */
    public void say(String user, String text) {
        if(text == null || text.trim().equals("")) {
            return;
        }
        chatEvents.publish(new Message(user, text));
    }
    
    /**
     * For long polling, as we are sometimes disconnected, we need to pass 
     * the last event seen id, to be sure to not miss any message
     */
    public CompletableFuture<List<F.IndexedEvent<Event>>> nextMessages(long lastReceived) {
        return chatEvents.nextEvents(lastReceived);
    }
    
    /**
     * For active refresh, we need to retrieve the whole message archive at
     * each refresh
     */
    public List<ChatRoom.Event> archive() {
        return chatEvents.archive();
    }
    
    // ~~~~~~~~~ Chat room events

    public static abstract class Event {
        
        final public String type;
        final public Long timestamp;
        
        public Event(String type) {
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }
        
    }
    
    public static class Join extends Event {
        
        final public String user;
        
        public Join(String user) {
            super("join");
            this.user = user;
        }
        
    }
    
    public static class Leave extends Event {
        
        final public String user;
        
        public Leave(String user) {
            super("leave");
            this.user = user;
        }
        
    }
    
    public static class Message extends Event {
        
        final public String user;
        final public String text;
        
        public Message(String user, String text) {
            super("message");
            this.user = user;
            this.text = text;
        }
        
    }
    
    // ~~~~~~~~~ Chat room factory

    static ChatRoom instance = null;
    public static ChatRoom get() {
        if(instance == null) {
            instance = new ChatRoom();
        }
        return instance;
    }
    
}

