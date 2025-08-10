package play.jte;

import play.mvc.Scope;
import play.mvc.results.RenderJte;

import java.util.HashMap;
import java.util.Map;

/**
 * abstract page untuk template JTE, semua template page extends dari class ini
 * field hanya templateName, dan argument selain itu implementasi per app
 * @author Arief Ardiyansah
 */
public abstract class AbstractPage {

    private final String templateName;
    private final Map<String, Object> arguments;

    public AbstractPage(String templateName) {
       this(templateName, new HashMap<>());
    }

    public AbstractPage(String templateName, Map<String, Object> arguments) {
        this.templateName = templateName;
        this.arguments = arguments;
    }

    public void put(String param, Object value) {
        this.arguments.put(param, value);
    }

    public void render() {
        Scope.RenderArgs templateBinding = Scope.RenderArgs.current();
        templateBinding.data.putAll(arguments);
        templateBinding.data.put("page", this);
        try {
            throw new RenderJte(templateName, templateBinding.data);
        }catch (Exception e){
            throw e;
        }
    }
}
