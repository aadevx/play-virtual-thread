package play.db.jdbc;

import javax.persistence.MappedSuperclass;

/**
 * Base class for model objects
 * Automatically provide a @Id Long id field
 */
@MappedSuperclass
public class Model extends BaseTable {

    @Id
    public Long id;

    public Long getId() {
        return id;
    }
}
