package play.db.jdbc;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import play.Logger;
import play.Play;
import play.cache.Cache;
import play.mvc.Scope;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;


/**Anda bisa override prePersist() untuk mengeksekusi code sebelum di-save()
 *
 * @author Mr. Andik
 *
 */
public abstract class BaseTable implements Serializable {

	private static final String EXCEPTION = "Please extends this class from play.db.jdbc.BaseTable, or error occurs in play.db.jdbc.JdbcEnhancer";

	private static final boolean disableCacheMerdeka=Boolean.getBoolean(Play.configuration.getProperty("cacheMerdeka.disabled", "false"));

	protected Class<? extends BaseTable> getRealClass() {
		return getClass();
	}//menyimpan informasi tentang class yg extend

	private static String getKeyOfModel(Class<? extends BaseTable> clz)
	{
		return CacheMerdeka.class.getName() +  "$" + clz.getName();
	}

	protected String[] pk() { // kolom primary key
		return  null;
	}
	/**Menambahkan instance dari BaseTable atau List<BaseTable> ke Cache
	 */
	protected static void addToCache(Class<? extends BaseTable> clz, String methodName, Object id, Object value, String expiration)
	{
		//CacheMerdeka can be disabled if application.conf contains: cacheMerdeka.disabled=true
		if(disableCacheMerdeka)
			return;

		String key=String.format("%s.%s#%s", clz.getName(), methodName, id);
		Cache.set(key, value, expiration);

		@SuppressWarnings("unchecked")
		/*cacheTracker menyimpan key-key dari model yg di-cache.
		 *caheTraker's key adalah parameter id. Artinya untuk id tertentu bisa ada beberapa
		 *cache karena berbeda methodName
		 *
		 */
				Map<Object, Set<String>> cacheTracker=(Map<Object, Set<String>>) Cache.get(getKeyOfModel(clz));
		Set<String> listOfCacheKey;
		if(cacheTracker==null)
		{
			cacheTracker=new HashMap<>();
			listOfCacheKey= new HashSet<>();
			cacheTracker.put(id,listOfCacheKey);
			Cache.set(getKeyOfModel(clz), cacheTracker, "12h");
		}
		else
		{
			listOfCacheKey=cacheTracker.get(id);
			if(listOfCacheKey==null)
				listOfCacheKey= new HashSet<>();
			cacheTracker.put(id,listOfCacheKey);
		}
		listOfCacheKey.add(key);
	}

	protected static Object getFromCache(Class<? extends BaseTable> clz, String methodName, Object id)
	{
		String key=String.format("%s.%s#%s", clz.getName(), methodName, id);
		return Cache.get(key);
	}

	/**Remove Cache with specific ID for a class.
	 * Ini dipanggil pada
	 * 1. save();
	 * 2. delete();
	 * @param clz
	 * @param id, if null means remove all ID for a Class
	 */
	protected static  void removeCacheAfterSaveOrDelete(Class<? extends BaseTable> clz, Object id)
	{
		//Jika ada Object yg di-save maka remove semua Cache Value yg terkait dengan  id ini
		@SuppressWarnings("unchecked")
		Map<Object, Set<String>> cacheTracker=(Map<Object, Set<String>>) Cache.get(getKeyOfModel(clz));
		if(cacheTracker!=null)
		{
			Set<String>  listOfCacheKey=cacheTracker.get(id);
			if(listOfCacheKey!=null)
				for(String cacheKey: listOfCacheKey)
					Cache.safeDelete(cacheKey);
			cacheTracker.remove(id);
		}
	}

	/**Remove all instances of clz in Cache
	 * DIpanggil oleh static method BaseTable.delete() dan variannya
	 * @param clz
	 */
	public static void clearCacheOfClass(Class<? extends BaseTable> clz)
	{
		@SuppressWarnings("unchecked")
		//dapatkan cacheTracker dari clz
		Map<Object, Set<String>> cacheTracker=(Map<Object, Set<String>>) Cache.get(getKeyOfModel(clz));
		if(cacheTracker!=null)
		{
			//untuk setiap value di cacheTracker, lakukan penghapusan
			for(Set<String>  listOfCacheKey:cacheTracker.values())
			{
				for(String cacheKey: listOfCacheKey)
					Cache.safeDelete(cacheKey);
			}
			Cache.safeDelete(getKeyOfModel(clz));
		}
	}

	/**Digunakan untuk clear Cache atas semua instance dari Model ini.
	 *
	 */
	public void clearCache()
	{

	}

