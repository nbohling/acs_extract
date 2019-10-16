import java.io.*;
import java.io.StreamTokenizer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.util.Stack;
import java.util.LinkedList;

/*************************************************************
 * ACSextract
 * Author: Neal Bohling, IBM
 * Date:   March 2016, Nov 2017
 * Version: 2017.11.19 (v1.0)
 *
 * Purpose:
 *   This tool seeks to read ACS routines and distill the
 *   logic held therin into a CSV file of "rules".
 *   Each rule documents the variables and conditions that
 *   are required for each SET statement.
 *
 * Syntax:
 *   - java ACSextract acsfile <output> <options>
 *
 * General algorithm:
 *   - Repeatedly loop over the next token
 *   - For IF and SELECT options, grab the condition statement
 *     in handleCondition. Then recursively call handleKeyword
 *     to handle statements within an IF or SELECT field.
 *     Stack the condition on a condition stack.
 *   - When a SET is encountered, merge the condition stack
 *     and create an ACS rule.
 *   - On END, pop off the associated condition statement
 *   - After all keywords are processed, print
 *     all the variables and each rule.
 *
 * Available options:
 *   - debug    : prints lots of debug messages
 *
 * Data structures:
 *   ACSrule = set of ACSconditions and a SET
 *   ACScondition = set of ACSpairs that define the condition
 *   ACSpair = stores VAR OP VAR, such as DSN = 'BOB'
 *
 *************************************************************/
public class ACSextract {

	// DEBUG option
	protected boolean DEBUG = false;

	// Variables for file input reader
	protected StreamTokenizer st;
	protected boolean eof;

	// Variables for writing
	protected BufferedWriter out;

	// Condition stack
	// Stack holds a linked list of ACScondition objects
	// Each condition object is a set of VALUE,COMP,VALUE pairs
	// Multiple pairs in a condition represent AND
	// Multiple conditions represent OR
	protected Stack<LinkedList<ACScondition>> conditionStack;
	protected int doDepth;

	// LinkedList of rules
	protected LinkedList<ACSrule> rules;
	protected int ruleSequence;

	// LinkedList of all of the variables used in tests in the ACS
	protected LinkedList<String> variableList;

	// String representing which ACS routine we're processing
	protected String procName;

	// LinkedList of FILTLISTs
	protected LinkedList<ACSfiltlist> filtlists;

	// MAIN proc. Creates class and starts program.
	public static void main( String[] argz ) {
		// If no argz, print the HELP.
		if( argz.length < 1 ) {
			help();
		}
		// Otherwise create object and call run();
		else {
			if( argz[0].equals("?") || argz[0].equals("-?") || argz[0].toLowerCase().equals("help") ) help();
			else {
				ACSextract in = new ACSextract( argz );
				in.run();
			}
		}
	}


	// Constructor
	// Set up object settings
	public ACSextract( String[] argz ) {
		// End of file = false
		this.eof = false;
		try {
			// Initialize the tokenizer
			Reader r = new BufferedReader( new FileReader(argz[0]) );
			this.st = new StreamTokenizer(r);
			this.st.resetSyntax();

			/* Define word characters */
			this.st.wordChars(42,42);        /* asterisk for mask */
			this.st.wordChars('%','%');      /* % sign for mask */
			this.st.wordChars(48,57);        /* numbers  */
			this.st.wordChars(65,90);        /* uppercase letters */
			this.st.wordChars(97,122);       /* lowercase letters */
			this.st.wordChars(95,95);        /* underscore */
			this.st.wordChars(38,38);        /* ampersand  */
			//this.st.wordChars('\'','\'');    /* quote      */
			this.st.wordChars('.', '.');     /* Period     */
			this.st.quoteChar('\'');

			/* Other tokenizer settings */
			this.st.slashStarComments(true); /* skip comments */
			this.st.whitespaceChars(9,32);   /* whitespace    */
			this.st.whitespaceChars(43,43);  /* whitespace +  */
			this.st.whitespaceChars(45,45);  /* whitespace -  */

			// Create output data set name
			String dsn = "";
			if( argz[0].lastIndexOf(".") > 0 ) {
				dsn = argz[0].substring(0,argz[0].lastIndexOf("."))+".csv";
			} else {
				dsn = argz[0]+".csv";
			}

			// If name is passed in
			if( argz.length >= 2 ) {
				if( argz[1].equals("debug") ) this.DEBUG = true;
				else dsn = argz[1];
			}
			// Open the output file
			out = new BufferedWriter( new FileWriter( dsn ) );

			/* Process any options */
			if( argz.length > 2 ) {
				for( int i=2; i<argz.length; i++ ) {
					switch( argz[i] ) {
						case "debug":
							this.DEBUG = true;
							break;
					}
				}
			}

			/* Initialize condition stack */
			this.conditionStack = new Stack<LinkedList<ACScondition>>();
			this.doDepth = 0;

			/* Initialize rules list */
			this.rules = new LinkedList<ACSrule>();
			this.ruleSequence = 1;

			/* Initialize list of variables */
			this.variableList = new LinkedList<String>();

			/* Initialize FILTLISTS */
			this.filtlists = new LinkedList<ACSfiltlist>();

		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(4);
		}
	}

