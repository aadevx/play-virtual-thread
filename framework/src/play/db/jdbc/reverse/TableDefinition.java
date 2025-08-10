package play.db.jdbc.reverse;

import org.apache.commons.lang3.StringUtils;
import play.Logger;
import play.db.jdbc.BaseTable;
import play.db.jdbc.Table;
import play.libs.IO;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

/**Class untuk utilitas baseTable
 * 
 * @author Mr. Andik
 *
 */
public class TableDefinition {

	
	private final Map<String, ColumnDefinition> mapColumns= new HashMap<>();
	public final Class<? extends BaseTable> clazz;

	public String tableName;
	
	public final Set<ColumnDefinition> primaryKeys = new HashSet<>();
	
	private final String applicationPath;
	
	
	public TableDefinition(Class<? extends BaseTable> clazz) throws ClassNotFoundException
	{
		this.clazz=clazz;
		Table table= clazz.getAnnotation(Table.class);
		tableName=table.name();
		if(StringUtils.isEmpty(tableName))
		{
			tableName=clazz.getName().toLowerCase();
			int pos=tableName.lastIndexOf('.')+1;
			if(pos>0)
				tableName=tableName.substring(pos, tableName.length());
		}		
		applicationPath=System.getProperty("application.path");
	}
	
	public void addColumn(String columnName, String type, boolean isPK) {
		columnName=columnName.toLowerCase();
		ColumnDefinition col= mapColumns.get(columnName);
		col=new ColumnDefinition(columnName, type, isPK);
		mapColumns.put(columnName, col);
		if(isPK)
			primaryKeys.add(col);
	}
	
	/**Dapatkan java source 
	 * @param mapOfTableDefinition 
	 * 
	 */
	
	public void generateJava(Map<String, TableDefinition> mapOfTableDefinition) {
		StringBuilder str=new StringBuilder();
		StringBuilder strGetter=new StringBuilder(); //untuk get()...
		//ubah sebagai sorted set supaya urut
		SortedSet<ColumnDefinition> sortedColl= new TreeSet<>(mapColumns.values());
		
		for(ColumnDefinition colDef: sortedColl)
		{
			try {
				if(clazz.getField(colDef.name)!=null){
					
				}
			} catch (NoSuchFieldException | SecurityException e) {
				if(colDef.isPK)
					str.append("\n    @play.db.jdbc.Id");
				str.append("\n    public ").append(colDef.type).append(' ').append(colDef.name).append(";\n");
				//Dapatkan foreign Key
				if(colDef.reference!=null)
				{
					TableDefinition refTableDefinition= mapOfTableDefinition.get(colDef.reference);
					if(refTableDefinition!=null)
						if(refTableDefinition.primaryKeys.size()==1) //hanya untuk PK yang satu kolom
						{
							String methodName=StringUtils.capitalize(colDef.name);
							String reffClassName=refTableDefinition.clazz.getName();
							String pk=refTableDefinition.primaryKeys.iterator().next().name;
							strGetter.append("\n\n    public ").append(reffClassName).append(" get").append(methodName).append("()");
							strGetter.append("\n    {");
							strGetter.append("\n         return ").append(reffClassName).append(".findById(").append(pk).append(");");
							strGetter.append("\n    }\n");
						}
				}
			}
		}
		
		str.append(strGetter);

		String asString = str.toString();
		
		String fileName=applicationPath+"/app/" + clazz.getName().replace('.', '/') + ".txt";
		File file=new File(fileName);

		if(file.exists())
			file.delete();
		if(StringUtils.isNotEmpty(asString)){
			Logger.debug("Reverse Engineer Java from Database Structure: %s, size: %,d", fileName, asString.length());
			IO.write(asString.getBytes(Charset.defaultCharset()), file);
		}
		
	}

	public String toString()
	{
		return String.format("[%s] %s", clazz, tableName);
	}

	public void addForeignKey(String fkColumnName, String pkTableName) {
		mapColumns.get(fkColumnName).reference=pkTableName;//name of referenced table
	}
}
