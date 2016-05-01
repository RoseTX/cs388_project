import java.util.*;
import java.lang.*;
import java.io.*;

public class Preprocessor {
	public static void main(String[] args) throws FileNotFoundException, IOException{
        String data_dir = args[0];
        
        preprocess_treebank(data_dir);
	}

    public static void preprocess_rawdata (String data_dir) throws FileNotFoundException, IOException{//synchronously removes empty blank lines
        String french_data = data_dir + "french_test_set";
        String english_data = data_dir + "english_test_set";

        BufferedReader french_raw_data = new BufferedReader(new FileReader(french_data));
        BufferedReader english_raw_data = new BufferedReader(new FileReader(english_data));

        PrintWriter french_output = new PrintWriter(french_data + "_noblanks");
        PrintWriter english_output = new PrintWriter(english_data + "_noblanks");

        int i = 0;
        for (String fr_line, en_line; ((fr_line = french_raw_data.readLine()) != null) && ((en_line = english_raw_data.readLine()) != null);) {
            i++;
            if(!fr_line.equals("") && !en_line.equals("")){
                french_output.println(fr_line);
                english_output.println(en_line);
            }
            else{
                System.out.println("Blank " + i);
            }
        }

        french_raw_data.close();
        french_output.close();
        english_raw_data.close();
        english_output.close();
    }

    public static void preprocess_treebank (String data_dir) throws FileNotFoundException, IOException{//synchronously removes empty parentheses lines
        String french_data = data_dir + "french_train_set_noblanks";
        String english_data = data_dir + "english_train_set_noblanks";

        BufferedReader french_raw_data = new BufferedReader(new FileReader(french_data));
        BufferedReader english_raw_data = new BufferedReader(new FileReader(english_data));

        PrintWriter french_output = new PrintWriter(french_data + "_clean");
        PrintWriter english_output = new PrintWriter(english_data + "_clean");

        int i = 0;
        for (String fr_line, en_line; ((fr_line = french_raw_data.readLine()) != null) && ((en_line = english_raw_data.readLine()) != null);) {
            i++;
            if(!fr_line.equals("(())") && !en_line.equals("(())")){
                french_output.println(fr_line);
                english_output.println(en_line);
            }
            else{
                System.out.println("Blank " + i);
            }
        }

        french_raw_data.close();
        french_output.close();
        english_raw_data.close();
        english_output.close();
    }
}