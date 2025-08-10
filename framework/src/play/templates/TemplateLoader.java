package play.templates;

import play.Logger;
import play.Play;
import play.exceptions.TemplateCompilationException;
import play.exceptions.TemplateNotFoundException;
import play.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TemplateLoader {

    protected static final Map<String, BaseTemplate> templates = new ConcurrentHashMap<>();
    /**
     * See getUniqueNumberForTemplateFile() for more info
     */
    private static final AtomicLong nextUniqueNumber = new AtomicLong(1000);//we start on 1000
    private static final Map<String, String> templateFile2UniqueNumber = new ConcurrentHashMap<>();

    /**
     * All loaded templates is cached in the templates-list using a key.
     * This key is included as part of the classname for the generated class for a specific template.
     * The key is included in the classname to make it possible to resolve the original template-file
     * from the classname, when creating cleanStackTrace
     *
     * This method returns a unique representation of the path which is usable as part of a classname
     *
     * @param path
     * @return a unique representation of the path which is usable as part of a classname
     */
    public static String getUniqueNumberForTemplateFile(String path) {
        return templateFile2UniqueNumber.computeIfAbsent(path, template -> Long.toString(nextUniqueNumber.getAndIncrement()));
    }

    /**
     * Load a template from a virtual file
     * @param file A VirtualFile
     * @return The executable template
     */
    public static Template load(File file) {
        // Try with plugin
        Template pluginProvided = Play.pluginCollection.loadTemplate(file);
        if (pluginProvided != null) {
            return pluginProvided;
        }

        // Use default engine
        String fileRelativePath = FileUtils.relativePath(file);
        String key = getUniqueNumberForTemplateFile(fileRelativePath);
        if (!templates.containsKey(key) || templates.get(key).compiledTemplate == null) {
            if (Play.usePrecompiled) {
                BaseTemplate template = new GroovyTemplate(fileRelativePath.replaceAll("\\{(.*)\\}", "from_$1").replace(':', '_').replace("..", "parent"), "");
                try {
                    template.loadPrecompiled();
                    templates.put(key, template);
                    return template;
                } catch (Exception e) {
                    Logger.warn(e, "Precompiled template %s not found, trying to load it dynamically...", fileRelativePath);
                }
            }
            BaseTemplate template = new GroovyTemplate(fileRelativePath, FileUtils.contentAsString(file));
            if (template.loadFromCache()) {
                templates.put(key, template);
            } else {
                templates.put(key, new GroovyTemplateCompiler().compile(file));
            }
        } else {
            BaseTemplate template = templates.get(key);
            if (Play.mode == Play.Mode.DEV && template.timestamp < file.lastModified()) {
                templates.put(key, new GroovyTemplateCompiler().compile(file));
            }
        }
        if (templates.get(key) == null) {
            throw new TemplateNotFoundException(fileRelativePath);
        }
        return templates.get(key);
    }

    /**
     * Load a template from a String
     * @param key A unique identifier for the template, used for retrieving a cached template
     * @param source The template source
     * @return A Template
     */
    public static BaseTemplate load(String key, String source) {
        if (!templates.containsKey(key) || templates.get(key).compiledTemplate == null) {
            BaseTemplate template = new GroovyTemplate(key, source);
            if (template.loadFromCache()) {
                templates.put(key, template);
            } else {
                templates.put(key, new GroovyTemplateCompiler().compile(template));
            }
        } else {
            BaseTemplate template = new GroovyTemplate(key, source);
            if (Play.mode == Play.Mode.DEV) {
                templates.put(key, new GroovyTemplateCompiler().compile(template));
            }
        }
        if (templates.get(key) == null) {
            throw new TemplateNotFoundException(key);
        }
        return templates.get(key);
    }

    /**
     * Clean the cache for that key
     * Then load a template from a String
     * @param key A unique identifier for the template, used for retrieving a cached template
     * @param source The template source
     * @return A Template
     */
    public static BaseTemplate load(String key, String source, boolean reload) {
        cleanCompiledCache(key);
        return load(key, source);
    }

    /**
     * Load template from a String, but don't cache it
     * @param source The template source
     * @return A Template
     */
    public static BaseTemplate loadString(String source) {
        BaseTemplate template = new GroovyTemplate(source);
        return new GroovyTemplateCompiler().compile(template);
    }

    /**
     * Cleans the cache for all templates
     */
    public static void cleanCompiledCache() {
        templates.clear();
    }

    /**
     * Cleans the specified key from the cache
     * @param key The template key
     */
    public static void cleanCompiledCache(String key) {
        templates.remove(key);
    }

    /**
     * Load a template
     * @param path The path of the template (ex: Application/index.html)
     * @return The executable template
     */
    public static Template load(String path) {
        Template template = null;
        for (File vf : Play.templatesPath) {
            if (vf == null) {
                continue;
            }
            File tf = new File(vf, path);
            boolean templateExists = tf.exists() || FileUtils.toURl(tf) != null;
            if (!templateExists && Play.usePrecompiled) { // find default template default
                String relatifePath = FileUtils.relativePath(tf);
                String name = relatifePath.replaceAll("\\{(.*)\\}", "from_$1").replace(':', '_').replace("..", "parent");
                templateExists = Play.getFile("precompiled/templates/" + name).exists();
            }
            if (templateExists) {
                template = TemplateLoader.load(tf);
                break;
            }
        }
        /*if (template == null) {
            template = TemplateLoader.load(VirtualFile.open(path));
        }*/
        if (template == null) {
            File tf = Play.getVirtualFile(path);
            if (tf != null && tf.exists()) {
                template = TemplateLoader.load(tf);
            } else {
                throw new TemplateNotFoundException(path);
            }
        }
        return template;
    }

    /**
     * List all found templates
     * @return A list of executable templates
     */
    public static List<Template> getAllTemplate() {
        List<Template> res = new ArrayList<>();
        for (File virtualFile : Play.templatesPath) {
            scan(res, virtualFile);
        }
        for (File root : Play.roots) {
            File vf = new File(root, "conf/routes");
            if (vf != null && vf.exists()) {
                Template template = load(vf);
                if (template != null) {
                    template.compile();
                }
            }
        }
        return res;
    }

    private static void scan(List<Template> templates, File current) {
        if (!current.isDirectory() && !current.getName().startsWith(".")) {
            long start = System.currentTimeMillis();
            Template template = load(current);
            if (template != null) {
                try {
                    template.compile();
                    if (Logger.isTraceEnabled()) {
                        Logger.trace("%sms to load %s", System.currentTimeMillis() - start, current.getName());
                    }
                } catch (TemplateCompilationException e) {
                    Logger.error("Template %s does not compile at line %d", e.getTemplate().name, e.getLineNumber());
                    throw e;
                }
                templates.add(template);
            }
        } else if (current.isDirectory() && !current.getName().startsWith(".")) {
            for (File virtualFile : FileUtils.list(current)) {
                scan(templates, virtualFile);
            }
        }
    }
}
