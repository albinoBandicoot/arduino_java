import java.util.*;
public class StmtTree extends Tree {
	public List<Tree> ch;	// if statements: 0 - condition, 1 - body of if, 2 - body of else (if exists)
							// while statements: 0 - condition, 1 - body
							// for loops: 0 - initializer, 1 - condition, 2 - increment, 3 - body
							// foreach?
							// switch statements: 0 - expr to switch on, 1 - body (case labels separate and point into body)
							// return: 0 - value
	
	public List<Case> cases;
	public VarST block_locals;	// only for BLOCK statements

	public class Case {
		public Token value;	// must be a literal
		public StmtTree body;
	}

	public StmtTree (Treetype t) {
		super(t);
		ch = new ArrayList<>();
		if (t == Treetype.SWITCH) {
			cases = new ArrayList<>();
		}
	}

	public String toString () {
		return type + "";
	}

	public String repr (int d) {
		StringBuffer sb = new StringBuffer (indent(d));
		sb.append(type + (block_locals != null ? " " + block_locals : "") + "\n");
		for (Tree t : ch) {
			sb.append (t == null ? "null\n" : t.repr(d+1));
		}
		// do something for cases.
		if (next != null) sb.append(next.repr(d));
		return sb + "";
	}

	public void makeParentLink (Tree p) {
		parent = p;
		for (Tree t : ch) t.makeParentLink(this);
		if (next != null) next.makeParentLink(p);
	}

	public void resolveKlassPlaceholders () throws CompilerException {
		for (Tree c : ch) {
			c.resolveKlassPlaceholders();
		}
		if (next != null) next.resolveKlassPlaceholders();
	}

	public void resolveNames (VarST vars, FuncST funcs) throws CompilerException {
		if (type == Treetype.BLOCK || type == Treetype.FOR) {	// FOR included b/c the initializer can declare variables
			block_locals = new VarST(vars);
			for (Tree c : ch) {
				c.resolveNames (block_locals, funcs);
			}
		} else {
			for (Tree c : ch) {
				c.resolveNames (vars, funcs);
			}
		}
		if (type == Treetype.RETURN) {	// check return expression type matches that in function declaration
			Tree p = parent;
			while (p != null && p.type != Treetype.FUNDEC) {	// find enclosing fundec
				p = p.parent;
			}
			if (p == null) Log.error(new SemanticException ("Return statement not inside a function body"));
			Type t = ((DeclTree) p).dtype;
			Type e = ((ExprTree) ch.get(0)).dtype;
			if (! e.isImplicitlyConvertibleTo(t)) {
				Log.error(new SemanticException ("Attempting to return " + e + " from " + ((DeclTree) p).name + ", which returns " + t));
			} 
		}
		if (next != null) next.resolveNames (vars, funcs);
	}
}
