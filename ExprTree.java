import java.util.*;
public class ExprTree extends Tree {

	public Token op;	// operator, null for IDs or literals etc.
	public ExprTree left, right;	// I don't think I need more than 2 children for anything
	public ExprTree params;	// for funcalls
	public String name;	// name for IDs, unresolved class name for 'new'
	public Type cast_type;	// also type for instanceof. Parser fills this in for new operator as well
	private int nparams = -1;

	public Type dtype;	// data type of result
	public DeclTree definition;	// what do we do for polymorphic stuff?

	public ExprTree (Treetype t) {
		super (t);
	}
	public ExprTree (Treetype t, Token op) {
		super (t);
		this.op = op;
	}

	public ExprTree (Treetype t, String name) {
		super(t);
		this.name = name;
	}

	public String repr (int d) {
		StringBuffer sb = new StringBuffer (indent(d));
		sb.append (type + ": " + (op != null ? op : "")  + " " + (cast_type == null ? "" : cast_type) + " " + ((name == null) ? " " : (name + (definition == null ? "" : "#" + definition.id))) + " ==> " + dtype + "\n");
		if (params != null) sb.append ("p>" + params.repr(d+1));
		if (left != null) sb.append ("l>" + left.repr(d+1));
		if (right != null) sb.append ("r>" + right.repr(d+1));
		if (next != null) sb.append (next.repr(d));
		return sb + "";
	}