	// Main Proc
	// Runs all the other subroutines
	//
	// Overall Procedure
	// - Read in ACS          |
	// - Strip out comments   | handled by tokenizer
	// - Tokenize             |
	// - Loop over keywords and process each one
	public void run() {

		// While there is still more to read.
		while( !this.eof ) {
			this.handleKeywords();
		}
		// When this finishes, the entire file has been read and processed

		// Generate header row
		String vars = this.procName+",";
		for( String cv : this.variableList ) {
			vars += cv+",";
		}
		vars += "Seq,Exit,LineNum";

		try {
			// Print the header
			System.out.println( vars );
			this.out.write( vars + "\n" );

			// Print the rule set
			for( ACSrule cr : this.rules ) {
				String output = cr.toCSV( this.variableList );
				System.out.print( output );
				this.out.write( output );
			}

			// Print FILTLISTS
			System.out.println("");
			System.out.println("FILTLISTS\nNAME,INCLUDE,EXCLUDE");
			this.out.write( "\n" );
			this.out.write( "FILTLISTS\nNAME,INCLUDE,EXCLUDE\n");

			// Loop over and print filtlists
			String toPrint = "";
			this.debug( "Filtlist list size: "+this.filtlists.size() );
			for( ACSfiltlist af : this.filtlists ) {
				toPrint = af.name + ",\""+af.include+"\",\""+af.exclude+"\"";
				System.out.println( toPrint );
				this.out.write( toPrint + "\n" );
			}

			// Close out the file
			this.out.flush();
			this.out.close();

			// Program is done!
			System.out.println( "Done." );

		}
		// Handle any errors
		catch(IOException e) {
			this.fail( "Problem writing output file." );
			e.printStackTrace();
		}
	}


	// handleKeyword
	// This switchpoint is used whenever any keyword could be next.
	// It will handle one statement and then return
	public boolean handleKeyword() {
		// Scan each keyword and handle appropriately
		String newKeyword="";
		String temp="";
		// Controls whether handleKeywords caller returns immediately or
		// keeps looping. TRUE = keep looping
		boolean toRet = true;
		// If not the end of the file, and the token looks good
		if( !eof && (temp = this.readToken() ) != null ) {
			this.debug( "handleKeyword "+temp );

			// Conditional on the keyword
			// PROC - Start of an ACS routine
			// FILTLIST - Definition of filter criteria
			// SET - Assigns a value to a read-write variable
			// DO - Start of statement group
			// IF - Provides conditional statement execution
			// SELECT - Defines a set of conditional execution statements
			// EXIT - Causes immediate termination of the ACS routine and can be used to force allocation failures
			// WRITE - Sends a message to the end user
			// END - End of statement group (DO or SELECT) or ACS routine (PROC).
			switch( temp ) {
				case "PROC":
					handleProc();
					break;
				case "FILTLIST":
					handleFiltlist();
					break;
				case "SET":
					handleSet();
					break;
				case "DO":
					handleDo();
					break;
				case "IF":
					handleIf();
					break;
				case "SELECT":
					handleSelect();
					break;
				case "EXIT":
					handleExit();
					break;
				case "WRITE":
					handleWrite();
					break;
				case "END":
					// Stop the handleKeywords looping
					handleEnd(); // just calls debug
					toRet = false;
					break;
				case "WHEN":
				case "OTHERWISE":
					// Stop the handleKeywords from looping
					toRet = false;
					// Push the OTHERWISE back on for SELECT processing to handle
					this.pushToken();
					break;
				default:
					// Ignore keywords we don't understand.
					this.fail( "Unknown keyword : "+temp+"... Moving on.", false);
					break;
			}
		}
		return toRet;
	}

