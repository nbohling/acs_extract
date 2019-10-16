/*****************************
*
* CLASS:   ACSrule
* Author:  Neal Bohling, IBM
* DATE :   2/21/2016
* Purpose: Holds a set of conditions and the SET value.
*          Created during a SET statement.
*          Represents the conditions that must be
*          true for a SET statement, and the value
*          set in that statement.
*****************************/
import java.util.Stack;
import java.util.LinkedList;

public class ACSrule {

	/* Local variables */
	// Conditions -- each entry is an OR, so requires a separate line in the output
	protected LinkedList<ACScondition> conditions;
	protected int sequence;
	protected boolean hasExit;
	protected String setVal;
	protected int lineNum;    // Line number for the set in the ACS

	/* Constructor. Takes no arguments */
	public ACSrule() {
		this.conditions = new LinkedList<ACScondition>();
		this.sequence = 0;
		this.hasExit = false;
		this.setVal = "";
		this.lineNum = 0;
	}

	// addCondition
	// Adds an ACScondition object to this rule
	public void addCondition( ACScondition nc ) {
		this.conditions.push( nc );
	}

	public void addConditions( LinkedList<ACScondition> ncs ) {
		this.conditions.addAll(ncs);
	}

	// setSetValue
	// Set the value set on the SET statement
	public void setSetValue( String sv ) {
		this.setVal = sv;
	}

	// setExit
	// Set whether or not there is an EXIT
	// after the SET statement.
	public void setExit( boolean x ) {
		this.hasExit = x;
	}

	// setSequence
	// The sequence indicates the order in which the
	// SET statement was found.
	public void setSequence( int sq ) {
		this.sequence = sq;
	}

	// setLineNumber
	public void setLineNumber( int nn ) {
		this.lineNum = nn;
	}

	// toCSV
	// Creates a string value with the CSV output of the SET statement and conditions.
	// Loops over the conditions and prints each one on a separate line
	// Order: SETVAL, &var values, sequence, EXIT(Y/N)
	public String toCSV(LinkedList<String> varOrder ) {
		StringBuilder toPrint = new StringBuilder();
		String t = "N";
		// Loop over all the conditions and print each one
		for( ACScondition cc : this.conditions ) {
			if( this.hasExit ) t = "Y"; else t = "N";
			toPrint.append( this.setVal );
			toPrint.append(",");
			toPrint.append(cc.toCSV(varOrder));
			toPrint.append(this.sequence);
			toPrint.append(",");
			toPrint.append(t);
			toPrint.append(",");
			toPrint.append(this.lineNum);
			toPrint.append("\n");
		}
		return toPrint.toString();
	}

	public ACSrule clone() {
		ACSrule toRet = new ACSrule();
		for( ACScondition toAdd : this.conditions ) {
			toRet.addCondition( toAdd );
		}
		toRet.setSequence(this.sequence);
		toRet.setLineNumber(this.lineNum);
		toRet.setExit(this.hasExit);
		toRet.setSetValue(this.setVal);

		return toRet;
	}
}
