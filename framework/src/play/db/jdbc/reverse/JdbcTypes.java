package play.db.jdbc.reverse;

public class JdbcTypes {

	//references: http://www.java2s.com/Tutorial/Java/0340__Database/MappingBetweenJavatoJDBCSQLTypes.htm
	public static String getJavaTypes(int jdbcType)
	{
		if(jdbcType==java.sql.Types.CHAR) return "java.lang.String";
		if(jdbcType==java.sql.Types.VARCHAR) return "java.lang.String";
		if(jdbcType==java.sql.Types.LONGVARCHAR) return "java.lang.String";
		if(jdbcType==java.sql.Types.NUMERIC) return "java.lang.Double";
		if(jdbcType==java.sql.Types.DECIMAL) return "java.lang.Double";
		if(jdbcType==java.sql.Types.BIT) return "boolean";
		if(jdbcType==java.sql.Types.TINYINT) return "byte";
		if(jdbcType==java.sql.Types.SMALLINT) return "short"; 
		if(jdbcType==java.sql.Types.INTEGER) return "int";
		if(jdbcType==java.sql.Types.BIGINT) return "long";
		if(jdbcType==java.sql.Types.REAL) return "float";
		if(jdbcType==java.sql.Types.FLOAT) return "double";
		if(jdbcType==java.sql.Types.DOUBLE) return "double";
		if(jdbcType==java.sql.Types.BINARY) return "byte[]";
		if(jdbcType==java.sql.Types.VARBINARY) return "byte[]";
		if(jdbcType==java.sql.Types.LONGVARBINARY) return "byte[]";
		if(jdbcType==java.sql.Types.DATE) return "java.sql.Date";
		if(jdbcType==java.sql.Types.TIME) return "java.sql.Time";
		if(jdbcType==java.sql.Types.TIMESTAMP) return "java.sql.Timestamp";
		if(jdbcType==java.sql.Types.CLOB) return "java.sql.Clob";
		if(jdbcType==java.sql.Types.BLOB) return "java.sql.Blob";
		if(jdbcType==java.sql.Types.ARRAY) return "java.sql.Array";
		if(jdbcType==java.sql.Types.DISTINCT) return "Mapping of underlying type";
		if(jdbcType==java.sql.Types.STRUCT) return "java.sql.Struct";
		if(jdbcType==java.sql.Types.REF) return "java.sql.Ref";
		return null;

	}
	
}
