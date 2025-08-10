package play.jte;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.TemplateException;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import gg.jte.runtime.Constants;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.pdf.ITextRenderer;
import play.Logger;
import play.Play;
import play.mvc.Http;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JtePlugin  {

    static final TemplateResolver codeResolver = new TemplateResolver();
    static TemplateEngine templateEngine = null;

    public static void render(String name, Map<String, Object> arguments, TemplateOutput output) {
        if(templateEngine == null)
            return;
        name = name.trim();
        if(name.startsWith("/"))
            name = name.substring(1);
        if(Play.mode.isDev()) {
            Http.Request request = Http.Request.current();
            if(request != null)
                Logger.info("render %s, request %s (%s)", name, Http.Request.current(), Http.Request.current().action);
        }
        Monitor monitor = MonitorFactory.start(name);
        try {
            templateEngine.render(name, arguments, output);
        } catch (TemplateException e) {
            throw e;
        } finally {
            monitor.stop();
            monitor = null;
        }
    }


    public static void precompile() {
        if (codeResolver.isUseJte()) {
            Logger.info("[Jte] template engine precompiling....");
            File classDir = Play.classPath();
            templateEngine = TemplateEngine.create(codeResolver, classDir.toPath(), ContentType.Html, Play.classloader, Constants.PACKAGE_NAME_PRECOMPILED);
            templateEngine.setTrimControlStructures(true);
            templateEngine.setBinaryStaticContent(true);
            templateEngine.precompileAll();
            // hapus java file at precompiled folder
            List<Path> templates = new ArrayList<>();
            codeResolver.scan(templates, new File(classDir, Constants.PACKAGE_NAME_PRECOMPILED.replace(".","/")), file -> !file.isDirectory() && file.getName().endsWith(".java"));
            templates.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    Logger.error(e, "[precompile] %s", e.getMessage());
                }
            });
        }
    }

    public static void load() {
        if (codeResolver.isUseJte()) {
            File classDir = Play.classPath();
            if (Play.mode.isProd() || Play.usePrecompiled) {
                Logger.info("[Jte] template engine precompiled");
//                File precompiledDir = new File(Play.applicationPath, "precompiled/java");
                templateEngine = TemplateEngine.createPrecompiled(classDir.toPath(), ContentType.Html, Play.classloader);
            } else  {
                Logger.info("[Jte] template engine");
                templateEngine = TemplateEngine.create(codeResolver, classDir.toPath(), ContentType.Html, Play.classloader, Constants.PACKAGE_NAME_PRECOMPILED);
            }
            templateEngine.setTrimControlStructures(true);
            templateEngine.setBinaryStaticContent(true);
        }
    }

    public static boolean hasTemplate(String name) {
        if(templateEngine == null)
            return false;
        return templateEngine.hasTemplate(name);
    }

    public static String render(String name, Map<String, Object> arguments) {
        TemplateOutput output = new StringOutput();
        render(name, arguments, output);
        return output.toString();
    }

    public static InputStream renderPdf(String content) {
        content = cleanTextContent(content);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()){
            long start = System.currentTimeMillis();
            Logger.debug("content : %s", content);
            ITextRenderer renderer = new ITextRenderer();
            SharedContext sharedContext = renderer.getSharedContext();
            sharedContext.setPrint(true);
            sharedContext.setInteractive(false);
            renderer.setDocumentFromString(content);
            renderer.layout();
            renderer.createPDF(os);
            renderer.finishPDF();
            InputStream is = new ByteArrayInputStream(os.toByteArray());
            Logger.debug("time to Cetak Pdf : %s", System.currentTimeMillis() - start);
            return is;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static InputStream renderPdf(String name, Map<String, Object> arguments) {
        String content = render(name, arguments);
        return renderPdf(content);
    }

    private static String cleanTextContent(String text)
    {
        // strips off all non-ASCII characters
        text = text.replaceAll("[^\\x00-\\x7F]", "");

        // erases all the ASCII control characters
        text = text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");

        // removes non-printable characters from Unicode
        text = text.replaceAll("\\p{C}", "");

        text = text.replace("<o:p>", "").replace("</o:p>", "");

        return text.trim();
    }
}
