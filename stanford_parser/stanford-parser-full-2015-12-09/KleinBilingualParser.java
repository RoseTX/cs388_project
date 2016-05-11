import edu.stanford.nlp.parser.lexparser.BinaryGrammar;
import edu.stanford.nlp.parser.lexparser.DependencyGrammar;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.UnaryGrammar;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.common.ArgUtils;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.EvaluateTreebank;
import edu.stanford.nlp.parser.lexparser.ExactGrammarCompactor;
import edu.stanford.nlp.parser.lexparser.GrammarCompactor;
import edu.stanford.nlp.parser.lexparser.ParseFiles;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.util.ErasureUtils;
import java.util.function.Function;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.tagger.io.TaggedFileRecord;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.IntPair;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.parser.lexparser.LexicalizedParserQuery;

import java.io.*;
import java.util.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// MALLET Shit
import cc.mallet.optimize.*;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Hudson
 */


public class KleinBilingualParser extends LexicalizedParser{
    private static final Redwood.RedwoodChannels log = Redwood.channels(LexicalizedParser.class);
    
    private Options op;
    @Override
    public Options getOp() { return op; }
    @Override
    public void setOptionFlags(String... flags) {
        op.setOptions(flags);
    }
    
    public KleinBilingualParser(Lexicon lex, BinaryGrammar bg, UnaryGrammar ug, DependencyGrammar dg, Index<String> stateIndex, Index<String> wordIndex, Index<String> tagIndex, Options op) {
        super(lex, bg, ug, dg, stateIndex, wordIndex, tagIndex, op);
        this.op = op;
    }
    
