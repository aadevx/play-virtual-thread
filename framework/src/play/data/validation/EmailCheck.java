package play.data.validation;

import net.sf.oval.Validator;
import net.sf.oval.configuration.annotation.AbstractAnnotationCheck;
import net.sf.oval.context.OValContext;

import java.util.regex.Pattern;

@SuppressWarnings("serial")
public class EmailCheck extends AbstractAnnotationCheck<Email> {

    static final String mes = "validation.email";
    static final Pattern emailPattern = Pattern.compile("[\\w!#$%&'*+/=?^_`{|}~-]+(?:\\.[\\w!#$%&'*+/=?^_`{|}~-]+)*@(?:[\\w](?:[\\w-]*[\\w])?\\.)+[a-zA-Z0-9](?:[\\w-]*[\\w])?");

    @Override
    public void configure(Email email) {
        setMessage(email.message());
    }

    @Override
    public boolean isSatisfied(Object validatedObject, Object value, OValContext context, Validator validator) {
        if (value == null || value.toString().isEmpty()) {
            return true;
        }
        return emailPattern.matcher(value.toString()).matches();
    }
   
}
