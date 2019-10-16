ACS Extract

For the SHARE conference in San Antonio in 2016, I presented
a session on reading someone else's ACS routines. As part of
that presentation, I created a small tool that reads ACS
routines and spits them out as a list of rules in CSV format.

You can view the original presentation at the following link:
https://share.confex.com/share/125/webprogram/Handout/Session17836/17836%20-%20Reading%20ACS.pdf

Syntax: 
   ./run inputfile.txt <outputfile> <options>

If you do not specify an output file, program will use the
output filename with extension .csv

Options: 
  debug - shows lots of debug messages.

Dependencies:
  Requires that you have a java compiler and ant build tool installed.

To compile:
  ant compile
