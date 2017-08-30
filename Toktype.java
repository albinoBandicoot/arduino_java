public enum Toktype {
	VOID, PRIMTYPE, 
	
	CHAR_LIT, INT_LIT, STR_LIT, FLOAT_LIT, BOOL_LIT, OBJ_LIT, 

	LPAREN, RPAREN, LBRACK, RBRACK, LBRACE, RBRACE, COMMA, SEMICOLON, COLON, DOT,

	PLUS, MINUS, TWIDDLE, TIMES, DIVIDE, MOD, AND, OR, XOR, LSHIFT, ULSHIFT, RSHIFT, INSTANCEOF,
	PLUSEQ, MINUSEQ, TWIDDLEEQ, TIMESEQ, DIVIDEEQ, MODEQ, ANDEQ, OREQ, XOREQ, LSHIFTEQ, ULSHIFTEQ, RSHIFTEQ, 
	DBLAND, DBLOR, INC, DEC, NOT, LT, LE, EQ, NE, GE, GT, ASSIGN, 
	
	ID,
	
	IF, ELSE, WHILE, DO, FOR, SWITCH, CASE, DEFAULT, NEW, DELETE, RETURN, BREAK, CONTINUE, VIS, CLASS, EXTENDS, 
	
	STATIC, ABSTRACT, FINAL, NATIVE,

	THIS, SUPER, 

	EOF;

	public boolean isLiteral () {
		int a = ordinal();
		return a >= CHAR_LIT.ordinal() && a <= OBJ_LIT.ordinal();
	}

	public boolean isOpEq () {
		int a = ordinal();
		return a >= PLUSEQ.ordinal() && a <= RSHIFTEQ.ordinal();
	}
	public boolean isOperator () {
		int a = ordinal();
		return a >= PLUS.ordinal() && a <= ASSIGN.ordinal();
	}
}
