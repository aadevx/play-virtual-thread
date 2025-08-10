package play.db.jdbc;

import org.apache.commons.lang3.StringUtils;
import play.Logger;
import play.db.DB;
import play.utils.Utils;

import javax.persistence.MappedSuperclass;
import javax.persistence.PersistenceUnit;
import javax.persistence.Transient;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class BaseTableDao<T extends BaseTable> {

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
    private final String duration;
    private final boolean useCacheMerdeka;
    private final String[] generatedKey;

    public static <T extends BaseTable> BaseTableDao getInstance(Class<T> clazz) {
        return new BaseTableDao(clazz);
    }

    BaseTableDao(Class<T> clazz) {
        this.clazz = clazz;
        Table tableAnn =(Table)clazz.getAnnotation(Table.class);
        String table = tableAnn.name();
        String dbname = DB.DEFAULT;
        PersistenceUnit pu = (PersistenceUnit)clazz.getAnnotation(PersistenceUnit.class);
        if (pu != null) {
            dbname = pu.name();
        }
        this.dbname = dbname;
        //apakah ada anotasi @CacheMerdeka
        String duration=null;
        boolean useCacheMerdeka=false;
        if(clazz.getAnnotation(CacheMerdeka.class) != null)
        {
            useCacheMerdeka=true;
            CacheMerdeka cache= clazz.getAnnotation(CacheMerdeka.class);
            duration=cache.duration();
            //class ini harus implement Serializable
            if(!Serializable.class.isAssignableFrom(clazz)){
                throw new RuntimeException("class implement serializable");
            }
        }
        this.useCacheMerdeka = useCacheMerdeka;
        this.duration = duration;
        if(Utils.isEmpty(table)) {
            String[] arrayTable = StringUtils.split(clazz.getName(), ".");
            if(arrayTable.length > 1)
                table=arrayTable[arrayTable.length - 1];// ambil nama class tanpa nama packagenya
            else
                table = clazz.getName();
        }
        if(!Utils.isEmpty(tableAnn.schema()))
            table = tableAnn.schema()+"."+table;
        this.table = table.toLowerCase(); //  nama table di set lower case agar membedakan dengan syntak SQL
        StringBuilder strField=new StringBuilder();
        StringBuilder strInsert=new StringBuilder();
        StringBuilder strFieldAuto = new StringBuilder();
        StringBuilder strInsertAuto = new StringBuilder();
        StringBuilder strUpdate=new StringBuilder();
        StringBuilder strWhere=new StringBuilder();
        List<Field> listField = new ArrayList<>(Arrays.asList(clazz.getDeclaredFields()));
        if(clazz.getSuperclass() != null && clazz.getSuperclass().getAnnotation(MappedSuperclass.class) != null) {
            listField.addAll(Arrays.asList(clazz.getSuperclass().getDeclaredFields()));
        }
        List<Field> primaryKey = new ArrayList<>();
        this.primaryKeyNames=new ArrayList();
        String nextSequenceFunction = null;
        String[] generatedKey = null;
        for (Field field : listField) {
            //tambahkan kecuali yang transient atau static modifier (10)
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()) || field.getAnnotation(Transient.class) != null)
                continue;
            //Check apakah ini PrimaryKey
            append(strField, field.getName(), ",");
            append(strInsert, ":"+field.getName(), ",");
            if (field.getAnnotation(Id.class) != null) {
                if (field.getType().isPrimitive())
                    Logger.error("PrimaryKey column should not be primitive, class: %s, field: %s", clazz.getName(), field.getName());
                primaryKey.add(field);
                primaryKeyNames.add(field.getName());

                Id ann = (Id) field.getAnnotation(Id.class);
                if (!ann.sequence().isEmpty()) { // jika memakai sequence , definisikan pakai function sequence ny
                    String sequenceName = !Utils.isEmpty(ann.schema()) ?ann.schema()+"."+ann.sequence() : ann.sequence();
                    nextSequenceFunction = ann.function() + "('"+sequenceName+"')";
                }
                if(ann.generated())
                    generatedKey = new String[]{field.getName()};
                append(strWhere, field.getName()+"=:"+field.getName(), " AND ");
            } else {
                append(strUpdate, field.getName()+"=:"+field.getName(), ",");
                append(strFieldAuto, field.getName(), ",");
                append(strInsertAuto, ":"+field.getName(), ",");
            }
            if (field.getAnnotation(JsonType.class) != null) {
                strInsert.append("::JSON");
                strUpdate.append("::JSON");
            }
            if (field.getAnnotation(JsonBinaryType.class) != null) {
                strInsert.append("::JSONB");
                strUpdate.append("::JSONB");
            }
        }
        String id_count = "*";
        if(primaryKey.isEmpty()) {
            Logger.error("Error in class: " + clazz.getName() + " No @play.db.jdbc.Id column was specified");
        } else {
            id_count = primaryKey.get(0).getName();
        }
        this.nextSequenceFunction = nextSequenceFunction;
        this.generatedKey = generatedKey;
        SQL_SELECT = "SELECT "+strField+" FROM "+table;
        SQL_SELECT_ID = "SELECT "+strField+" FROM "+table+" WHERE "+strWhere;
        SQL_DELETE_ID = "DELETE FROM "+table+" WHERE "+strWhere;
        SQL_DELETE = "DELETE FROM "+table;
        SQL_COUNT_ID = "SELECT COUNT("+id_count+") FROM "+table;
        SQL_INSERT = "INSERT INTO "+table+"("+strField+") VALUES ("+strInsert+")";
        SQL_UPDATE = "UPDATE "+table+" SET "+strUpdate+" WHERE "+strWhere;
        SQL_INSERT_AUTO = "INSERT INTO "+table+"("+strFieldAuto+") VALUES ("+strInsertAuto+")";
    }

    private void append(StringBuilder sql, String commandAdd, String separator) {
        if(sql.length() > 0)
            sql.append(separator);
        sql.append(commandAdd);
    }

    public String getTableName(){
        return table;
    }

    public String getDbname(){
        return dbname;
    }

    public String getPrimaryKeys(){
        return StringUtils.join(primaryKeyNames, ',');
    }

    public long count() {
        return Query.count(QueryBuilder.create(SQL_COUNT_ID).using(dbname), Long.class);
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

    public List<T> order(String order) {
        return Query.findList(QueryBuilder.create(SQL_SELECT).using(dbname).orderBy(order), clazz);
    }

    public T findById(Object id) {
        if(useCacheMerdeka) {
            T obj = (T)BaseTable.getFromCache(clazz, "findById()", id);
            if(obj == null){
                obj = QueryBuilder.create(SQL_SELECT_ID).createQuery().setParameter(primaryKeyNames.get(0), id).executeAndFetchFirst(clazz);
                BaseTable.addToCache(clazz, "findById()", id, obj, duration);
            }
            return obj;
        }
        return QueryBuilder.create(SQL_SELECT_ID).createQuery().setParameter(primaryKeyNames.get(0), id).executeAndFetchFirst(clazz);
    }

    public List<T> findAll() {
        if(useCacheMerdeka) {
            List<T> list = (List<T>) BaseTable.getFromCache(clazz, "findAll()", null);
            if(list == null) {
                list = Query.findList(QueryBuilder.create(SQL_SELECT).using(dbname), clazz);
                BaseTable.addToCache(clazz, "findAll()", null, list, duration);
            }
            return list;
        }
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

    public void delete(T obj) {
        obj.preDelete();
        BaseTable.removeCacheAfterSaveOrDelete(clazz, primaryKeyNames.get(0));
        Query.bindUpdate(QueryBuilder.create(SQL_DELETE_ID).using(dbname), obj);
    }

    public void deleteAll() {
        clearCache();
        Query.update(QueryBuilder.create(SQL_DELETE).using(dbname));
    }

    public void delete(String sql, Object... params) {
        delete(QueryBuilder.create(sql, params));
    }

    public void delete(QueryBuilder builder) {
        clearCache();
        QueryBuilder filter = QueryBuilder.create(SQL_DELETE).using(dbname);
        if(builder != null && !builder.isEmpty())
            filter.append("WHERE").append(builder);
        Query.update(filter);
    }

    public void clearCache() {
        BaseTable.clearCacheOfClass(clazz);
    }

    private boolean isObjectExisit(T obj) {
        return QueryBuilder.create(SQL_SELECT_ID).createQuery().bind(obj).executeAndFetchFirst(clazz) != null;
    }

    public void save(T obj) {
        if(Utils.isEmpty(primaryKeyNames) || Utils.isEmpty(SQL_INSERT) || Utils.isEmpty(SQL_UPDATE))
            return;
        try {
            String query = SQL_INSERT;
            obj.prePersist();
            boolean insert = false;
            for (String o : primaryKeyNames) {
                Field field = clazz.getField(o);
                field.setAccessible(true);
                Object objpk = field.get(obj);
                if (Objects.isNull(objpk)) {
                    insert = true;
                    if (!Utils.isEmpty(nextSequenceFunction)) {
                        Object key = Query.findObject("SELECT " + nextSequenceFunction, field.getType());
                        field.set(obj, key);
                    } else if(Objects.isNull(objpk) && !Utils.isEmpty(generatedKey)) {
                        query = SQL_INSERT_AUTO;
                    }
                }
            }
            // check again for sure will be insert or update
            if(!insert)
                insert = isObjectExisit(obj) ? false : true;
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
            if(primaryKeyNames.size() == 1){//tambahkan statement untuk clear cache, khusus jika PK ada 1 kolom
                BaseTable.removeCacheAfterSaveOrDelete(obj.getClass(), primaryKeyNames.get(0));
            }
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public void insert(T obj) {
        if(Utils.isEmpty(primaryKeyNames) || Utils.isEmpty(SQL_INSERT))
            return;
        try {
            String query = SQL_INSERT;
            obj.prePersist();
            Object objpk = null;
            for (String o : primaryKeyNames) {
                Field field = clazz.getField(o);
                field.setAccessible(true);
                objpk = field.get(obj);
                if (Objects.isNull(objpk)) {
                    if (!Utils.isEmpty(nextSequenceFunction)) {
                        Object key = Query.findObject("SELECT " + nextSequenceFunction, field.getType());
                        field.set(obj, key);
                    } else if(!Utils.isEmpty(generatedKey)) {
                        query = SQL_INSERT_AUTO;
                    }
                }
            }
            Query q = QueryBuilder.create(query).createQuery(generatedKey);
            q.bind(obj).executeUpdate();
            if(!Utils.isEmpty(generatedKey)) {
               for(String o: generatedKey) {
                   Field field = clazz.getField(o);
                   Object key = q.getKey(field.getType());
                   objpk = field.get(obj);
                   if (Objects.isNull(objpk) && key != null) {
                       field.setAccessible(true);
                       field.set(obj, key);
                   }
               }
            }
            obj.postPersist();
            if(primaryKeyNames.size() == 1){//tambahkan statement untuk clear cache, khusus jika PK ada 1 kolom
                BaseTable.removeCacheAfterSaveOrDelete(obj.getClass(), primaryKeyNames.get(0));
            }
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public void saveAll(List<T> params) {
        if(Utils.isEmpty(primaryKeyNames) || Utils.isEmpty(SQL_UPDATE) || Utils.isEmpty(params))
            return;
        try {
            String query = SQL_INSERT;
            Class type = null;
            List<T> paramsInsert = new ArrayList<>();
            List<T> paramsUpdate = new ArrayList<>();
            for (T obj : params) {
                obj.prePersist();
                boolean insert = false;
                for (String o : primaryKeyNames) {
                    Field field = clazz.getField(o);
                    type = field.getType();
                    Object objpk = field.get(obj);
                    if (Objects.isNull(objpk)) {
                        insert = true;
                        if (!Utils.isEmpty(nextSequenceFunction)) {
                            Object key = Query.findObject("SELECT " + nextSequenceFunction, field.getType());
                            field.set(obj, key);
                        } else if(!Utils.isEmpty(generatedKey)) {
                            query = SQL_INSERT_AUTO;
                        }
                    }
                }
                if(!insert)
                    insert = isObjectExisit(obj) ? false : true;
                if(insert)
                    paramsInsert.add(obj);
                else
                    paramsUpdate.add(obj);
            }
            if(!Utils.isEmpty(paramsInsert)) {
                List keys = Query.bindUpdateAll(query, paramsInsert, generatedKey, type);
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


    public void saveAll(Object[] params) {
        saveAll(Arrays.asList((T[])params));
    }

    public void insertAll(List<T> params) {
        if(Utils.isEmpty(primaryKeyNames) || Utils.isEmpty(SQL_INSERT) || Utils.isEmpty(params))
            return;
        try {
            String query = SQL_INSERT;
            Class type = null;
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
                    }
                }
            }
            List keys = Query.bindUpdateAll(query, params, generatedKey, type);
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
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public void insertAll(Object[] params) {
        insertAll(Arrays.asList((T[])params));
    }
}
