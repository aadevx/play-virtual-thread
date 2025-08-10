package play.mvc;

import play.exceptions.PlayException;
import play.exceptions.UnexpectedException;

public class WebSocketInvoker {

    public static void invoke(Http.Request request, Http.Inbound inbound, Http.Outbound outbound) {
        try {
           ActionInvoker.invoke(request, null);
        }catch (PlayException e) {
            throw e;
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }

    }
}