    public static void main(String[] args) {
        boolean trainF = false;
        boolean trainE = false;
        boolean saveToSerializedFile = false;
        boolean saveToTextFile = false;
        String serializedInputFileOrUrl = null;
        String textInputFileOrUrl = null;
        String serializedOutputFileOrUrl = null;
        String textOutputFileOrUrl = null;
        String treebankPathF = null;
        Treebank testTreebankF = null;
        Treebank tuneTreebankF = null;
        String testPathF = null;
        FileFilter testFilterF = null;
        String treebankPathE = null;
        Treebank testTreebankE = null;
        Treebank tuneTreebankE = null;
        String testPathE = null;
        FileFilter testFilterE = null;
        String tunePath = null;
        FileFilter tuneFilter = null;
        FileFilter trainFilterF = null;
        FileFilter trainFilterE = null;
        String secondaryTreebankPath = null;
        double secondaryTreebankWeight = 1.0;
        FileFilter secondaryTrainFilter = null;
        
        // variables needed to process the files to be parsed
        TokenizerFactory<? extends HasWord> tokenizerFactory = null;
        String tokenizerOptions = null;
        String tokenizerFactoryClass = null;
        String tokenizerMethod = null;
        boolean tokenized = false; // whether or not the input file has already been tokenized
        Function<List<HasWord>, List<HasWord>> escaper = null;
        String tagDelimiter = null;
        String sentenceDelimiter = null;
        String elementDelimiter = null;
        int argIndex = 0;
        if (args.length < 1) {
            log.info("Basic usage (see Javadoc for more): java edu.stanford.nlp.parser.lexparser.LexicalizedParser parserFileOrUrl filename*");
            return;
        }
        
        Options fOp = new Options();
        Options eOp = new Options();
        List<String> optionArgs = new ArrayList<>();
        String encodingF = null;
        // while loop through option arguments
        while (!args[argIndex].equals("--") && argIndex < args.length && args[argIndex].charAt(0) == '-') {
            if (args[argIndex].equalsIgnoreCase("-train") ||
                args[argIndex].equalsIgnoreCase("-trainTreebank")) {
                trainF = true;
                Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-train");
                argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
                treebankPathF = treebankDescription.first();
                trainFilterF = treebankDescription.second();
            } else if (args[argIndex].equalsIgnoreCase("-tLPP") && (argIndex + 1 < args.length)) {
                try {
                    fOp.tlpParams = (TreebankLangParserParams) Class.forName(args[argIndex + 1]).newInstance();
                } catch (ClassNotFoundException e) {
                    log.info("Class not found: " + args[argIndex + 1]);
                    throw new RuntimeException(e);
                } catch (InstantiationException e) {
                    log.info("Couldn't instantiate: " + args[argIndex + 1] + ": " + e.toString());
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    log.info("Illegal access" + e);
                    throw new RuntimeException(e);
                }
                argIndex += 2;
            } else if (args[argIndex].equalsIgnoreCase("-encoding")) {
                // sets encoding for TreebankLangParserParams
                // redone later to override any serialized parser one read in
                encodingF = args[argIndex + 1];
                fOp.tlpParams.setInputEncoding(encodingF);
                fOp.tlpParams.setOutputEncoding(encodingF);
                argIndex += 2;
            } else if (args[argIndex].equalsIgnoreCase("-treebank") ||
                       args[argIndex].equalsIgnoreCase("-testTreebank") ||
                       args[argIndex].equalsIgnoreCase("-test")) {
                Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-test");
                argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
                testPathF = treebankDescription.first();
                testFilterF = treebankDescription.second();
            } else {
                int oldIndex = argIndex;
                argIndex = fOp.setOptionOrWarn(args, argIndex);
                optionArgs.addAll(Arrays.asList(args).subList(oldIndex, argIndex));
            }
            
            System.out.println(argIndex + " " + args.length);
        } // end while loop through arguments for french
        
        argIndex++;//go to english arguments
        
        while (argIndex < args.length && args[argIndex].charAt(0) == '-') {
            if (args[argIndex].equalsIgnoreCase("-train") ||
                args[argIndex].equalsIgnoreCase("-trainTreebank")) {
                trainE = true;
                Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-train");
                argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
                treebankPathE = treebankDescription.first();
                trainFilterE = treebankDescription.second();
            } else if (args[argIndex].equalsIgnoreCase("-treebank") ||
                       args[argIndex].equalsIgnoreCase("-testTreebank") ||
                       args[argIndex].equalsIgnoreCase("-test")) {
                Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-test");
                argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
                testPathE = treebankDescription.first();
                testFilterE = treebankDescription.second();
            } else {
                int oldIndex = argIndex;
                argIndex = eOp.setOptionOrWarn(args, argIndex);
                optionArgs.addAll(Arrays.asList(args).subList(oldIndex, argIndex));
            }
        } // end while loop through arguments for english
        
        //    if (!train && fOp.testOptions.verbose) {
        //      StringUtils.logInvocationString(log, args);
        //    }
        LexicalizedParser lpF; // always initialized in next if-then-else block
        LexicalizedParser lpE;
        //TRAIN A PARSER
        // so we train a parser using the treebank
        GrammarCompactor compactorF = null;
        GrammarCompactor compactorE = null;
        if (fOp.trainOptions.compactGrammar() == 3) {
            compactorF = new ExactGrammarCompactor(fOp, false, false);
        }
        if (eOp.trainOptions.compactGrammar() == 3) {
            compactorE = new ExactGrammarCompactor(eOp, false, false);
        }
        
        Treebank trainTreebankF = makeTreebank(treebankPathF, fOp, trainFilterF);
        Treebank trainTreebankE = makeTreebank(treebankPathE, eOp, trainFilterE);
        
        fOp.testOptions.quietEvaluation = true;
        eOp.testOptions.quietEvaluation = true;
        
        lpF = getParserFromTreebank(trainTreebankF, null, secondaryTreebankWeight, compactorF, fOp, tuneTreebankF, null);
        lpE = getParserFromTreebank(trainTreebankE, null, secondaryTreebankWeight, compactorE, eOp, tuneTreebankE, null);
        
        //GET FRENCH TREEBANK
        
        // the following has to go after reading parser to make sure
        // op and tlpParams are the same for train and test
        // THIS IS BUTT UGLY BUT IT STOPS USER SPECIFIED ENCODING BEING
        // OVERWRITTEN BY ONE SPECIFIED IN SERIALIZED PARSER
        if (encodingF != null) {
            fOp.tlpParams.setInputEncoding(encodingF);
            fOp.tlpParams.setOutputEncoding(encodingF);
        }
        
        if (testFilterF != null || testPathF != null) {
            if (testPathF == null) {
                if (treebankPathF == null) {
                    throw new RuntimeException("No test treebank path specified...");
                } else {
                    log.info("No test treebank path specified.  Using train path: \"" + treebankPathF + '\"');
                    testPathF = treebankPathF;
                }
            }
            testTreebankF = fOp.tlpParams.testMemoryTreebank();
            testTreebankF.loadPath(testPathF, testFilterF);
        }
        
        fOp.trainOptions.sisterSplitters = Generics.newHashSet(Arrays.asList(fOp.tlpParams.sisterSplitters()));
        
        //GET ENGLISH TREEBANK
        
        if (testFilterE != null || testPathE != null) {
            if (testPathE == null) {
                if (treebankPathE == null) {
                    throw new RuntimeException("No test treebank path specified...");
                } else {
                    log.info("No test treebank path specified.  Using train path: \"" + treebankPathE + '\"');
                    testPathE = treebankPathE;
                }
            }
            testTreebankE = eOp.tlpParams.testMemoryTreebank();
            testTreebankE.loadPath(testPathE, testFilterE);
        }
        
        eOp.trainOptions.sisterSplitters = Generics.newHashSet(Arrays.asList(eOp.tlpParams.sisterSplitters()));
        
        //WORD ALIGNMENT 


        //HARD CODED NEED TO CHANGE to ARGUMENT TO PARSER
        ArrayList<TreeMap<Integer,ArrayList<Integer>>> alignments=null;
        String alignFile="../../berkeleyaligner/output/test.align";
        try{
           
           AlignmentProcessor p = new AlignmentProcessor(alignFile);
           alignments = p.createAlignments();
          
          }catch(FileNotFoundException e){ throw new RuntimeException(e);
          }catch(IOException e){throw new RuntimeException(e);}
       



        //PARALLEL ALIGNMENT FEATURE CALCULATION
        
        Iterator<Tree> eTrees = testTreebankE.iterator();
        Iterator<Tree> fTrees = testTreebankF.iterator();
        Iterator<TreeMap<Integer,ArrayList<Integer>>> alignIterator = alignments.iterator();

        int kE = 10;
        int kF = 10;
        int numFeatures = 4;
        //features are used in the order they are defined
        double A[][][][] = new double[testTreebankE.size()][numFeatures][kE][kF];
        int ePsGold[] = new int[testTreebankE.size()];
        int fPsGold[] = new int[testTreebankF.size()];
        
        int i = 0;
        while(eTrees.hasNext() && fTrees.hasNext() && alignIterator.hasNext()){

            TreeMap<Integer,ArrayList<Integer>> alignMap = alignIterator.next();
            Tree fTree = fTrees.next();
            Tree eTree = eTrees.next();
            
            List<? extends HasWord> sentenceF = Sentence.toCoreLabelList(fTree.yieldWords());
            List<? extends HasWord> sentenceE = Sentence.toCoreLabelList(eTree.yieldWords());


            
            LexicalizedParserQuery lpqE = (LexicalizedParserQuery) lpE.parserQuery();
            LexicalizedParserQuery lpqF = (LexicalizedParserQuery) lpF.parserQuery();
            
            lpqE.parse(sentenceE);
            lpqF.parse(sentenceF);
            
            List<ScoredObject<Tree>> kBestF = lpqF.getKBestPCFGParses(kF);
            List<ScoredObject<Tree>> kBestE = lpqE.getKBestPCFGParses(kE);
            
            fPsGold[i] = 3;
            ePsGold[i] = 3;
            
            int j = 0;
            int k = 0;
            
            for (ScoredObject<Tree> eScoredObj : kBestE){
                k = 0;
                for (ScoredObject<Tree> fScoredObj : kBestF){
                    HashMap<Tree, Tree> alignment = getSampleNodeAlignment(eScoredObj.object(), fScoredObj.object());
                    
                    //had to reduce likelihood scores by factor of 10 to keep the optimizer working
                    A[i][0][j][k] = eScoredObj.score()/10;
                    A[i][1][j][k] = fScoredObj.score()/10;
                    
                    for (Map.Entry entry : alignment.entrySet()){
                        Tree nodeF = (Tree) entry.getKey();
                        Tree nodeE = (Tree) entry.getValue();
                        nodeF.setSpans();
                        nodeE.setSpans();

                        double inBoth_feature=insideBoth(nodeF,nodeE, alignMap);
                        double inSrcOutTgt_feature= insideSrcOutsideTgt(nodeF,nodeE, alignMap);
                        double inTgtOutSrc_feature= insideTgtOutsideSrc(nodeF,nodeE, alignMap);
                        
                        A[i][2][j][k] += (double) spanDiff(nodeF, nodeE);
                        A[i][3][j][k] += (double) numChildren(nodeF, nodeE);
                    }
                    
                    k++;
                }
                j++;
            }
            i++;
        }
        
        
        ///////////////////////
        //
        //  MALLET optimizer
        //
        ///////////////////////
        System.out.println();
        System.out.println("*!*!*!*!*!*!*!*!*!*!*!*!*!*!*!*!*!*!*");
        System.out.println();
        System.out.println("Beginning convex optimization...");
        System.out.println();
        System.out.println("*!*!*!*!*!*!*!*!*!*!*!*!*!*!*!*!*!*!*");
        System.out.println();
        
        double[] initWeights = new double[4];
//        initWeights[0] = 0.1;
//        initWeights[1] = -0.2;
//        initWeights[2] = 0.02;
//        initWeights[3] = 0.2;
        
        OptimizerExample optimizable = new OptimizerExample(initWeights, A, ePsGold, fPsGold);
        Optimizer optimizer = new LimitedMemoryBFGS(optimizable);
        
        boolean converged = false;
        
        try {
            converged = optimizer.optimize();
        } catch (IllegalArgumentException e) {
            // This exception may be thrown if L-BFGS
            //  cannot step in the current direction.
            // This condition does not necessarily mean that
            //  the optimizer has failed, but it doesn't want
            //  to claim to have succeeded...
        } catch (cc.mallet.optimize.OptimizationException e) {
            System.out.println(e.getMessage());
        }
        
        System.out.println(optimizable.getParameter(0) + ", " + optimizable.getParameter(1) + ", " + optimizable.getParameter(2) + ", " + optimizable.getParameter(3));
        
    } // end main
    
