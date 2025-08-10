package play.db.jdbc;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import play.Logger;
import play.db.DB;
import play.utils.Utils;

import javax.persistence.MappedSuperclass;
import javax.persistence.PersistenceUnit;
import javax.persistence.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class AbstractDao<T extends GenericModel> implements Dao<T> {

    private final Class<T> clazz;
    private final String table;
    private final String dbname;
    private final String nextSequenceFunction;
    private final List<String> primaryKeyNames;
    private final String SQL_SELECT;
    private final String SQL_SELECT_ID;
    private final String SQL_UPDATE;
    private final String SQL_DELETE_ID;
    private final String SQL_DELETE;
    private final String SQL_COUNT_ID;
    private final String SQL_INSERT;
    private final String SQL_INSERT_AUTO;
    private final String[] generatedKey;

    protected AbstractDao(Class<T> clazz) {
        this.clazz = clazz;
        Table tableAnn = clazz.getAnnotation(Table.class);
        String table = tableAnn.name();
        String dbname = DB.DEFAULT;
        PersistenceUnit pu = clazz.getAnnotation(PersistenceUnit.class);
        if (pu != null) {
            dbname = pu.name();
        }
        this.dbname = dbname;
        if(StringUtils.isEmpty(table)) {
            String[] arrayTable = StringUtils.split(clazz.getName(), ".");
            if(arrayTable.length > 1)
                table=arrayTable[arrayTable.length - 1];// ambil nama class tanpa nama packagenya
            else
                table = clazz.getName();
        }
        if(!StringUtils.isEmpty(tableAnn.schema()))
            table = tableAnn.schema()+"."+table;
        this.table = table.toLowerCase(); //  nama table di set lower case agar membedakan dengan syntak SQL
        StringBuilder strField=new StringBuilder();
        StringBuilder strInsert=new StringBuilder();
        StringBuilder strFieldAuto = new StringBuilder();
        StringBuilder strInsertAuto = new StringBuilder();
        StringBuilder strUpdate=new StringBuilder();
        StringBuilder strWhere=new StringBuilder();
        Field[] listField = clazz.getDeclaredFields();
        if(clazz.getSuperclass() != null && clazz.getSuperclass().getAnnotation(MappedSuperclass.class) != null) {
            listField = ArrayUtils.addAll(listField, clazz.getSuperclass().getDeclaredFields());
        }
        List<Field> primaryKey = new ArrayList<>();
        this.primaryKeyNames=new ArrayList();
        String nextSequenceFunction = null;
        String[] generatedKey = null;
        for (Field field : listField) {
            //tambahkan kecuali yang transient atau static modifier (10)
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()) || field.getAnnotation(Transient.class) != null)
                continue;
            strField.append(!strField.isEmpty() ? ",":"").append(field.getName());
            strInsert.append(!strInsert.isEmpty() ? ",":"").append(":").append(field.getName());
            //Check apakah ini PrimaryKey
            if (field.getAnnotation(Id.class) != null) {
                if (field.getType().isPrimitive())
                    Logger.error("PrimaryKey column should not be primitive, class: %s, field: %s", clazz.getName(), field.getName());
                primaryKey.add(field);
                primaryKeyNames.add(field.getName());
                Id ann = field.getAnnotation(Id.class);
                if (!ann.sequence().isEmpty()) { // jika memakai sequence , definisikan pakai function sequence ny
                    String sequenceName = !StringUtils.isEmpty(ann.schema()) ?ann.schema()+"."+ann.sequence() : ann.sequence();
                    nextSequenceFunction = ann.function() + "('"+sequenceName+"')";
                }
                if(ann.generated())
                    generatedKey = new String[]{field.getName()};
                strWhere.append(!strWhere.isEmpty()?" AND ":"").append(field.getName()).append("=:").append(field.getName());
            } else {
                strUpdate.append(!strUpdate.isEmpty()?",":"").append(field.getName()).append("=:").append(field.getName());
                strFieldAuto.append(!strFieldAuto.isEmpty() ? ",":"").append(field.getName());
                strInsertAuto.append(!strInsertAuto.isEmpty() ? ",":"").append(":").append(field.getName());
            }
            if (field.getAnnotation(play.db.jdbc.JsonType.class) != null) {
                strInsert.append("::JSON");
                strUpdate.append("::JSON");
            }
            if (field.getAnnotation(play.db.jdbc.JsonBinaryType.class) != null) {
                strInsert.append("::JSONB");
                strUpdate.append("::JSONB");
            }
        }
        this.nextSequenceFunction = nextSequenceFunction;
        this.generatedKey = generatedKey;
        String id_count = "*";
        if(primaryKey.isEmpty()) {
            Logger.error("Error in class: " + clazz.getName() + " No @play.db.jdbc.Id column was specified");
        } else {
            id_count = primaryKey.get(0).getName();
        }
        SQL_SELECT = "SELECT "+strField+" FROM "+table;
        SQL_SELECT_ID = "SELECT "+strField+" FROM "+table+" WHERE "+strWhere;
        SQL_DELETE_ID = "DELETE FROM "+table+" WHERE "+strWhere;
        SQL_DELETE = "DELETE FROM "+table;
        SQL_COUNT_ID = "SELECT COUNT("+id_count+") FROM "+table;
        SQL_INSERT = "INSERT INTO "+table+"("+strField+") VALUES ("+strInsert+")";
        SQL_UPDATE = "UPDATE "+table+" SET "+strUpdate+" WHERE "+strWhere;
        SQL_INSERT_AUTO = "INSERT INTO "+table+"("+strFieldAuto+") VALUES ("+strInsertAuto+")";
    }

    public void delete(T obj) {
        obj.preDelete();
        Query.bindUpdate(QueryBuilder.create(SQL_DELETE_ID).using(dbname), obj);
    }

    public void deleteAll() {
        Query.update(QueryBuilder.create(SQL_DELETE).using(dbname));
    }

    public void delete(String sql, Object... params) {
        delete(QueryBuilder.create(sql, params));
    }

    public void delete(QueryBuilder builder) {
        QueryBuilder filter = QueryBuilder.create(SQL_DELETE).using(dbname);
        if(builder != null && !builder.isEmpty())
            filter.append("WHERE").append(builder);
        Query.update(filter);
    }

    public void save(T obj) {
        if(primaryKeyNames == null || primaryKeyNames.isEmpty())
            return;
        try {
            String query = SQL_INSERT;
            obj.prePersist();
            boolean insert = false;
            for (String o : primaryKeyNames) {
                Field field = clazz.getField(o);
                field.setAccessible(true);
                Object objPk = field.get(obj);
                if (Objects.isNull(objPk)) {
                    insert = true;
                    if(!Utils.isEmpty(nextSequenceFunction)) {
                        Object key = Query.findObject("SELECT " + nextSequenceFunction, field.getType());
                        field.set(obj, key);
                    } else if(Objects.isNull(objPk) && !Utils.isEmpty(generatedKey)) {
                        query = SQL_INSERT_AUTO;
                    }
                }
                else if (Query.findObject("SELECT "+o+ " FROM " + table + " WHERE " + o + "=?", field.getType(), objPk) == null) {
                    insert = true;
                }
            }
            Query q = insert ? QueryBuilder.create(query).createQuery(generatedKey) : QueryBuilder.create(SQL_UPDATE).createQuery();
            q.bind(obj).executeUpdate();
            if(!Utils.isEmpty(generatedKey)) {
                for (String o : generatedKey) {
                    Field field = clazz.getField(o);
                    Object key = q.getKey(field.getType());
                    Object objfield = field.get(obj);
                    if (Objects.isNull(objfield) && key != null) {
                        field.set(obj, key);
                    }
                }
            }
            obj.postPersist();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public T findById(Object id) {
        return QueryBuilder.create(SQL_SELECT_ID).createQuery()
                .setParameter(primaryKeyNames.get(0), id).executeAndFetchFirst(clazz);
    }

    public List<T> findAll() {
        return Query.findList(QueryBuilder.create(SQL_SELECT).using(dbname), clazz);
    }

    public T findObject(String sql, Object... params) {
        return findObject(QueryBuilder.create(sql, params));
    }

    public T findObject(QueryBuilder builder) {
        QueryBuilder filter = QueryBuilder.create(SQL_SELECT).using(dbname);
        if(builder != null && !builder.isEmpty())
            filter.append("WHERE").append(builder);
        return Query.findObject(filter, clazz);
    }

    public List<T> findList(String sql, Object... params) {
        return findList(QueryBuilder.create(sql, params));
    }

    public List<T> findList(QueryBuilder builder) {
        QueryBuilder filter = QueryBuilder.create(SQL_SELECT).using(dbname);
        if(builder != null && !builder.isEmpty())
            filter.append("WHERE").append(builder);
        return Query.findList(filter, clazz);
    }

    public long count() {
        QueryBuilder filter = QueryBuilder.create(SQL_COUNT_ID).using(dbname);
        return Query.count(filter, Long.class);
    }

    public long count(String sql, Object... params) {
        return count(QueryBuilder.create(sql, params));
    }

    public long count(QueryBuilder builder) {
        QueryBuilder filter = QueryBuilder.create(SQL_COUNT_ID).using(dbname);
        if(builder != null && !builder.isEmpty())
            filter.append("WHERE").append(builder);
        return Query.count(filter, Long.class);
    }

    public void saveAll(List<T> params) {
        if(params == null || params.isEmpty() || primaryKeyNames == null || primaryKeyNames.isEmpty())
            return;
        try {
            String query = SQL_INSERT;
            Class type = null;
            List<T> paramsInsert = new ArrayList<>();
            List<T> paramsUpdate = new ArrayList<>();
            for (T obj : params) {
                obj.prePersist();
                for (String o : primaryKeyNames) {
                    Field field = clazz.getField(o);
                    type = field.getType();
                    Object objpk = field.get(obj);
                    if (Objects.isNull(objpk)) {
                        if (!Utils.isEmpty(nextSequenceFunction)) {
                            Object key = Query.findObject("SELECT " + nextSequenceFunction, field.getType());
                            field.set(obj, key);
                        } else if(!Utils.isEmpty(generatedKey)) {
                            query = SQL_INSERT_AUTO;
                        }
                        paramsInsert.add(obj);
                    }
                    else if (Query.findObject("SELECT "+o+ " FROM " + table + " WHERE " + o + "=?", field.getType(), objpk) == null) {
                        paramsInsert.add(obj);
                    } else {
                        paramsUpdate.add(obj);
                    }
                }
            }
            if(!Utils.isEmpty(paramsInsert)) {
                Query q = QueryBuilder.create(query).createQuery(generatedKey);
                for (T obj : paramsInsert) {
                    q.bind(obj).addToBatch();
                }
                q.executeBatch();
                List keys = q.getKeys(type);
                // after execute
                int i = 0;
                for (T obj : params) {
                    if(!Utils.isEmpty(generatedKey)) {
                        for(String o: generatedKey) {
                            Field field = clazz.getField(o);
                            Object objpk = field.get(obj);
                            if (Objects.isNull(objpk) && keys.get(i) != null) {
                                field.setAccessible(true);
                                field.set(obj, keys.get(i));
                            }
                        }
                    }
                    obj.postPersist();
                    i++;
                }
            }
            if(!Utils.isEmpty(paramsUpdate)) {
                Query.bindUpdateAll(SQL_UPDATE, paramsUpdate);
                for (T obj : params) {
                    obj.postPersist();
                }
            }
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }


    public void saveAll(T... params) {
        saveAll(Arrays.asList(params));
    }
}
