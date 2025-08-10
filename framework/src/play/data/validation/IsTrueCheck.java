package play.data.validation;

import net.sf.oval.Validator;
import net.sf.oval.configuration.annotation.AbstractAnnotationCheck;
import net.sf.oval.context.OValContext;

@SuppressWarnings("serial")
public class IsTrueCheck extends AbstractAnnotationCheck<IsTrue> {

    static final String mes = "validation.isTrue";

    @Override
    public void configure(IsTrue isTrue) {
        setMessage(isTrue.message());
    }

    @Override
    public boolean isSatisfied(Object validatedObject, Object value, OValContext context, Validator validator) {
        if (value == null) {
            return false;
        }
        if (value instanceof String str) {
            try {
                return Boolean.parseBoolean(str);
            } catch (Exception e) {
                return false;
            }
        }
        if (value instanceof Number number) {
            try {
                return number.doubleValue() != 0;
            } catch (Exception e) {
                return false;
            }
        }
        if (value instanceof Boolean bool) {
            try {
                return bool;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
   
}
