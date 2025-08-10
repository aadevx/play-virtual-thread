package play.db.jdbc.reverse;

public class ColumnDefinition implements Comparable<ColumnDefinition>{
	
	public ColumnDefinition(String name, String type, boolean isPK) {
		super();
		this.name = name;
		this.type = type;
		this.isPK = isPK;
	}

	public final String name;
	public final String type;
	
	//apakah PK
	public final boolean isPK;
	
	//reference ke kelas lain;
	public String reference;

	@Override
	public int compareTo(ColumnDefinition o) {
		if(o.isPK && !isPK)
			return 1;
		if(isPK && !o.isPK)
			return -1;
		return name.compareTo(o.name);
	}
	
	public String toString()
	{
		return String.format("%s %s(%s) %s", type, name, isPK, reference==null ? "" : "=>" + reference);
	}
}
