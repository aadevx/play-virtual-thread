package play.plugins;

import play.Logger;
import play.PlayPlugin;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.classloading.enhancers.ConstructorEnhancer;
import play.classloading.enhancers.Enhancer;
import play.classloading.enhancers.LocalvariablesNamesEnhancer;
import play.db.jdbc.JdbcEnhancer;
import play.exceptions.UnexpectedException;

/**
 * Plugin used for core tasks
 */
public class EnhancerPlugin extends PlayPlugin {

    private final Enhancer[] defaultEnhancers = new Enhancer[] { new ConstructorEnhancer(), new LocalvariablesNamesEnhancer()};

    @Override
    public void enhance(ApplicationClass applicationClass) {
        for (Enhancer enhancer : defaultEnhancers) {
            try {
                long start = System.currentTimeMillis();
                enhancer.enhanceThisClass(applicationClass);
                if (Logger.isTraceEnabled()) {
                    Logger.trace("%sms to apply %s to %s", System.currentTimeMillis() - start, enhancer.getClass().getSimpleName(),
                            applicationClass.name);
                }
            } catch (Exception e) {
                throw new UnexpectedException("While applying " + enhancer + " on " + applicationClass.name, e);
            }
        }
    }
}