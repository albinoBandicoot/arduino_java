package type;

import common.CompilerException;
import common.Log;
import frontend.Tree;

class Void extends Type {

	public static final Void v = new Void();

	private Void() {
		refsize = allocsize = 0;
	}

	public String name () {
		return "void";
	}

	public boolean canCastTo (Type t) {
		return t == this;
	}

	public Type merge (Type t) {
		Log.warn ("Merging Void type");
		if (t instanceof type.Void) return this;
		return null;
	}

	public Type resolveKlassPlaceholders (Tree loc) throws CompilerException {
		return this;
	}

	public int implicitConversionSteps (Type t) {
		return -1;
	}

	public String toString () {
		return "void";
	}
}