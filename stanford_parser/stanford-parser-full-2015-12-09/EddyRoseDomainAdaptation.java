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
import edu.stanford.nlp.parser.metrics.Evalb;
import edu.stanford.nlp.parser.metrics.AbstractEval;
import edu.stanford.nlp.trees.MemoryTreebank;

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


public class EddyRoseDomainAdaptation extends LexicalizedParser{
    private static final Redwood.RedwoodChannels log = Redwood.channels(LexicalizedParser.class);
    
    private Options op;
    @Override
    public Options getOp() { return op; }
    @Override
    public void setOptionFlags(String... flags) {
        op.setOptions(flags);
    }
    
    public EddyRoseDomainAdaptation(Lexicon lex, BinaryGrammar bg, UnaryGrammar ug, DependencyGrammar dg, Index<String> stateIndex, Index<String> wordIndex, Index<String> tagIndex, Options op) {
        super(lex, bg, ug, dg, stateIndex, wordIndex, tagIndex, op);
        this.op = op;
    }
    
    public static void main(String[] args) {
        boolean trainF = false;
        boolean trainE = false;
        boolean bitrainE = false;
        boolean bitrainF = false;
        boolean saveToSerializedFile = false;
        boolean saveToTextFile = false;
        String serializedInputFileOrUrl = null;
        String textInputFileOrUrl = null;
        String serializedOutputFileOrUrl = null;
        String textOutputFileOrUrl = null;
        String treebankPathF = null;
        Treebank testTreebankF = null;
        Treebank seqTestTreebank = null;
        Treebank tuneTreebankF = null;
        String testPathF = null;
        FileFilter testFilterF = null;
        String treebankPathE = null;
        Treebank testTreebankE = null;
        Treebank tuneTreebankE = null;
        String testPathE = null;
        FileFilter testFilterE = null;
        String seqTestPath = null;
        FileFilter seqTestFilter = null;
        String tunePath = null;
        FileFilter tuneFilter = null;
        FileFilter trainFilterF = null;
        FileFilter trainFilterE = null;
        String secondaryTreebankPath = null;
        double secondaryTreebankWeight = 1.0;
        FileFilter secondaryTrainFilter = null;

        String trainAlignFile=null;
        String testAlignFile=null;
        String  bitrainPathE = null;
        FileFilter bitrainFilterE = null;
        String  bitrainPathF = null;
        FileFilter bitrainFilterF = null;
        Treebank bitrainTreebankF = null;
        Treebank bitrainTreebankE = null;

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
                    }else if (args[argIndex].equalsIgnoreCase("-bitrain") ||
                        args[argIndex].equalsIgnoreCase("-bitrainTreebank")) {
                        bitrainF = true;
                        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-bitrain");
                        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
                        bitrainPathF = treebankDescription.first();
                        bitrainFilterF = treebankDescription.second();
                    }  
                    else if (args[argIndex].equalsIgnoreCase("-tLPP") && (argIndex + 1 < args.length)) {
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
                    }else if (args[argIndex].equalsIgnoreCase("-bitrain") ||
                        args[argIndex].equalsIgnoreCase("-bitrainTreebank")) {
                        bitrainE = true;
                        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-bitrain");
                        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
                        bitrainPathE = treebankDescription.first();
                        bitrainFilterE = treebankDescription.second();
                    } 
                     else if (args[argIndex].equalsIgnoreCase("-treebank") ||
                               args[argIndex].equalsIgnoreCase("-testTreebank") ||
                               args[argIndex].equalsIgnoreCase("-test")) {
                        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-test");
                        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
                        testPathE = treebankDescription.first();
                        testFilterE = treebankDescription.second();
                    } else if (args[argIndex].equalsIgnoreCase("-seqtest")) {
                        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-test");
                        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
                        seqTestPath = treebankDescription.first();
                        seqTestFilter = treebankDescription.second();
                    } else if (args[argIndex].equalsIgnoreCase("-trainAlignFile"))
                    {
                        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-trainAlignFile");
                        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
                        trainAlignFile=treebankDescription.first();
                    }else if (args[argIndex].equalsIgnoreCase("-testAlignFile"))
                    {
                        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-testAlignFile");
                        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
                        testAlignFile=treebankDescription.first();
                    }else {
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
        if (fOp.trainOptions.compactGrammar() == 3) {
            compactorF = new ExactGrammarCompactor(fOp, false, false);
        }
        Treebank trainTreebankF = makeTreebank(treebankPathF, fOp, trainFilterF);
        fOp.testOptions.quietEvaluation = true;

        GrammarCompactor compactorE = null;
        if (eOp.trainOptions.compactGrammar() == 3) {
            compactorE = new ExactGrammarCompactor(eOp, false, false);
        }
        
        Treebank trainTreebankE = makeTreebank(treebankPathE, eOp, trainFilterE);
        
        eOp.testOptions.quietEvaluation = true;
        
        lpF = getParserFromTreebank(trainTreebankF, null, secondaryTreebankWeight, compactorF, fOp, tuneTreebankF, null);
        lpE = getParserFromTreebank(trainTreebankE, null, secondaryTreebankWeight, compactorE, eOp, tuneTreebankE, null);
        

        
        // the following has to go after reading parser to make sure
        // op and tlpParams are the same for train and test
        // THIS IS BUTT UGLY BUT IT STOPS USER SPECIFIED ENCODING BEING
        // OVERWRITTEN BY ONE SPECIFIED IN SERIALIZED PARSER
        if (encodingF != null) {
            fOp.tlpParams.setInputEncoding(encodingF);
            fOp.tlpParams.setOutputEncoding(encodingF);
        }
        
        if (bitrainFilterF != null || bitrainPathF != null) {
            if (bitrainPathF == null) {
                //?
                if (treebankPathF == null) {
                    throw new RuntimeException("No bitrain treebank path specified...");
                } else {
                    log.info("No test treebank path specified.  Using train path: \"" + treebankPathF + '\"');
                    bitrainPathF = treebankPathF;
                }
            }
            bitrainTreebankF = fOp.tlpParams.testMemoryTreebank();
            bitrainTreebankF.loadPath(bitrainPathF, bitrainFilterF);
        }             
        
        if (bitrainFilterE != null || bitrainPathE != null) {
            if (bitrainPathE == null) {
                if (treebankPathE == null) {
                    throw new RuntimeException("No test treebank path specified...");
                } else {
                    log.info("No test treebank path specified.  Using train path: \"" + treebankPathE + '\"');
                    bitrainPathE = treebankPathE;
                }
            }
            bitrainTreebankE = eOp.tlpParams.testMemoryTreebank();
            bitrainTreebankE.loadPath(bitrainPathE, bitrainFilterE);
        }

        
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

        //generate sequioa treebank
        seqTestTreebank = fOp.tlpParams.testMemoryTreebank();
        seqTestTreebank.loadPath(seqTestPath, seqTestFilter);
        
        fOp.trainOptions.sisterSplitters = Generics.newHashSet(Arrays.asList(fOp.tlpParams.sisterSplitters()));
        

        
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
        


        //////////////////////
        // 
        // Self-Training
        //
        //////////////////////

         MemoryTreebank selfTrainInitTreebank = new MemoryTreebank();
         MemoryTreebank selfTrainFinalTreebank = new MemoryTreebank();

         selfTrainInitTreebank.addAll(trainTreebankF);
         selfTrainFinalTreebank.addAll(trainTreebankF);

         LexicalizedParser fSelfTrainInit = getParserFromTreebank(selfTrainInitTreebank, null, secondaryTreebankWeight, compactorF, fOp, tuneTreebankF, null);

         int z = 0;
         boolean runningAveragesF = Boolean.parseBoolean(fOp.testOptions.evals.getProperty("runningAverages"));
         AbstractEval factLBf = new Evalb("factor LP/LR", runningAveragesF);

         for (Tree goldTree : testTreebankF) {
             List<? extends HasWord> sentence = Sentence.toCoreLabelList(goldTree.yieldWords());
             Tree guessTree = fSelfTrainInit.parseTree(sentence);
             selfTrainFinalTreebank.add(guessTree);
             factLBf.evaluate(guessTree, goldTree);
             System.out.println("Self-training : " + (++z));
         }

         LexicalizedParser fSelfTrainFinal = getParserFromTreebank(selfTrainFinalTreebank, null, secondaryTreebankWeight, compactorF, fOp, tuneTreebankF, null);
         EvaluateTreebank evaluatorH = new EvaluateTreebank(fSelfTrainFinal);
         double scoreF1 = evaluatorH.testOnTreebank(seqTestTreebank);

         System.out.println("------------------------");
         System.out.println("    Self Train Results  ");
         System.out.println("------------------------");
         System.out.println("Test set F1: " + scoreF1);
         System.out.println("F1 on projected training data: " + factLBf.getEvalbF1Percent());

        //////////////////////
        //////////////////////

        //PARALLEL ALIGNMENT FEATURE CALCULATION, CALCULATION OF 'A' MATRIX

        double[] weights = new double[8];
        double diff;
       weights[0] = 0.01;
       weights[1] = -0.002;
       weights[2] = 0.002;
       weights[3] = 0.002;
       weights[4] = 0.002;
        weights[5] = 0.002;
        weights[6] = -0.002;
        weights[7] = -0.002;


        ArrayList<HashMap<Integer,ArrayList<Integer>>> bitrainAlignments=null;
        ArrayList<HashMap<Integer,ArrayList<Integer>>> testAlignments=null;
        //String alignFile="../../berkeleyaligner/output/test.align";
        try{
           
           AlignmentProcessor trainAP = new AlignmentProcessor(trainAlignFile);
           bitrainAlignments = trainAP.createAlignments();

           AlignmentProcessor testAP = new AlignmentProcessor(testAlignFile);
           testAlignments = testAP.createAlignments();

          
          }catch(FileNotFoundException e){ throw new RuntimeException(e);
          }catch(IOException e){throw new RuntimeException(e);}
        

        int kE = 10;
        int kF = 10;
        int numFeatures = 8;
        int numBigSentences = 0;
        do {
            diff = 0.0;
            Iterator<Tree> eTrees = bitrainTreebankE.iterator();
            Iterator<Tree> fTrees = bitrainTreebankF.iterator();
            Iterator<HashMap<Integer,ArrayList<Integer>>> alignIterator = bitrainAlignments.iterator();
            numBigSentences = 0;
            
            //features are used in the order they are defined
            double A[][][][] = new double[bitrainTreebankE.size()][numFeatures][kE][kF];
            int ePsGold[] = new int[bitrainTreebankE.size()];
            int fPsGold[] = new int[bitrainTreebankF.size()];
            
            int i = 0;
            while(eTrees.hasNext() && fTrees.hasNext() && alignIterator.hasNext()){
                HashMap<Integer,ArrayList<Integer>> alignMap = alignIterator.next();
                Tree fTree = fTrees.next();
                Tree eTree = eTrees.next();
                
                if (fTree.getLeaves().size() > 70 || fTree.getLeaves().size() > 70){
                    //System.out.println("Too big : " + i);
                    numBigSentences++;
                    
                    fPsGold[i] = 3;
                    ePsGold[i] = 3;
                    
                    i++;
                    continue;
                }
                
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
                        eScoredObj.object().setSpans();
                        fScoredObj.object().setSpans();
                        HashMap<Tree, Tree> alignment = getHungarianAlignment(eScoredObj.object(), fScoredObj.object(), weights, alignMap);
                        
                        //had to reduce likelihood scores by factor of 10 to keep the optimizer working
                        A[i][0][j][k] = eScoredObj.score()/1000;
                        A[i][1][j][k] = fScoredObj.score()/1000;
                        
                        for (Map.Entry entry : alignment.entrySet()){
                            Tree nodeF = (Tree) entry.getKey();
                            Tree nodeE = (Tree) entry.getValue();
                            
                            A[i][2][j][k] += spanDiff(nodeF, nodeE);
                            A[i][3][j][k] += numChildren(nodeF, nodeE);
                            A[i][4][j][k] += insideBoth(nodeF,nodeE, alignMap);
                            A[i][5][j][k] += insideSrcOutsideTgt(nodeF,nodeE, alignMap);
                            A[i][6][j][k] += insideTgtOutsideSrc(nodeF,nodeE, alignMap);
                            A[i][7][j][k] += bias(nodeF,nodeE);
                        }
                        
                        k++;
                    }
                    j++;
                }
                //System.out.println("Sentence " + i);
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
            
            OptimizerExample optimizable = new OptimizerExample(weights, A, ePsGold, fPsGold);
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

            for (int x = 0; x < weights.length; x++){
                diff += (optimizable.getParameter(x) - weights[x])*(optimizable.getParameter(x) - weights[x]);
                weights[x] = optimizable.getParameter(x);
                System.out.print(weights[x] + ", ");
            }
            System.out.println();
            diff /= weights.length;

            System.out.println("Current difference: " + diff);
        }
        while (diff > 0.0005);


