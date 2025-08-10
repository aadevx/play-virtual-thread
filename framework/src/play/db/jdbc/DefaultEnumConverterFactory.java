package play.db.jdbc;

import org.sql2o.converters.Converter;
import org.sql2o.converters.ConverterException;
import org.sql2o.converters.EnumConverterFactory;

import javax.persistence.EnumType;

/**
 * Default implementation of {@link EnumConverterFactory},
 * used by sql2o to convert a value from the database into an {@link Enum}.
 */
public class DefaultEnumConverterFactory implements EnumConverterFactory {
    @SuppressWarnings("rawtypes")
	public <E extends Enum> Converter<E> newConverter(final Class<E> enumType) {
        return new Converter<E>() {
            @SuppressWarnings("unchecked")
            public E convert(Object val) throws ConverterException {
                if (val == null) {
                    return null;
                }
                try {
                    if (val instanceof String str){
                        return (E)Enum.valueOf(enumType, str);
                    } else if (val instanceof Number number){
                        return enumType.getEnumConstants()[number.intValue()];
                    }
                } catch (Throwable t) {
                    throw new ConverterException("Error converting value '" + val + "' to " + enumType.getName(), t);
                }
                throw new ConverterException("Cannot convert type '" + val.getClass().getName() + "' to an Enum");
            }

            public Object toDatabaseParam(Enum val) {
            	Enumerated annotasi = enumType.getAnnotation(Enumerated.class);
            	if(annotasi.value() == EnumType.ORDINAL)
            		return val.ordinal();
            	else
            		return val.name();
            }
        };
    }
}
