package play.libs;
import org.xhtmlrenderer.pdf.ITextRenderer;
import play.Logger;
import play.templates.Template;
import play.templates.TemplateLoader;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * @author  Arief ardiyansah
 * berisi tentang fungsi2 export (pdf, json, csv, exel dll)
 */

public class Export {

    public static byte[] htmlToPDF(String templateSrc, Map<String, Object> params) {
        Template template = TemplateLoader.load(templateSrc);
        return htmlToPDF(template.render(params));
    }

    public static byte[] htmlToPDF(String content) {
        byte[] result = null;
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()){
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(content);
            renderer.layout();
            renderer.createPDF(os);
            renderer.finishPDF();
            result = os.toByteArray();
        } catch (Exception e) {
            Logger.error(e, "generatePDF %s", e.getLocalizedMessage());
        }
        return result;
    }

}
