import java.util.*;
public class DeclTree extends Tree {

    public enum Vis {
		PUBLIC, PROTECTED, PRIVATE;
	}

//	private static int ID = 0;

//	public int id;
	public String name;
	public Vis vis;
	public boolean isStatic = true;
	public boolean isNative;
	public Type.Ext ext;
	public Type dtype;	// type for variable or return type for function or object type for class (which will contain everything)
	public DeclTree params;	// use next pointer to access all params. Only used for FUNDEC nodes
	public Tree body;	// initializer for vardecs, body for fundec and classdec
	private int nparams = -1;
	public Symtable scope;
	public DeclTree enclosingClass;

	public DeclTree override;	// pointer to declaration of the function that this one overrides

	public Tree static_init;	// only for CLASSDECs 
	public Tree inst_init;

	public DeclTree (Treetype t) {
		super(t);
//		id = ++ID;
		vis = Vis.PUBLIC;
		ext = Type.Ext.REG;
		enclosingClass = Compiler.currClass;
	}

	public String repr (int d) {
		StringBuffer sb = new StringBuffer (indent(d));
		sb.append(type + ": " + vis + (isStatic ? " static " : " ") + (ext == null ? "" : ext + " ") + dtype + " " + name + "#" + id);
		if (dtype instanceof Klass && type == Treetype.CLASSDEC) {
			Klass k = (Klass) dtype;
			sb.append (" extends " + (k.superclass == null ? "_|_" : k.superclass) + "; " + k.ctx.vars + "; " + k.ctx.funcs + "\n");
		} else {
			sb.append ("\n");
		}
		if (params != null) sb.append ("p>" + params.repr(d+1));
		if (static_init != null) sb.append ("SI>" + static_init.repr(d+1));
		if (inst_init != null) sb.append ("II>" + inst_init.repr(d+1));
		if (body != null) sb.append ("b>" + body.repr(d+1));
		if (next != null) sb.append (next.repr(d));
		return sb + "";
	}

	public String toString () {
		StringBuffer sb;
		Tree p = params;
		switch (type){
			case VARDEC:
				return dtype + " " + name + "#" + id;
			//	return vis + (isStatic ? " static " : " ") + (ext == null ? "" : ext + " ") + dtype + " " + name + "#" + id;
			case FUNDEC:
				sb = new StringBuffer ();
				sb.append(dtype + " " + name + "#" + id + "(");
				while (p != null) {
					sb.append (p + ", ");
					p = p.next;
				}
				sb.append (")");
				return sb + "";
			case CLASSDEC:
				return name + "#" + id;
		}
		return "OOPS";
	}

	public int nparams () {
		if (nparams == -1) {
			nparams = 0;
			Tree e = params;
			while (e != null) {
				e = e.next;
				nparams++;
			}
		}
		return nparams;
	}

	public boolean pprofMatches (DeclTree d) {
		if (nparams() != d.nparams()) return false;
		if (!dtype.equals(d.dtype)) return false;	// compare return types
		Tree e = params;
		Tree f = d.params;
		while (e != null) {	// compare parameter types
			if (!((DeclTree) e).dtype.equals(((DeclTree) f).dtype)) return false;
			e = e.next;
			f = f.next;
		}
		return true;
	}


	public Klass classType () throws CompilerException {
		if (dtype == null) Log.fatal(new InternalError ("Trying get classType of something with a null dtype"));
		if (dtype instanceof Klass) return (Klass) dtype;
		Log.fatal(new InternalError ("Trying to get classType of a non class type"));
		return null;
	}

	/*
	public String pprof () {
		if (type == Treetype.VARDEC) {
			return name;
		} else if (type == Treetype.FUNDEC) {
			return name + "|" + dtype.name() + "(" + params.parpprof() + ")";
		}
	}

	public String parpprof () {
		if (type == Treetype.VARDEC) {
			if (next != null) return dtype.name() + "," + next.parpprof();
			return dtype.name();
		}
		return "OOPS";
	}
	*/

	public int compareTo (DeclTree other) {
		return -1;
	}

	public void makeParentLink (Tree p) {
		parent = p;
		if (body != null) body.makeParentLink(this);
		if (params != null) params.makeParentLink(this);
		if (next != null) next.makeParentLink(p);
		if (static_init != null) static_init.makeParentLink(this);
		if (inst_init != null) inst_init.makeParentLink(this);
	}

	public void resolveKlassPlaceholders () throws CompilerException {
		if (type == Treetype.CLASSDEC) {
			Klass k = this.classType();
			if (k.superclass != null) {	// not Object
				Klass par = Compiler.findKlass (k.superclass.name);
				if (par == null) Log.error(new SemanticException ("Cannot find class " + k.superclass.name));
				k.linkParent (par);
			}
		} else {
			if (dtype != null) dtype = dtype.resolveKlassPlaceholders(this);
		}
		if (body != null) body.resolveKlassPlaceholders ();
		if (params != null) params.resolveKlassPlaceholders ();
		if (static_init != null) static_init.resolveKlassPlaceholders();
		if (inst_init != null) inst_init.resolveKlassPlaceholders();
		if (next != null) next.resolveKlassPlaceholders ();
	}

	public void resolveNames (Context ctx, boolean dotchain) throws CompilerException {
		System.out.println(">> ResolveNames on " + this);
		switch (type) {
			case VARDEC:
				if (name.equals("this") || name.equals("super")) Log.error( new SemanticException ("'" + name + "' is a reserved word and cannot be used as an identifier"));
				ctx.vars.add (this);
				if (body != null) {
					body.resolveNames (ctx);
					ExprTree e = (ExprTree) body;
					if (!e.dtype.isImplicitlyConvertibleTo(dtype)) Log.error (new SemanticException("Cannot implicitly convert type " + e.dtype + " to " + dtype));
					break;
				}
			case FUNDEC:
				if (name.equals("this") || name.equals("super")) Log.error( new SemanticException ("'" + name + "' is a reserved word and cannot be used as an identifier"));
				VarST locals = new VarST (ctx.vars);
				if (params != null) {
					params.resolveNames(new Context(locals, ctx.funcs));
				}
				if (!isStatic) {
					DeclTree thisTree = new DeclTree (Treetype.VARDEC);
					DeclTree superTree = new DeclTree (Treetype.VARDEC);
					thisTree.name = "this";
					superTree.name = "super";
					thisTree.dtype = Compiler.currClass.dtype;
					superTree.dtype = ((Klass) Compiler.currClass.dtype).superclass;
					locals.add (thisTree);
					locals.add (superTree);
				}
				if (body != null) body.resolveNames(new Context(locals, ctx.funcs));
				break;
			case CLASSDEC:
				if (name.equals("this") || name.equals("super")) Log.error( new SemanticException ("'" + name + "' is a reserved word and cannot be used as a class name"));
				// symbol tables for fields and methods of the class have already been made and populated.
				if (body != null) body.resolveNames (ctx);
				if (static_init != null) static_init.resolveNames (ctx);
				if (inst_init != null) inst_init.resolveNames (ctx);
				break;
			default:
				Log.fatal(new InternalError ("DeclTree of non *DEC type"));
		}
		if (next != null) next.resolveNames(ctx);
	}

	public boolean accessOK () {
		switch (vis) {
			case PUBLIC:	return true;
			case PROTECTED:
							Klass k = (Klass) Compiler.currClass.dtype;
							while (k != null) {
								if (k == scope.context) return true;
								k = k.superclass;
							}
							return false;
			case PRIVATE:	return Compiler.currClass.dtype == scope.context;
		}
		return true;
	}

}
