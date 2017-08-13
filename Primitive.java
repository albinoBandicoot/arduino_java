public final class Primitive extends Type {

	public static boolean arduino_sizes = true;

	public static final int char_bytes  = 1;
	public static final int short_bytes = 2;
	public static final int int_bytes   = arduino_sizes ? 2 : 4;
	public static final int long_bytes  = arduino_sizes ? 4 : 8;

	public static final Primitive BOOLEAN 	= new Primitive ("boolean", 1, false, false, 0);
	public static final Primitive CHAR 		= new Primitive ("char", char_bytes, true, true, 0);
	public static final Primitive UCHAR 	= new Primitive ("uchar", char_bytes, false, true, 0);
	public static final Primitive SHORT 	= new Primitive ("short", short_bytes, true, true, 1);
	public static final Primitive USHORT 	= new Primitive ("ushort", short_bytes, false, true, 1);
	public static final Primitive INT 		= new Primitive ("int", int_bytes, true, true, 2);
	public static final Primitive UINT 		= new Primitive ("uint", int_bytes, false, true, 2);
	public static final Primitive LONG 		= new Primitive ("long",long_bytes, true, true, 3);
	public static final Primitive ULONG 	= new Primitive ("ulong", long_bytes, false, true, 3);
	public static final Primitive FLOAT 	= new Primitive ("float", 4, true, false, 4);
	public static final Primitive DOUBLE 	= new Primitive ("double", 8, true, false, 5);

	private static final Primitive[] signedInts   = {CHAR, SHORT, INT, LONG};
	private static final Primitive[] unsignedInts = {UCHAR, USHORT, UINT, ULONG};

	public static final Primitive FLOAT_LITERAL = arduino_sizes ? FLOAT: DOUBLE;

	public boolean signed;
	public boolean integral;
	public int level;
	public String name;

	private Primitive (String n, int size, boolean signed, boolean integral, int level) {
		name = n;
		refsize = size;
		allocsize = size;
		this.level = level;
		this.signed = signed;
	}

	public static final Primitive parse (String s) throws CompilerException {
		switch (s) {
			case "boolean":	return BOOLEAN;
			case "char":	return CHAR; 
			case "uchar":	return UCHAR;
			case "byte":	return CHAR;
			case "ubyte":	return UCHAR;
			case "short":	return SHORT;
			case "ushort":	return USHORT;
			case "int":		return INT;
			case "uint":	return UINT;
			case "long":	return LONG;
			case "ulong":	return ULONG;
			case "float":	return FLOAT;
			case "double":	return DOUBLE;
		}
		System.out.println("BAD PRIMITIVE NAME " + s);
		return null;
	}

	public Primitive signedEquivalent () {
		if (signed || this == BOOLEAN || this == FLOAT || this == DOUBLE) return this;
		return signedInts[level];
	}
		
	public String toString() {
		return name;
	}

	public String name () {
		return name;
	}

	public boolean canCastTo (Type t) {
		return t instanceof Primitive;
	}

	public int implicitConversionSteps (Type t) {
		if (this == t) return 0;
		if (!(t instanceof Primitive)) return -1;
		if (this == BOOLEAN || t == BOOLEAN) return -1;
		return Math.max (-1, ((Primitive) t).level - level);
	}
}
