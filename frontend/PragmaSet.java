package frontend;

public class PragmaSet {

	private Pragma[] pragmas;

	public PragmaSet () {
	}

	public Pragma get (String prname) {
		if (pragmas == null) return null;
		for (Pragma p : pragmas) {
			if (p.type.name().equals(prname)) {
				return p;
			}
		}
		return null;
	}

	public void add (Pragma p) {
		if (pragmas == null) {
			pragmas = new Pragma[]{p};
		} else {
			Pragma[] n = new Pragma[pragmas.length + 1];
			for (int i = 0; i < pragmas.length; i++) {
				n[i] = pragmas[i];
			}
			n[pragmas.length] = p;
			pragmas = n;
		}
	}
}
