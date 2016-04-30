
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

///////////////////////////////////

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.NumberRangeFileFilter;
import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.tagger.io.TaggedFileRecord;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.Triple;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import edu.stanford.nlp.parser.lexparser.MLEDependencyGrammarExtractor;
import edu.stanford.nlp.parser.lexparser.BinaryGrammarExtractor;
import edu.stanford.nlp.parser.lexparser.LinearGrammarSmoother;
import edu.stanford.nlp.parser.lexparser.TreeAnnotatorAndBinarizer;
import edu.stanford.nlp.parser.lexparser.TreeAnnotator;
import edu.stanford.nlp.parser.lexparser.ParentAnnotationStats;

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

    Options op = new Options();
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
      } else if (args[argIndex].equalsIgnoreCase("-treebank") ||
                 args[argIndex].equalsIgnoreCase("-testTreebank") ||
                 args[argIndex].equalsIgnoreCase("-test")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-test");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        testPath = treebankDescription.first();
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
    if (train) {
      //StringUtils.logInvocationString(log, args);

      // so we train a parser using the treebank
      GrammarCompactor compactor = null;
      if (op.trainOptions.compactGrammar() == 3) {
        compactor = new ExactGrammarCompactor(op, false, false);
      }

      Treebank trainTreebank = makeTreebank(treebankPath, op, trainFilter);

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

    if (testFilter != null || testPath != null) {
      if (testPath == null) {
        if (treebankPath == null) {
          throw new RuntimeException("No test treebank path specified...");
        } else {
          log.info("No test treebank path specified.  Using train path: \"" + treebankPath + '\"');
          testPath = treebankPath;
        }
      }
      testTreebank = op.tlpParams.testMemoryTreebank();
      testTreebank.loadPath(testPath, testFilter);
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
      } else if (textOutputFileOrUrl == null && testTreebank == null) {
        // no saving/parsing request has been specified
        log.info("usage: " + "java edu.stanford.nlp.parser.lexparser.LexicalizedParser " + "-train trainFilesPath [fileRange] -saveToSerializedFile serializedParserFilename");
      }
    }

    if (op.testOptions.verbose || train) {
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

    if (testTreebank != null) {
      // test parser on treebank
      EvaluateTreebank evaluator = new EvaluateTreebank(lp);
      evaluator.testOnTreebank(testTreebank);
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

  public static LexicalizedParser getParserFromTreebank(Treebank trainTreebank,
                        Treebank secondaryTrainTreebank,
                        double weight,
                        GrammarCompactor compactor,
                        Options op,
                        Treebank tuneTreebank,
                        List<List<TaggedWord>> extraTaggedWords)
  {
    System.err.println("Currently " + new Date());
    printOptions(true, op);
    Timing.startTime();

    Triple<Treebank, Treebank, Treebank> treebanks = getAnnotatedBinaryTreebankFromTreebank(trainTreebank, secondaryTrainTreebank, tuneTreebank, op);

    Treebank trainTreebankRaw = trainTreebank;
    trainTreebank = treebanks.first();
    secondaryTrainTreebank = treebanks.second();
    tuneTreebank = treebanks.third();

    // +1 to account for the boundary symbol
    trainTreebank = new FilteringTreebank(trainTreebank, new LengthTreeFilter(op.trainOptions.trainLengthLimit + 1));
    if (secondaryTrainTreebank != null) {
      secondaryTrainTreebank = new FilteringTreebank(secondaryTrainTreebank, new LengthTreeFilter(op.trainOptions.trainLengthLimit + 1));
    }
    if (tuneTreebank != null) {
      tuneTreebank = new FilteringTreebank(tuneTreebank, new LengthTreeFilter(op.trainOptions.trainLengthLimit + 1));
    }

    Index<String> stateIndex;
    Index<String> wordIndex;
    Index<String> tagIndex;

    Pair<UnaryGrammar, BinaryGrammar> bgug;
    Lexicon lex;

    stateIndex = new HashIndex<String>();
    wordIndex = new HashIndex<String>();
    tagIndex = new HashIndex<String>();
    
    // extract grammars
    BinaryGrammarExtractor bgExtractor = new BinaryGrammarExtractor(op, stateIndex);
    // Extractor lexExtractor = new LexiconExtractor();
    //TreeExtractor uwmExtractor = new UnknownWordModelExtractor(trainTreebank.size());
    System.err.print("Extracting PCFG...");
    if (secondaryTrainTreebank == null) {
      bgug = bgExtractor.extract(trainTreebank);
    } else {
      bgug = bgExtractor.extract(trainTreebank, 1.0, 
                                 secondaryTrainTreebank, weight);
    }
    Timing.tick("done.");
    
    System.err.print("Extracting Lexicon...");
    lex = op.tlpParams.lex(op, wordIndex, tagIndex);
    
    double trainSize = trainTreebank.size();
    if (secondaryTrainTreebank != null) {
      trainSize += (secondaryTrainTreebank.size() * weight);
    }
    if (extraTaggedWords != null) {
      trainSize += extraTaggedWords.size();
    }
    
    lex.initializeTraining(trainSize);
    // wsg2012: The raw treebank has CoreLabels, which we need for FactoredLexicon
    // training. If TreeAnnotator is updated so that it produces CoreLabels, then we can
    // remove the trainTreebankRaw.
    lex.train(trainTreebank, trainTreebankRaw);
    if (secondaryTrainTreebank != null) {
      lex.train(secondaryTrainTreebank, weight);
    }
    if (extraTaggedWords != null) {
      for (List<TaggedWord> sentence : extraTaggedWords) {
        // TODO: specify a weight?
        lex.trainUnannotated(sentence, 1.0);
      }
    }
    lex.finishTraining();
    Timing.tick("done.");
      
    //TODO: wsg2011 Not sure if this should come before or after
    //grammar compaction
    if (op.trainOptions.ruleSmoothing) {
      System.err.print("Smoothing PCFG...");
      Function<Pair<UnaryGrammar,BinaryGrammar>,Pair<UnaryGrammar,BinaryGrammar>> smoother = new LinearGrammarSmoother(op.trainOptions, stateIndex, tagIndex);
      bgug = smoother.apply(bgug);
      Timing.tick("done.");
    }

    if (compactor != null) {
      System.err.print("Compacting grammar...");
      Triple<Index<String>, UnaryGrammar, BinaryGrammar> compacted = compactor.compactGrammar(bgug, stateIndex);
      stateIndex = compacted.first();
      bgug.setFirst(compacted.second());
      bgug.setSecond(compacted.third());
      Timing.tick("done.");
    }

    System.err.print("Compiling grammar...");
    BinaryGrammar bg = bgug.second;
    bg.splitRules();
    UnaryGrammar ug = bgug.first;
    ug.purgeRules();
    Timing.tick("done");

    DependencyGrammar dg = null;
    if (op.doDep) {
    }

    System.err.println("Done training parser.");
    if (op.trainOptions.trainTreeFile!=null) {
      try {
        System.err.print("Writing out binary trees to "+ op.trainOptions.trainTreeFile+"...");
        IOUtils.writeObjectToFile(trainTreebank, op.trainOptions.trainTreeFile);
        IOUtils.writeObjectToFile(secondaryTrainTreebank, op.trainOptions.trainTreeFile);
        Timing.tick("done.");
      } catch (Exception e) {
        System.err.println("Problem writing out binary trees.");
      }
    }
    return new LexicalizedParser(lex, bg, ug, dg, stateIndex, wordIndex, tagIndex, op);
  }

  public static Triple<Treebank, Treebank, Treebank> getAnnotatedBinaryTreebankFromTreebank(Treebank trainTreebank,
      Treebank secondaryTreebank,
      Treebank tuneTreebank,
      Options op) {
    // setup tree transforms
    TreebankLangParserParams tlpParams = op.tlpParams;
    TreebankLanguagePack tlp = tlpParams.treebankLanguagePack();

    if (op.testOptions.verbose) {
      PrintWriter pwErr = tlpParams.pw(System.err);
      pwErr.print("Training ");
      pwErr.println(trainTreebank.textualSummary(tlp));
      if (secondaryTreebank != null) {
        pwErr.print("Secondary training ");
        pwErr.println(secondaryTreebank.textualSummary(tlp));
      }
    }

    log.info("Binarizing trees...");

    TreeAnnotatorAndBinarizer binarizer = buildTrainBinarizer(op);
    CompositeTreeTransformer trainTransformer = buildTrainTransformer(op, binarizer);

    Treebank wholeTreebank;
    if (secondaryTreebank == null) {
      wholeTreebank = trainTreebank;
    } else {
      wholeTreebank = new CompositeTreebank(trainTreebank, secondaryTreebank);
    }

    if (op.trainOptions.selectiveSplit) {
      op.trainOptions.splitters = ParentAnnotationStats.getSplitCategories(wholeTreebank, op.trainOptions.tagSelectiveSplit, 0, op.trainOptions.selectiveSplitCutOff, op.trainOptions.tagSelectiveSplitCutOff, tlp);
      removeDeleteSplittersFromSplitters(tlp, op);
      if (op.testOptions.verbose) {
        List<String> list = new ArrayList<>(op.trainOptions.splitters);
        Collections.sort(list);
        log.info("Parent split categories: " + list);
      }
    }

    if (op.trainOptions.selectivePostSplit) {
      // Do all the transformations once just to learn selective splits on annotated categories
      TreeTransformer myTransformer = new TreeAnnotator(tlpParams.headFinder(), tlpParams, op);
      wholeTreebank = wholeTreebank.transform(myTransformer);
      op.trainOptions.postSplitters = ParentAnnotationStats.getSplitCategories(wholeTreebank, true, 0, op.trainOptions.selectivePostSplitCutOff, op.trainOptions.tagSelectivePostSplitCutOff, tlp);
      if (op.testOptions.verbose) {
        log.info("Parent post annotation split categories: " + op.trainOptions.postSplitters);
      }
    }
    if (op.trainOptions.hSelSplit) {
      // We run through all the trees once just to gather counts for hSelSplit!
      int ptt = op.trainOptions.printTreeTransformations;
      op.trainOptions.printTreeTransformations = 0;
      binarizer.setDoSelectiveSplit(false);
      for (Tree tree : wholeTreebank) {
        log.info(tree.toString());
        trainTransformer.transformTree(tree);
      }
      binarizer.setDoSelectiveSplit(true);
      op.trainOptions.printTreeTransformations = ptt;
    }
    // we've done all the setup now. here's where the train treebank is transformed.
    trainTreebank = trainTreebank.transform(trainTransformer);
    if (secondaryTreebank != null) {
      secondaryTreebank = secondaryTreebank.transform(trainTransformer);
    }
    if (op.trainOptions.printAnnotatedStateCounts) {
      binarizer.printStateCounts();
    }
    if (op.trainOptions.printAnnotatedRuleCounts) {
      binarizer.printRuleCounts();
    }

    if (tuneTreebank != null) {
      tuneTreebank = tuneTreebank.transform(trainTransformer);
    }

    Timing.tick("done.");
    if (op.testOptions.verbose) {
      binarizer.dumpStats();
    }

    return new Triple<>(trainTreebank, secondaryTreebank, tuneTreebank);
  }

   private static void removeDeleteSplittersFromSplitters(TreebankLanguagePack tlp, Options op) {
    if (op.trainOptions.deleteSplitters != null) {
      List<String> deleted = new ArrayList<>();
      for (String del : op.trainOptions.deleteSplitters) {
        String baseDel = tlp.basicCategory(del);
        boolean checkBasic = del.equals(baseDel);
        for (Iterator<String> it = op.trainOptions.splitters.iterator(); it.hasNext(); ) {
          String elem = it.next();
          String baseElem = tlp.basicCategory(elem);
          boolean delStr = checkBasic && baseElem.equals(baseDel) || elem.equals(del);
          if (delStr) {
            it.remove();
            deleted.add(elem);
          }
        }
      }
      if (op.testOptions.verbose) {
        log.info("Removed from vertical splitters: " + deleted);
      }
    }
  }

}