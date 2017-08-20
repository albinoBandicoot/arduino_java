public class Token {

	public String lexeme;
	public Toktype type;
	public Marker loc;

	public Token (String lex, Toktype typ, Marker m) {
		lexeme = lex;
		type = typ;
		loc = m;
	}

	public long integralValue () {
		if (type == Toktype.CHAR_LIT) {
			return lexeme.charAt(0);
		} else if (type == Toktype.INT_LIT) {
			if (lexeme.startsWith("0x")) {
				return Long.parseLong (lexeme.substring(2), 16);
			} else {
				return Long.parseLong (lexeme);
			}
		} else {
			Log.fatal("Internal: trying to get integral value of something other than int or char literal (" + this + ")");
		}
		return -1;
	}

	public String toString () {
		return type + " [" + lexeme + "] @" + loc;
	}
}
