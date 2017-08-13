public abstract class CompilerException extends Exception {

	public String mesg;

	public CompilerException (String mesg) {
		super(mesg);
		this.mesg = mesg;
	}
}

