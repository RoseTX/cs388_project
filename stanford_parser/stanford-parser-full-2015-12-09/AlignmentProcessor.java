import java.util.*;
import java.lang.*;
import java.io.*;

public class AlignmentProcessor {

    private String fileName;//alignment file
    private ArrayList<HashMap<Integer,ArrayList<Integer>>> frenchtoEnglishIndices;
    //frenchtoEnglishIndices[0][1] represents the second word of the first line in French and its corresponding word indice in the englihs sentcence

	public static void main(String[] args) throws FileNotFoundException, IOException{
        AlignmentProcessor p = new AlignmentProcessor(args[0]);
        ArrayList<HashMap<Integer,ArrayList<Integer>>> r = p.createAlignments();
        System.out.println(p.getAlignmentForWord(1,3).get(0));

       
	}

    public AlignmentProcessor(String f)
    {
        this.fileName=f;
        this.frenchtoEnglishIndices=new ArrayList<HashMap<Integer,ArrayList<Integer>>>();
    }

    public ArrayList<HashMap<Integer,ArrayList<Integer>>> createAlignments () throws FileNotFoundException, IOException{//synchronously removes empty blank lines

        //data_dir should point to the file.
        BufferedReader align_data = new BufferedReader(new FileReader(this.fileName));

        for (String line; ((line = align_data.readLine()) != null);) {
            

            String[] pairs= line.split(" ");
            int numWords=pairs.length;
            //System.out.println(numWords);

            HashMap<Integer,ArrayList<Integer>> align = new HashMap<Integer,ArrayList<Integer>>();
            for(String word:pairs)
            {

                int index1 = Integer.parseInt(word.split("-")[0]);
                int index2 = Integer.parseInt(word.split("-")[1]);
                if(!align.containsKey(index1))
                    align.put(index1,new ArrayList<Integer>());
                align.get(index1).add(index2);

            }
            /*
            for(int k: align.keySet())
            {
                System.out.println("K:"+k);
                for(int j: align.get(k))
                    System.out.print(j+" ");
                System.out.println();
            }*/
            
            frenchtoEnglishIndices.add(align);


        }

        return frenchtoEnglishIndices;

    }

    public ArrayList<Integer> getAlignmentForWord(int line, int word)
    {
        // DOES NOT CHECK FOR OUT OF BOUNDS OR NULL, DO NEXT
        return frenchtoEnglishIndices.get(line).get(word);
    }

}