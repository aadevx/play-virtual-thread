package play.db.jdbc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Primary Key dari model / Table dalam DB
 * @author arief ardiyansah
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Id {

	String sequence() default "";
	
	String function() default "nextval";

	String schema() default "";

	boolean generated() default false;
}
