import java.util.ArrayList;
import java.util.List;

public class Pragma {

	public PragmaType type;
	public List<String> args;

	public Pragma (Token id) {
		type = PragmaType.valueOf(id.lexeme);
		args = new ArrayList<>();
	}
}