	// handleKeywords
	// Wrapper to repeatedly handle keywords
	// Useful for inside DO blocks
	// When this returns, the DO block has ENDed
	protected void handleKeywords() {
		while( !this.eof && this.handleKeyword() ) ;
	}

	// handleProc
	// Processes the PROC keyword.
	// Establishes which keyword we are setting.
	protected void handleProc() {
		this.debug( "handleProc " );

		// Get the next token
		this.procName = this.readToken();

		// Check if the next token is a number
		// If it is a number, re-read
		if( this.isNumber( this.procName ) ) {
			this.procName = this.readToken();
		}

		// Validity check
		if( !this.isValidProc( this.procName ) ) this.fail( this.procName+" is not a valid PROC name.");

		return;
	}

	// handleFiltList
	// Reads in the INCLUDE and EXCLUDE lists and stores them as entries in a LinkedList
	// Normal filtlist syntax:
	// FILTLIST INCLUDE(OPT1,'OPT2',MASK*) EXCLUDE(EX*,'EX2')
	protected void handleFiltlist() {
		this.debug( "handleFiltlist" );

		// Next token should be a name
		ACSfiltlist nf = new ACSfiltlist();
		nf.name = this.readToken();
		nf.include = "";
		nf.exclude = "";

		// After that should either be INCLUDE or EXCLUDE
		String temp = this.readToken();
		// If it's not INCLUDE or EXCLUDE, then something is wrong
		while( temp.equals( "INCLUDE" ) || temp.equals("EXCLUDE") ) {
			// Next should be a (
			String filt = this.readToken();
			if( filt.equals("(") ) {
				// Just grab all values up the next paren
				String val = this.getToNext( ")" );
				// If it's the INCLUDE list, add it
				if( temp.equals( "INCLUDE" ) ) {
					nf.include = val;
				}
				// If not INCLUDE, then it's an EXCLUDE
				else {
					nf.exclude = val;
				}
			} else {
				this.fail( "Bad FILTLIST format. ( not found where expected." );
			}
			// Try for next. See if there is another EXCLUDE or INCLUDE
			temp = this.readToken();
		}
		// When we're done, put back the next token.
		// This is because we pull it off to tell when we are done with FILTLIST processing
		this.pushToken();

		// Add the filtlist to the pile
		this.filtlists.add( nf );

		this.debug( "leaving handleFiltlist" );
		return;
	}

	// HandleSet
	// If we encounter a SET, then we grab the next three tokens, should be like &STORCLAS = "value"
	// Create a ACSrule and apply the setval
	// Copy the current stack of conditions into the ACSrule
	protected void handleSet() {
		this.debug( "handleSet " );
		// Grab the next three tokens and validity check
		// Should be &STORCLAS = "Value"
		String nt = this.readToken();
		if( this.isVar( nt ) && this.isValidProc( nt ) ) {
			// Validity check that it matches the PROC
			if( !nt.equals( "&"+this.procName ) && !nt.equals( this.procName ) ) this.fail( "SET variable doesn't match PROC name. SET "+nt, false );

			// Continue processing SET
			// Ensure there is an equal sign
			nt = this.readToken();
			// should be = or EQ
			if( !nt.equals("=") && !nt.equals("EQ")) this.fail( "SET statement incorrect. Should have an = or EQ." );

			// Read assignment value
			nt = this.readToken();
			// should be VALUE - no good way to validity check

			// Create a new rule
			ACSrule newRule = new ACSrule();
			newRule.setSetValue( nt );
			newRule.setLineNumber( this.st.lineno() );

			// Run the current condition stack and add them to the rule
			// Each entry on the stack is an IF statement
			// In a list, multiple entries means an OR statement.
			// To flatten, we must AND the rules from each layer
			// to the previous and future rules.
			LinkedList<ACScondition> flattened = new LinkedList<ACScondition>();
			for( LinkedList<ACScondition> ccl : this.conditionStack ) {
				flattened = this.ANDconditions( ccl, flattened );
				/*
				for( ACScondition cc : ccl ) {
					newRule.addCondition( cc );
				}
				*/
				newRule.addConditions(flattened);
			}

			// Set sequence for rule
			newRule.setSequence( this.ruleSequence++ );

			// Put the rule on the list
			this.rules.add( newRule );

		} else fail( "Not a valid SET R/W Variable: "+nt );

		this.debug( "leaving handleSet " );
		return;
	}

