
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
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
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.ling.Sentence;

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


public class LexicalizedParserUnsupervisedDA extends LexicalizedParser{
    private static final Redwood.RedwoodChannels log = Redwood.channels(LexicalizedParser.class);
    
    private Options op;
    @Override
    public Options getOp() { return op; }
    @Override
    public void setOptionFlags(String... flags) {
      op.setOptions(flags);
    }
    
    public LexicalizedParserUnsupervisedDA(Lexicon lex, BinaryGrammar bg, UnaryGrammar ug, DependencyGrammar dg, Index<String> stateIndex, Index<String> wordIndex, Index<String> tagIndex, Options op) {
        super(lex, bg, ug, dg, stateIndex, wordIndex, tagIndex, op);
        this.op = op;
    }
    
    public static void main(String[] args) {
    boolean seed = false;
    boolean saveToSerializedFile = false;
    boolean saveToTextFile = false;
    String serializedInputFileOrUrl = null;
    String textInputFileOrUrl = null;
    String serializedOutputFileOrUrl = null;
    String textOutputFileOrUrl = null;
    String treebankPath = null;
    Treebank selfTrainTreebank = null;
    MemoryTreebank finalTrainTreebank = null;
    Treebank tuneTreebank = null;
    String testPath = null;
    String inTestPath = null;
    String selfTrainPath = null;
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

    Options op = new Options();
    List<String> optionArgs = new ArrayList<>();
    String encoding = null;
    // while loop through option arguments
    while (argIndex < args.length && args[argIndex].charAt(0) == '-') {
        if (args[argIndex].equalsIgnoreCase("-inTest")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-inTest");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        inTestPath = treebankDescription.first();
      }
        else if (args[argIndex].equalsIgnoreCase("-test")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-test");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        testPath = treebankDescription.first();
      }
        else if (args[argIndex].equalsIgnoreCase("-seed")) {
        seed = true;
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-seed");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        treebankPath = treebankDescription.first();
        trainFilter = treebankDescription.second();
      } else if (args[argIndex].equalsIgnoreCase("-train2")) {
        // train = true;     // cdm july 2005: should require -train for this
        Triple<String, FileFilter, Double> treebankDescription = ArgUtils.getWeightedTreebankDescription(args, argIndex, "-train2");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        secondaryTreebankPath = treebankDescription.first();
        secondaryTrainFilter = treebankDescription.second();
        secondaryTreebankWeight = treebankDescription.third();
      } else if (args[argIndex].equalsIgnoreCase("-tLPP") && (argIndex + 1 < args.length)) {
        try {
          op.tlpParams = (TreebankLangParserParams) Class.forName(args[argIndex + 1]).newInstance();
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
        op.tlpParams.setInputEncoding(encoding);
        op.tlpParams.setOutputEncoding(encoding);
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-tokenized")) {
        tokenized = true;
        argIndex += 1;
      } else if (args[argIndex].equalsIgnoreCase("-escaper")) {
        try {
          escaper = ReflectionLoading.loadByReflection(args[argIndex + 1]);
        } catch (Exception e) {
          log.info("Couldn't instantiate escaper " + args[argIndex + 1] + ": " + e);
        }
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-tokenizerOptions")) {
        tokenizerOptions = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-tokenizerFactory")) {
        tokenizerFactoryClass = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-tokenizerMethod")) {
        tokenizerMethod = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-sentences")) {
        sentenceDelimiter = args[argIndex + 1];
        if (sentenceDelimiter.equalsIgnoreCase("newline")) {
          sentenceDelimiter = "\n";
        }
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-parseInside")) {
        elementDelimiter = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-tagSeparator")) {
        tagDelimiter = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-loadFromSerializedFile") ||
                 args[argIndex].equalsIgnoreCase("-model")) {
        // load the parser from a binary serialized file
        // the next argument must be the path to the parser file
        serializedInputFileOrUrl = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-loadFromTextFile")) {
        // load the parser from declarative text file
        // the next argument must be the path to the parser file
        textInputFileOrUrl = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-saveToSerializedFile")) {
        saveToSerializedFile = true;
        if (ArgUtils.numSubArgs(args, argIndex) < 1) {
          log.info("Missing path: -saveToSerialized filename");
        } else {
          serializedOutputFileOrUrl = args[argIndex + 1];
        }
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-saveToTextFile")) {
        // save the parser to declarative text file
        saveToTextFile = true;
        textOutputFileOrUrl = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-saveTrainTrees")) {
        // save the training trees to a binary file
        op.trainOptions.trainTreeFile = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-selfTrain")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-selfTrain");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        selfTrainPath = treebankDescription.first();
        testFilter = treebankDescription.second();
      } else if (args[argIndex].equalsIgnoreCase("-tune")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-tune");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        tunePath = treebankDescription.first();
        tuneFilter = treebankDescription.second();
      } else {
        int oldIndex = argIndex;
        argIndex = op.setOptionOrWarn(args, argIndex);
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
      tuneTreebank = op.tlpParams.testMemoryTreebank();
      tuneTreebank.loadPath(tunePath, tuneFilter);
    }

//    if (!train && op.testOptions.verbose) {
//      StringUtils.logInvocationString(log, args);
//    }
    LexicalizedParser lp; // always initialized in next if-then-else block
    if (seed) {
      //StringUtils.logInvocationString(log, args);

      // so we train a parser using the treebank
      GrammarCompactor compactor = null;
      if (op.trainOptions.compactGrammar() == 3) {
        compactor = new ExactGrammarCompactor(op, false, false);
      }

      Treebank trainTreebank = makeTreebank(treebankPath, op, trainFilter);
      finalTrainTreebank = new MemoryTreebank();
      finalTrainTreebank.addAll(trainTreebank);

      Treebank secondaryTrainTreebank = null;
      if (secondaryTreebankPath != null) {
        secondaryTrainTreebank = makeSecondaryTreebank(secondaryTreebankPath, op, secondaryTrainFilter);
      }

      List<List<TaggedWord>> extraTaggedWords = null;
      if (op.trainOptions.taggedFiles != null) {
        extraTaggedWords = new ArrayList<>();
        List<TaggedFileRecord> fileRecords = TaggedFileRecord.createRecords(new Properties(), op.trainOptions.taggedFiles);
        for (TaggedFileRecord record : fileRecords) {
          for (List<TaggedWord> sentence : record.reader()) {
            extraTaggedWords.add(sentence);
          }
        }
      }

      op.testOptions.quietEvaluation = true;
      lp = getParserFromTreebank(trainTreebank, secondaryTrainTreebank, secondaryTreebankWeight, compactor, op, tuneTreebank, extraTaggedWords);
    } else if (textInputFileOrUrl != null) {
      // so we load the parser from a text grammar file
      lp = getParserFromTextFile(textInputFileOrUrl, op);
    } else {
      // so we load a serialized parser - 
      if (serializedInputFileOrUrl == null && argIndex < args.length) {
        // the next argument must be the path to the serialized parser
        serializedInputFileOrUrl = args[argIndex];
        argIndex++;
      }
      if (serializedInputFileOrUrl == null) {
        log.info("No grammar specified, exiting...");
        return;
      }
      String[] extraArgs = new String[optionArgs.size()];
      extraArgs = optionArgs.toArray(extraArgs);
      try {
        lp = loadModel(serializedInputFileOrUrl, op, extraArgs);
        op.setOptions(extraArgs);//CHANGED
      } catch (IllegalArgumentException e) {
        log.info("Error loading parser, exiting...");
        throw e;
      }
    }

    // set up tokenizerFactory with options if provided
    if (tokenizerFactoryClass != null || tokenizerOptions != null) {
      try {
        if (tokenizerFactoryClass != null) {
          Class<TokenizerFactory<? extends HasWord>> clazz = ErasureUtils.uncheckedCast(Class.forName(tokenizerFactoryClass));
          Method factoryMethod;
          if (tokenizerOptions != null) {
            factoryMethod = clazz.getMethod(tokenizerMethod != null ? tokenizerMethod : "newWordTokenizerFactory", String.class);
            tokenizerFactory = ErasureUtils.uncheckedCast(factoryMethod.invoke(null, tokenizerOptions));
          } else {
            factoryMethod = clazz.getMethod(tokenizerMethod != null ? tokenizerMethod : "newTokenizerFactory");
            tokenizerFactory = ErasureUtils.uncheckedCast(factoryMethod.invoke(null));
          }
        } else {
          // have options but no tokenizer factory.  use the parser
          // langpack's factory and set its options
          tokenizerFactory = op.langpack().getTokenizerFactory();
          tokenizerFactory.setOptions(tokenizerOptions);
        }
      } catch (IllegalAccessException | InvocationTargetException | ClassNotFoundException | NoSuchMethodException e) {
        log.info("Couldn't instantiate TokenizerFactory " + tokenizerFactoryClass + " with options " + tokenizerOptions);
        throw new RuntimeException(e);
      }
    }


    // the following has to go after reading parser to make sure
    // op and tlpParams are the same for train and test
    // THIS IS BUTT UGLY BUT IT STOPS USER SPECIFIED ENCODING BEING
    // OVERWRITTEN BY ONE SPECIFIED IN SERIALIZED PARSER
    if (encoding != null) {
      op.tlpParams.setInputEncoding(encoding);
      op.tlpParams.setOutputEncoding(encoding);
    }

    if (testFilter != null || selfTrainPath != null) {
      if (selfTrainPath == null) {
        if (treebankPath == null) {
          throw new RuntimeException("No test treebank path specified...");
        } else {
          log.info("No test treebank path specified.  Using train path: \"" + treebankPath + '\"');
          selfTrainPath = treebankPath;
        }
      }
      selfTrainTreebank = op.tlpParams.testMemoryTreebank();
      selfTrainTreebank.loadPath(selfTrainPath, testFilter);
    }

    op.trainOptions.sisterSplitters = Generics.newHashSet(Arrays.asList(op.tlpParams.sisterSplitters()));

    // at this point we should be sure that op.tlpParams is
    // set appropriately (from command line, or from grammar file),
    // and will never change again.  -- Roger

    // Now what do we do with the parser we've made
    if (saveToTextFile) {
      // save the parser to textGrammar format
      if (textOutputFileOrUrl != null) {
        lp.saveParserToTextFile(textOutputFileOrUrl);
      } else {
        log.info("Usage: must specify a text grammar output path");
      }
    }
    if (saveToSerializedFile) {
      if (serializedOutputFileOrUrl != null) {
        lp.saveParserToSerialized(serializedOutputFileOrUrl);
      } else if (textOutputFileOrUrl == null && selfTrainTreebank == null) {
        // no saving/parsing request has been specified
        log.info("usage: " + "java edu.stanford.nlp.parser.lexparser.LexicalizedParser " + "-seed trainFilesPath [fileRange] -saveToSerializedFile serializedParserFilename");
      }
    }

    if (op.testOptions.verbose || seed) {
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
      log.info("ParserPack is " + op.tlpParams.getClass().getName());
      log.info("Lexicon is " + lp.lex.getClass().getName());
      if (op.testOptions.verbose) {
        log.info("Tags are: " + lp.tagIndex);
        // log.info("States are: " + lp.pd.stateIndex); // This is too verbose. It was already printed out by the below printOptions command if the flag -printStates is given (at training time)!
      }
      printOptions(false, op);
    }

    if (selfTrainTreebank != null) {
        Treebank selfTrainTest = makeTreebank(testPath, op, null);
        Treebank inTest = makeTreebank(inTestPath, op, null);
        EvaluateTreebank evaluator = new EvaluateTreebank(lp);
        double baseLineOutDomain = evaluator.testOnTreebank(selfTrainTest);
        double baseLineInDomain = evaluator.testOnTreebank(inTest);
        // annotate unlabeled data
        System.out.println("Starting selftraining...");
        int i = 0;
        for (Tree goldTree : selfTrainTreebank) {
            List<? extends HasWord> sentence = Sentence.toCoreLabelList(goldTree.yieldWords());;
            finalTrainTreebank.add(lp.parseTree(sentence));
            System.out.println("Self-training : " + (++i));
        }
        System.out.println("Finished creating the final dataset");
        GrammarCompactor compactor = null;
        if (op.trainOptions.compactGrammar() == 3) {
            compactor = new ExactGrammarCompactor(op, false, false);
        }
        op.testOptions.quietEvaluation = true;
        lp = getParserFromTreebank(finalTrainTreebank, null, 1.0, compactor, op, tuneTreebank, null);
        
      evaluator = new EvaluateTreebank(lp);
      double finalF1 = evaluator.testOnTreebank(selfTrainTest);
      
      System.out.println("------------------------");
      System.out.println("The results that matter:");
      System.out.println("------------------------");
      System.out.println("Baseline In Domain F1 : " + baseLineInDomain);
      System.out.println("Baseline Out Domain F1 : " + baseLineOutDomain);
      System.out.println("Self-Trained Out Domain F1 : " + finalF1);
      
    } else if (argIndex >= args.length) {
      // no more arguments, so we just parse our own test sentence
      PrintWriter pwOut = op.tlpParams.pw();
      PrintWriter pwErr = op.tlpParams.pw(System.err);
      ParserQuery pq = lp.parserQuery();
      if (pq.parse(op.tlpParams.defaultTestSentence())) {
        lp.getTreePrint().printTree(pq.getBestParse(), pwOut);
      } else {
        pwErr.println("Error. Can't parse test sentence: " +
                      op.tlpParams.defaultTestSentence());
      }
    } else {
      // We parse filenames given by the remaining arguments
      ParseFiles.parseFiles(args, argIndex, tokenized, tokenizerFactory, elementDelimiter, sentenceDelimiter, escaper, tagDelimiter, op, lp.getTreePrint(), lp);
    }

  } // end main
    
    private static Treebank makeTreebank(String treebankPath, Options op, FileFilter filt) {
    log.info("Making a Treebank from treebank dir: " + treebankPath);
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
