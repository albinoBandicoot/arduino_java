import java.util.*;
public class Klass extends Type {

	public boolean isLeaf;	// true if final
	public String name;
	public Klass superclass;
	public DeclTree definition;

	public boolean flag;

	public VarST fields;
	public FuncST methods;

	public Klass (String name) {	// for making placeholders
		flag = false;
		this.name = name;
	}

	public String toString () {
		return name + (definition == null ? "!!!":"");
	}

	public String name () {
		return name;
	}

	public int implicitConversionSteps (Type t) {
		int ct = 0;
		if (t instanceof Klass) {
			Klass k = this;
			while (k != null) {
				if (t == k) return ct;
				ct++;
				k = k.superclass;
			}
		}
		return -1;
	}

	public boolean isDescendantOf (Klass k) {
		return k.isAncestorOf (this);
	}

	public boolean isAncestorOf (Klass k) {
		Log.write ("Checking if " + this + " is an ancestor of " + k);
		while (k != null) {
			Log.write ("\tChecking if " + k + " is this");
			if (k == this) return true;
			k = k.superclass;
		}
		return false;
	}

	public boolean canCastTo (Type t) {
		Log.warn("Checking castability of " + this + " to " + t);
		if (t instanceof Klass) {
			return isAncestorOf((Klass) t) || isDescendantOf((Klass) t);
		}
		return false;
	}

	public Klass resolveKlassPlaceholders (Tree loc) throws CompilerException {
		Klass k = Compiler.findKlass (name);
		if (k == null) Log.error (loc.new SemanticException("Could not resolve class name " + name));
		return k;
	}

	// checks for loops in the class heirarchy
	public void checkForLoops () throws CompilerException {
		Klass a = this;
		Klass b = superclass;
		while (b != null) {
			if (a == b) throw definition.new SemanticException ("Derived class loop!");
			a = a.superclass;
			b = b.superclass;
			if (b != null) b = b.superclass;
		}
	}

	// creates the class level symbol tables and links them to the superclass. Fills in the static and instance methods.
	public void makeSymtables () throws CompilerException {
		if (superclass == null) {
			fields = new VarST (null, this);
			methods = new FuncST (null, this);
		} else {
			fields = new VarST (superclass.fields, this);
			methods = new FuncST (superclass.methods, this);
		}
		Tree t = definition.body;
		while (t != null) {
			/* Only add functions now. Variable declarations (and recursing into functions) must be done later. */
			if (t.type == Treetype.FUNDEC) {
				methods.add ((DeclTree) t);
			}
			t = t.next;
		}
	}

	// fills in all the class and instance fields. This must be done for all classes before name resolution begins.
	public void fillVarSymtables () throws CompilerException {
		Tree t = definition.body;
		while (t != null) {
			if (t.type == Treetype.VARDEC) {
				DeclTree d = (DeclTree) t;
				fields.add (d);
				if (d.body != null) {	// has initializer
					d.body.resolveNames(fields, methods);
				}
			}
			t = t.next;
		}
	}
}