	// handleDo
	// Handles a block of statements together
	// Doesn't actually have to do much. Recursively calls handleKeyword
	// to process the block of statements.
	// handleEnd will cause a return that will come back through here.
	protected void handleDo() {
		int startLine = this.st.lineno();
		this.debug( "handleDo " );
		this.doDepth++;
		this.handleKeywords();
		this.doDepth--;
		this.debug( "leaving handleDo from "+startLine );
	}

	// handleIf
	// Processes the IF conditions and adds them to the stack
	// Also checks for a THEN and a DO
	// If no do, then check for SET. Anything else, ignore it.
	protected void handleIf() {
		this.debug( "handleIf" );
		// Now we get to try to decipher the condition
		// Normal IF statement is as follows
		// IF &DSN = 'HELP' THEN .. ELSE ..
		// If there is a ( then we need to decipher the clause recursively
		// If there are multiple conditions joined by AND/OR
		// AND or &&  - And
		// OR  or |   - Or
		String tok = null;
		int initialLine = this.st.lineno();

		// Two options
		// Either a simple VAR CMP VALUE set, or nested ( ) items.
		// ( &DSN EQ 'BOB' ) | ( ( &DSN = LARR* ) AND &DSORG NE 'FB' )
		// Two rules:
		//   1. DSN EQ BOB
		//   2. DSN = LARR* AND DSORG NE FB
		// Sample2: ( ( &SIZE > 1000 | &SIZE < 10 ) && DSN(3) = NYC )
		// Rules:
		//   1. SIZE > 1000 AND DSN(3) = NYC
		//   2. SIZE < 10   AND DSN(3) = NYC
		// Need a good way to recursively parse and combine these into rules
		// Or complicates things. OR creates permutations

		// Algorithm 1
		// If token is a keyword, then we probably just have a single comparison
		// Pull that
		// If token is a (, then recurse in and re-check first token
		//
		// After comparison is done, add it to a linkedlist of conditions
		// If next token is AND or OR, then parse it as well
		// For AND, combine the tokens
		// For OR, return each as it's own token


		// Call HandleCondition to parse the IF middle
		LinkedList<ACScondition> newConditions = this.handleConditions();

		// Add these conditions to the global stack
		this.conditionStack.push( newConditions );

		// Double check that we have a THEN statement
		tok = this.readToken();
		if( !tok.equals("THEN") ) fail( "Missing THEN after IF." );

		// If still good, then call handleKeyword to parse whatever is next
		this.handleKeyword();

		// Remove the condition
		if( this.conditionStack.pop() != newConditions ) this.fail( "Program logic error. Statements in IF not handled correctly resulting in mixed condition stack.");

		// Check for ELSE
		if( this.readToken().equals( "ELSE" ) ) {
			// Invert the last set of conditions and add them back
			newConditions = this.deMorgan( newConditions );
			this.conditionStack.push( newConditions );

			// Handle anything in there
			this.handleKeyword();

			// Pop the condition off the stack
			if( this.conditionStack.pop() != newConditions ) this.fail( "Program logic error. Statements in ELSE not handled correctly resulting in mixed condition stack.");
		} else {
			this.pushToken();
		}
		this.debug( "leaving handleIf, IF @ "+initialLine );
	}

