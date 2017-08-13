import java.util.*;
import java.io.*;
public class Assembler {

	public static int line;

	private ArrayList<Optemplate.Op> ops;
	private int pc = 0;
	private HashMap<String, Integer> labels;
	private boolean datamode = false;
	private byte[] data;
	private boolean[] used;
	private int dtop = 0;


	public Assembler () {
		ops = new ArrayList<>();
		labels = new HashMap<>();
		data = new byte[32768];	
		used = new boolean[32768];
	}

	private static String skip (String s, String delim) {
		int i=0;
		while (i < s.length() && delim.contains(new String(new char[]{s.charAt(i)}))) i++; 
		return s.substring(i);
	}

	private static String token (String s, String delim) {
		int i=0;
		while (i < s.length() && !delim.contains(new String(new char[]{s.charAt(i)}))) i++; 
		return s.substring(0,i);
	}

	private static String token (String s, char delim) {
		int i=0;
		while (i < s.length() && s.charAt(i) != delim) {
			if (delim == ' ' && s.charAt(i) == '\t') break;
			i++;
		}
		return s.substring(0,i);
	}

	private static String getStringLiteral (String s) throws AssemblerException {
		char delim = s.charAt(0);
		StringBuffer sb = new StringBuffer();
		if (delim != '\'' && delim != '"') throw new AssemblerException ("String literals must start with either ' or \"");
		int i = 1;
		char c = s.charAt(i);
		while (c != delim) {
			if (c == '\\') {
				char d = s.charAt(i+1);
				switch (d) {
					case '\'':
						sb.append ('\'');	break;
					case '"':
						sb.append ('"');	break;
					case '\n':
						sb.append ('\n');	break;
					case '\t':
						sb.append ('\t');	break;
					case '\\':
						sb.append ('\\');
				}
				i += 2;
			} else {
				sb.append (c);
				i ++;
			}
			if (i < s.length()) {
				c = s.charAt(i);
			} else {
				throw new AssemblerException ("Runaway string literal");
			}
		}
		return sb.toString();
	}

	public int getLabel (String s) throws AssemblerException {
		int mode = 0;
		if (s.endsWith(".lo")) {
			mode = 1;
			s = s.substring(0, s.length() - 3);
		} else if (s.endsWith(".hi")) {
			mode = 2;
			s = s.substring(0, s.length() - 3);
		}
		Integer lab = labels.get(s);
		if (lab == null) throw new AssemblerException ("Label " + s + " does not exist.");
		System.err.println("Got label " + s + " with value " + lab);
		switch (mode) {
			case 0:	return lab;
			case 1:	return lab & 0xff;
			case 2: return (lab >>> 8) & 0xff;
		}
		return 0;
	}	

	private void storeByteData (int x) throws AssemblerException {
		int p = pc * (datamode ? 1 : 2);
		if (used[p]) throw new AssemblerException ("Attempted to store byte into occupied location " + p);
		dtop = Math.max (dtop, p);
		used[p] = true;
		data[pc++] = (byte) x;
	}

	private void storeShortData (int y) throws AssemblerException {
		int p = pc * (datamode ? 1 : 2);
		if (used[p] || used[p+1]) throw new AssemblerException ("Attempted to store word into occupied location " + p);
		dtop = Math.max (dtop, p+1);
		data[p] = (byte) (y & 0xff);
		data[p+1] = (byte) ((y >>> 8) & 0xff);
		used[p] = used[p+1] = true;
		pc += datamode ? 2 : 1;	
	}

	private void storeLongData (int x) throws AssemblerException {
		int p = pc * (datamode ? 1 : 2);
		if (used[p] || used[p+1] || used[p+2] || used[p+3]) throw new AssemblerException ("Attempted to store long into occupied location " + p);
		dtop = Math.max (dtop, p+3);
		data[p] = (byte) (x & 0xff);
		data[p+1] = (byte) ((x >>> 8) & 0xff);
		data[p+2] = (byte) ((x >>> 16) & 0xff);
		data[p+3] = (byte) ((x >>> 24) & 0xff);
		used[p] = used[p+1] = used[p+2] = used[p+3] = true;
		pc += datamode ? 4 : 2;
	}

