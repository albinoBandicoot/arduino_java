import java.util.*;
public class Klass extends Type {

	public boolean isLeaf;	// true if final
	public String name;
	public Klass superclass;
	public DeclTree definition;

	public boolean flag;

	public Context ctx;

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
		if (ctx != null) return;	// already done
		Log.warn ("Making symtables for " + name);
		if (superclass == null) {
			ctx = new Context(new VarST(null, this), new FuncST(null, this));
		} else {
			if (superclass.ctx == null) {
				superclass.makeSymtables();
			}
			ctx = new Context(new VarST (superclass.ctx.vars, this), new FuncST (superclass.ctx.funcs, this));
		}
		Tree t = definition.body;
		while (t != null) {
			/* Only add functions now. Variable declarations (and recursing into functions) must be done later. */
			if (t.type == Treetype.FUNDEC) {
				ctx.funcs.add ((DeclTree) t);
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
				ctx.vars.add (d);
				if (d.body != null) {	// has initializer
					d.body.resolveNames(ctx);
				}
			}
			t = t.next;
		}
	}
}