	// handleConditions
	// Loops through the condition pairs in the IF statement
	// and builds a list of conditions out of them.
	// OR is handled by adding conditions
	// AND is handled by merging conditions
	// Returns: LinkedList of ACSconditions
	protected LinkedList<ACScondition> handleConditions() {
		this.debug( "handleConditions " );
		// String to hold tokens
		String tok = this.readToken();
		// Linkedlist of conditions
		LinkedList<ACScondition> ncl = null;

		// See if we have a clause in parenthesis
		if( tok.equals("(") ) {
			ncl = this.handleConditions();
			// Ensure a closing parentheses
			tok = this.readToken();
			if( !tok.equals(")") ) this.fail( "Missing closing parenthesis" );
		// Check for extraneous closing paren
		} else if( tok.equals( ")" ) ) {
			fail( "Invalid closing parentheses" );
		} else {
			// Default cause
			// Build a new ACScondition
			ACScondition nc = new ACScondition();
			ncl = new LinkedList<ACScondition>();
			this.pushToken();

			// Now parse it
			String compvar = this.readAndValidateVariable();
			if( compvar == null ) compvar = this.readToken();

			String compari = this.readAndValidateComparison();

			String compval = this.readAndValidateVariable();
			if( compval == null ) compval = this.readToken();

			ACSpair np = new ACSpair();
			if( compari != null ) {
				// If in format &VAR = 'VALUE'
				if( this.isVar( compvar ) ) {
					np.setVariable( compvar );
					np.setComparison( compari );
					np.setValue( compval );
				// If for some reason they use 'VALUE' = &VAR
				} else if( this.isVar( compval ) ) {
					// Variables are valid as a comparison (Such as filtlists),
					// so this path may indicate a missed &
					this.fail( "Possible missed &: "+compvar+" "+compari+" "+compval, false );
					np.setVariable( compval );
					np.setComparison( compari );
					np.setValue( compvar );
				} else {
					this.fail( "Invalid comparison: "+compvar+" "+compari+" "+compval );
				}
				// Add it to the global stack
				this.addVariable( np.getVariable() );
			} else {
				this.fail( "Invalid comparison."+compvar+" "+compval );
			}

			// Load the ACScondition with the new pair
			nc.addPair( np );
			// Put the new condition on the list
			ncl.add( nc );
		}

		// Check if we have an AND or OR as next token
		tok = this.readToken();
		this.debug( "Checking for AND/OR: "+tok );

		// If AND, parse and combine
		if( tok.equals( "AND" ) || tok.equals( "&&" ) ) {
			this.debug( "handleConditions processing AND" );
			ncl = this.ANDconditions( ncl, this.handleConditions() );
		}
		// If OR, then simply add the new ones to the bottom of the list
		else if( tok.equals( "OR" ) || tok.equals( "|" ) ) {
			this.debug( "handleConditions processing OR" );
			ncl.addAll( this.handleConditions() );
		}
		// If it's something else, then it's probably an END or a problem
		else {
			//Push it back and let the parent routine handle it!
			this.pushToken();
		}

		// If we made it this far, then we can return the conditions
		this.debug( "leaving handleConditions. Condition pairs:" );
		this.debug( ncl.toString() );
		return ncl;
	}

	// ANDconditions
	// Takes two list of conditions and combines them into one list
	// If we think of multiple ACSconditions as conditions joined by OR
	// then this algorithm simply multiplies.
	protected LinkedList<ACScondition> ANDconditions( LinkedList<ACScondition> list1, LinkedList<ACScondition> list2 ) {
		this.debug( "ANDconditions" );
		LinkedList<ACScondition> toRet = new LinkedList<ACScondition>();
		this.debug( "AND input length: "+list1.size()+" "+list2.size() );

		// loop over each condition in list1
		// add all the pairs to each item in list2
		ACScondition temp;
		if( list1.size() == 0 ) toRet = list2;
		else if( list2.size() == 0 ) toRet = list1;
		else {
			for (ACScondition lc1 : list1 ) {
				temp = new ACScondition();
				for( ACScondition lc2 : list2 ) {
					// Add all the pairs for this combo to the new ACScondition
					for( ACSpair cp : lc1.getPairs() ) {
						temp.addPair( cp );
					}
					for( ACSpair cp : lc2.getPairs() ) {
						temp.addPair( cp );
					}
				}
				// Add it to the return stack
				toRet.add( temp );
			}
		}
		return toRet;
	}


