public abstract class Type {

	static class Void extends Type {

		public static final Void v = new Void();

		private Void () {
			refsize = allocsize = 0;
		}

		public String name () {
			return "void";
		}

		public boolean canCastTo (Type t) {
			return t == this;
		}

		public int implicitConversionSteps (Type t) {
			return -1;
		}
	}

	public static Klass objekt = new Klass("Object");
	public static Klass string = new Klass("String");
	public static Klass klass = new Klass("Class");
	public static final Void voyd = Void.v;

	public int refsize;	// size of data for primitive types, size of reference for array or object types
	public int allocsize;	// size of data allocation, if known (will be for objects, probably not for arrays);

	public enum Ext {
		REG, ABSTRACT, FINAL;
	}

	public static final Type literalType (Token t) {
		switch (t.type) {
			case BOOL_LIT:	return Primitive.BOOLEAN;
			case CHAR_LIT:	return Primitive.CHAR;
			case INT_LIT:	return Primitive.INT;	// I guess?
			case FLOAT_LIT:	return Primitive.FLOAT_LITERAL;
			case OBJ_LIT:	return objekt;	// is this right? it should probably be a value of every class instead...
			case STR_LIT:	return string;
		}
		return null;
	}

	public abstract String name () ;

	public final boolean isImplicitlyConvertibleTo (Type t) {
		return implicitConversionSteps(t) >= 0;
	}

	public abstract int implicitConversionSteps (Type t) ;	
	public abstract boolean canCastTo (Type t) ;

	public final boolean isNumeric () {
		if (this instanceof Primitive) {
			return this != Primitive.BOOLEAN;
		}
		return false;
	}

	public final boolean isIntegral () {
		return isNumeric() && this != Primitive.FLOAT && this != Primitive.DOUBLE;
	}
}