    /////////////////////////
    //
    //  FEATURES
    //
    /////////////////////////
    
    private static double eLikelihood (Tree nodeF, Tree nodeE){//included for clarity
        return 0.0;
        //this function is calculated directly in the
    }
    
    private static double fLikelihood (Tree nodeF, Tree nodeE){//included for clarity
        return 0.0;
    }
    
    private static int spanDiff (Tree nodeF, Tree nodeE){
        return Math.abs(nodeF.getLeaves().size() - nodeE.getLeaves().size());
    }
    
    //assuming this is an indicator that checks if the number of children is the same or not
    private static int numChildren (Tree nodeF, Tree nodeE){
        if (nodeF.numChildren() == nodeE.numChildren()){
            return 1;
        }
        else {
            return 0;
        }
    }

private static double insideBoth(Tree nodeF, Tree nodeE, TreeMap<Integer,ArrayList<Integer>> alignMap)
{
  
    IntPair spanF=nodeF.getSpan();
    IntPair spanE=nodeF.getSpan();
    /*
    List<Word> sentenceF = nodeF.yieldWords();
    List<Word> sentenceE = nodeE.yieldWords();

          for(int h=0;h<sentenceF.size();h++)
              System.out.print(sentenceF.get(h)+" ");
            System.out.println();

            for(int h=0;h<sentenceE.size();h++)
              System.out.print(sentenceE.get(h)+" ");
            System.out.println();    
    */

   /* if(spanF.getSource()!=spanE.getSource() || spanF.getTarget()!=spanE.getTarget())
    {
      System.out.println("DIFFERENT");
        System.out.println(spanF.getSource()+" "+spanF.getTarget());
      System.out.println(spanE.getSource()+" "+spanE.getTarget());
    }
    */

    double sum=0;
    for (int f=spanF.getSource();f<=spanF.getTarget();f++)
    {
      for (int e=spanE.getSource();e<=spanE.getTarget();e++)
      {
        //System.out.println("indices:"+f+" "+e);
        if(alignMap.get(f)!=null && alignMap.get(f).contains(e))
          sum++;
      }
    }


    return sum;
}

private static double insideSrcOutsideTgt(Tree nodeF, Tree nodeE, TreeMap<Integer,ArrayList<Integer>> alignMap)
{
  
    IntPair spanF=nodeF.getSpan();
    IntPair spanE=nodeF.getSpan();

    double sum=0;
    for (int f=spanF.getSource();f<=spanF.getTarget();f++)
    {
      for (int e=0;e<spanE.getSource();e++)
      {
        if(alignMap.get(f)!=null && alignMap.get(f).contains(e))
          sum++;
      }
    }

    for (int f=spanF.getSource();f<=spanF.getTarget();f++)
    {
      for (int e=spanE.getTarget()+1;e<nodeE.size();e++)
      {
        if(alignMap.get(f)!=null && alignMap.get(f).contains(e))
          sum++;
      }
    }

    return sum;
}


private static double insideTgtOutsideSrc(Tree nodeF, Tree nodeE, TreeMap<Integer,ArrayList<Integer>> alignMap)
{
  
    IntPair spanF=nodeF.getSpan();
    IntPair spanE=nodeF.getSpan();

    double sum=0;
    for (int e=spanE.getSource();e<=spanE.getTarget();e++)
    {
      for (int f=0;e<spanF.getSource();f++)
      {
        if(alignMap.get(f)!=null && alignMap.get(f).contains(e))
          sum++;
      }
    }

    for (int e=spanE.getSource();e<=spanE.getTarget();e++)
    {
      for (int f=spanF.getTarget()+1;f<nodeE.size();f++)
      {
        if(alignMap.get(f)!=null && alignMap.get(f).contains(e))
          sum++;
      }
    }
    return sum;
}


    
    /////////////////////////
    /////////////////////////
    
    
    /////////////////////////
    //
    //  KLEIN FUNCTIONS
    //
    /////////////////////////
    
