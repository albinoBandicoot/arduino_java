package frontend;

import common.CompilerException;
import common.Log;
import type.Klass;

import java.util.*;
public class VarST extends Symtable {

	private VarST parent;
	private Map<String, DeclTree> entries;

	private static int ID = 0;

	public VarST (VarST p) {
		id = ++ID;
		parent = p;
		entries = new TreeMap<>();
		context = p.context;
		Log.write ("Created new VST: "+this);
	}

	public VarST (VarST p, Klass k) {
		id = ++ID;
		parent = p;
		entries = new TreeMap<>();
		context = k;
		Log.write ("Created new VST: "+this);
	}

	public void add (DeclTree d) throws CompilerException {
		String s = d.name;
		if (d.type != Treetype.VARDEC) throw new InternalError("Adding a non-VARDEC to a frontend.VarST");
		if (entries.get(s) != null) Log.error(d.new SemanticException ("Double-declaration of variable " + s));
		entries.put (s,d);
		d.scope = this;
		Log.write ("Added " + d + " to " + this);
	}

	public DeclTree lookup (String s) {
		Log.write ("\tLooking for " + s + " in " + this);
		DeclTree d = entries.get(s);
		if (d == null && parent != null) return parent.lookup(s);
		return d;
	}

	public String repr () {
		StringBuffer sb = new StringBuffer (toString() + "\n");
		for (DeclTree d : entries.values()) {
			sb.append (d + "\n");
		}
		return sb + "";
	}

	public String toString () {
		return "VST #" + id + " (p:" + (parent == null ? "null" : parent.id) + ")";
	}

}
