import java.util.*;
public class Parser {

	public static Parser currentParser = null;
	public static Marker getMarker () {
		if (currentParser == null) return new Marker ("<Generated>", 0,0);
		return currentParser.tok.loc;
	}

	private Lexer lex;
	private Token tok, next, pb;
	private Deque<Tree> block_chain;

	public class ParserException extends CompilerException {

		public ParserException (String mesg) {
			super("[PARSE] at " + tok + ": " + mesg);
		}
	}

	private class Chain {
		public Tree base, p;

		public Chain () {
		}

		public void add (Tree t) {
			if (base == null) {
				base = t;
			} else {
				p.next = t;
			}
			p = t;
		}
	}

	public Parser (Lexer lex) throws CompilerException {
		this.lex = lex;
		tok = lex.getToken();
		next = lex.getToken();
		currentParser = this;
		block_chain = new LinkedList<>();
	}

	public boolean on (String s) throws CompilerException {
		if (tok.lexeme.equals(s)) {
			advance();
			return true;
		}
		return false;
	}

	public boolean aon (Toktype t) throws CompilerException {
		advance();
		return tok.type == t;
	}

	public boolean ona (Toktype... types) throws CompilerException {
		for (Toktype t : types) {
			if (tok.type == t) {
				advance();
				return true;
			}
		}
		return false;
	}

	public boolean on (Toktype... types) {
		for (Toktype t : types) {
			if (tok.type == t) return true;
		}
		return false;
	}

	public void advance () throws Lexer.LexerException {
		tok = next;
		next = pb == null ? lex.getToken() : pb;
		pb = null;
	}

	public void putback (Token t) {
		pb = next;
		next = tok;
		tok = t;
	}

	public Tree program () throws CompilerException {
		return klass();
	}

	public Tree klass () throws CompilerException {
		DeclTree t = new DeclTree (Treetype.CLASSDEC);
		Type.Ext ex = ext();
		if (!ona(Toktype.CLASS)) Log.fatal(new ParserException ("Expected 'class'"));
		if (!on(Toktype.ID)) Log.fatal(new ParserException ("Expected class name after 'class'"));
		Klass kt = new Klass (tok.lexeme);
		t.ext = ex;
		if (aon(Toktype.EXTENDS)) {
			if (!aon(Toktype.ID)) Log.fatal(new ParserException ("Expected class name after 'extends'"));
			kt.superclass = new Klass(tok.lexeme);
			advance();
		} else {
			kt.superclass = Type.objekt;
		}
		if (!on(Toktype.LBRACE)) Log.fatal(new ParserException ("Expected { after class introduction"));
		advance();
		Tree d = declist(t);
		t.body = d;
		t.name = kt.name;
		t.dtype = kt;
		if (!ona (Toktype.RBRACE)) Log.fatal(new ParserException ("Expected } after class body"));
		if (!on (Toktype.EOF)) Log.error(new ParserException ("Garbage at end of file"));
		kt.definition = t;
		return t;
	}

	private void checkIllegalName (String s) throws CompilerException {
		if (s.equals("this") || s.equals("super")) Log.error(new ParserException ("'" + s + "' is a reserved word and cannot be used as an identifier"));
	}

