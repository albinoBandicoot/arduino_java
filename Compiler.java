import java.util.*;
import java.io.*;
public class Compiler {

	public static Map<String, DeclTree> classes;
	public static DeclTree currClass;

	public static Klass findKlass (String s) throws CompilerException {
		DeclTree d = classes.get(s);
		if (d == null) return null;
		return d.classType();
	}

	public static void main (String[] args) throws CompilerException, IOException {
		classes = new HashMap<String, DeclTree>();

		DeclTree obj = new DeclTree (Treetype.CLASSDEC);
		obj.dtype = Type.objekt;
		obj.name = "Object";
		Type.objekt.definition = obj;
		classes.put ("Object", obj);

		DeclTree str = new DeclTree (Treetype.CLASSDEC);
		str.dtype = Type.string;
		str.name = "String";
		Type.string.definition = str;
		Type.string.superclass = Type.objekt;
		classes.put ("String", str);

		VarST global_vars = new VarST(null, null);
		FuncST global_funcs = new FuncST (null, null);

		// run parser
		for (String s : args) {
			DeclTree cd = (DeclTree) new Parser (new Lexer(s, new FileInputStream(new File(s)))).klass();
			if (classes.get(cd.name) != null) throw cd.new SemanticException ("Multiple definition of class " + cd.name);
			classes.put (cd.name, cd);
		}
		// resolve class name placeholders
		for (DeclTree d : classes.values()) {
			currClass = d;
			d.resolveKlassPlaceholders ();
		}
		Log.nextPhase();
		// check for loops in class hierarchy, and connect Klass STs
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
