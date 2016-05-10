import java.util.*;
import java.lang.*;
import java.io.*;

public class AlignmentProcessor {

    private static ArrayList<TreeMap<Integer,ArrayList<Integer>>> frenchtoEnglishIndices;
    //frenchtoEnglishIndices[0][1] represents the second word of the first line in French and its corresponding word indice in the englihs sentcence

	public static void main(String[] args) throws FileNotFoundException, IOException{
        frenchtoEnglishIndices= new ArrayList<TreeMap<Integer,ArrayList<Integer>>>();
        createAlignments(args[0]);
       
	}

    public static void createAlignments (String data_dir) throws FileNotFoundException, IOException{//synchronously removes empty blank lines

        //data_dir should point to the file.
        BufferedReader align_data = new BufferedReader(new FileReader(data_dir));

        for (String line; ((line = align_data.readLine()) != null);) {
            

            String[] pairs= line.split(" ");
            int numWords=pairs.length;
            System.out.println(numWords);

            TreeMap<Integer,ArrayList<Integer>> align = new TreeMap<Integer,ArrayList<Integer>>();
            for(String word:pairs)
            {

                int index1 = Integer.parseInt(word.split("-")[0]);
                int index2 = Integer.parseInt(word.split("-")[1]);
                if(!align.containsKey(index1))
                    align.put(index1,new ArrayList<Integer>());
                align.get(index1).add(index2);

            }

            for(int k: align.keySet())
            {
                System.out.println("K:"+k);
                for(int j: align.get(k))
                    System.out.print(j+" ");
                System.out.println();
            }
            
            frenchtoEnglishIndices.add(align);


        }

        System.out.println(frenchtoEnglishIndices.size());

    }

}