    // assume alignment from french nodes to english nodes
    // omitting leaf node alignments - even if one of the node is a leaf
    // the below function assumes
    private static HashMap<Tree, Tree> getSampleNodeAlignment(Tree eParseTree, Tree fParseTree){
        HashMap<Tree, Tree> alignment = new HashMap<>();
        
        Iterator<Tree> eSubtrees = eParseTree.iterator();
        for (Tree fSubTree : fParseTree){
            if (!eSubtrees.hasNext()){
                break;
            }
            else {
                Tree eSubTree = eSubtrees.next();
                if (!fSubTree.isLeaf() && !eSubTree.isLeaf()){
                    alignment.put(fSubTree, eSubTree);
                }
            }
        }
        
        return alignment;
    }
    
    /////////////////////////
    /////////////////////////
    
    
    /////////////////////////
    //
    //  MALLET
    //
    /////////////////////////
    
    private static class OptimizerExample implements Optimizable.ByGradientValue {
        
        // Optimizables encapsulate all state variables,
        //  so a single Optimizer object can be used to optimize
        //  several functions.
        
        double[] parameters;
        
        double[][][][] A;
        int[] ePseGold;
        int[] fPseGold;
        
        public OptimizerExample(double[] weights, double[][][][] A, int[] ePseGold, int[] fPseGold) {
            parameters = weights;
            this.A = A;
            this.ePseGold = ePseGold;
            this.fPseGold = fPseGold;
        }
        
