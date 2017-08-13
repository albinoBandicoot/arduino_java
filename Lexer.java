//import static Toktype;
import java.util.*;
import java.io.*;
public class Lexer {

	public ArrayList<Token> toks;
	private ArrayList<String> lines;
	public Carat read, peek;
	private String name;

	private static final int EOF = -1;

	public class LexerException extends CompilerException {

		public LexerException (String mesg) {
			super("Lexer error at " + read + ":  " + mesg);
		}
	}

	class Carat {
		public int line, pos;
		public boolean eof;

		public Carat (int l, int p) {
			line = l;
			pos = p;
			eof = false;
		}

		public void set (Carat c) {
			line = c.line;
			pos = c.pos;
			eof = c.eof;
		}

		public void setb (Carat c) {
			set(c);
			unget();
			eof = false;
		}

		public boolean equals (Object other) {
			Carat c = (Carat) other;
			return c.line == line && c.pos == pos;
		}

		public String toString () {
			return line + ":" + pos + (eof ? "[EOF]" : "");
		}

		public int get () {
			if (eof) return EOF;
			return lines.get(line).charAt(pos);
		}

		public int getnext () {
			if (eof) return EOF;
			if (pos+1 == lines.get(line).length()) {
				if (line+1 == lines.size()) return EOF;
				return lines.get(line+1).charAt(0);
			}
			return lines.get(line).charAt(pos+1);
		}

		public void nextline () {
			line++;
			pos = 0;
		}

		public int read () {
			int x = get();
			eof = advance();
			return x;
		}

		public int aread () {
			eof = advance();
			return get();
		}

		public void unget () {
			pos -= 1;
			if (pos == -1) {
				if (line == 0) {
					pos = 0;
					return;
				}
				line --;
				pos = lines.get(line).length() - 1;
			}
		}

		public boolean advance ()  {
			if (eof) return true;
			pos++;
			if (pos == lines.get(line).length()) {
				pos = 0;
				line ++;
				if (line == lines.size()) return true;
			}
			return false;
		}
	}

	public Lexer (String name, InputStream in) {
		this.name = name;
		lines = new ArrayList<>();
		toks = new ArrayList<>();
		Scanner sc = new Scanner(in);
		while (sc.hasNextLine()) {
			lines.add (sc.nextLine() + " ");
		}
		read = new Carat(0,0);
		peek = new Carat(0,0);
	}

	public Marker marker () {
		return new Marker (name, read.line+1, read.pos);
	}

	public boolean isspace (int x) {
		if (x == EOF) return false;
		return Character.isSpace ((char) x);
	}

	public boolean on (String s) {
		int i = 0;
		while (i < s.length() && peek.read() == s.charAt(i)) i++;
		return i == s.length();
	}

	public Token attempt (String s, Toktype type) {
		if (on(s)) {
			Token t = new Token (s, type, marker());
			read.set(peek);
			return t;
		}
		peek.set(read);
		return null;
	}

	public boolean skipSpace () {
		while (isspace(peek.read())) ;
		read.setb(peek);
		boolean e = peek.eof;
		peek.set(read);
		return e;
	}

	public char convertEscape () {
		if (peek.get() == '\\') {
			int k = peek.aread();
			peek.advance();
			switch (k) {
				case 'n':	return '\n';
				case 'r':	return '\r';
				case '\\':	return '\\';
				case 't':	return '\t';
				default:	return (char) k;
			}
		} else {
			return (char) peek.read();
		}
	}

	private boolean isDigit (int dig, boolean hex) {
		if (hex) {
			return dig != EOF && (Character.isDigit(dig) || ('a' <= dig && dig <= 'f') || ('A' <= dig && dig <= 'F'));
		} else {
			return dig != EOF && Character.isDigit(dig);
		}
	}

