import java.util.*;
import java.lang.*;
import java.io.*;

public class RemoveParenSentences {
	public static void main(String[] args) throws FileNotFoundException, IOException{
		BufferedReader treebank = new BufferedReader(new FileReader(args[0]));
		PrintWriter output = new PrintWriter(args[1]);

		int i = 0;
		for (String line; (line = treebank.readLine()) != null;) {
			i++;
            if(!line.contains("-LRB-") && !line.contains("-RRB-")){
            	output.println(line);
            }
            else{
            	System.out.println(i);
            }
        }

        treebank.close();
        output.close();
	}
}