	public Tree declist (DeclTree cd) throws CompilerException {
		Chain list = new Chain();
		Chain statinit = new Chain();
		Chain instinit = new Chain();
		while (!on(Toktype.RBRACE) && !on(Toktype.EOF)) {
			// static or instanec initialization block
			if (on(Toktype.STATIC) && next.type == Toktype.LBRACE || on(Toktype.LBRACE)) {
				boolean st = ona (Toktype.STATIC);
				Tree b = block();
				if (st) {
					statinit.add (b);
				} else {
					instinit.add (b);
				}
				continue;
			}
			// variable or function declaration
			DeclTree t = new DeclTree(Treetype.VARDEC);	// may change
			DeclTree proto = t;
			t.vis = DeclTree.Vis.PUBLIC;
			if (on(Toktype.VIS)) {
				t.vis = tok.lexeme.equals("public") ? DeclTree.Vis.PUBLIC : DeclTree.Vis.PRIVATE;
				advance();
			}
			if (ona(Toktype.NATIVE)) t.isNative = true;
			t.isStatic = ona(Toktype.STATIC);
			if (ona(Toktype.NATIVE)) t.isNative = true;
			t.ext = ext();
			if (ona(Toktype.NATIVE)) t.isNative = true;
			Type dectype = type();
			t.dtype = dectype;
			boolean cont = true;
			while (cont) {
				if (on(Toktype.LPAREN)) {	// constructor
					t.name = "new";
					if (proto != t) Log.fatal(new ParserException ("Unexpected ("));
				} else {
					if (!on(Toktype.ID)) Log.fatal( new ParserException ("Expected identifier for declaration name"));
					checkIllegalName (tok.lexeme);
					t.name = tok.lexeme;
					advance();
				}
				if (on(Toktype.SEMICOLON) || on(Toktype.ASSIGN)) {
					list.add (t);
					if (ona(Toktype.ASSIGN)) {	
						t.body = expr();
						if (ona(Toktype.SEMICOLON)) break; 
						if (!ona(Toktype.COMMA)) Log.fatal(new ParserException ("Expecting comma"));
					} else {
						advance();
						break;
					}
				} else if (ona (Toktype.LPAREN)) {
					cont = false;
					if (proto != t) Log.fatal(new ParserException ("Unexpected ("));
					t.type = Treetype.FUNDEC;
					Chain params = new Chain();
					while (!ona(Toktype.RPAREN)) {
						params.add (param());
						if (ona(Toktype.COMMA)) ;
					}
					t.params = (DeclTree) params.base;
					if (t.ext == Type.Ext.ABSTRACT) {
						if (!ona(Toktype.SEMICOLON)) Log.error (new ParserException("Expected semicolon after abstract function declaration"));
					} else if (t.isNative) {
						if (!ona(Toktype.SEMICOLON)) Log.error(new ParserException("Expected semicolon after native function declaration"));
					} else {
						t.body = block();
					}
					list.add (t);
				} else if (ona(Toktype.COMMA)) {
					list.add (t);
				} else {
					Log.fatal(new ParserException ("Garbage after declaration"));
				}
				t = new DeclTree (Treetype.VARDEC);
				t.ext = proto.ext;
				t.isStatic = proto.isStatic;
				t.vis = proto.vis;
				t.dtype = proto.dtype;
			}
		}
		if (on(Toktype.EOF)) Log.error(new ParserException ("Hit end of file looking for }"));
		cd.static_init = statinit.base;
		cd.inst_init = instinit.base;
		return list.base;
	}

	public DeclTree param () throws CompilerException {
		DeclTree t = new DeclTree(Treetype.VARDEC);
		t.ext = ext();
		t.dtype = type();
		t.isStatic = true;
		if (!on(Toktype.ID)) Log.fatal(new ParserException ("Expecting name after parameter type specifier"));
		checkIllegalName (tok.lexeme);
		t.name = tok.lexeme;
		advance();
		return t;
	}

	public Type type () throws CompilerException {
		if (ona(Toktype.VOID)) return Type.voyd;
		Type base = null;
		if (on(Toktype.PRIMTYPE)) {
			base = Primitive.parse(tok.lexeme);
			advance();
		} else if (on(Toktype.ID)) {
			base = new Klass (tok.lexeme);
			advance();
		} else {
			Log.fatal( new ParserException ("Expecting type - must start with either a primitive type or a class ID"));
		}
		Type arr = base;
		while (ona(Toktype.LBRACK)) {
			if (!ona(Toktype.RBRACK)) Log.fatal(new ParserException ("Expected ] after ["));
			arr = new Array(arr);

		}
		return arr;
	}

