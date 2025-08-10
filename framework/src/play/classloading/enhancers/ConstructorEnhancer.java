package play.classloading.enhancers;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtNewConstructor;
import javassist.NotFoundException;
import play.Logger;
import play.classloading.ApplicationClasses;
import play.exceptions.UnexpectedException;

/**
 *
 */
public class ConstructorEnhancer extends Enhancer{
    @Override
    public void enhanceThisClass(ApplicationClasses.ApplicationClass applicationClass) throws Exception {
        CtClass ctClass = makeClass(applicationClass);
        if (ctClass.isInterface() || ctClass.getName().endsWith(".package")) {
            return;
        }

        addDefaultConstructor(ctClass);
        applicationClass.enhancedByteCode = ctClass.toBytecode();
        ctClass.defrost();
    }

    private void addDefaultConstructor(CtClass ctClass) {
        try {
            boolean hasDefaultConstructor = hasDefaultConstructor(ctClass);
            if (!hasDefaultConstructor && !ctClass.isInterface()) {
                CtConstructor defaultConstructor = CtNewConstructor.make("public " + ctClass.getSimpleName() + "() {}", ctClass);
                ctClass.addConstructor(defaultConstructor);
            }
        } catch (Exception e) {
            Logger.error(e, "Failed to generate default constructor for " + ctClass.getName());
            throw new UnexpectedException("Failed to generate default constructor for " + ctClass.getName(), e);
        }
    }

    private boolean hasDefaultConstructor(CtClass ctClass) throws NotFoundException {
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length == 0) {
                return true;
            }
        }
        return false;
    }
}
