package common;

public class InternalError extends CompilerException {

	public InternalError (String mesg) {
		super("!! INTERNAL ERROR !! " + mesg);
	}
}
