package codegen;

import java.util.*;
import java.util.regex.*;
import java.io.*;
public class Optemplate {

	public static Map<String, Optemplate> templates;

	public String name;
	public int nw;
	public ArrayList<InstrParam> params;
	public String pattern;
	public int basepat;	// pattern, with letters set to 0

	public Optemplate (String line) {
		params = new ArrayList<>();
		Scanner sc = new Scanner(line);
		sc.useDelimiter(Pattern.compile(","));
		name = sc.next();
		nw = sc.nextInt();
		int np = sc.nextInt();
		for (int i=0; i < np; i++) {
			String p = sc.next();
			char type = p.charAt(0);
			int len = Integer.parseInt(p.substring(1));
			params.add(new InstrParam(type, len));
		}
		if (nw != 0) {
			pattern = sc.next();
			basepat = stringToBinary (pattern);
		}
	}

	public static void readTemplates (File f) throws AssemblerException, IOException {
		templates = new HashMap<String, Optemplate>();
		Scanner sc = new Scanner (f);
		while (sc.hasNextLine()) {
			Optemplate op = new Optemplate (sc.nextLine());
			templates.put (op.name, op);
		}
	}

	public static int swab (int x) {
		return ((x >>> 8) | ((x & 0xff) << 8));
	}

	public static int swab2 (int x) {
		return ((x >>> 16) | ((x & 0xffff) << 16));
		//return (swab(x >>> 16) << 16) | swab(x & 0xffff);
	}

	public static int stringToBinary (String p) {
		int res = 0;
		for (int i=0; i < p.length(); i++) {
			res <<= 1;
			if (p.charAt(i) == '1') {
				res |= 1;
			}
		}
		return res;
	}

	public static boolean fitsInBits (int x, int bits) {
		if (x >= 0) {
			if (x < (1 << bits)) {
				if (x >= (1 << (bits-1))) System.err.println("Warning: constant specified as positive (" + x + ") might be treated as negative in a " + bits + " bit immediate.");
				return true;
			}
			return false;
		}
		return -x <= (1 << (bits-1));
	}

	public static String istr (int x, int base, int len) {
		StringBuffer sb = new StringBuffer();
		String s = base == 2 ? Integer.toBinaryString(x) : Integer.toHexString(x);
		for (int i=s.length(); i < len; i++) {
			sb.append('0');
		}
		sb.append(s);
		return sb.toString();
	}


	public static String binstr (int x) {
		return istr (x, 2, 16);
	}

	class Op {

		public ArrayList<InstrParam> myparams;
		public Optemplate parent;
		public int pc;

		public Op (int pc) {
			this.pc = pc;
			parent = Optemplate.this;
			myparams = new ArrayList<>();
			for (InstrParam p : params) {
				myparams.add (new InstrParam (p.type, p.len));
			}
		}

		public Op pseudo () {
			Op res = null;
			switch (name){ 
				case "rol":
					res = templates.get("adc").new Op(pc);
					break;
				case "clr":
					res = templates.get("eor").new Op(pc);
					break;
				case "lsl":
					res = templates.get("add").new Op(pc);
					break;
				case "tst":
					res = templates.get("and").new Op(pc);
					break;
				case "cbr":
					res = templates.get("andi").new Op(pc);
					res.myparams.get(0).value = myparams.get(0).value;
					res.myparams.get(1).value = ~myparams.get(1).value;
					return res;
				default:
					return this;
			}
			int dest = myparams.get(0).value;
			res.myparams.get(0).value = dest;
			res.myparams.get(1).value = dest;
			return res;
		}

		public int generate (Assembler as) throws AssemblerException {
			int res = basepat;
//			System.err.println ("pattern: " + pattern + "; basepattern: " + binstr(basepat));
			for (InstrParam p : myparams) {
				if (p.label != null) {
					p.value = as.getLabel (p.label);
					if (name.equals("rjmp") || name.equals("rcall") || name.startsWith("br")) {
						System.err.println ("pc = " + pc + "; target = " + p.value + "; offset = " + (p.value - pc - 1));
						p.value = p.value - pc - 1;
						if (!fitsInBits(p.value, p.len)) throw new AssemblerException ("Relative jump/call too far (" + p.value + "); must fit in " + p.len + " bits.");
					}
				}
//				System.err.println ("filling type " + p.type + " with value " + binstr(p.value));
				int v = p.value;
				for (int i=pattern.length()-1; i >= 0 ; i--) {
					if (pattern.charAt(i) == p.type) {
						res |= (v & 1) << (pattern.length() - i-1);
						v >>>= 1;
//						System.err.println("\ti = " + i + "; res = " + binstr(res) + "; v = " + binstr(v));
					}
				}
			}
			/*
			if (nw == 1) {
				return swab (res);
			} else if (nw == 2) {
				return swab2 (res);
			}
			*/
			return res;
		}

	}

	class InstrParam {

		public char type;
		public int len;
		public int value;
		public String label;

		public InstrParam (char type, int len) {
			this.type = type;
			this.len = len;
		}

		public void set (String s) throws AssemblerException {
			switch (type) {
				case 'r':
				case 'd':
					if (s.charAt(0) != 'r') throw new AssemblerException ("Expecting register, got " + s);
					int rnum = Integer.parseInt (s.substring(1));
					System.err.println("Rnum = " + rnum);
					switch (len) {
						case 5:
							if (rnum < 0 || rnum >= 32) throw new AssemblerException ("Register number must be beetween 0 and 31 for the " + name + " instruction");
							value = rnum;	break;
						case 4:
							if (rnum < 16 || rnum >= 32) throw new AssemblerException ("Register number must be beetween 16 and 31 for the " + name + " instruction");
							value = rnum - 16;	break;
						case 3:
							if (rnum < 16 || rnum >= 24) throw new AssemblerException ("Register number must be beetween 16 and 23 for the " + name + " instruction");
							value = rnum - 16;	break;
					}
					break;

				case 'k':
				case 'a':
					// could either be an integer or a label
					if (!Character.isDigit(s.charAt(0)) && s.charAt(0) != '-') {	// label
						label = s;
						break;
					}
					// else no break
				case 'b':
				case 'q':
					try {
						value = Integer.decode(s);
					} catch (NumberFormatException ex) {
						throw new AssemblerException ("Invalid immediate value [" + s + "]");
					}
					if (!fitsInBits(value, len)) throw new AssemblerException ("Immediate value " + value + " too long to fit in " + len + " bits.");
					break;
				case 'p':
					if (s.charAt(0) != 'r') throw new AssemblerException ("Expecting register, got " + s);
					rnum = Integer.parseInt (s.substring(1));
					if (rnum < 24 || rnum > 30 || rnum % 2 != 0) throw new AssemblerException ("Register pairs must start at 24,26,28, or 30");
					value = (rnum-24)/2;
					break;
				case 'x':
					switch (s) {
						case "X":
							value = len == 3 ? 7 : 3; break;
						case "Y":
							value = 2; break;
						case "Z":
							value = 0;
						default:
							throw new AssemblerException ("Address register for " + name + " must be X,Y, or Z");
					}
					break;
				case 'y':
					if (s.equals("Y")) {
						value = 1;
					} else if (s.equals("Z")) {
						value = 0;
					} else {
						throw new AssemblerException("Register pair for " + name + " must be Y or Z");
					}
					break;

			}
		}

	}

}
