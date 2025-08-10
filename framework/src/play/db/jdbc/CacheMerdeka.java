package play.db.jdbc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**CacheMerdeka dipasang di Class yg extends BaseTable serta di static method yg akan di-cache
 * Secara default, yg akan dicache yaitu
 * - findById()
 * - count()
 * - count(query)
 * - count(query, params)
 * 
 * @author Andik Yulianto (andik@lkpp.go.id)
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface CacheMerdeka {
	
	/**Durasi dari HotCache dalam format String seperti Cache.set(key, val, duration);
	 * Jika null artinya cache disabled
	 * 
	 * 
	 * @return
	 */
	String duration() default "10min";
}