	// handleSelect
	// Handled much like IF
	// Two options:
	//   1. &VARIABLE is on the SELECT statement.
	//   2. &VARIABLE in WHEN statement.
	protected void handleSelect() {
		this.debug( "handleSelect" );
		// Read in the next token
		String tok = this.readToken();
		String compVar = null;
		int startLine = this.st.lineno();

		// Keep track of the list of comparisons
		LinkedList<LinkedList<ACScondition>> otherwiseList = new LinkedList<LinkedList<ACScondition>>();

		// If the next token is a (, then we'll assume it's option #1
		// Otherwise, we'll assume that it is option #2
		if( tok.equals( "(" ) ) {
			compVar = this.readAndValidateVariable();
			// Make sure it's a valid variable
			if( compVar == null ) this.fail( "Bad select statement." );

			// Check for )
			if( !this.readToken().equals(")") ) this.fail( "Missing parentheses" );
		} else {
			this.pushToken();
			compVar = null;
		}

		// new ACS conditoin list
		LinkedList<ACScondition> ncl = null;

		// Now find the WHEN statement
		while( (tok = this.readToken()).equals("WHEN") ) {
			this.debug("Found the when.");
			// Check for (
			if( !this.readToken().equals("(") ) this.fail( "Missing opening parentheses on WHEN statement. " );

			// If it's option 1, then just read the comparison and build pair
			if( compVar != null ) {
				// Pull value
				String compVal = this.readToken();
				// Build condition
				ACScondition nc = new ACScondition();
				ACSpair np = new ACSpair();
				np.setVariable( compVar );
				np.setComparison( "EQ" );
				np.setValue( compVal );

				// Add the variable name to the global stack
				this.addVariable( compVar );

				nc.addPair( np );

				// Create ncl if we don't have it yet
				if( ncl == null ) ncl = new LinkedList<ACScondition>();
				ncl.add( nc );
			}
			// If option 2, call handleConditions to read the pair
			else {
				ncl = this.handleConditions();
			}

			// Check for closing paren
			if( !this.readToken().equals(")") ) this.fail( "Missing ending parentheses on WHEN statement. " );

			// Add the condition to the stack
			this.conditionStack.push( ncl );

			// Handle keywords inside
			this.handleKeywords();

			// Pop the condition
			LinkedList<ACScondition> tempc = this.conditionStack.pop();
			if( tempc != ncl ) this.fail( "Program error: Bad condition POP within WHEN clause" );
			otherwiseList.add( ncl );
		}

		// Check for otherwise
		// To create the condition for OTHERWISE, we'll
		// AND together all of the previous ones with NOT
		// Aka, if A and B are previous WHEN conditions,
		// then the otherwise is NOT(A) AND NOT(B)
		this.debug("handleSelect - Checking for otherwise: "+tok);
		if( tok.equals("OTHERWISE") ) {
			ncl = new LinkedList<ACScondition>();
			for( LinkedList<ACScondition> oc : otherwiseList ) {
				ncl = this.ANDconditions( ncl, this.deMorgan( oc ) );
			}
			this.conditionStack.push( ncl );
			this.handleKeywords();
			// Pop the condition
			LinkedList<ACScondition> tempc = this.conditionStack.pop();
			if( tempc != ncl ) this.fail( "Program error: Bad condition POP for OTHERWISE clause" );
		}

		this.pushToken();

		this.debug( "leaving handleSelect started at "+startLine+". Current token: "+tok );
		// The handleKeyword should run right up until the END, at which point it will return
		// So we don't need to check for it here.
		return;
	}

	// Handle exit
	// At some point, I'd like to properly flag rules with whether there was an exit or not
	// But it'll be hard to pair the exit with the SET. If too much happens between, then the EXITs may get flagged incorrectly.
	protected void handleExit() {
		this.debug( "handleExit" );
		// See if they included a CODE(xx) after the EXIT. If so, discard it
		String tok = this.readToken();
		if( tok.equals("CODE") ) {
			tok = this.readToken(); // (
			tok = this.readToken(); // xx
			tok = this.readToken(); // )
		} else {
			this.pushToken();
		}
		// Mark the last rule we made as HAS EXIT
		// NOTE: May not always be correct. If EXIT is coded after a large SELECT, then only the last one would get marked.
		this.rules.getLast().setExit( true );
	}

	// handleWrite -- write statements are mostly ignored.
	// To process, pull the rest of the line and any continuation lines
	protected void handleWrite() {
		this.debug( "handleWrite" );
		// Loop finding blocks enclosed in ' ' or variables

		String tok = this.readToken();
		boolean cont = true;

		while( cont ) {
			// Remove quoted strings and variables.
			if( tok.startsWith("'") ) {
				tok = this.readToken();
			} else if( this.isVar( tok ) ) {
				tok = this.readToken();
			} else {
				cont = false;
			}
		}
		// Push the last token back on the stack
		this.pushToken();
		this.debug( "leaving handleWrite" );
	}

	// handleEnd()
	// Handles the END keyword.
	// Not actually using this as handleKeyword just returns for END.
	protected void handleEnd() {
		this.debug( "handleEnd" );
		return;
	}

	// getToNext( String )
	// Returns a String of all the tokens up to the next token matching the passed var
	protected String getToNext( String nextToken ) {
		this.debug( "getToNext" );
		String toRet = "";
		boolean isFirst = true;
		String temp = this.readToken();
		while( !temp.equals( nextToken ) ) {
			if( isFirst ) {
				isFirst = false;
			} else {
				toRet += "";
			}
			toRet += temp;
			temp = this.readToken();
		}
		// Discard the last matching token
		// Return what we found
		return toRet;
	}

