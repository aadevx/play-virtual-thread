package play.db.jdbc;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.classloading.enhancers.Enhancer;

/**
 * enchancer model BaseTable
 * Hanya model dari BaseTable yang di Enhance
 * @author Arief
 *
 */
public class JdbcEnhancer extends Enhancer {
	@Override
	public void enhanceThisClass(ApplicationClass applicationClass) throws Exception {
		CtClass ctClass = makeClass(applicationClass);
		if (!ctClass.subtypeOf(classPool.get(BaseTable.class.getName()))) {
			return;
		}
		if (!hasAnnotation(ctClass, Table.class.getName())) {
			return;
		}
		String className = ctClass.getName();

		CtField field = CtField.make("private static final play.db.jdbc.BaseTableDao dao = play.db.DB.model("+className+".class);", ctClass);
		ctClass.addField(field);
		//buat method
		CtMethod method = CtMethod.make("public static long count() { return dao.count();}", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static long count(String sql) { return dao.count(sql, null); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static long count(String sql, java.lang.Object[] params) { return dao.count(sql, params); }", ctClass);
		method.setModifiers(method.getModifiers() | Modifier.VARARGS);
		ctClass.addMethod(method);
		method = CtMethod.make("public static long count(play.db.jdbc.QueryBuilder builder) { return dao.count(builder); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static java.util.List order(String order){ return dao.order(order); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public String getPrimaryKeys(){ return dao.getPrimaryKeys(); }", ctClass);
		ctClass.addMethod(method);

//		//method getNonPrimaryKeys()
//		String nonPrimaryKeysStr=StringUtils.join(nonPrimaryKeyNames, ',');
//		script = new StringBuilder("public String getNonPrimaryKeys(){ return \"") .append(nonPrimaryKeysStr).append("\";} ");
//		method = CtMethod.make(script.toString(), ctClass);
//		ctClass.addMethod(method);

		method = CtMethod.make("public String getTableName(){ return dao.getTableName(); } ", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public String getDbname(){ return dao.getDbname(); } ", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static java.util.List findAll() { return dao.findAll(); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static play.db.jdbc.BaseTable findById(Object id) { return dao.findById(id); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static play.db.jdbc.BaseTable findObject(String sql, java.lang.Object[] params) { return dao.findObject(sql, params);} ", ctClass);
		method.setModifiers(method.getModifiers() | Modifier.VARARGS);
		ctClass.addMethod(method);
		method = CtMethod.make("public static play.db.jdbc.BaseTable findObject(String sql) { return dao.findObject(sql, null); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static play.db.jdbc.BaseTable findObject(play.db.jdbc.QueryBuilder builder) { return dao.findObject(builder); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static java.util.List findList(String sql, java.lang.Object[] params) { return dao.findList(sql, params); }", ctClass);
		method.setModifiers(method.getModifiers() | Modifier.VARARGS);
		ctClass.addMethod(method);
		method = CtMethod.make("public static java.util.List findList(String sql) { return dao.findList(sql, null); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static java.util.List findList(play.db.jdbc.QueryBuilder builder) { return dao.findList(builder); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static void deleteAll() { dao.deleteAll(); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static void delete(String sql, java.lang.Object[] params) { dao.delete(sql, params); }", ctClass);
		method.setModifiers(method.getModifiers() | Modifier.VARARGS);
		ctClass.addMethod(method);
		method = CtMethod.make("public static void delete(play.db.jdbc.QueryBuilder builder) { return dao.delete(builder); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static void delete(String sql) { return dao.delete(sql, null); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public void delete() { return dao.delete(this); }", ctClass);
		ctClass.addMethod(method);
//		method = CtMethod.make("public String getNextSequenceName() { return dao.getNextSequenceName(); }", ctClass);
//		ctClass.addMethod(method);
		method = CtMethod.make("public void save() { return dao.save(this); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public void insert() { return dao.insert(this); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static void saveAll(java.util.List params) { dao.saveAll(params); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static void saveAll(java.lang.Object[] params) { dao.saveAll(params); }", ctClass);
		method.setModifiers(method.getModifiers() | Modifier.VARARGS);
		ctClass.addMethod(method);
		method = CtMethod.make("public static void insertAll(java.util.List params) {  dao.insertAll(params); }", ctClass);
		ctClass.addMethod(method);
		method = CtMethod.make("public static void insertAll(java.lang.Object[] params) { dao.insertAll(params);  }", ctClass);
		method.setModifiers(method.getModifiers() | Modifier.VARARGS);
		ctClass.addMethod(method);
		//tambahan method untuk clearCache
		method = CtMethod.make("public void clearCache() { dao.clearCache();}", ctClass);
		ctClass.addMethod(method);
		//tulis model ke bytecode
		applicationClass.enhancedByteCode = ctClass.toBytecode();
		ctClass.defrost();
	}
}
