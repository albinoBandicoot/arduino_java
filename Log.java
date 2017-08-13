public class Log {

	public static final int INFO = 0;
	public static final int WARN = 1;
	public static final int ERROR = 2;

	public static int level = INFO;

	private static boolean errorState = false;

	public static void nextPhase () {
		if (errorState) System.exit(1);
		errorState = false;
	}

	public static void write (String s) {
		if (level <= INFO) System.out.println (s);
	}

	public static void warn (CompilerException ex) {
		warn (ex.mesg);
	}

	public static void warn (String s) {
		if (WARN >= level) {
			bold(true);
			setColor (YELLOW);
			System.out.println ("Warning: " + s);
			setColor (DEFAULT);
			bold(false);
		}
	}

	public static void error (CompilerException ex) {
		error (ex.mesg);
	}

	public static void error (String s) {
		if (ERROR >= level) {
			bold(true);
			setColor (RED);
			System.out.println ("Error: " + s);
			setColor (DEFAULT);
			bold(false);
		}
		errorState = true;
	}

	public static void fatal (CompilerException ex) {
		fatal (ex.mesg);
	}

	public static void fatal (String s) {
		bold(true);
		setColor (CYAN);
		System.out.println ("Fatal Error: " + s);
		setColor (DEFAULT);
		bold(false);
		System.exit(1);
	}

	private static final int DEFAULT = 9;
	private static final int RED = 1;
	private static final int GREEN = 2;
	private static final int YELLOW = 3;
	private static final int CYAN = 6;
	private static final int MAGENTA = 5;

	private static void csi () {
		System.out.write (0x1B);
		System.out.write ('[');
	}

	private static void bold (boolean on) {
		csi();
		System.out.print(on ? "1m" : "0m");
	}

	private static void setColor (int color) {
		csi();
		System.out.print ("9" + color + "m");
	}

}
