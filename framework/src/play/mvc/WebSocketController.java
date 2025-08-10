package play.mvc;

import play.classloading.enhancers.LocalvariablesNamesEnhancer.LocalVariablesSupport;
import play.data.validation.Validation;
import play.mvc.results.WebSocketDisconnect;

import java.util.concurrent.CompletableFuture;

public class WebSocketController implements LocalVariablesSupport, PlayController {

    protected static Http.Request request() {
        return Http.Request.current();
    }
    protected static Http.Inbound inbound() {
        return Http.Inbound.current();
    }
    protected static Http.Outbound outbound() {
        return Http.Outbound.current();
    }

    protected static Scope.Params params() {
        return Scope.Params.current();
    }

    protected static Validation validation(){
        return Validation.current();
    }

    protected static Scope.Session session() {
        return Scope.Session.current();
    }

    protected static void await(String timeout) {
        Controller.await(timeout);
    }

    protected static void await(int millis) {
        Controller.await(millis);
    }

    protected static <T> T await(CompletableFuture<T> future) {
        return Controller.await(future);
    }

    protected static void disconnect() {
        throw new WebSocketDisconnect();
    }

}
