package playground.jjoubert.CommercialTraffic;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class FixMinorLocationsOutput {
	// String value that must be set
	final static String PROVINCE = "Gauteng";
	// Mac
//	final static String ROOT = "/Users/johanwjoubert/MATSim/workspace/MATSimData/";
	// IVT-Sim0
	final static String ROOT = "/home/jjoubert/";
	// Derived string values:
	final static String IN_FILE = ROOT + PROVINCE + "/Activities/" + PROVINCE + "MinorLocations.txt";
	final static String OUT_FILE = ROOT + PROVINCE + "/Activities/new" + PROVINCE + "MinorLocations.txt";
	// Other paramaters
	final static int THRESHOLD = ActivityLocations.HOME_DURATION_THRESHOLD;
	final static String DELIMITER = ",";

	public static void main(String args[]){
		try {
			Scanner input = new Scanner(new BufferedReader(new FileReader( new File(IN_FILE ) ) ) );
			BufferedWriter output = new BufferedWriter(new FileWriter(new File(OUT_FILE ) ) );
			
			String header = input.nextLine();
			output.write( header );
			while(input.hasNextLine() ){
				String line = input.nextLine();
				String [] lineSplit = line.split( DELIMITER );
				if( Integer.parseInt(lineSplit[4]) < THRESHOLD ){
					output.write( line );
					output.newLine();
				}
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
