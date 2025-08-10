package play.data.validation;

import net.sf.oval.Validator;
import net.sf.oval.configuration.annotation.AbstractAnnotationCheck;
import net.sf.oval.context.FieldContext;
import net.sf.oval.context.OValContext;
import play.db.DB;
import play.db.jdbc.*;
import play.exceptions.UnexpectedException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Check which proof if one or a set of properties is unique.
 *
 */
public class UniqueCheck extends AbstractAnnotationCheck<Unique> {

    static final String mes = "validation.unique";
    private String uniqueKeyContext = null;
    static final Pattern pattern = Pattern.compile("[,;\\s][\\s]*");

    @Override
    public void configure(Unique constraintAnnotation) {
        uniqueKeyContext = constraintAnnotation.value();
        setMessage(constraintAnnotation.message());
    }

    @Override
    public Map<String, String> createMessageVariables() {
        Map<String, String> messageVariables = new TreeMap<>();
        messageVariables.put("2-properties", uniqueKeyContext);
        return messageVariables;
    }

    private String[] getPropertyNames(String uniqueKey) {
        String completeUniqueKey;
        if (!uniqueKeyContext.isEmpty()) {
            completeUniqueKey = uniqueKeyContext + ";" + uniqueKey;
        } else {
            completeUniqueKey = uniqueKey;
        }
        return pattern.split(completeUniqueKey);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isSatisfied(Object validatedObject, Object value, OValContext context, Validator validator) {
        requireMessageVariablesRecreation();
        if (value == null) {
            return true;
        }
        String[] propertyNames = getPropertyNames(((FieldContext) context).getField().getName());
        Class<?> clazz = validatedObject.getClass();
        if(validatedObject instanceof BaseTable model) {
        	Table tableAnn = model.getClass().getAnnotation(Table.class);
        	String dbname = DB.getDBName(validatedObject.getClass());
        	Field[] fields = model.getClass().getDeclaredFields();
        	List<Object> values = new ArrayList<Object>();       	        	
        	StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM "+tableAnn.name());
        	sql.append(" WHERE ");
        	Field field = null;
			if (propertyNames != null && propertyNames.length > 0) {
				for (int i = 0; i < propertyNames.length; i++) {
					field = getField(clazz, propertyNames[i]);
					field.setAccessible(true);
					try {
						values.add(field.get(model));
//						Logger.info("field %s , value %s", field.getName(), field.get(model));
					} catch (Exception ex) {
						throw new UnexpectedException(ex);
					}
					if (i > 0) {
						sql.append(" AND ");
					}					
					sql.append(propertyNames[i]).append(" = ? ");
				}
			}
        	for(int i = 0 ;i < fields.length; i++) {
        		field = fields[i];
        		if(field.isAnnotationPresent(Id.class)) {        	
        			try {
        				Object o= field.get(model);
        				if(o != null) {
        					values.add(o);
        					sql.append(" AND ").append(field.getName()).append(" =  ? ");
        				}						
					} catch (Exception ex) {
						throw new UnexpectedException(ex);
					}       			
        		}        		
        	}        	        	
        	int count = Query.count(new QueryBuilder(sql.toString(), values.toArray()).using(dbname),Integer.class);
        	return count == 0;
        } 
        return false;
    }

    private Field getField(Class clazz, String fieldName) {
        Class c = clazz;
        try {
            while (!c.equals(Object.class)) {
                try {
                    return c.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
        } catch (Exception e) {
            throw new UnexpectedException("Error while determining the field " +
                    fieldName + " for an object of type " + clazz, e);
        }
        throw new UnexpectedException("Cannot get the field " +  fieldName +
                " for an object of type " + clazz);
    }
}