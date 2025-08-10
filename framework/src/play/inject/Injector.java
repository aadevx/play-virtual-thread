package play.inject;

import org.apache.commons.lang3.reflect.FieldUtils;
import play.Play;
import play.mvc.Controller;
import play.mvc.GenericController;
import play.test.TestEngine;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Injector {

    private static BeanSource beanSource = new DefaultBeanSource();
    private static final Map<Class, Object> beanMap = new HashMap<>();

    public static void setBeanSource(BeanSource beanSource) {
        Injector.beanSource = beanSource;
    }

    public static <T> T getBeanOfType(String className) {
        Class<T> clazz = (Class<T>) Play.classes.loadClass(className);
        if(clazz == null) {
            try {
                clazz = (Class<T>) Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return getBeanOfType(clazz);
    }

    public static <T> T getBeanOfType(Class<T> clazz) {
        if(GenericController.class.isAssignableFrom(clazz) && beanMap.containsKey(clazz)) {
            return (T) beanMap.get(clazz);
        }
        return beanSource.getBeanOfType(clazz);
    }

    public static void init() {
        inject(beanSource);
    }
    /**
     * For now, inject beans in controllers and any classes that include @RequireInjection.
     *
     * @param source
     *            the beanSource to inject
     */
    public static void inject(BeanSource source) {
        beanMap.clear();
        List<Class> classes = new ArrayList<>();
        classes.addAll(Play.classes.getAnnotatedClasses(Singleton.class));
        classes.addAll(Play.classes.getAssignableClasses(GenericController.class));
        // scanning class , setup beanmap
        for (Class<?> clazz : classes) {
            beanMap.computeIfAbsent(clazz, k -> getBeanOfType(clazz));
        }
        classes.addAll(Play.classes.getAssignableClasses(Controller.class));
        if (Play.runningInTestMode()) {
            classes.addAll(TestEngine.allFunctionalTests());
            classes.addAll(TestEngine.allUnitTests());
        }
        for (Class<?> clazz : classes) {
            for (Field field : FieldUtils.getAllFields(clazz)) {
                if (field.isAnnotationPresent(Inject.class)) {
                    Class<?> type = field.getType();
                    Object obj = beanMap.get(type);
                    if(obj == null && type.isInterface()) {
                        List<Class> list = Play.classes.getAssignableClasses(type);
                        for(Class<?> clz : list){
                            obj = beanMap.get(clz);
                            if(obj != null)
                                break;
                        }
                    }
                    field.setAccessible(true);
                    try {
                        if(Modifier.isStatic(field.getModifiers()))
                            field.set(null, obj);
                        else {
                            field.set(beanMap.get(clazz), obj);
                        }
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    public static void scanObject(Object object) {
        for (Field field : object.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                Class<?> type = field.getType();
                Object obj = beanMap.get(type);
                if(obj == null && type.isInterface()) {
                    List<Class> list = Play.classes.getAssignableClasses(type);
                    for(Class<?> clz : list){
                        obj = beanMap.get(clz);
                        if(obj != null)
                            break;
                    }
                }
                try {
                    field.setAccessible(true);
                    field.set(object, obj);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}