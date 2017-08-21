import java.util.*;
public abstract class Tree {

	public Treetype type;
	public Tree next;
	public Tree parent;
	public DeclTree enclosingFunc;
	public Marker mark;
	public int id;

	private static int ID = 0;

	public class SemanticException extends CompilerException {
		public SemanticException (String mesg) {
			super("[SEM] in " + type + " node at " + mark + ": " + mesg);
		}
	}

	public Tree (Treetype t) {
		type = t;
		mark = Parser.getMarker();
		id = ID++;
	}

	public abstract String repr (int depth);
	public abstract String toString ();

	public String repr () {
		return repr(0);
	}

	public static final String indent (int d) {
		StringBuffer sb = new StringBuffer();
		for (int i=0; i < d; i++) {
			sb.append ("\t");
		}
		return sb + "";
	}

	public final DeclTree enclosingFunc () {
		if (enclosingFunc == null) {
			Tree t = this;
			while (t != null && t.type != Treetype.FUNDEC) {
				t = t.parent;
			}
			enclosingFunc = (DeclTree) t;
		}
		return enclosingFunc;
	}

	// returns the variable declaration this tree is part of, or null if it's not part of a vardec
	public final DeclTree enclosingVardec () {
		Tree t = this;
		while (t != null && t.type != Treetype.VARDEC) t = t.parent;
		return (DeclTree) t;
	}

	/* Semantics methods */
	public abstract void makeParentLink (Tree p);
	public abstract void resolveKlassPlaceholders ()throws CompilerException ;
	public abstract void resolveNames (Context ctx, boolean dotchain) throws CompilerException;

	public void resolveNames (Context ctx) throws CompilerException {
		resolveNames (ctx, false);
	}

}
