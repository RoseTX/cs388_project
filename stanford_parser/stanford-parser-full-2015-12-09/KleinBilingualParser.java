
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
    boolean train = false;
    boolean saveToSerializedFile = false;
    boolean saveToTextFile = false;
    String serializedInputFileOrUrl = null;
    String textInputFileOrUrl = null;
    String serializedOutputFileOrUrl = null;
    String textOutputFileOrUrl = null;
    String treebankPath = null;
    Treebank testTreebank = null;
    Treebank tuneTreebank = null;
    String testPath = null;
    FileFilter testFilter = null;
    String tunePath = null;
    FileFilter tuneFilter = null;
    FileFilter trainFilter = null;
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
    String encoding = null;
    // while loop through option arguments
    while (argIndex < args.length && args[argIndex].charAt(0) == '-') {
      if (args[argIndex].equalsIgnoreCase("-train") ||
          args[argIndex].equalsIgnoreCase("-trainTreebank")) {
        train = true;
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-train");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        treebankPath = treebankDescription.first();
        trainFilter = treebankDescription.second();
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
        encoding = args[argIndex + 1];
        fOp.tlpParams.setInputEncoding(encoding);
        fOp.tlpParams.setOutputEncoding(encoding);
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-treebank") ||
                 args[argIndex].equalsIgnoreCase("-testTreebank") ||
                 args[argIndex].equalsIgnoreCase("-test")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-test");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        testPath = treebankDescription.first();
        testFilter = treebankDescription.second();
      } else {
        int oldIndex = argIndex;
        argIndex = fOp.setOptionOrWarn(args, argIndex);
        optionArgs.addAll(Arrays.asList(args).subList(oldIndex, argIndex));
      }
    } // end while loop through arguments

    // all other arguments are order dependent and
    // are processed in order below

    if (tuneFilter != null || tunePath != null) {
      if (tunePath == null) {
        if (treebankPath == null) {
          throw new RuntimeException("No tune treebank path specified...");
        } else {
          log.info("No tune treebank path specified.  Using train path: \"" + treebankPath + '\"');
          tunePath = treebankPath;
        }
      }
      tuneTreebank = fOp.tlpParams.testMemoryTreebank();
      tuneTreebank.loadPath(tunePath, tuneFilter);
    }

//    if (!train && fOp.testOptions.verbose) {
//      StringUtils.logInvocationString(log, args);
//    }
    LexicalizedParser lp; // always initialized in next if-then-else block
    //TRAIN A PARSER
      // so we train a parser using the treebank
      GrammarCompactor compactor = null;
      if (fOp.trainOptions.compactGrammar() == 3) {
        compactor = new ExactGrammarCompactor(fOp, false, false);
      }

      Treebank trainTreebank = makeTreebank(treebankPath, fOp, trainFilter);

      lp = getParserFromTreebank(trainTreebank, null, secondaryTreebankWeight, compactor, fOp, tuneTreebank, null);


    // the following has to go after reading parser to make sure
    // op and tlpParams are the same for train and test
    // THIS IS BUTT UGLY BUT IT STOPS USER SPECIFIED ENCODING BEING
    // OVERWRITTEN BY ONE SPECIFIED IN SERIALIZED PARSER
    if (encoding != null) {
      fOp.tlpParams.setInputEncoding(encoding);
      fOp.tlpParams.setOutputEncoding(encoding);
    }

    if (testFilter != null || testPath != null) {
      if (testPath == null) {
        if (treebankPath == null) {
          throw new RuntimeException("No test treebank path specified...");
        } else {
          log.info("No test treebank path specified.  Using train path: \"" + treebankPath + '\"');
          testPath = treebankPath;
        }
      }
      testTreebank = fOp.tlpParams.testMemoryTreebank();
      testTreebank.loadPath(testPath, testFilter);
    }

    fOp.trainOptions.sisterSplitters = Generics.newHashSet(Arrays.asList(fOp.tlpParams.sisterSplitters()));

    // at this point we should be sure that fOp.tlpParams is
    // set appropriately (from command line, or from grammar file),
    // and will never change again.  -- Roger

    if (fOp.testOptions.verbose || train) {
      // Tell the user a little or a lot about what we have made
      // get lexicon size separately as it may have its own prints in it....
      String lexNumRules = lp.lex != null ? Integer.toString(lp.lex.numRules()): "";
      log.info("Grammar\tStates\tTags\tWords\tUnaryR\tBinaryR\tTaggings");
      log.info("Grammar\t" +
          lp.stateIndex.size() + '\t' +
          lp.tagIndex.size() + '\t' +
          lp.wordIndex.size() + '\t' +
          (lp.ug != null ? lp.ug.numRules(): "") + '\t' +
          (lp.bg != null ? lp.bg.numRules(): "") + '\t' +
          lexNumRules);
      log.info("ParserPack is " + fOp.tlpParams.getClass().getName());
      log.info("Lexicon is " + lp.lex.getClass().getName());
      if (fOp.testOptions.verbose) {
        log.info("Tags are: " + lp.tagIndex);
        // log.info("States are: " + lp.pd.stateIndex); // This is too verbose. It was already printed out by the below printOptions command if the flag -printStates is given (at training time)!
      }
      printOptions(false, fOp);
    }

    if (testTreebank != null) {
      // test parser on treebank
      EvaluateTreebank evaluator = new EvaluateTreebank(lp);
      evaluator.testOnTreebank(testTreebank);
    } else if (argIndex >= args.length) {
      // no more arguments, so we just parse our own test sentence
      PrintWriter pwOut = fOp.tlpParams.pw();
      PrintWriter pwErr = fOp.tlpParams.pw(System.err);
      ParserQuery pq = lp.parserQuery();
      if (pq.parse(fOp.tlpParams.defaultTestSentence())) {
        lp.getTreePrint().printTree(pq.getBestParse(), pwOut);
      } else {
        pwErr.println("Error. Can't parse test sentence: " +
                      fOp.tlpParams.defaultTestSentence());
      }
    } else {
      // We parse filenames given by the remaining arguments
      ParseFiles.parseFiles(args, argIndex, tokenized, tokenizerFactory, elementDelimiter, sentenceDelimiter, escaper, tagDelimiter, fOp, lp.getTreePrint(), lp);
    }

  } // end main
    
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