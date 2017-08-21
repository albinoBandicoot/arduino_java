
public class Array extends Type {
	public Type basetype;

	public Array (Type bt) {
		basetype = bt;
	}

	public String toString () {
		return basetype + "[]";
	}

	public int rank () {
		if (basetype instanceof Array) return 1 + ((Array) basetype).rank();
		return 1;
	}

	public Type rootType () {
		if (basetype instanceof Array) return ((Array) basetype).rootType();
		return basetype;
	}

	public int implicitConversionSteps (Type t) {
		if (t instanceof Array) {
			Array a = (Array) t;
			if (rank() == a.rank()) {
				Type r = rootType();
				Type tr = a.rootType();
				if (r instanceof Klass) return r.implicitConversionSteps (tr);
				if (r instanceof Primitive) {
					if (r == tr) return 0;
				}
			}
		}
		return -1;
	}

	public Type resolveKlassPlaceholders (Tree loc) throws CompilerException {
		basetype = basetype.resolveKlassPlaceholders(loc);
		return this;
	}

	public boolean canCastTo (Type t) {
		return implicitConversionSteps (t) >= 0;
	}

	public Type merge (Type t) {
		if (t instanceof Array) {
			return new Array(basetype.merge(((Array) t).basetype));
		}
		return null;
	}

	public String name () {
		return this.toString();
	}
}