	private boolean isIDStartChar (int c) {
		return c != EOF && (c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
	}

	private boolean isIDChar (int c) {
		return c != EOF && (c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'));
	}

	public String extract (boolean includePeek) {	// returns the string betewen read and peek 
		StringBuffer sb = new StringBuffer();
		Carat c = new Carat(0,0);
		c.set (read);
		while (!c.equals(peek)) {
			sb.append((char) c.read());
		}
		if (includePeek) sb.append((char) c.get());
		return sb + "";
	}

	public String extractNumber () throws LexerException {
		if (peek.get() == '0') {
			if (peek.aread() == 'x') {
				while (isDigit(peek.aread(), true)) ;
				if (isIDStartChar(peek.get())) throw new LexerException ("Integer literal adjacent to identifier [" + (char) peek.get() + "]");
				return extract(false);
			}
			peek.unget();
		}
		while (isDigit(peek.aread(), false)) ;	// ok bc we know first char is a digit
		if (peek.get() == '.') {
			System.out.println("decimal");
			while (isDigit(peek.aread(), false)) ;
			if (peek.get() == 'e' || peek.get() == 'E') {
				System.out.println("and exponent");
				if (peek.aread() == '-' || peek.get() == '+' || isDigit(peek.get(), false)) {
					while (isDigit(peek.aread(), false)) ;
				}
			}
		} else if (peek.get() == 'e' || peek.get() == 'E') {
			System.out.println("Exponent, no decimal");
			int c = peek.aread();
			if (c == '-' || c == '+' || isDigit(c, false)) {
				while (isDigit(peek.aread(), false)) ;
			}
		}
		return extract(false);
	}

	public Token getToken () throws LexerException {
		Token t;
		if (skipSpace()) return new Token ("", Toktype.EOF, marker());
		boolean foundComment = false;
		while (true) {
			foundComment = false;
			if (on("//")) {
				foundComment = true;
				System.out.println ("Found comment.");
				read.nextline();
				peek.nextline();
				if (skipSpace()) return new Token ("", Toktype.EOF, marker());
			}
			peek.set(read);
			if (on("/*")) {
				foundComment = true;
				int c;
				while (!peek.eof) {
					if (peek.read() == '*') {
						if (peek.read() == '/') {
							break;
						}
					}
				}
				if (peek.eof) throw new LexerException ("Unclosed block comment starting at " + read);
				read.set(peek);
			}
			peek.set(read);
			if (skipSpace()) return new Token ("", Toktype.EOF, marker());
			if (!foundComment) break;
		}

		if (peek.get() == '"') {	// string literal
			peek.advance();
			StringBuffer sb = new StringBuffer();
			while (!peek.eof && peek.get() != '"') {
				sb.append (convertEscape());
			}
			if (peek.eof) throw new LexerException ("Unclosed string literal");
			peek.advance();
			read.set(peek);
			return new Token (sb.toString(), Toktype.STR_LIT, marker());

		} else if (peek.get() == '\'') {	// character literal
			peek.advance();
			char c = convertEscape ();
			if (peek.eof) throw new LexerException ("Unclosed character literal");
			if (peek.get() != '\'') throw new LexerException ("More than one character in character literal");
			peek.advance();
			return new Token (new String(new char[]{c}), Toktype.CHAR_LIT, marker());

		} else if (Character.isDigit((char) peek.get())) {	// int or float
			String num = extractNumber();
			read.set(peek);
			if (num.contains(".") || num.contains("e") || num.contains("E")) {	// float
				return new Token (num, Toktype.FLOAT_LIT, marker());
			} else {
				return new Token (num, Toktype.INT_LIT, marker());
			}
		}
		if ((t = attempt (">>>=", Toktype.ULSHIFTEQ)) != null) return t;
		if ((t = attempt (">>=", Toktype.LSHIFTEQ)) != null) return t;
		if ((t = attempt ("<<=", Toktype.RSHIFTEQ)) != null) return t;
		if ((t = attempt ("+=", Toktype.PLUSEQ)) != null) return t;
		if ((t = attempt ("*=", Toktype.TIMESEQ)) != null) return t;
		if ((t = attempt ("-=", Toktype.MINUSEQ)) != null) return t;
		if ((t = attempt ("/=", Toktype.DIVIDEEQ)) != null) return t;
		if ((t = attempt ("%=", Toktype.MODEQ)) != null) return t;
		if ((t = attempt ("~=", Toktype.TWIDDLEEQ)) != null) return t;
		if ((t = attempt ("&=", Toktype.ANDEQ)) != null) return t;
		if ((t = attempt ("|=", Toktype.OREQ)) != null) return t;

		if ((t = attempt (">>>", Toktype.ULSHIFT)) != null) return t;
		if ((t = attempt (">>", Toktype.LSHIFT)) != null) return t;
		if ((t = attempt ("<<", Toktype.RSHIFT)) != null) return t;
		if ((t = attempt ("<=", Toktype.LE)) != null) return t;
		if ((t = attempt (">=", Toktype.GE)) != null) return t;
		if ((t = attempt ("==", Toktype.EQ)) != null) return t;
		if ((t = attempt ("!=", Toktype.NE)) != null) return t;
		if ((t = attempt ("&&", Toktype.DBLAND)) != null) return t;
		if ((t = attempt ("||", Toktype.DBLOR)) != null) return t;
		if ((t = attempt ("++", Toktype.INC)) != null) return t;
		if ((t = attempt ("--", Toktype.DEC)) != null) return t;
		if ((t = attempt ("+", Toktype.PLUS)) != null) return t;
		if ((t = attempt ("*", Toktype.TIMES)) != null) return t;
		if ((t = attempt ("-", Toktype.MINUS)) != null) return t;
		if ((t = attempt ("/", Toktype.DIVIDE)) != null) return t;
		if ((t = attempt ("%", Toktype.MOD)) != null) return t;
		if ((t = attempt ("(", Toktype.LPAREN)) != null) return t;
		if ((t = attempt (")", Toktype.RPAREN)) != null) return t;
		if ((t = attempt ("[", Toktype.LBRACK)) != null) return t;
		if ((t = attempt ("]", Toktype.RBRACK)) != null) return t;
		if ((t = attempt ("{", Toktype.LBRACE)) != null) return t;
		if ((t = attempt ("}", Toktype.RBRACE)) != null) return t;
		if ((t = attempt (":", Toktype.COLON)) != null) return t;
		if ((t = attempt (";", Toktype.SEMICOLON)) != null) return t;
		if ((t = attempt (".", Toktype.DOT)) != null) return t;
		if ((t = attempt (",", Toktype.COMMA)) != null) return t;
		if ((t = attempt ("~", Toktype.TWIDDLE)) != null) return t;
		if ((t = attempt ("!", Toktype.NOT)) != null) return t;
		if ((t = attempt ("=", Toktype.ASSIGN)) != null) return t;
		if ((t = attempt ("&", Toktype.AND)) != null) return t;
		if ((t = attempt ("|", Toktype.OR)) != null) return t;
		if ((t = attempt ("^", Toktype.XOR)) != null) return t;
		if ((t = attempt (">", Toktype.GT)) != null) return t;
		if ((t = attempt ("<", Toktype.LT)) != null) return t;
		if ((t = attempt ("?", Toktype.LT)) != null) return t;

		if ((t = attempt ("void", Toktype.VOID)) != null) return t;
		if ((t = attempt ("boolean", Toktype.PRIMTYPE)) != null) return t;
		if ((t = attempt ("char", Toktype.PRIMTYPE)) != null) return t;
		if ((t = attempt ("uchar", Toktype.PRIMTYPE)) != null) return t;
		if ((t = attempt ("byte", Toktype.PRIMTYPE)) != null) return t;
		if ((t = attempt ("ubyte", Toktype.PRIMTYPE)) != null) return t;
		if ((t = attempt ("short", Toktype.PRIMTYPE)) != null) return t;
		if ((t = attempt ("ushort", Toktype.PRIMTYPE)) != null) return t;
		if ((t = attempt ("int", Toktype.PRIMTYPE)) != null) return t;
		if ((t = attempt ("uint", Toktype.PRIMTYPE)) != null) return t;
		if ((t = attempt ("long", Toktype.PRIMTYPE)) != null) return t;
		if ((t = attempt ("ulong", Toktype.PRIMTYPE)) != null) return t;
		if ((t = attempt ("float", Toktype.PRIMTYPE)) != null) return t;
		if ((t = attempt ("double", Toktype.PRIMTYPE)) != null) return t;

		if ((t = attempt ("true", Toktype.BOOL_LIT)) != null) return t;
		if ((t = attempt ("false", Toktype.BOOL_LIT)) != null) return t;
		if ((t = attempt ("null", Toktype.OBJ_LIT)) != null) return t;

		if ((t = attempt ("if", Toktype.IF)) != null) return t;
		if ((t = attempt ("else", Toktype.ELSE)) != null) return t;
		if ((t = attempt ("for", Toktype.FOR)) != null) return t;
		if ((t = attempt ("while", Toktype.WHILE)) != null) return t;
		if ((t = attempt ("do", Toktype.DO)) != null) return t;
		if ((t = attempt ("switch", Toktype.SWITCH)) != null) return t;
		if ((t = attempt ("case", Toktype.CASE)) != null) return t;
		if ((t = attempt ("default", Toktype.DEFAULT)) != null) return t;
		if ((t = attempt ("new", Toktype.NEW)) != null) return t;
		if ((t = attempt ("delete", Toktype.DELETE)) != null) return t;
		if ((t = attempt ("return", Toktype.RETURN)) != null) return t;
		if ((t = attempt ("break", Toktype.BREAK)) != null) return t;
		if ((t = attempt ("continue", Toktype.CONTINUE)) != null) return t;
		if ((t = attempt ("public", Toktype.VIS)) != null) return t;
		if ((t = attempt ("protected", Toktype.VIS)) != null) return t;
		if ((t = attempt ("private", Toktype.VIS)) != null) return t;
		if ((t = attempt ("class", Toktype.CLASS)) != null) return t;
		if ((t = attempt ("extends", Toktype.EXTENDS)) != null) return t;
		if ((t = attempt ("instanceof", Toktype.INSTANCEOF)) != null) return t;
		if ((t = attempt ("static", Toktype.STATIC)) != null) return t;
//		if ((t = attempt ("this", Toktype.THIS)) != null) return t;
//		if ((t = attempt ("super", Toktype.SUPER)) != null) return t;
		if ((t = attempt ("abstract", Toktype.ABSTRACT)) != null) return t;
		if ((t = attempt ("final", Toktype.FINAL)) != null) return t;

		// identifier
		if (isIDStartChar (peek.get())) {
			while (isIDChar (peek.aread())) ;
			String id = extract(false);
			read.set(peek);
			return new Token (id, Toktype.ID, marker());
		}

		Log.error ("Returning null token at " + marker());
		return null;
	}

	public static void main (String[] args) throws LexerException {
		Lexer lex = new Lexer ("stdin", System.in);
		Token t;
		while ((t = lex.getToken()).type != Toktype.EOF) {
			System.out.println(t);
		}
	}
}

