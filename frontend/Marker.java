package frontend;

public class Marker {

	public String fname;
	public int line;
	public int cpos;

	public Marker (String f, int l, int c) {
		fname = f;
		line = l;
		cpos = c;
	}

	public String toString() {
		return fname + ":" + line + ":" + cpos;
	}
}