	// readAndValidateVariable
	// Reads in a variable name and makes sure it's
	// a valid variable, like &VAR.
	// Returns null if not a real variable.
	protected String readAndValidateVariable() {
		this.debug( "readAndValidateVariable" );
		String tok = this.readToken();
		if( isVar( tok ) ) {
			// Check for parens after the variable
			String t = this.readToken();
			if( t.equals( "(" ) ) {
				tok = tok+t+this.readToken()+this.readToken();
			} else {
				this.pushToken();
			}
		} else {
			this.pushToken();
			tok = null;
		}
		this.debug( "leaving readAndValidateVariable" );
		return tok;
	}

	// isVar( String )
	// Returns true if the String is a valid variable name
	protected boolean isVar( String inToken ) {
		return (inToken.indexOf("&") == 0);
	}

	// isKeyword
	// Returns true if word matches any of the keywords
	protected boolean isKeyword( String inToken ) {
		boolean toRet = false;
		if( inToken.equals("IF") ||
          inToken.equals("SELECT") ||
			 inToken.equals("WRITE") ||
			 inToken.equals("PROC") ||
			 inToken.equals("FILTLIST") ||
			 inToken.equals("SET") ||
			 inToken.equals("DO") ||
			 inToken.equals("EXIT") ||
			 inToken.equals("END")
		) toRet = true;

		return toRet;
	}

	// isValidProc( String )
	// Returns true if it matches STORCLAS, STORGRP, MGMTCLAS, or DATACLAS
	protected boolean isValidProc( String inval ) {
		if( inval.indexOf("&") >= 0 ) {
			inval = inval.substring( inval.indexOf("&")+1 );
		}
		return (inval.equals("STORCLAS") ||
		        inval.equals("DATACLAS") ||
		        inval.equals("MGMTCLAS") ||
		        inval.equals("STORGRP" ) );
	}

	// readAndValidateComparison
	// Read in a comparison and make sure the WHOLE comparison is included.
	// Since some of the comparisons are two characters, we need to pull them both.
	protected String readAndValidateComparison() {
		this.debug( "readAndValidateComparison" );
		String toRet = "";
		String tok = this.readToken();
		toRet = tok;
		// If it might be the start of a two-char operator
		if( tok.equals( ">" ) || tok.equals( "<" ) || tok.equals( "^" ) || tok.equals( "¬" ) ) {
			// Try to get the next token
			tok = this.readToken();
			if( tok.equals( ">" ) || tok.equals( "<" ) || tok.equals( "=" ) ) {
				toRet += tok;
			} else {
				// If we don't need it, put it back on the stack
				this.pushToken();
			}
		}
		// Make sure it's a valid comparison
		if( !this.isValidComparison( toRet ) ) {
			toRet = null;
			this.pushToken();
		}
		this.debug( "leaving readAndValidateComparison" );
		return toRet;
	}

	// isValidComparison( String )
	// Tests if COMPARISON is one of the following:
	// GT or >  Greater than
	// LT or <  Less than
	// NG or ¬> Not greater than
	// NL or ¬< Not less than
	// EQ or =  Equal
	// NE or ¬= Not equal
	// GE or >= Greater than or equal
	// LE or <= Less than or equal
	protected boolean isValidComparison( String comp ) {
		return ( (comp.equals("GT") ) || (comp.equals(">") ) ||
		         (comp.equals("LT") ) || (comp.equals("<") ) ||
		         (comp.equals("NG") ) || (comp.equals("¬>") ) || (comp.equals("^>") ) ||
		         (comp.equals("NL") ) || (comp.equals("¬<") ) || (comp.equals("^<") ) ||
		         (comp.equals("EQ") ) || (comp.equals("=") ) ||
		         (comp.equals("NE") ) || (comp.equals("¬=") ) || (comp.equals( "^=") ) ||
		         (comp.equals("GE") ) || (comp.equals(">=") ) ||
		         (comp.equals("LE") ) || (comp.equals("<=") ) );
	}

	// deMorgan( LinkedList<ACScondition>, LinkedList<ACScondition>)
	// Compute the logical NOT of a set of condition
	protected LinkedList<ACScondition> deMorgan( LinkedList<ACScondition> inC ) {
		// De Morgan's law says
		// NOT( A and B ) = (NOT A) OR (NOT B)
		// NOT( A OR B ) = (NOT A) AND (NOT B)
		// Pairs within a condition are AND
		// Pairs are OR if they are separate conditions
		//
		// A & B & C & D | E & F & G & H & I
		// NOT( A & B & C & D ) & NOT( E & F & G & H & I )
		//
		// To convert a list of ACSconditions....
		//   1. Convert each ACScondition into a new list of NOT ACSconditions
		//   2. AND them together
		LinkedList<ACScondition> toRet = new LinkedList<ACScondition>();
		for ( ACScondition cc : inC ) {
			toRet = this.ANDconditions( cc.not(), toRet );
		}
		return toRet;
	}