	public Type.Ext ext () throws CompilerException {
		if (ona(Toktype.FINAL)) return Type.Ext.FINAL;
		if (ona(Toktype.ABSTRACT)) return Type.Ext.ABSTRACT;
		return Type.Ext.REG;
	}

	public Tree block () throws CompilerException {
		if (ona(Toktype.LBRACE)) {
			StmtTree b = new StmtTree (Treetype.BLOCK);
			while (!ona(Toktype.RBRACE)) {
				Tree s = stmt();
				if (s != null) b.ch.add(s);	
			}
			return b;
		} else {
			return stmt();
		}
	}

	public Tree stmt () throws CompilerException {
		if (ona(Toktype.IF)) return if_stmt();
		if (ona(Toktype.FOR)) return for_stmt();
		if (ona(Toktype.WHILE)) return while_stmt();
		if (ona(Toktype.DO)) return do_stmt();
		if (ona(Toktype.SWITCH)) return switch_stmt();
		if (ona(Toktype.RETURN)) return return_stmt();
		if (ona(Toktype.DELETE)) return delete_stmt();
		if (on(Toktype.LBRACE)) return block();
		if (ona(Toktype.BREAK)) {
			if (!ona(Toktype.SEMICOLON)) Log.error(new ParserException ("Expecting semicolon after statement."));
			StmtTree t = new StmtTree(Treetype.BREAK);
			if (block_chain.isEmpty()) Log.error(new ParserException ("break outside switch or loop"));
			t.enclosingBlock = block_chain.peek();
			return t;
		}
		if (ona(Toktype.CONTINUE)) { 
			if (!ona(Toktype.SEMICOLON)) Log.error(new ParserException ("Expecting semicolon after statement."));
			StmtTree t = new StmtTree(Treetype.CONTINUE);
			if (block_chain.isEmpty()) Log.error(new ParserException ("continue outside switch or loop"));
			t.enclosingBlock = block_chain.peek();
			return t;
		}
		if (on(Toktype.INC) || on(Toktype.DEC)) {	// pre-increment/decrement
			ExprTree e = new ExprTree (Treetype.OP, tok);
			advance();
			e.right = lvalue();
			if (!ona(Toktype.SEMICOLON)) Log.error(new ParserException ("Expecting semicolon after statement."));
			return e;
		}
		Type.Ext ext = ext();
		if (on(Toktype.PRIMTYPE)) {	// must be a vardec
			return varinit(ext);
		}
		if (on(Toktype.ID)) {	// could be vardec (class type), or assignment, or function call
			Token id = tok;
			advance();
			if (on(Toktype.LBRACK) && next.type == Toktype.RBRACK || on(Toktype.ID)) {	// array variable declaration or object declaration
				putback(id);
				return varinit(ext);
			} else {	// either funcall or assignment
				putback (id);
				ExprTree t = lvalue();
				if (t.type == Treetype.FUNCALL || t.type == Treetype.OP && (t.op.type == Toktype.DOT && t.right.type == Treetype.FUNCALL)) {
					if (!ona(Toktype.SEMICOLON)) Log.error(new ParserException ("Expecting semicolon after lvalue"));
					return t;
				} else if (on(Toktype.INC) || on(Toktype.DEC)) {
					ExprTree e = new ExprTree (Treetype.OP, tok);
					advance();
					e.left = t;
					if (!ona(Toktype.SEMICOLON)) Log.error(new ParserException ("Expecting semicolon after statement."));
					return e;
				} else if (tok.type.isOpEq() || on(Toktype.ASSIGN)) {
					ExprTree e = new ExprTree (Treetype.OP, tok);
					advance();
					e.left = t;
					e.right = expr();
					if (!ona(Toktype.SEMICOLON)) Log.error(new ParserException ("Expecting semicolon"));
					return e;
				} else {
					Log.fatal(new ParserException ("Garbage after lvalue"));
				}
			}
		}
		Log.fatal(new ParserException ("Expecting valid statement start (if, while, for, switch, break, continue, return; or a variable declaration or assignment or function call."));	
		return null;
	}