	public void assemble (Scanner sc, PrintWriter out) throws AssemblerException, IOException {
		line = 0;
		while (sc.hasNextLine()) {
			line++;
			String line = sc.nextLine();
			int com = line.indexOf ('#');
			if (com != -1) line = line.substring(0,com);	// discard comments
			int c = 0;
			if ((c = line.indexOf(':')) != -1) {
				if (line.substring(0,c).contains(" ")) throw new AssemblerException ("Label contains space!");
				labels.put (line.substring(0,c), pc);
				line = skip(line.substring(c+1), " \t");
			}
			line = skip(line, " \t");
			String mne = token(line, " \t");
			line = skip (line.substring(mne.length()), " \t");
			if (mne.equals("")) continue;
			if (mne.startsWith(".")) {		// assembler directive
				String arg1, arg2;
				switch (mne) {
					case ".data":
						if (datamode) {
							System.err.println("Warning: alread in data mode!");
						}
						datamode = true;
						pc = pc*2;
						break;
					case ".text":
						if (!datamode) {
							System.err.println("Warning: alread in text mode!");
						}
						datamode = false;
						pc = (pc+1)/2;
						continue;
					case ".define":
						arg1 = token (line, " \t");
						line = skip (line.substring(arg1.length()), " \t");
						arg2 = token (line, " \t\n");
						labels.put (arg1, Integer.decode (arg2));
						continue;
					case ".loc":
						pc = Integer.decode (token(line, " \t"));
						continue;
					case ".skip":
						pc += Integer.decode (token(line, " \t"));
						continue;
					case ".byte":
						storeByteData (Integer.decode (token(line, " \t")));
						break;
					case ".word":
						storeShortData (Integer.decode (token(line, " \t")));
						break;
					case ".long":
						storeLongData (Integer.decode (token(line, " \t")));
						break;
					case ".float":
						storeLongData (Float.floatToRawIntBits(Float.parseFloat (token(line, " \t"))));
						break;
					case ".string":
						arg1 = getStringLiteral(line);
						for (int i=0; i < arg1.length(); i++) {
							storeByteData ((byte) arg1.charAt(i));
						}
						break;
					default:
						throw new AssemblerException ("Unknown directive " + mne);
				}
			}
			if (datamode) { 

			} else {	// text section
				Optemplate templ = Optemplate.templates.get(mne);
				if (templ == null) throw new AssemblerException ("Invalid opcode [" + mne + "]");
				Optemplate.Op op = templ.new Op(pc);
				pc += templ.nw;
				for (Optemplate.InstrParam p : op.myparams) {
					String ptok = token(line, " \t,");
					p.set (ptok);
					line = skip (line.substring(ptok.length()), " \t,");
				}
				ops.add (op.pseudo());
			}
		}

		// write the instructions to memory
		datamode = false;
		for (Optemplate.Op op : ops) {
			pc = op.pc;
			int v = op.generate(this);
			if (op.parent.nw == 1) {
				storeShortData ((short) v);
			} else {
				storeShortData ((v >> 16) & 0xffff);
				storeShortData (v & 0xffff);
			}
		}
		
		for (int i=0; i <= dtop; i++) {
			out.print (Optemplate.istr(((int) data[i]) & 0xff, 16, 2));
		}
		out.close();
	}

	public static void main (String[] args) throws IOException, AssemblerException {
		Optemplate.readTemplates (new File ("opcodes"));
		Assembler as = new Assembler ();
		PrintWriter out = new PrintWriter (System.out);
		if (args.length >= 2 && !args[1].equals("-")) {
			out = new PrintWriter (args[1]);
		}
		as.assemble (new Scanner (new File(args[0])), out);
	}

}
