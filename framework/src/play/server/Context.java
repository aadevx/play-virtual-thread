package play.server;

import play.InvocationContext;
import play.mvc.Http;
import play.mvc.results.NotFound;

public interface Context {

    Http.Request request();

    Http.Response response();

    void serve404(NotFound e);

    InvocationContext getInvocationContext();

    void serve500(Exception e);

    void release();

    void execute();

    void writeResponse() throws Exception;
}
