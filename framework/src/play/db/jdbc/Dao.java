package play.db.jdbc;

import java.util.List;

public interface Dao<T extends GenericModel> {

    T findById(Object id);
    List<T> findAll();
    T findObject(String sql, Object... params);
    T findObject(QueryBuilder builder);
    List<T> findList(String sql, Object... params);
    List<T> findList(QueryBuilder builder);
    long count();
    long count(String sql, Object... params);
    long count(QueryBuilder builder);
    void saveAll(List<T> params);
    void saveAll(T... params);
    void delete(T obj);
    void deleteAll();
    void delete(String sql, Object... params);
    void delete(QueryBuilder builder);
    void save(T obj);
}