	public ExprTree funcall_params () throws CompilerException {
		Chain params = new Chain();
		while (!ona(Toktype.RPAREN)) {
			params.add (expr());
			if (ona(Toktype.COMMA)) ;
		}
		//if (!ona(Toktype.SEMICOLON)) throw new ParserException ("Expecting semicolon");
		return (ExprTree) params.base;
	}

	public DeclTree varinit (Type.Ext ext) throws CompilerException {
		DeclTree d = new DeclTree (Treetype.VARDEC);
		d.ext = ext;
		d.dtype = type();
		if (!on(Toktype.ID)) Log.fatal(new ParserException ("Expected name after type"));
		checkIllegalName (tok.lexeme);
		d.name = tok.lexeme;
		if (aon(Toktype.ASSIGN)) {
			ExprTree e = new ExprTree (Treetype.OP, tok);
			advance();
			d.body = expr();
		} 
		if (!ona(Toktype.SEMICOLON)) Log.error(new ParserException ("Expecting semicolon or assignment"));
		return d;
	}

	public Tree if_stmt () throws CompilerException {
		StmtTree t = new StmtTree (Treetype.IF);
		if (!ona(Toktype.LPAREN)) Log.error(new ParserException ("Expecting ( after 'if')"));
		ExprTree cond = expr();
		if (cond == null) Log.error(new ParserException ("if condition cannot be empty"));
		t.ch.add(cond);
		if (!ona(Toktype.RPAREN)) Log.fatal(new ParserException ("Unclosed ) in 'if'"));
		t.ch.add(block());
		if (ona(Toktype.ELSE)) {
			t.ch.add(block());
		}
		return t;
	}

	public Tree while_stmt () throws CompilerException {
		StmtTree t = new StmtTree (Treetype.WHILE);
		block_chain.push(t);
		if (!ona(Toktype.LPAREN)) Log.fatal(new ParserException ("Expecting ( after 'while'"));
		t.ch.add(expr());
		if (!ona(Toktype.RPAREN)) Log.fatal(new ParserException ("Unclosed ) in 'while'"));
		t.ch.add(block());
		block_chain.pop();
		return t;
	}

	public Tree do_stmt () throws CompilerException {
		StmtTree t = new StmtTree (Treetype.DO);
		block_chain.push(t);
		Tree body = block();
		if (!ona(Toktype.WHILE)) Log.fatal(new ParserException ("Expecting 'while' after do-loop body"));
		if (!ona(Toktype.LPAREN)) Log.fatal(new ParserException ("Expecting ( after 'while'"));
		t.ch.add(expr());
		t.ch.add(body);
		if (!ona(Toktype.RPAREN)) Log.fatal( new ParserException ("Unclosed ) in do-while"));
		if (!ona(Toktype.SEMICOLON)) Log.error( new ParserException ("Expected semicolon after do-loop condition"));
		block_chain.pop();
		return t;
	}

