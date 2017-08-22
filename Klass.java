import java.util.*;
public class Klass extends Type {

	public boolean isLeaf;	// true if final
	public String name;
	public Klass superclass;
	public List<Klass> subclasses;
	public DeclTree definition;

	public boolean flag;

	public Context ctx;

	public Klass (String name) {
		flag = false;
		this.name = name;
		subclasses = new ArrayList<Klass>();
	}

	public String toString () {
		return name + (definition == null ? "!!!":"");
	}

	public String name () {
		return name;
	}

	public void linkParent (Klass p) {
		Log.warn("Adding " + this + " as a subclass of " + p);
		superclass = p;
		p.subclasses.add(this);
	}

	public int implicitConversionSteps (Type t) {
		int ct = 0;
		if (t instanceof Nulltype) return 0;
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

	public Type merge (Type t) {
		if (t instanceof Nulltype) return this;
		if (t instanceof Klass) {
			Klass k = (Klass) t;
			if (isAncestorOf(k)) return this;
			if (isDescendantOf(k)) return k;
		}
		return null;
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
		if (superclass != null && superclass.definition.ext == Type.Ext.FINAL) Log.error (definition.new SemanticException("Cannot extend final class " + superclass));
		while (b != null) {
			if (a == b) Log.fatal(definition.new SemanticException ("Derived class loop!"));
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
				if (((DeclTree) t).ext == Type.Ext.ABSTRACT && definition.ext != Type.Ext.ABSTRACT) Log.error (t.new SemanticException("Class " + this + " is not abstract, cannot have abstract methods"));
				ctx.funcs.add ((DeclTree) t);
			}
			t = t.next;
		}
	}

	class DeclIterator implements Iterator<DeclTree> {
		private Tree curr;
		private Treetype type;

		public DeclIterator (Treetype t) {
			type = t;
			curr = definition.body;
		}

		public boolean hasNext () {
			if (curr == null) return false;
			Tree t = curr.next;
			while (t != null) {
				if (t.type == type) return true;
				t = t.next;
			}
			return false;
		}

		public DeclTree next () throws NoSuchElementException {
			curr = curr.next;
			if (curr == null) throw new NoSuchElementException();
			while (curr != null) {
				if (curr.type == type) return (DeclTree) curr;
				curr = curr.next;
			}
			throw new NoSuchElementException();
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

	private void findOverride (DeclTree t) {
		Klass s = superclass;
		while (s != null) {
			DeclTree d = s.ctx.funcs.findMatching(t);
			if (d != null && !d.isStatic) {
				if (d.ext != Type.Ext.FINAL) {
					t.override = d;
				} else {
					Log.error (t.new SemanticException("Cannot override final method"));
				}
				return;
			}
			s = s.superclass;
		}
	}

	public DeclIterator iterator (Treetype t) {
		return new DeclIterator (t);
	}

	public void linkOverrides () {
		DeclIterator i = iterator (Treetype.FUNDEC);
		while (i.hasNext()) {
			findOverride (i.next());
		}
	}

	public void checkOverrides () {
		checkOverrides (new ArrayList<DeclTree>());
	}

	// check that all abstract methods are implemented if class not abstract. This is a top-down pass.
	private void checkOverrides (List<DeclTree> pending) {
		linkOverrides();
		DeclIterator j = iterator (Treetype.FUNDEC);
		List<DeclTree> abstract_funcs = new ArrayList<>();
		abstract_funcs.addAll (pending);

		Log.write ("Class " + name + ": pending abstract funcs: " + abstract_funcs);
		// remove methods that we've implemented
		while (j.hasNext()) {
			DeclTree t = j.next();
			if (t.ext != Type.Ext.ABSTRACT && t.override != null) {
				abstract_funcs.remove(t.override);
			}
		}
		Log.write ("after removal of implemeneted methods: " + abstract_funcs);

		// if abstract class, add all abstract methods
		if (definition.ext == Type.Ext.ABSTRACT) {
			DeclIterator i = iterator (Treetype.FUNDEC);
			while (i.hasNext()) {
				DeclTree t = i.next();
				if (t.ext == Type.Ext.ABSTRACT) {
					abstract_funcs.add (t);
				}
			}
		} else {
			for (DeclTree d : abstract_funcs) {	// should be empty.
				Log.error (definition.new SemanticException("Class " + name + " is not abstract and does not override method " + d + " in class " + d.enclosingClass));
			}
		}

		// check subclasses
		for (Klass k : subclasses) {
			k.checkOverrides(abstract_funcs);
		}
	}


}