        //GENERATE TRAINING DATA USING KLEIN RERANKER
        //assumes the 'test' data from KleinBilingualParser.java is the unannotated data
        //that the reranker has to annotate.
        
        factLBf = new Evalb("factor LP/LR", runningAveragesF);
        MemoryTreebank eddyRoseFullTrainTreebank = new MemoryTreebank();
        eddyRoseFullTrainTreebank.addAll(trainTreebankF);
        eddyRoseFullTrainTreebank.addAll(bitrainTreebankF);

        Treebank unannotTreebankF = testTreebankF;
        Treebank annotTreebankE = testTreebankE;
        Iterator<Tree> eTreesBling = unannotTreebankF.iterator();
        Iterator<Tree> fTreesBling = annotTreebankE.iterator();

        int i = 0;
        Iterator<HashMap<Integer,ArrayList<Integer>>> alignIteratorTEST = testAlignments.iterator();
        while (eTreesBling.hasNext() && fTreesBling.hasNext() && alignIteratorTEST.hasNext()){
            HashMap<Integer,ArrayList<Integer>> alignMap = alignIteratorTEST.next();

            Tree fTree = fTreesBling.next();
            Tree eTree = eTreesBling.next();

            List<? extends HasWord> sentenceF = Sentence.toCoreLabelList(fTree.yieldWords());
            LexicalizedParserQuery lpqF = (LexicalizedParserQuery) fSelfTrainFinal.parserQuery();
            lpqF.parse(sentenceF);
            List<ScoredObject<Tree>> kBestF = lpqF.getKBestPCFGParses(kF);


            int j = 0;
            int k = 0;

            double maxScore = -Double.MAX_VALUE;
            Tree bestFtree = null;
            
            for (ScoredObject<Tree> fScoredObj : kBestF){
                eTree.setSpans();
                fScoredObj.object().setSpans();
                HashMap<Tree, Tree> alignment = getHungarianAlignment(eTree, fScoredObj.object(), weights, alignMap);

                double currentScore = 0.0;
                
                for (Map.Entry entry : alignment.entrySet()){
                    Tree nodeF = (Tree) entry.getKey();
                    Tree nodeE = (Tree) entry.getValue();

                    currentScore += weights[0]*0.0;//because gold standard tree is assumed to have probability 1
                    currentScore += weights[1]*fScoredObj.score()/1000;
                    currentScore += weights[2]*spanDiff(nodeF, nodeE);
                    currentScore += weights[3]*numChildren(nodeF, nodeE);
                    currentScore += weights[4]*insideBoth(nodeF,nodeE, alignMap);
                    currentScore += weights[5]*insideSrcOutsideTgt(nodeF,nodeE, alignMap);
                    currentScore += weights[6]*insideTgtOutsideSrc(nodeF,nodeE, alignMap);
                    currentScore += weights[7]*bias(nodeF,nodeE);
                }

                if (currentScore > maxScore) {
                    maxScore = currentScore;

                    bestFtree = fScoredObj.object();
                }
                
                k++;
            }
            i++;

            System.out.println("Reranker " + i);
            eddyRoseFullTrainTreebank.add(bestFtree);
            factLBf.evaluate(bestFtree, fTree);
        }