	public Tree for_stmt () throws CompilerException {
		StmtTree t = new StmtTree (Treetype.FOR);
		block_chain.push(t);
		if (!ona(Toktype.LPAREN)) Log.fatal( new ParserException ("Expecting ( after 'for')"));
		// can either be an expr or a varinit. Check if we start with a type.
		Type.Ext ext = ext();
		if (on(Toktype.PRIMTYPE)) {	// must be a varinit
			t.ch.add(varinit(ext));
		} else if (on(Toktype.ID)) {
			Token id = tok;
			advance();
			if (on(Toktype.LBRACK) && next.type == Toktype.RBRACK || on(Toktype.ID)) {	
				putback(id);
				t.ch.add(varinit(ext));
			} else {
				putback(id);
			}
		}
		if (t.ch.isEmpty()) {	// wasn't a varinit. get an expr
			t.ch.add(expr());
			if (!ona(Toktype.SEMICOLON)) Log.error(new ParserException ("Expecting semicolon after for loop initializer"));
		}
		t.ch.add(expr());	// condition
		if (!ona(Toktype.SEMICOLON)) Log.error(new ParserException ("Expecting semicolon after for loop condition"));
		t.ch.add(expr());	// increment
		if (!ona(Toktype.RPAREN)) Log.fatal( new ParserException ("Unclosed ) in 'for'"));
		t.ch.add(block());	// body
		block_chain.pop();
		return t;
	}

	public StmtTree switch_stmt () throws CompilerException {
		StmtTree t = new StmtTree (Treetype.SWITCH);
		block_chain.push(t);
		if (!ona(Toktype.LPAREN)) Log.fatal(new ParserException ("Expecting ( after switch"));
		t.ch.add(expr());	// what we're switching on
		if (!ona(Toktype.RPAREN)) Log.error(new ParserException ("Expecting ) after switch expression"));
		if (!ona(Toktype.LBRACE)) Log.fatal(new ParserException ("Expecting { after switch"));
		List<StmtTree.Case> currCases = new ArrayList<>();
		boolean seenDefault = false;
		boolean beforeCases = true;
		StmtTree blk = new StmtTree (Treetype.BLOCK);
		while (!ona(Toktype.RBRACE)) {
			if (on(Toktype.CASE) || on(Toktype.DEFAULT)) {
				blk = new StmtTree (Treetype.BLOCK);
				beforeCases = false;
				if (on(Toktype.DEFAULT)) {
					if (seenDefault) Log.error ("Only one default branch allowed in a switch statement");
					seenDefault = true;
					currCases.add (t.new Case(tok));
					advance();
					if (!ona(Toktype.COLON)) Log.fatal (new ParserException("Expecting : after case label"));
				} else {
					advance();
					if (tok.type == Toktype.INT_LIT || tok.type == Toktype.CHAR_LIT || tok.type == Toktype.STR_LIT) {
						currCases.add (t.new Case(tok));
						advance();
						if (!ona(Toktype.COLON)) {
							if (tok.type.isOperator()) Log.fatal(new ParserException("Case labels must be int, char, or string literals"));
							Log.fatal (new ParserException ("Expecting : after case label"));
						}
					} else {
						Log.fatal (new ParserException("Case labels must be int, char, or string literals"));
					}
				}
			} else {
				if (beforeCases) {
					Log.error (new ParserException ("Expecting case, default, or }"));
					advance();
					continue;
				}
				Tree s = stmt();
				blk.ch.add (s);
				for (StmtTree.Case c : currCases) {
					c.body = blk;
					t.cases.add (c);
				}
				if (!currCases.isEmpty()) {
					t.ch.add(blk);
				}
				currCases.clear();
			}
		}
		block_chain.pop();
		return t;
	}

	public Tree return_stmt () throws CompilerException {
		StmtTree t = new StmtTree (Treetype.RETURN);
		t.ch.add(expr());
		if (!ona(Toktype.SEMICOLON)) Log.error(new ParserException ("Expected semicolon after return expression"));
		return t;
	}

	public Tree delete_stmt () throws CompilerException {
		StmtTree t = new StmtTree (Treetype.DELETE);
		do {
			t.ch.add (expr());
		} while (ona(Toktype.COMMA));
		if (!ona(Toktype.SEMICOLON)) Log.error(new ParserException ("Expected semicolon after list of objects to delete"));
		return t;
	}

	public ExprTree expr () throws CompilerException {
		if (on(Toktype.LBRACE)) return aggregate();
		return En(-2);
	}

