public class Token {

	public String lexeme;
	public Toktype type;
	public Marker loc;

	public Token (String lex, Toktype typ, Marker m) {
		lexeme = lex;
		type = typ;
		loc = m;
	}

	public String toString () {
		return type + " [" + lexeme + "] @" + loc;
	}
}