        public double getValue() {
            double result = 0.0;
            
            for (int i = 0; i < this.A.length; i++){
                double pseGoldResult = 0.0;
                double allPairs = 0.0;
                
                for (int j = 0; j < this.A[0][0].length; j++){
                    for (int k = 0; k < this.A[0][0][0].length; k++){
                        double singlePairScore = 1.0;
                        
                        for (int m = 0; m < this.parameters.length; m++){
                            singlePairScore *= Math.exp(this.A[i][m][j][k] * this.parameters[m]);
                        }
                        
                        allPairs += singlePairScore;
                        if (j < this.ePseGold[i] && k < this.fPseGold[i]){
                            pseGoldResult += singlePairScore;
                        }
                    }
                }
                
                result += (Math.log(pseGoldResult) - Math.log(allPairs));
            }
            
            return result;
        }
        
        public void getValueGradient(double[] gradient) {
            for (int m = 0; m < this.parameters.length; m++){
                gradient[m] = 0.0;
            }
            
            for (int i = 0; i < this.A.length; i++){
                double pseGoldResultDenom = 0.0;
                double allPairsDenom = 0.0;
                double[] pseGoldResultNumer = new double[this.parameters.length];
                double[] allPairsNumer = new double[this.parameters.length];
                
                for (int j = 0; j < this.A[0][0].length; j++){
                    for (int k = 0; k < this.A[0][0][0].length; k++){
                        double singlePairScore = 1.0;
                        
                        for (int m = 0; m < this.parameters.length; m++){
                            singlePairScore *= Math.exp(this.A[i][m][j][k] * this.parameters[m]);
                        }
                        
                        allPairsDenom += singlePairScore;
                        if (j < this.ePseGold[i] && k < this.fPseGold[i]){
                            pseGoldResultDenom += singlePairScore;
                        }
                        
                        for (int m = 0; m < this.parameters.length; m++){
                            allPairsNumer[m] += A[i][m][j][k]*singlePairScore;
                            if (j < this.ePseGold[i] && k < this.fPseGold[i]){
                                pseGoldResultNumer[m] += A[i][m][j][k]*singlePairScore;
                            }
                        }
                    }
                }
                
                for (int m = 0; m < this.parameters.length; m++){
                    gradient[m] += (pseGoldResultNumer[m]/pseGoldResultDenom - allPairsNumer[m]/allPairsDenom);
                }
            }
        }
        
