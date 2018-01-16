package type;

import common.CompilerException;
import frontend.Tree;

public class Nulltype extends Type {

	public static final Nulltype n = new Nulltype();

	private Nulltype () {
	}

	public String name () {
		return "nulltype";
	}

	public String toString () {
		return name();
	}

	public Type resolveKlassPlaceholders (Tree loc)throws CompilerException {
		return this;
	}

	public int implicitConversionSteps (Type t) {
		if (t.isReference()) return 0;
		return -1;
	}

	public boolean canCastTo (Type t) {
		return t.isReference();
	}

	public Type merge (Type t) {
		if (t.isReference()) return t;
		return null;
	}
}
