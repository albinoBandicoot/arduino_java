public class AssemblerException extends Exception {

	public AssemblerException (String mesg) {
		super("ERROR in line " + Assembler.line + ": " + mesg);
	}
}
