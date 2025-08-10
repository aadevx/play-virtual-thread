package play.db.jdbc.reverse;

import play.db.DB;
import play.db.jdbc.BaseTable;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class JFromSqlGenerator {

	private final Set<Class<? extends BaseTable>> setOfBaseTables;
	private final Map<String, TableDefinition> mapOfTableDefinition= new HashMap<>();
	public JFromSqlGenerator(Set<Class<? extends BaseTable>> setOfBaseTables)
	{
		this.setOfBaseTables=setOfBaseTables;
	}
	
	public void generateJavaClass()
	{
		for(Class<? extends BaseTable> baseTableClass: setOfBaseTables)
			compareColumnAndField(baseTableClass);
	
		
		for(TableDefinition def: mapOfTableDefinition.values())
		{
		
			def.generateJava(mapOfTableDefinition);
		}
		
	}

	/**Bandingkan kolom di DB dengan field pada Java class
	 * 
	 * @param clazz
	 */
	private void compareColumnAndField(Class<? extends BaseTable> clazz) {
		java.sql.Connection conn=DB.getConnection();
	
		/* #1 Baca database dan 'refleksikan' sebagai TableDefinition class

		 */
		
		try {
			DatabaseMetaData md=conn.getMetaData();
			TableDefinition tableDef=new TableDefinition(clazz);
			mapOfTableDefinition.put(tableDef.tableName, tableDef);
			String schema="";
			String catalog=conn.getCatalog();
			//check non-PK columns
			ResultSet rsColumns=md.getColumns(catalog, schema,  tableDef.tableName, null);
			while(rsColumns.next())
			{
				String colDB=rsColumns.getString("COLUMN_NAME").toLowerCase();
				String type=getColumnType(md, tableDef.tableName, colDB);
				tableDef.addColumn(colDB, type, false);
			}
			rsColumns.close();
			
			//check primary key (MUST BE AFTER NON-PK)
			ResultSet rsPrimary=md.getPrimaryKeys(catalog, schema, tableDef.tableName);
			while(rsPrimary.next())
			{
				String colDB=rsPrimary.getString("COLUMN_NAME").toLowerCase();
				String type=getColumnType(md, tableDef.tableName, colDB);
				tableDef.addColumn(colDB, type, true);
			}			
			rsPrimary.close();
			
			/* //TODO Pengecekan FK belum berfungsi di mySQL

			 */
			/*
Contoh Data
	orders(city_id)-> city(city_id)
	orders(country_id)-> country(country_id)
	orders(currency_id)-> currency(currency_id)
	orders(expedition_id)-> expedition(expedition_id)
	orders(buyer_partner_id)-> partner(partner_id)
	orders(seller_partner_id)-> partner(partner_id)
	orders(province_id)-> province(province_id)

			 */
			ResultSet rsFK=md.getImportedKeys(catalog, schema, tableDef.tableName);
			while(rsFK.next())
			{
				String fkColumnName=rsFK.getString("FKCOLUMN_NAME");
				String pkTableName=rsFK.getString("PKTABLE_NAME");
				TableDefinition currentTable=mapOfTableDefinition.get(tableDef.tableName);
				currentTable.addForeignKey(fkColumnName, pkTableName);
			}
			rsFK.close();

			
		} catch (SQLException | ClassNotFoundException e) {
		
			e.printStackTrace();
		}
		
	
	}	
	
	private String getColumnType(DatabaseMetaData md, String tableName, String columnName) throws SQLException
	{
		ResultSet rs=md.getColumns(null, null, tableName, columnName);
		int type=-1;
		if(rs.next())
			type=rs.getInt("DATA_TYPE");
		rs.close();
		return JdbcTypes.getJavaTypes(type);
	}
}
