
import edu.stanford.nlp.parser.lexparser.BinaryGrammar;
import edu.stanford.nlp.parser.lexparser.DependencyGrammar;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.UnaryGrammar;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.ling.HasWord;
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

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.trees.Tree;

import java.io.*;
import java.util.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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

    //PARALLEL ALIGNMENT FEATURE CALCULATION

int i = 0;
    Iterator<Tree> eTrees = testTreebankE.iterator();
    Iterator<Tree> fTrees = testTreebankF.iterator();

    while(eTrees.hasNext() && fTrees.hasNext()){
      Tree fTree = fTrees.next();
      Tree eTree = eTrees.next();

      List<? extends HasWord> sentenceF = Sentence.toCoreLabelList(fTree.yieldWords());
      List<? extends HasWord> sentenceE = Sentence.toCoreLabelList(eTree.yieldWords());

      Tree fTreeParsed = lpF.parseTree(sentenceF);
      Tree eTreeParsed = lpE.parseTree(sentenceE);

      //BEGIN CALCULATION OF FEATURES

      HashMap<Tree, Tree> alignment = getSampleNodeAlignment(eTreeParsed, fTreeParsed);
      System.out.println(alignment.size());
    }

  } // end main
    
/////////////////////////
//
//  FEATURES
//
/////////////////////////



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