	public void save() {

	}

	// persist data to db with check sequence and pk column value exist
	public void insert() {

	}

	public static long count() {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static long count(String sql) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static long count(String sql, Object... params) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static long count(QueryBuilder builder) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static <T> List<T> order(String order) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static <T> List<T> findAll() {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	/**findById untuk BaseTable tidak diijinkan jika PK lebih dari 2 kolom.
	 * @param id
	 * @return
	 */
	public static <T extends BaseTable> T findById(Object id) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public void delete() {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	// bulk delete
	public static void deleteAll() {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static void delete(String sql, Object... params) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static void delete(QueryBuilder builder) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static void delete(String sql) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	// set anything before save
	protected void prePersist() {

	}
	// set load after created;
	protected void postLoad() {

	}

	// do something before delete;
	protected void preDelete() {

	}

	// refresh set object model , setup postLoad
	public void refresh() {
		postLoad();
	}

	// set anything after save
	protected void postPersist() {

	}

	public static <T extends BaseTable> void saveAll(List<T> params) {
		throw new UnsupportedOperationException(EXCEPTION);
	}


	public static void saveAll(Object[] params) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static <T extends BaseTable> void insertAll(List<T> params) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static void insertAll(Object[] params) {
		throw new UnsupportedOperationException(EXCEPTION);
	}


	/**Copy properties from other object
	 * method ini sangat bermanfaat pada halaman yang berisi edit
	 *
	 * void editPegawai(Pegawai peg)
	 * {
	 *      Pegawai peg1=Pegawai.findById(peg.peg_id);
	 *      peg1.copyFrom(peg, "nama,alamat"); //HTML hanya mengubah 2 field ini saja, jadi sisanya ambil dari DB
	 *      peg1.save();
	 * }
	 *
	 * @param source object to copy from
	 * @param properties property names: comma separated
	 * @throws Exception
	 */
	public void copyProperties(Object source, String properties) throws Exception {
		Class<? extends BaseTable> cls=getClass();
		try
		{
			if(properties!=null)
				for(String fieldName: properties.split(","))
				{
					Field field=cls.getField(fieldName.trim());
					field.set(this, field.get(source));
				}
		}
		catch(Exception e)
		{
			throw new Exception(e);
		}
	}

	/**Save hanya field/properties tertentu. Hanya untuk UPDATE, tidak bisa INSERT;
	 * artinya PK harus sudah ada di DB.
	 *  Mengapa?
	 * Karena kalau bisa INSERT, takutnya ada FIELD yg mandatory tapi tidak di-Insert jadinya error
	 * @param properties comma-separated properties to save
	 *
	 * Contoh pengguaan
	 *
	rkn.rkn_nama="rkn_xxx";
	rkn.rkn_alamat="rkn_yyy";
	rkn.rkn_npwp="rkn_zzz";
	rkn.saveProperties("rkn_nama,rkn_alamat,rkn_npwp");
	 * @throws Throwable
	 *
	 */
	public void saveProperties(String properties) throws Throwable {
		String[] ary =getPrimaryKeys().split(",");
		String nonPrimaryKeys=getNonPrimaryKeys() + ",";
		try
		{
			StringBuilder str=new StringBuilder("");
			int i=0;
			//UPDATE xxx=xxx
			if(properties!=null)
				for(String fieldName: properties.split(","))
				{
					fieldName=fieldName.trim();
					if(!nonPrimaryKeys.contains(fieldName + ","))
						throw new RuntimeException("Property tidak ditemukan: " + getClass().getName() + "." + fieldName);
					appendUpdate(str, fieldName, i);
					i++;
				}
			//WHERE
			i=0;
			for(String pk: ary)
			{
				appendWhere(str, pk, i);
				i++;
			}
			QueryBuilder query = QueryBuilder.create(str.toString());
			query.using(getDbname());
			Query.bindUpdate(query, this);
		}
		catch(Exception e)
		{
			throw new Throwable(e);
		}

	}

	/**
	 * Berlawanan dengan saveProperties, yaitu menyimpan properties kecuali
	 * yang disebutkan pada parameter
	 *
	 * @param propertiesExcept Daftar properties yg TIDAK disimpan
	 */
	public void savePropertiesExcept(String propertiesExcept) {
		String[] ary =getNonPrimaryKeys().split(",");

		try
		{
			StringBuilder str=new StringBuilder("");
			int i=0;
			//UPDATE xxx=xxx
			if(propertiesExcept!=null)
			{
				propertiesExcept=propertiesExcept+",";
				String properties=getNonPrimaryKeys();
				for(String fieldName: properties.split(","))
				{
					fieldName=fieldName.trim();
					if(propertiesExcept.contains(fieldName+","))
						continue;
					appendUpdate(str, fieldName, i);
					i++;
				}
			}
			//WHERE
			i=0;
			ary=getPrimaryKeys().split(",");
			for(String pk: ary)
			{
				appendWhere(str, pk, i);
				i++;
			}
			QueryBuilder query = QueryBuilder.create(str.toString());
			query.using(getDbname());
			Query.bindUpdate(query, this);
		}
		catch(Exception e)
		{
			throw new RuntimeException(e);
		}

	}

	private void appendUpdate(StringBuilder str, String fieldName, int i)
	{

		if(i==0)
			str.append("UPDATE ").append(getTableName())
					.append(" SET ").append(fieldName).append("=:").append(fieldName);
		else
			str.append(" \n,").append(fieldName).append("=:").append(fieldName);
	}


	private void appendWhere(StringBuilder str, String pk, int i)
	{
		if(i==0)
			str.append(" WHERE ").append(pk).append("=:").append(pk);
		else
			str.append(" \nAND ").append(pk).append("=:").append(pk);
	}

	public String getTableName()
	{
		return null;
	}

	public String getDbname()
	{
		return null;
	}

	public String getPrimaryKeys()
	{
		return null;
	}

	public String getNextSequenceName() {
		return null;
	}


	public String getNonPrimaryKeys()
	{
		return null;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}

	// for submission form, get from flash
	public void flash() {
		Scope.Flash flash = Scope.Flash.current();
		if(flash != null) {
			for(Field field : getClass().getDeclaredFields()) {
				if(Modifier.isFinal(field.getModifiers()) || Modifier.isPrivate(field.getModifiers()) || Modifier.isProtected(field.getModifiers()))
					continue;
				String value = flash.get(field.getName());
				if(!StringUtils.isEmpty(value)) {
					try {
						field.setAccessible(true);
						if(field.getType().equals(Integer.class) || field.getType().equals(int.class))
							field.set(this, Integer.parseInt(value));
						else if(field.getType().equals(Long.class) || field.getType().equals(long.class))
							field.set(this, Long.parseLong(value));
						else if(field.getType().equals(Boolean.class) || field.getType().equals(boolean.class))
							field.set(this, Boolean.parseBoolean(value));
						else if(field.getType().equals(Double.class) || field.getType().equals(double.class))
							field.set(this, Double.parseDouble(value));
						else if(field.getType().equals(Date.class))
							field.set(this, new Date(Long.parseLong(value)));
						else
							field.set(this, value);
					} catch (IllegalAccessException e) {
						Logger.error(e, "[BaseTable.flash()] %s", e.getMessage());
					}
				}
			}
		}
	}

	public void paramFlash() {
		paramFlash(new String[]{});
	}

	// for submission form, set anything to flash
	public void paramFlash(String... colomnExcept) {
		Scope.Flash flash = Scope.Flash.current();
		if(flash != null) {
			for(Field field : getClass().getDeclaredFields()) {
				if(Modifier.isFinal(field.getModifiers()) || Modifier.isPrivate(field.getModifiers()) || Modifier.isProtected(field.getModifiers()))
					continue;
				if(!ArrayUtils.isEmpty(colomnExcept) && ArrayUtils.contains(colomnExcept, field.getName()))
					continue;
				try {
					field.setAccessible(true);
					Object value = field.get(this);
					if(Objects.nonNull(value)) {
						if(field.getType().equals(Date.class))
							flash.put(field.getName(), ((Date)value).getTime());
						else
							flash.put(field.getName(), value);
					}
				} catch (IllegalAccessException e) {
					Logger.error(e, "[BaseTable.paramFlash()] %s", e.getMessage());
				}
			}
		}
	}

	public static <T extends BaseTable> T findObject(String sql) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static <T extends BaseTable> T findObject(String sql, Object... params) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static <T extends BaseTable> T findObject(QueryBuilder builder) {
		throw new UnsupportedOperationException(EXCEPTION);
	}


	public static <T extends BaseTable> List<T> findList(String sql) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static <T extends BaseTable> List<T> findList(String sql, Object... params) {
		throw new UnsupportedOperationException(EXCEPTION);
	}

	public static <T extends BaseTable> List<T> findList(QueryBuilder builder) {
		throw new UnsupportedOperationException(EXCEPTION);
	}
}
