package frontend;

import common.CompilerException;
import type.Klass;
import common.Log;

import java.util.*;
public class FuncST extends Symtable {

	private FuncST parent;
	private Map<String, List<DeclTree>> entries;

	private static int ID = 0;

	public FuncST (FuncST p) {
		id = ++ID;
		parent = p;
		context = p.context;
		entries = new TreeMap<>();
		Log.write ("Created new FST: "+this);
	}

	public FuncST (FuncST p, Klass k) {
		id = ++ID;
		parent = p;
		context = k;
		entries = new TreeMap<>();
		Log.write ("Created new FST: "+this);
	}

	public void add (DeclTree d) throws CompilerException {
		String s = d.name;
		if (d.type != Treetype.FUNDEC) throw new InternalError("Adding a non-FUNDEC to a frontend.FuncST");
		if (entries.get(s) != null) {
			for (DeclTree t : entries.get(s)) {
				if (t.pprofMatches (d)) {
					Log.error (d.new SemanticException("A function with this name and parameter profile already exists in this scope"));
					return;
				}
			}
			entries.get(s).add(d);
			Log.write ("Added " + d + " to " + this + "  <overloaded>");
		} else {
			ArrayList<DeclTree> list = new ArrayList<>();
			list.add (d);
			entries.put (s, list);
			Log.write ("Added " + d + " to " + this + "  <new name>");
		}
		d.scope = this;
	}

	public List<DeclTree> lookupConstructor () {
		List<DeclTree> d = entries.get("new");
		if (d == null) return new ArrayList<>();
		return d;
	}

	public List<DeclTree> lookup (String s) {
		List<DeclTree> list = entries.get(s);
		if (list == null) {
			if (parent == null) return new ArrayList<>();
			return parent.lookup(s);
		}
		return list;
	}

	// find a function with the same name and profile in the current context. Useful for searching for
	// overridden functions
	public DeclTree findMatching (DeclTree d) {
		for (DeclTree t : lookup(d.name)) {
			if (t.pprofMatches(d)) return t;
		}
		return null;
	}

	public String repr () {
		StringBuffer sb = new StringBuffer (toString() + "\n");
		for (List<DeclTree> list : entries.values()) {
			for (DeclTree d : list) {
				sb.append (d + "\n");
			}
		}
		return sb + "";
	}

	public String toString () {
		return "FST #" + id + " (p:" + (parent == null ? "null" : parent.id) + ")";
	}
}