        LexicalizedParser lpEddyRose = getParserFromTreebank(eddyRoseFullTrainTreebank, null, secondaryTreebankWeight, compactorF, fOp, tuneTreebankF, null);
        EvaluateTreebank evaluator = new EvaluateTreebank(lpEddyRose);
        double eddyRoseF1 = evaluator.testOnTreebank(seqTestTreebank);

        System.out.println("------------------------");
        System.out.println("    EddyRose Results    ");
        System.out.println("------------------------");
        System.out.println("Test set F1: " + eddyRoseF1);
        System.out.println("F1 on projected training data: " + factLBf.getEvalbF1Percent());
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
    
    private static double spanDiff (Tree nodeF, Tree nodeE){
        return ((double) Math.abs(nodeF.getLeaves().size() - nodeE.getLeaves().size()))/100;
    }
    
    //assuming this is an indicator that checks if the number of children is the same or not
    private static double numChildren (Tree nodeF, Tree nodeE){
        if (nodeF.numChildren() == nodeE.numChildren()){
            return 1.0/10;
        }
        else {
            return 0.0;
        }
    }

    private static double insideBoth(Tree nodeF, Tree nodeE, HashMap<Integer,ArrayList<Integer>> alignMap)
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
        if (alignMap.containsKey(f)){
            for (Integer alignedIndex : alignMap.get(f)){
                if (alignedIndex >= spanE.getSource() && alignedIndex <= spanE.getTarget()){
                    sum++;
                }
            }
        }
    }


    return sum/10;
}

