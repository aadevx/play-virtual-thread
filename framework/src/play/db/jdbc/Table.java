package play.db.jdbc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value={ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Table {

	// nama table, jika ada schema, sertakan langsung pada nama table
	String name() default "";
	
	// nama schema
	String schema() default "";
}