	// addVariable
	// Adds a variable to the global list, after ensuring it doesn't already exist
	protected void addVariable( String var ) {
		// Search for the variable
		boolean found = false;
		for( String cv : this.variableList ) {
			if( cv.equals( var ) ) found = true;
		}
		// If it's not already listed, add it.
		if( !found ) this.variableList.add( var );
	}


	// Wrapper for main call
	public String readToken() {
		return this.readToken(false);
	}

	// readToken
	// Reads the file, tokenizes, and handles the token.
	// Returns null when you get to the end of the file
	public String readToken(boolean nested) {
		String toRet = null;
		try {
			// As long as not at end of file
			if( !this.eof ) {
				// print the stream tokens
				int token = this.st.nextToken();
				switch (token) {
					// If it's EOF
					case StreamTokenizer.TT_EOF:
						this.eof = true;
						break;
					// If it's a word
					case StreamTokenizer.TT_WORD:
						toRet = this.st.sval.toUpperCase();

						// If we hit a 8-digit number as a token, assume it's a line number.
						// There shouldn't be any 8-digit number tokens flying solo
						// We may need to later find a way to strip out line numbers before operating
						if( this.isNumber( toRet ) && toRet.length() == 8 ) {
							toRet = this.readToken(true);
						}
						break;
					// If it's a number -- not actually used
					// This program treats numbers like words
					case StreamTokenizer.TT_NUMBER:
						//System.out.println("Number: " + st.nval);
						toRet = Double.toString(this.st.nval);
						break;
					// If it's quoted text, save the quoted text
					case '\'':
						toRet = '\''+this.st.sval.toUpperCase()+'\'';
						break;
					// Anything else, return it
					default:
						//System.out.println((char) token + " encountered.");
						toRet = ""+(char)token;
						toRet = toRet.toUpperCase();
				}
			}
			// If at end of FILE
			else {
				toRet = "EOF";
			}
		} catch (Exception ex) {
			this.fail( "Problem reading token. ", false );
			ex.printStackTrace();
		}
		if( !nested ) this.debug( "READ TOKEN: "+toRet );
		return toRet;
	}

	// Count # of times a given string shows up in a larger string
	protected int stringCount( String in, String needle ) {
		int toRet = 0;
		int lastIndex = in.indexOf( needle );;
		while( lastIndex >= 0 ) {
			lastIndex = in.indexOf( needle, lastIndex+1 );
			toRet ++;
		}
		return toRet;
	}

	// isNumber - determines if a given string is a decimal number
	protected boolean isNumber( String input ) {
		boolean isNum = true;
		for( char c : input.toCharArray() ) {
			if (!Character.isDigit(c)) isNum = false;
		}
		return isNum;
	}

	// pushToken
	// Pushes last token back onto the stack
	public void pushToken() {
		this.debug( "pushToken" );
		this.st.pushBack();
	}

	// fail
	// FAIL prints a message. Optionally stops the program
	// if you pass TRUE as the separate parameter.
	protected void fail(String reason, boolean terminate ) {
		System.out.println( "Error @"+this.st.lineno()+": "+reason );
		if( terminate ) System.exit(8);
	}

	// fail
	// FAIL wrapper -- always terminates
	protected void fail( String reason ) {
		this.fail( reason, true );
	}

	// debug
	// Simple function to print DEBUG messages and the line currently in processing
	protected void debug( String message ) {
		if( this.DEBUG ) System.out.println( "Line: "+this.st.lineno()+" D"+ this.doDepth+" : "+message );
	}

	// Help
	// Displays help message
	public static void help() {
		 System.out.println("ACS Extract");
		 System.out.println("Reads in an ACS routine and generates a CSV list of rules.");
		 System.out.println("Syntax: ");
		 System.out.println("   java ACSextract inputfile.txt <outputfile> <options>");
		 System.out.println();
		 System.out.println("If you do not specify an output file, program will use the");
		 System.out.println("output filename with extension .csv" );
		 System.out.println();
		 System.out.println("Options: ");
		 System.out.println("  debug - shows lots of debug messages." );
	}


	// Class ACSfiltlist
	// Represents a FILTLIST
	public class ACSfiltlist {
		public String name;
		public String include;
		public String exclude;
	}

}