private static double insideSrcOutsideTgt(Tree nodeF, Tree nodeE, HashMap<Integer,ArrayList<Integer>> alignMap)
{
    IntPair spanF=nodeF.getSpan();
    IntPair spanE=nodeF.getSpan();

    double sum=0;
    for (int f=spanF.getSource();f<=spanF.getTarget();f++)
    {
        if (alignMap.containsKey(f)){
            for (Integer alignedIndex : alignMap.get(f)){
                if (alignedIndex < spanE.getSource() && alignedIndex > spanE.getTarget()){
                    sum++;
                }
            }
        }
    }
    
    return sum/10;
}


private static double insideTgtOutsideSrc(Tree nodeF, Tree nodeE, HashMap<Integer,ArrayList<Integer>> alignMap)
{
  
    IntPair spanF=nodeF.getSpan();
    IntPair spanE=nodeF.getSpan();

    double sum=0;
    for (int f=0;f<spanF.getSource();f++)
  {
    if (alignMap.containsKey(f)){
        for (Integer alignedIndex : alignMap.get(f)){
            if (alignedIndex >= spanE.getSource() && alignedIndex <= spanE.getTarget()){
                sum++;
            }
        }
    }
  }

  for (int f=spanF.getTarget()+1;f<nodeF.size();f++)
  {
    if (alignMap.containsKey(f)){
        for (Integer alignedIndex : alignMap.get(f)){
            if (alignedIndex >= spanE.getSource() && alignedIndex <= spanE.getTarget()){
                sum++;
            }
        }
    }
  } 

    return sum/10;
}
    
    private static double bias (Tree nodeF, Tree nodeE){
        return 1.0;
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

    private static HashMap<Tree, Tree> getHungarianAlignment(Tree eParseTree, Tree fParseTree, double[] weights, HashMap<Integer,ArrayList<Integer>> alignMap){
      // remember to ignore the top two weights because they are monolingual features
      int numFrenchNodes = fParseTree.size() - fParseTree.getLeaves().size();
      int numEnglishNodes = eParseTree.size() - eParseTree.getLeaves().size();

      double[][] costMatrix = new double[numFrenchNodes][numEnglishNodes];

      int i,j;
      i = 0;

      for (Tree fSubTree : fParseTree){
        if (!fSubTree.isLeaf()){
          j = 0;
          for (Tree eSubTree : eParseTree){
            if (!eSubTree.isLeaf()){
                //IF IT GETS TOO SLOW DON'T COMPUTE WORD ALIGNMENT FEATURES FOR LARGE SENTENCES
                costMatrix[i][j] = weights[2]*spanDiff(fSubTree, eSubTree) + weights[3]*numChildren(fSubTree, eSubTree) + weights[7]*bias(fSubTree, eSubTree);
                if (numFrenchNodes < 50 && numEnglishNodes < 50){
                    costMatrix[i][j] += weights[4]*insideBoth(fSubTree,eSubTree, alignMap) + weights[5]*insideSrcOutsideTgt(fSubTree,eSubTree, alignMap) + weights[6]*insideTgtOutsideSrc(fSubTree,eSubTree, alignMap);
                }
              costMatrix[i][j] = 0 - costMatrix[i][j];
              j++;
            }
          }
          i++;
        }
      }

      HungarianAlgorithm hungAlgSolver = new HungarianAlgorithm(costMatrix);
      int[] assignments = hungAlgSolver.execute();

      HashMap<Tree, Tree> alignment = new HashMap<>();

      i = 0;
      for (Tree fSubTree : fParseTree){
        if (!fSubTree.isLeaf()){
          j = 0;
          for (Tree eSubTree : eParseTree){
            if (!eSubTree.isLeaf()){
              if (j == assignments[i]){
                alignment.put(fSubTree, eSubTree);
              }
              j++;
            }
          }
          i++;
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
            parameters = new double[weights.length];
            deepArrayCopy(weights, parameters);
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
        
        public int getNumParameters() { return parameters.length; }
        public double getParameter(int i) { return parameters[i]; }
        public void getParameters(double[] buffer) {
            deepArrayCopy(parameters, buffer);
        }
        
        public void setParameter(int i, double r) {
            parameters[i] = r;
        }
        public void setParameters(double[] newParameters) {
            deepArrayCopy(newParameters, parameters);
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

    ///////////////////////////////
    //
    //  MISCELLANEOUS FUNCTIONS
    //
    ///////////////////////////////

    private static void deepArrayCopy(double[] src, double[] dest){
        for (int i = 0; i < src.length; i++){
            dest[i] = src[i];
        }
    }
    
}