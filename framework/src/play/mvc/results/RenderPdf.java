package play.mvc.results;

import play.exceptions.UnexpectedException;
import play.libs.Export;
import play.mvc.Http;
import play.templates.Template;

import java.util.Map;

public class RenderPdf extends Result {

    private final byte[] content;
    private final long renderTime;

    public RenderPdf(Template template, Map<String, Object> arguments) {
        if (arguments.containsKey("out")) {
            throw new RuntimeException("Arguments should not contain out");
        }
        long start = System.currentTimeMillis();
        this.content  = Export.htmlToPDF(template.render(arguments));
        this.renderTime = System.currentTimeMillis() - start;
    }

    public RenderPdf(String template, Map<String, Object> arguments) {
        if (arguments.containsKey("out")) {
            throw new RuntimeException("Arguments should not contain out");
        }
        long start = System.currentTimeMillis();
        this.content  = Export.htmlToPDF(template, arguments);
        this.renderTime = System.currentTimeMillis() - start;
    }

    @Override
    public void apply(Http.Request request, Http.Response response) {
        try {
            setContentTypeIfNotSet(response, "application/pdf");
            response.out.write(content);
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    public long getRenderTime() {
        return renderTime;
    }
}
