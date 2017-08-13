
public class Array extends Type {
	public Type basetype;

	public Array (Type bt) {
		basetype = bt;
	}

	public String toString () {
		return basetype + "[]";
	}

	public int implicitConversionSteps (Type t) {
		if (t instanceof Array) {
			if (basetype == ((Array) t).basetype) return 0;
			if (basetype instanceof Klass) return basetype.implicitConversionSteps (((Array) t).basetype);
		}
		return -1;
	}

	public boolean canCastTo (Type t) {
		if (t instanceof Array) {
			return basetype.canCastTo (((Array) t).basetype);
		}
		return false;
	}

	public String name () {
		return this.toString();
	}
}