	public ExprTree En (int i) throws CompilerException {
		switch (i) {
			case -2:	
				ExprTree k = En(-1);
				if (tok.type.isOpEq() || on(Toktype.ASSIGN)) {
					ExprTree a = new ExprTree (Treetype.OP, tok);
					advance();
					a.left = k;
					a.right = En(-2);
					return a;
				}
				return k;
			case -1:	return E(i, Toktype.DBLOR);
			case 0:		return E(i, Toktype.DBLAND);
			case 1:		return E(i, Toktype.OR);
			case 2:		return E(i, Toktype.XOR);
			case 3:		return E(i, Toktype.AND);
			case 4:		return E(i, Toktype.EQ, Toktype.NE);
			case 5:		return E(i, Toktype.LE, Toktype.LT, Toktype.GT, Toktype.GE);	
			case 6:		// instanceof
						ExprTree q = En(7);
						if (on(Toktype.INSTANCEOF)) {
							ExprTree e = new ExprTree(Treetype.OP, tok);
							advance();
							Type t = type();
							e.left = q;
							e.cast_type = t;
							return e;
						}
						return q;
			case 7:		return E(i, Toktype.LSHIFT, Toktype.RSHIFT, Toktype.ULSHIFT);
			case 8:		return E(i, Toktype.PLUS, Toktype.MINUS);
			case 9:		return E(i, Toktype.TIMES, Toktype.DIVIDE, Toktype.MOD);
			case 10:	
						if (on(Toktype.INC) || on(Toktype.DEC)) {	// preincrement
							ExprTree e = new ExprTree(Treetype.OP, tok);
							advance();
							e.right = En(11);
							return e;
						} else if (on(Toktype.NOT, Toktype.MINUS, Toktype.TWIDDLE)) {	// unary op
							ExprTree p = new ExprTree (Treetype.OP, tok);
							advance();
							p.left = En(10);
							return p;
						} else if (on(Toktype.LPAREN)) {	// typecast -- maybe. Or could be a parenthesized expr.
							ExprTree p = new ExprTree (Treetype.CAST);
							advance();
							p.cast_type = type();
							if (!ona(Toktype.RPAREN)) Log.error(new ParserException ("Expecting ) after cast type"));
							p.left = En(10);
							return p;
						} else {
							ExprTree e = En(11);
							if (on(Toktype.INC, Toktype.DEC)) {
								ExprTree p = new ExprTree (Treetype.OP, tok);
								advance();
								p.right = e;
								return p;
							}
							return e;
						}
			case 11:
						if (ona(Toktype.LPAREN)) {
							ExprTree e = expr();
							if (!ona(Toktype.RPAREN)) Log.error( new ParserException ("Unclosed ("));
							return e;
						} else if (tok.type.isLiteral()) {
							ExprTree e = new ExprTree (Treetype.VALUE, tok);
							e.dtype = Type.literalType (tok);
							advance();
							return e;
						} else if (ona(Toktype.NEW)) {
							ExprTree e = new ExprTree (Treetype.NEW);
							if (on(Toktype.PRIMTYPE, Toktype.ID)) {
								int ndims = 0;
								Type base;
								if (on(Toktype.ID)) {
									base = new Klass (tok.lexeme);
								} else {
									base = Primitive.parse (tok.lexeme);
								}
								advance();
								if (on(Toktype.LBRACK)) {
									Chain c = new Chain();
									Type arr = base;
									while (ona(Toktype.LBRACK)) {
										c.add(expr());
										if (!ona(Toktype.RBRACK)) Log.error( new ParserException ("Unclosed ["));
										arr = new Array(arr);
									}
									e.cast_type = arr;
									e.params = (ExprTree) c.base;
									if (on(Toktype.LBRACE)) {	// aggregate initializer
										e.left = aggregate();
									}
									return e;
								} else if (base instanceof Klass) {
									e.cast_type = base;
									if (ona(Toktype.LPAREN)) {
										e.params = funcall_params();
										return e;
									} else {
										Log.fatal(new ParserException ("Expecting '(' to begin parameters for constructor call"));
									}
								} else {
									Log.error( new ParserException ("Can't use 'new' to create a primitive type"));
								}
							} else Log.fatal(new ParserException ("Expecting type after 'new'"));
						} else {
							ExprTree e = lvalue();
							if (ona(Toktype.LPAREN)) {	// funcall
								ExprTree f = new ExprTree (Treetype.FUNCALL);
								f.left = e;
								f.params = funcall_params();
								return f;
							}
							return e;
						}
		}
		Log.fatal(new ParserException ("Internal error - fell off switch in En"));
		return null;
	}

