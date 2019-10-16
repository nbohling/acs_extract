/******************************************
 *
 * CLASS:  	ACSpair
 * Author:  Neal Bohling, IBM
 * Purpose:	Holds one pair of &VAR / VALUE pairs.
 *          Such as &DSN EQ SYS1*
 *          variable = &DSN
 *          comparison = EQ
 *          value = SYS1*
 *
 *****************************************/
public class ACSpair {

	// Local variables
	protected String variable;
	protected String comparison;
	protected String value;

	// Constructor. Init to zero
	public ACSpair() {
		this.variable = "";
		this.value = "";
		this.comparison = "";
	}

	// Constructor, accepts 3 parameters to create new pair
	public ACSpair( String nv, String nc, String nval ) {
		this.variable = nv;
		this.comparison = nc;
		this.value = nval;
	}

	// getVariable
	// Return the variable
	public String getVariable() {
		return this.variable;
	}

	// getValue
	// Return the value
	public String getValue() {
		return this.value;
	}

	// getComparison
	// Return the comparison (EQ, NE, etc)
	public String getComparison() {
		return this.comparison;
	}

	// toString
	// Return a string representing this pair.
	// Used mostly for debugging
	public String toString() {
		return this.variable + " " + this.comparison + " " + this.value;
	}

	// setVariable
	// Set the variable
	public void setVariable(String nv) {
		this.variable = nv;
	}

	// setComparison
	// Set the comparison
	public void setComparison(String nc) {
		this.comparison = nc;
	}

	// setValue
	// Set the value
	public void setValue( String nv) {
		this.value = nv;
	}

	// not
	// Returns the opposite of the current pair.
	// Done by flipping the comparison.
	public ACSpair not() {
		// Get and test the comparison
		String inComp = this.getComparison();
		String outComp = "";
		// Handle GT
		if( inComp.equals("GT") ) outComp = "LE";
		if( inComp.equals(">") ) outComp = "<=";
		// Handle LT
		if( inComp.equals("LT") ) outComp = "GE";
		if( inComp.equals("<") ) outComp = ">";
		// Handle NG
		if( inComp.equals("NG") ) outComp = "GT";
		if( inComp.equals("¬>") ) outComp = ">";
		if( inComp.equals("^>") ) outComp = ">";
		// Handle NL
		if( inComp.equals("NL") ) outComp = "LT";
		if( inComp.equals("¬<") ) outComp = ">";
		if( inComp.equals("^<") ) outComp = ">";
		// Handle EQ
		if( inComp.equals("EQ") ) outComp = "NE";
		if( inComp.equals("=") ) outComp = "¬=";
		// Handle NE
		if( inComp.equals("NE") ) outComp = "EQ";
		if( inComp.equals("¬=") ) outComp = "=";
		if( inComp.equals("^=") ) outComp = "=";
		// Handle GE
		if( inComp.equals("GE") ) outComp = "LT";
		if( inComp.equals(">=") ) outComp = "<";
		// Handle LE
		if( inComp.equals("LE") ) outComp = "GT";
		if( inComp.equals("<=") ) outComp = ">";

		// Create a new pair and set the values
		ACSpair np = new ACSpair();
		np.setVariable( this.getVariable() );
		np.setComparison( outComp );
		np.setValue( this.getValue() );

		// Rerturn it
		return np;
	}
}