        // The following get/set methods satisfy the Optimizable interface
        
        public int getNumParameters() { return 4; }
        public double getParameter(int i) { return parameters[i]; }
        public void getParameters(double[] buffer) {
            buffer[0] = parameters[0];
            buffer[1] = parameters[1];
            buffer[2] = parameters[2];
            buffer[3] = parameters[3];
        }
        
        public void setParameter(int i, double r) {
            parameters[i] = r;
        }
        public void setParameters(double[] newParameters) {
            parameters[0] = newParameters[0];
            parameters[1] = newParameters[1];
            parameters[2] = newParameters[2];
            parameters[3] = newParameters[3];
        }
    }
    
    /////////////////////////
    /////////////////////////
    
    
    ///////////////////////////////
    //
    //  STANFORD PARSER FUNCTIONS
    //
    ///////////////////////////////
    
    private static Treebank makeTreebank(String treebankPath, Options op, FileFilter filt) {
        log.info("Training a parser from treebank dir: " + treebankPath);
        Treebank trainTreebank = op.tlpParams.diskTreebank();
        log.info("Reading trees...");
        if (filt == null) {
            trainTreebank.loadPath(treebankPath);
        } else {
            trainTreebank.loadPath(treebankPath, filt);
        }
        
        Timing.tick("done [read " + trainTreebank.size() + " trees].");
        return trainTreebank;
    }
    
    private static DiskTreebank makeSecondaryTreebank(String treebankPath, Options op, FileFilter filt) {
        log.info("Additionally training using secondary disk treebank: " + treebankPath + ' ' + filt);
        DiskTreebank trainTreebank = op.tlpParams.diskTreebank();
        log.info("Reading trees...");
        if (filt == null) {
            trainTreebank.loadPath(treebankPath);
        } else {
            trainTreebank.loadPath(treebankPath, filt);
        }
        Timing.tick("done [read " + trainTreebank.size() + " trees].");
        return trainTreebank;
    }
    
    private static void printOptions(boolean train, Options op) {
        op.display();
        if (train) {
            op.trainOptions.display();
        } else {
            op.testOptions.display();
        }
        op.tlpParams.display();
    }
    
}