	public ExprTree E (int i, Toktype... types) throws CompilerException {
		ExprTree e = En(i+1);
		while (on(types)) {
			ExprTree p = new ExprTree (Treetype.OP, tok);
			advance();
			p.left = e;
			p.right = En(i+1);
			e = p;
		}
		return e;
	}

	public ExprTree lvalue () throws CompilerException {
		if (!on(Toktype.ID)) return null;	// to allow empty expressions
		ExprTree e = lvalue_comp(); //new ExprTree (Treetype.ID, tok.lexeme);
//		advance();
		while (true) {
			if (on(Toktype.DOT)) {
				ExprTree d = new ExprTree(Treetype.OP, tok);
				advance();
				d.left = e;
				d.right = lvalue_comp ();
				/*
				d.right = new ExprTree (Treetype.ID, tok.lexeme);
				advance();
				*/
				e = d;
			} else if (on(Toktype.LBRACK)) {
				ExprTree d = new ExprTree (Treetype.OP, tok);
				advance();
				d.left = e;
				d.right = expr();
				if (!ona(Toktype.RBRACK)) Log.error( new ParserException ("Unclosed ["));
				e = d;
/*			} else if (ona(Toktype.LPAREN)) {
				ExprTree d = new ExprTree (Treetype.FUNCALL);
				d.left = e;
				d.params = funcall_params();
				e = d;
*/			} else break;

		}
		return e;
	}

	public ExprTree lvalue_comp () throws CompilerException {
		if (!on(Toktype.ID)) Log.fatal(new ParserException ("Expected ID after '.'"));
		ExprTree e = new ExprTree (Treetype.ID, tok.lexeme);
		if (aon(Toktype.LPAREN)) {
			advance();
			ExprTree f = new ExprTree (Treetype.FUNCALL);
			f.left = e;
			f.params = funcall_params();
			return f;
		}
		return e;
	}

	public ExprTree aggregate () throws CompilerException {
		if (ona(Toktype.LBRACE)) {
			ExprTree e = new ExprTree (Treetype.AGG);
			Chain c = new Chain();
			boolean nested = false;
			boolean first = true;
			while (!ona(Toktype.RBRACE)) {
				if (on(Toktype.LBRACE)) {	// nested aggregate
					if (!first && !nested) Log.error(new ParserException ("Aggregate contains a mixture of scalar and aggregate entries"));
					nested = true;
					c.add(aggregate());
				} else {
					if (!first && nested) Log.error(new ParserException ("Aggregate contains a mixture of scalar and aggregate entries"));
					c.add (expr());
				}
				if (ona(Toktype.COMMA)) ;
				first = false;
			}
			if (nested) {
				e.dtype = new Array(((ExprTree) c.base).dtype);
			} else {
				e.dtype = new Array(Type.voyd);	// fill in the actual type later
			}
			e.left = (ExprTree) c.base;
			return e;
		} else Log.fatal(new ParserException ("Expected { to start aggregate"));
		return null;
	}


	public static void main (String[] args) throws CompilerException {
		Parser p = new Parser (new Lexer ("stdin", System.in));
		System.out.println(p.klass());
		int x, y = 19, z = y+1;
	}

}
