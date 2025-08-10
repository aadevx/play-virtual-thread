package play.data.validation;

import net.sf.oval.Validator;
import net.sf.oval.configuration.annotation.AbstractAnnotationCheck;
import net.sf.oval.context.OValContext;
import play.exceptions.UnexpectedException;
import play.libs.I18N;
import play.libs.Time;
import play.utils.Utils.AlternativeDateFormat;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("serial")
public class InFutureCheck extends AbstractAnnotationCheck<InFuture> {

    static final String mes = "validation.future";

    Date reference;

    @Override
    public void configure(InFuture future) {
        try {
            this.reference = future.value().isEmpty() ? new Date() : AlternativeDateFormat.getDefaultFormatter().parse(future.value());
        } catch (ParseException ex) {
            throw new UnexpectedException("Cannot parse date " +future.value(), ex);
        }
        if(!future.value().isEmpty() && future.message().equals(mes)) {
            setMessage("validation.after");
        } else {
            setMessage(future.message());
        }
    }

    @Override
    public boolean isSatisfied(Object validatedObject, Object value, OValContext context, Validator validator) {
        requireMessageVariablesRecreation();
        if (value == null) {
            return true;
        }
        if (value instanceof Date date) {
            try {
                return reference.before(date);
            } catch (Exception e) {
                return false;
            }
        }
        if (value instanceof Long longValue) {
            try {
                return reference.before(new Date(longValue));
            } catch (Exception e) {
                return false;
            }
        }
        if (value instanceof LocalDate localDate) {
            try {
                return reference.before(Date.from((localDate).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            } catch (Exception e) {
                return false;
            }
        }
        if (value instanceof LocalDateTime localDateTime) {
            try {
                return reference.before(Date.from((localDateTime).atZone(ZoneId.systemDefault()).toInstant()));
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @Override
    public Map<String, String> createMessageVariables() {
        Map<String, String> messageVariables = new HashMap<>();
        messageVariables.put("reference", DateTimeFormatter.ofPattern(I18N.getDateFormat()).format(Time.toLocalDateTime(reference)));
        return messageVariables;
    }
   
}
