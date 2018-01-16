package frontend;

import type.Klass;

import java.util.List;
public class Context {

	public VarST vars;
	public FuncST funcs;

	public VarST chain_vars;
	public FuncST chain_funcs;

	public Context (VarST v, FuncST f) {
		vars = v;
		funcs = f;
		chain_vars = v;
		chain_funcs = f;
	}

	public Context setChainClass (Klass k) {
		Context c = new Context (vars, funcs);
		c.chain_vars = k.ctx.vars;
		c.chain_funcs = k.ctx.funcs;
		return c;
	}

	public DeclTree lookupVar (String name, boolean dotchain) {
		return dotchain ? chain_vars.lookup(name) : vars.lookup(name);
	}

	public List<DeclTree> lookupFunc (String name, boolean dotchain) {
		return dotchain ? chain_funcs.lookup(name) : funcs.lookup(name);
	}

}
