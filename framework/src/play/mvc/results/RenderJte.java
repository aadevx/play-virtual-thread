package play.mvc.results;

import gg.jte.output.Utf8ByteOutput;
import play.exceptions.UnexpectedException;
import play.jte.JtePlugin;
import play.libs.MimeTypes;
import play.mvc.Http;

import java.io.OutputStream;
import java.util.Map;

public class RenderJte extends Result{

    private final String name;
    private final Utf8ByteOutput content;

    public RenderJte(String name, Map<String, Object> arguments) {
        this.name = name;
        this.content = new Utf8ByteOutput();
        JtePlugin.render(name, arguments, content);
    }
    @Override
    public void apply(Http.Request request, Http.Response response) {
        String contentType = MimeTypes.getContentType(name, "text/plain");
        setContentTypeIfNotSet(response, contentType);
        try (OutputStream os = response.out){
            content.writeTo(os);
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }
}
