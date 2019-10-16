/*****************************
 *
 * CLASS:   ACScondition
 * Author:  Neal Bohling, IBM
 * DATE :   2/21/2016
 * Purpose: Holds a single condition.
 *          One condition is a set of VAR / VALUE pairs.
 *          The combined set of all the ACSpairs creates a condition
 *
 *****************************/
import java.util.LinkedList;

public class ACScondition {

	// Set of pairs that represent the condition
	protected LinkedList<ACSpair> pairs;

	// Constructor, no parms
	public ACScondition() {
		this.pairs = new LinkedList<ACSpair>();
	}

	// Constructor, accepts parameters to build a new pair
	public void addPair( String variable, String comparison, String value ) {
		this.addPair( new ACSpair( variable, comparison, value ) );
	}

	// addPair
	// Adds a new pair, but only if it's not a duplicate
	public void addPair( ACSpair newpair ) {
		// Check for duplicate
		boolean found = false;
		for( ACSpair cp : this.pairs ) {
			if( ( cp == newpair ) ||
			    ( cp.getVariable().equals( newpair.getVariable() ) &&
			      cp.getComparison().equals( newpair.getComparison() ) &&
			      cp.getValue().equals( cp.getValue() ) ) )
				found = true;
		}
		// If not a duplicate, then add it
		if( !found ) this.pairs.add( newpair );
	}

	// getVars
	// Returns a String array of the variables set in the pairs
	public String[] getVars() {
		String[] toRet = new String[pairs.size()];
		int ix = 0;
		for( ACSpair apair : pairs ) {
			toRet[ix++] = apair.getVariable();
		}
		return toRet;
	}

	// toCSV
	// Exports the variables into CSV
	// Accepts a parameter and prints the values in that order.
	public String toCSV( LinkedList<String> varOrder ) {
		String toRet = "";
		// Loop over the var order
		for( String cl : varOrder ) {
			// Find the pair that matches that order
			boolean mult = false;
			for( ACSpair cp : pairs ) {
				if( cl.equals(cp.getVariable()) ) {
					// Only print the operator if it's not =
					if( cp.getComparison().equals( "=" ) ) {
						if( mult ) toRet = toRet + "; ";
						toRet = toRet + cp.getValue();
					} else {
						if( mult ) toRet = toRet + "; ";
						toRet = toRet + cp.getComparison()+" "+cp.getValue();
					}
					mult = true;
				}
			}
			toRet = toRet + ",";
		}
		return toRet;
	}

	// getPairs
	// Returns the pairs
	public LinkedList<ACSpair> getPairs() {
		return this.pairs;
	}

	// Not
	// Return the logical opposite of this condition
	// Since each pair in a condition is connected by AND, a NOT will
	// result in a list of entries representing OR.
	// Ex: Cond1 with Pair(A=B) & Pair(C=D) results in
	//     Cond1 with Pair(A^=B)
	//     Cond2 with Pair(C^=D) (OR)
	public LinkedList<ACScondition> not() {
		LinkedList<ACScondition> newList = new LinkedList<ACScondition>();

		// Loop over the pairs and build a new condition from the opposite of each
		for( ACSpair cp : this.pairs ) {
			ACScondition nc = new ACScondition();
			nc.addPair( cp.not() );
			newList.add( nc );
			//System.out.println( "NOT PAIR: Old: "+cp.toString()+" New:"+nc.toString() );
		}
		return newList;
	}

	// toString
	// Return a string showing all the pairs
	public String toString() {
		String toRet = this.pairs.size()+" pairs: ";
		for( ACSpair cp : this.pairs ) {
			toRet += cp.toString()+" ";
		}
		return toRet;
	}

	// removeDuplicatePairs
	// Compares all the pairs and removes duplicates
	public void removeDuplicatePairs() {
		LinkedList<ACSpair> newList = new LinkedList<ACSpair>();
		for( ACSpair cp : this.pairs ) {
			boolean found = false;
			for( ACSpair np : newList ) {
				if( cp.getValue().equals( np.getValue() ) &&
				    cp.getComparison().equals( np.getComparison() ) &&
				    cp.getVariable().equals( np.getVariable() ) ) {
						found = true;
				}
			}
			if( !found ) newList.add( cp );
		}
		this.pairs = newList;
	}

}
