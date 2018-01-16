package common;

import frontend.*;
import type.Klass;
import type.Type;

import java.util.*;
import java.io.*;
public class Compiler {

	public static Map<String, DeclTree> classes;
	public static DeclTree currClass;
	private static Set<String> classNames;

	public static Klass findKlass (String s) throws CompilerException {
		DeclTree d = classes.get(s);
		if (d == null) return null;
		return d.classType();
	}

	public static boolean classExists(String s) {
		return classNames.contains(s);
	}

	public static void main (String[] args) throws CompilerException, IOException {
		classes = new HashMap<>();
		classNames = new HashSet<>();

		DeclTree obj = new DeclTree (Treetype.CLASSDEC);
		obj.dtype = Type.objekt;
		obj.name = "Object";
		Type.objekt.definition = obj;
		classes.put ("Object", obj);
		classNames.add("Object");

		DeclTree str = new DeclTree (Treetype.CLASSDEC);
		str.dtype = Type.string;
		str.name = "String";
		Type.string.definition = str;
		Type.string.superclass = Type.objekt;
		classes.put ("String", str);
		classNames.add("String");

		VarST global_vars = new VarST(null, null);
		FuncST global_funcs = new FuncST(null, null);

		// add all the class names to the global list of class names. We must do this before parsing, since the
		// parser uses this list to disambiguate type casts from parenthesized expressions.
		for (String s : args) {
			if (s.contains(".")) s = s.substring(0, s.indexOf('.'));
			classNames.add(s);
		}
		// run parser
		for (String s : args) {
			DeclTree cd = (DeclTree) new Parser(new Lexer(s, new FileInputStream(new File(s)))).klass();
			if (classes.get(cd.name) != null) throw cd.new SemanticException ("Multiple definition of class " + cd.name);
			classes.put (cd.name, cd);
		}
		// resolve class name placeholders
		for (DeclTree d : classes.values()) {
			currClass = d;
			d.resolveKlassPlaceholders ();
		}
		Log.nextPhase();
		// check for loops in class hierarchy, and connect type.Klass STs
		for (DeclTree d : classes.values()) {
			currClass = d;
			Klass k = d.classType();
			k.checkForLoops();
			k.makeSymtables();
			// ensure no final class is extended
			if (k.superclass != null && k.superclass.definition.ext == Type.Ext.FINAL) {
				throw d.new SemanticException ("Class " + k.name + " cannot extend final class " + k.superclass.name);
			}
			d.makeParentLink (null);
		}
		Log.nextPhase();
		System.out.println (classes.get("Test").repr(0));
//		System.out.println ("Symbol table for functions for class Test: " + findKlass("Test").methods.repr());

		for (DeclTree d : classes.values()) {
			currClass = d;
			Klass k = d.classType();
			d.resolveNames (k.ctx);
			//StringBuffer sb = new StringBuffer();
			Log.warn ("Subclasses of " + k.name + " are: " + k.subclasses);
		}
		Log.nextPhase();
		
		Type.objekt.checkOverrides();
		Log.nextPhase();

		System.out.println (classes.get("Test").repr(0));
		System.out.println (classes.get("Foo").repr(0));

	}
}
