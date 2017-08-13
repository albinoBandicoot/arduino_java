public abstract class Symtable {
	public Symtable parent;
	protected int id;
	public Klass context;

	public abstract void add (DeclTree d) throws CompilerException ;
}
