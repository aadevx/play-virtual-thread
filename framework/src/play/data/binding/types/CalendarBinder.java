package play.data.binding.types;

import play.data.binding.AnnotationHelper;
import play.data.binding.TypeBinder;
import play.i18n.Lang;
import play.libs.I18N;
import play.libs.Time;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;

/**
 * Binder that support Calendar class.
 */
public class CalendarBinder implements TypeBinder<Calendar> {

    @Override
    public Calendar bind(String name, Annotation[] annotations, String value, Class actualClass, Type genericType) throws Exception {
    	if (value == null || value.isBlank()) {
            return null;
        }
        Calendar cal = Calendar.getInstance(Lang.getLocale());

        Date date = AnnotationHelper.getDateAs(annotations, value);
        if (date != null) {
            cal.setTime(date);
        } else {
            Date d = Time.toDate(LocalDateTime.from(DateTimeFormatter.ofPattern(I18N.getDateFormat()).parse(value)));
            cal.setTime(d);
        }
        return cal;
    }
}
