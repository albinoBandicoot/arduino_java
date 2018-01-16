package frontend;

import common.CompilerException;
import common.Log;
import type.Type;

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
	public Tree enclosingBlock;	// for break and continue

	public class Case {
		public Token value;	// must be a literal
		public Tree body;	// this will be a single BLOCK frontend.StmtTree

		public Case (Token t) {
			value = t;
		}

		public String repr (int d) {
			return indent(d) + "CASE " + value + ":\n";
		}

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
		sb.append(type + (block_locals != null ? " " + block_locals : "") + " [" + id + "]" + (enclosingBlock == null ? "" : " --> " + enclosingBlock.id) + "\n");
		if (type == Treetype.SWITCH) {
			sb.append (ch.get(0).repr(d+1));
			if (cases.isEmpty()) return sb + "";
			for (int i=0; i < cases.size(); i++) {
				Case c = cases.get(i);
				sb.append (c.repr(d+1));
				Tree nextbod = i == cases.size()-1 ? null : cases.get(i+1).body;
				if (c.body != nextbod) {
					sb.append (c.body.repr(d+2));
				}
			}
		} else {
			for (Tree t : ch) {
				sb.append (t == null ? "null\n" : t.repr(d+1));
			}
		}
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

	public void resolveNames (Context ctx, boolean dotchain) throws CompilerException {
		if (type == Treetype.BLOCK || type == Treetype.FOR) {	// FOR included b/c the initializer can declare variables
			block_locals = new VarST(ctx.vars);
			for (Tree c : ch) {
				c.resolveNames (new Context(block_locals, ctx.funcs));
			}
		} else {
			for (Tree c : ch) {
				c.resolveNames (ctx);
			}
		}
		if (type == Treetype.SWITCH) {
			Type t = ((ExprTree) ch.get(0)).dtype;
			if (!(t.isIntegral() || t == Type.string)) Log.error (new SemanticException("Expression to switch on must have an integral or string type"));
			if (t == Type.string) {
				HashSet<String> labels = new HashSet<>();
				for (Case c : cases) {
					if (c.value.type != Toktype.STR_LIT) Log.error (new SemanticException("Case labels type must match switch expression type"));
					String lab = c.value.lexeme;
					if (labels.contains(lab)) Log.error (new SemanticException("Duplicate case label: " + lab));
					labels.add(lab);
				}
			} else {
				HashSet<Long> labels = new HashSet<>();
				for (Case c : cases) {
					Long lab = c.value.integralValue();
					if (c.value.type != Toktype.INT_LIT && c.value.type != Toktype.CHAR_LIT) Log.error (new SemanticException("Case labels type must match switch expression type"));
					if (labels.contains(lab)) Log.error (new SemanticException("Duplicate case label: " + lab));
					labels.add(lab);
				}
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
		if (next != null) next.resolveNames (ctx);
	}
}