	public String toString () {
		return (type + ": " + (op != null ? op : "")  + " " + (cast_type == null ? "" : cast_type) + " " + ((name == null) ? "\n" : (name + "\n")));
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


	public void makeParentLink (Tree p) {
		parent = p;
		if (left != null) left.makeParentLink(this);
		if (right != null) right.makeParentLink(this);
		if (params != null) params.makeParentLink(this);
		if (next != null) next.makeParentLink(p);
	}

	public void resolveKlassPlaceholders () throws CompilerException {
		if (dtype != null && dtype instanceof Klass) {
			Klass k = Compiler.findKlass (dtype.name());
			if (k == null) Log.error( new SemanticException ("Could not resolve class name " + dtype.name()));
			dtype = k;
		}
		if (cast_type != null && cast_type instanceof Klass) {
			Klass k = Compiler.findKlass (cast_type.name());
			if (k == null) Log.error( new SemanticException ("Could not resolve class name " + cast_type.name()));
			cast_type = k; //Compiler.findKlass (cast_type.name());
		}
		if (left != null) left.resolveKlassPlaceholders ();
		if (right != null) right.resolveKlassPlaceholders ();
		if (params != null) params.resolveKlassPlaceholders ();
		if (next != null) next.resolveKlassPlaceholders ();
	}

	public boolean isStaticContext () {
		if (parent instanceof ExprTree) {
			ExprTree e = (ExprTree) parent;
			if (e.op != null && e.op.type == Toktype.DOT) {
				if (this == e.left) return enclosingFunc().isStatic;
				return false;
			}
			if (enclosingFunc() != null) return enclosingFunc().isStatic;
		}
		return true;
		// what about instance initializer?
	}

	public void resolveNames (VarST vars, FuncST funcs) throws CompilerException {
		System.out.println("  > resolveNames() on " + this);
		if (left != null) left.resolveNames(vars, funcs);	
		if (op != null && op.type == Toktype.DOT) {
			Type t = left.dtype;
			if (t == null) Log.fatal( new InternalError ("Dtype not assigned"));
			if (t instanceof Klass) {
				Klass k = (Klass) t;
				if (k == Type.klass) {
					Klass c = (Klass) left.definition.dtype;
					System.out.println("*** found " + c);
					if (right != null) right.resolveNames (c.fields, c.methods);
				} else {
					if (right != null) right.resolveNames (k.fields, k.methods);
				}
			} else if (t instanceof Array) {
				Log.write ("Array attribute reference not yet supported");

			} else Log.error( new SemanticException ("Cannot reference field of primitive type"));

		} else {
			if (right != null) right.resolveNames(vars, funcs);	
		}

		if (params != null) params.resolveNames(vars, funcs);	

		switch (type) {
			case ID:
				if (parent.type == Treetype.FUNCALL && this == ((ExprTree) parent).left) {	// the function name, not in a param
					if (name.equals("this")) {
						definition = resolveFunction (((Klass) Compiler.currClass.dtype).methods.lookupConstructor());
						if (isStaticContext()) Log.error( new SemanticException ("Cannot reference 'this' from static context"));
					} else if (name.equals("super")) {
						definition = resolveFunction (((Klass) Compiler.currClass.dtype).superclass.methods.lookupConstructor());
						if (isStaticContext()) Log.error( new SemanticException ("Cannot reference 'super' from static context"));
					} else {
						definition = ((ExprTree) parent).resolveFunction (funcs.lookup(name));
						if (isStaticContext() && !definition.isStatic) Log.error( new SemanticException ("Cannot call non-static function " + name + " from a static context"));
					}
					if (!definition.accessOK()) Log.error( new SemanticException (name + " has private access and cannot be referenced from this context"));
					dtype = definition.dtype;
				} else { 	//if (parent.type == Treetype.OP && ((ExprTree) parent).op.type == Toktype.DOT) {
					// IDs that are direct children of DOTs are variables.
					definition = vars.lookup(name);
					if (definition == null) {
						// might be a Class name
						Klass k = Compiler.findKlass (name);
						if (k == null) Log.error( new SemanticException ("Undeclared variable " + name));
						if (!definition.isStatic) Log.error( new SemanticException (name + " is not static in " + k));
						dtype = Type.klass;
						definition = k.definition;
					} else {
						if (isStaticContext() && !definition.isStatic) Log.error( new SemanticException ("Cannot reference non-static member " + name + " from a static context"));
						if (!definition.accessOK()) Log.error( new SemanticException (name + " has private access and cannot be referenced from this context"));
						dtype = definition.dtype;
						Log.write ("Linked use of " + name + " at " + mark + " to " + definition);
					}
				}
				break;
			case OP:
				if (op.type == Toktype.DOT) {
					if (right.dtype == null) Log.fatal( new InternalError ("Assigning null to DOT dtype"));
					dtype = right.dtype;
				} else {
					dtype = resolveOperatorType ();
					if (op.type.isOpEq()) {
						if (!dtype.isImplicitlyConvertibleTo(left.dtype)) Log.error( new SemanticException ("Cannot assign expression of type " + dtype + " to variable of type " + left.dtype));
					}
				}
				break;
			case CAST:
				if (!left.dtype.canCastTo(cast_type)) Log.error( new SemanticException ("Cannot cast " + left.dtype + " to " + cast_type));
				dtype = cast_type;
				break;
			case NEW:
				dtype = cast_type;
				if (dtype instanceof Klass) {
					// resolve constructor: note this has to be in just the ST for the class, not its parents
					definition = resolveFunction (((Klass) dtype).methods.lookupConstructor());
					if (!definition.accessOK()) Log.error( new SemanticException (dtype + " constructor has private access and cannot be referenced from this context"));
				}
				break;
			case VALUE:
				dtype = Type.literalType (op);
				break;
			case AGG:
				ExprTree e = left;
				if (! (dtype instanceof Array)) Log.fatal( new InternalError ("Aggregate has non-array type?!"));
				Array arr = (Array) dtype;
				while (e != null) {
					if (e.dtype != arr.basetype) Log.error( new SemanticException ("Aggregate element has wrong type (" + e.dtype + "); should be " + arr.basetype));
					e = (ExprTree) e.next;
				}
				break;
			case FUNCALL:
				dtype = left.dtype;
				break;
			default:
				Log.fatal( new InternalError ("Uncovered treetype: " + type));
		}

		if (next != null) next.resolveNames(vars, funcs);	
	}

	public DeclTree resolveFunction (List<DeclTree> decs) throws CompilerException {
		System.out.println("Tring tyo resolve function. Got candidates: " );
		for (DeclTree d : decs) {
			System.out.println("\t* " + d);
		}

		List<DeclTree> possible = new ArrayList<>();
		int ns = Integer.MAX_VALUE;
		for (DeclTree d : decs) {
			System.out.println ("Considering option: " + d + "; parameters: " + nparams() + "; d.nparams: " + d.nparams());
			if (nparams() != d.nparams()) continue;
			ExprTree p = params;
			DeclTree dp = d.params;
			int totalSteps = 0;
			while (p != null) {
				int convSteps = p.dtype.implicitConversionSteps (dp.dtype);
				System.out.println("\t\tCsteps: " + convSteps);
				if (convSteps == -1) break;
				totalSteps += convSteps;
				p = (ExprTree) p.next;
				dp = (DeclTree) dp.next;
			}
			if (p == null) {	// we made it all the way through
				if (totalSteps < ns) {
					possible.clear();
					possible.add (d);
					ns = totalSteps;
					System.out.println("\tTakes " +totalSteps  + " conversions, a new low.");
				} else if (totalSteps == ns) {
					possible.add (d);
					System.out.println("\tTakes " + totalSteps + " conversions, same as current low");
				} else {
					System.out.println("\tTakes " +totalSteps  + " conversions, above best so far");
				}
			}
		}
		boolean constr = type == Treetype.NEW;
		String nm = constr ? dtype + " constructor" : "function " + name;
		System.out.println ("# of possibilities: " + possible.size());
		if (possible.size() == 1) return possible.get(0);
		if (possible.isEmpty()) Log.error( new SemanticException ("No viable candidates for " + nm));
		Log.error( new SemanticException ("Call to " + nm + " is ambiguous (" + possible.size() + " candidates found)"));
		return null;	// this may cause problems
	}

	public Type resolveOperatorType () throws CompilerException {
		System.out.println(" > resolving operator type " + op);
		/* Type restrictions on operators:
		 *
		 * +				numeric types, classes (implicit call to toString())
		 * ++, --, -, *, % 	numeric types
		 * <<, >>, >>>		integral types
		 * &, |, ^, ~		integral and boolean types
		 * &&, ||, !		boolean
		 * <,>,<=,>=		numeric types
		 * ==, !=			any type
		 * instanceof		any non-primitive (reference) type
		 *
		*/
		switch (op.type) {
			case PLUS:
			case PLUSEQ:
				if (left.dtype.isNumeric() && right.dtype.isNumeric()) return resultantNumericType (left.dtype, right.dtype);
				if (left.dtype == Type.string || right.dtype == Type.string) return Type.string;
				Log.error( new SemanticException ("Bad types for operands of +  (" + left.dtype + " and " + right.dtype + ")"));

			case INC:
			case DEC:
				ExprTree active = left == null ? right : left;
				if (active.dtype.isNumeric()) return active.dtype;
				Log.error( new SemanticException ("Bad type for operand of " + op.lexeme + " (" + active.dtype + ")"));

			case MINUS:
			case MINUSEQ:
			case TIMES:
			case TIMESEQ:
			case DIVIDE:
			case DIVIDEEQ:
			case MOD:
			case MODEQ:
				if (left.dtype.isNumeric() && right.dtype.isNumeric()) {
					return resultantNumericType (left.dtype, right.dtype);
				}
				Log.error( new SemanticException ("Bad type for operand of " + op.lexeme + " ( " + left.dtype + " and " + right.dtype + ")"));

			case LSHIFT:
			case LSHIFTEQ:
			case RSHIFT:
			case RSHIFTEQ:
			case ULSHIFT:
			case ULSHIFTEQ:
				if (left.dtype.isIntegral() && right.dtype.isIntegral()) return left.dtype;
				Log.error( new SemanticException ("Bad type for operand of " + op.lexeme + " ( " + left.dtype + " and " + right.dtype + ")"));

			case AND:
			case ANDEQ:
			case OR:
			case OREQ:
			case XOR:
			case XOREQ:
				if (left.dtype == Primitive.BOOLEAN && right.dtype == Primitive.BOOLEAN) return Primitive.BOOLEAN;
				if (left.dtype.isIntegral() && right.dtype.isIntegral()) return resultantNumericType (left.dtype, right.dtype);
				Log.error( new SemanticException ("Bad type for operand of " + op.lexeme + " ( " + left.dtype + " and " + right.dtype + ")"));

			case TWIDDLE:
			case TWIDDLEEQ:
				if (left.dtype.isIntegral()) return left.dtype;
				Log.error( new SemanticException ("Bad type for operand of ~  (" + left.dtype + ")"));

			case DBLAND:
			case DBLOR:
				if (left.dtype == Primitive.BOOLEAN && right.dtype == Primitive.BOOLEAN) return Primitive.BOOLEAN;
				Log.error( new SemanticException ("Bad type for operand of " + op.lexeme + " (" + left.dtype + " and " + right.dtype + ")"));

			case NOT:
				if (left.dtype == Primitive.BOOLEAN) return Primitive.BOOLEAN;
				Log.error( new SemanticException ("Bad type for operand of ! (" + left.dtype + ")"));

			case LT:
			case LE:
			case GE:
			case GT:
				if (left.dtype.isNumeric() && right.dtype.isNumeric()) return Primitive.BOOLEAN;
				Log.error( new SemanticException ("Bad type for operand of " + op.lexeme + " (" + left.dtype + " and " + right.dtype + ")"));

			case EQ:
			case NE:
				return Primitive.BOOLEAN;

			case LBRACK:
				if (! (left.dtype instanceof Array)) Log.error( new SemanticException ("Cannot subscript non-array type " + left.dtype));
				if (!right.dtype.isIntegral()) Log.error( new SemanticException ("Array index must be an integral type"));
				return ((Array) left.dtype).basetype;

			case ASSIGN:
				if (right.dtype.isImplicitlyConvertibleTo(left.dtype)) return left.dtype;
				Log.error( new SemanticException ("Cannot assign expression of type " + right.dtype + " to variable of type " + left.dtype));
		}
		Log.fatal( new InternalError ("Unimplemented operator Toktype in resolveOperatorType()"));
		return null;
	}

	private static Type resultantNumericType (Type a, Type b) {
		Primitive pa = (Primitive) a;
		Primitive pb = (Primitive) b;
		if (pa.level > pb.level) {
			if (pa.signed || pb.signed) return pa.signedEquivalent();
			return pa;
		} else {
			if (pa.signed || pb.signed) return pb.signedEquivalent();
			return pb;
		}
	}

}
