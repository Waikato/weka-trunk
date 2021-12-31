/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 *    MLRClassifierImpl.java
 *    Copyright (C) 2012-2018 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.classifiers.mlr.impl;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.NoTypePermission;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPGenericVector;
import org.rosuda.REngine.REXPString;
import org.rosuda.REngine.REXPVector;
import org.rosuda.REngine.RList;
import weka.classifiers.mlr.MLRClassifier;
import weka.classifiers.rules.ZeroR;
import weka.core.Attribute;
import weka.core.AttributeStats;
import weka.core.BatchPredictor;
import weka.core.Capabilities;
import weka.core.Capabilities.Capability;
import weka.core.CapabilitiesHandler;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.RLoggerAPI;
import weka.core.RSession;
import weka.core.RUtils;
import weka.core.RevisionHandler;
import weka.core.RevisionUtils;
import weka.core.SelectedTag;
import weka.core.SparseInstance;
import weka.core.Tag;
import weka.core.Utils;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.RemoveUseless;
import weka.filters.unsupervised.attribute.ReplaceMissingValues;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation class for a wrapper classifier for the MLR R library:<br>
 * <br>
 * 
 * https://cran.r-project.org/web/packages/mlr/
 * 
 * <p>
 * The class will attempt to install and load the MLR library if it is not
 * already present in the user's R environment. Similarly, it will attempt to
 * install and load specific base learners as required.
 * <p>
 * 
 * The classifier supports serialization by serializing to binary inside of R,
 * retrieving the serialized classifier via the JRI native interface to R and
 * then finally serializing the JRI REXP object to XML using XStream. The last
 * step is required because JRI REXP objects do not implement Serializable.
 * 
 * 
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @author Eibe Frank
 * @version $Revision$
 */
public class MLRClassifierImpl implements BatchPredictor, OptionHandler,
  CapabilitiesHandler, RevisionHandler, Serializable {

  /** For serialization */
  private static final long serialVersionUID = 3554353477500239283L;

  // availability and initialization
  protected transient boolean m_mlrAvailable = false;
  protected transient boolean m_initialized = false;
  protected transient boolean m_baseLearnerLibraryAvailable = false;

  /** Don't use Weka's replace missing values filter? */
  protected boolean m_dontReplaceMissingValues;

  /** Remove useless attributes */
  protected RemoveUseless m_removeUseless;

  /** Replace missing values */
  protected ReplaceMissingValues m_missingFilter;

  /** The R learner to use */
  protected int m_rLearner = MLRClassifier.R_CLASSIF_RPART;

  /** Hyperparameters (comma separated) for the learner */
  protected String m_schemeOptions = "";

  /**
   * Batch prediction size (default: 100 instances)
   */
  protected String m_batchPredictSize = "100";

  /** Whether to output info/warning messages from R to the console */
  protected boolean m_logMessagesFromR = false;

  /** The random number seed. */
  protected int m_Seed = 1;

  /** Holds the textual representation of the R model */
  protected StringBuffer m_modelText;

  /**
   * Holds the serialized R model (R binary serialization followed by XStream
   * because Rserve/JRI REXP objects do not implement Serializable)
   */
  protected StringBuffer m_serializedModel;

  /** Used to make this model unique in the R environment */
  protected String m_modelHash;

  /** Header to use when processing the test data */
  protected Instances m_testHeader;

  /**
   * Holds the original training header (after removing useless attributes). We
   * use this if the training data had zero-frequency nominal labels deleted in
   * order to set values to missing in test instances.
   */
  protected Instances m_originalTrainingHeader;

  /** Whether the selected scheme can produce probabilities */
  protected boolean m_schemeProducesProbs;

  /** Debugging output */
  protected boolean m_Debug;

  /** Simple console logger to capture info/warning messages from R */
  protected transient RLoggerAPI m_logger;

  /**
   * Fall back to Zero R if there are no instances with non-missing class or
   * only the class is present in the data
   */
  protected ZeroR m_zeroR;

  protected transient List<String> m_errorsFromR;

  /**
   * The cleaned attribute names and values from R.
   */
  String[] m_cleanedAttNames = null;
  String[][] m_cleanedAttValues = null;

  /**
   * Global info for this wrapper classifier.
   * 
   * @return the global info suitable for displaying in the GUI.
   */
  public String globalInfo() {
    return "Classifier that wraps the MLR R library for building "
      + "and making predictions with various R classifiers and regression methods. Check\n\n"
      + "  https://mlr-org.github.io/mlr/articles/tutorial/integrated_learners.html\n\n"
      + "for the list of algorithms in MLR, and\n\n"
      + "  https://cran.r-project.org/web/packages/mlr\n\n"
      + "for further information on the package and its algorithms.\n\n"
      + "Note that a classifier/regressor will be installed in R when it is selected as the "
      + "learner parameter for MLRClassifier and has not been installed before in R. This may take a little while"
      + "because it involves downloading and processing R packages.";
  }

  protected void enableCapabilitiesForLearner(Capabilities result,
    String mlrIdentifier) {

    RSession eng = null;
    try {
      eng = RSession.acquireSession(this);
      if (eng == null) {
        return;
      }
      eng.setLog(this, m_logger);
      eng.clearConsoleBuffer(this);

      if (m_modelHash == null) {
        m_modelHash = "" + hashCode();
      }

      String learnString = "";
      learnString = "l" + m_modelHash + " <- makeLearner(\"" + mlrIdentifier + "\")";

      // Make learner in R
      eng.parseAndEval(this, learnString);

      // Get list of properties
      List<String> props =
        Arrays.asList(eng.parseAndEval(this, "l" + m_modelHash + "$properties").asStrings());

      eng.parseAndEval(this, "remove(l" + m_modelHash + ")");

      if (props.contains("numerics")) {
        result.enable(Capability.NUMERIC_ATTRIBUTES);
      }
      if (props.contains("factors")) {
        result.enable(Capability.NOMINAL_ATTRIBUTES);
      }
      if ((!m_dontReplaceMissingValues) || props.contains("missings")) {
        result.enable(Capability.MISSING_VALUES);
      }
      if (props.contains("twoclass")) {
        result.enable(Capability.BINARY_CLASS);
      }
      if (props.contains("multiclass")) {
        result.enable(Capability.NOMINAL_CLASS);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    } finally {
      if (eng != null) {
        RSession.releaseSession(this);
      }
    }
  }

  /**
   * Returns default capabilities of the classifier.
   * 
   * @return the capabilities of this classifier
   */
  @Override
  public Capabilities getCapabilities() {
    Capabilities result = new Capabilities(this);
    result.disableAll();

    m_baseLearnerLibraryAvailable = false;
    try {
      if (!m_initialized) {
        init();
      }

      if (!m_mlrAvailable) {
        throw new Exception(
                "MLR is not available for some reason - can't continue setting capabilities!");
      }

      loadBaseLearnerLibrary();
    } catch (Exception e) {
      return result;
    }

    String mlrIdentifier = MLRClassifier.TAGS_LEARNER[m_rLearner].getReadable();

    if (!m_baseLearnerLibraryAvailable) {
      System.err
        .println("Unable to load base learner library for " + mlrIdentifier);
    }

    enableCapabilitiesForLearner(result, mlrIdentifier);

    if (mlrIdentifier.startsWith("classif")) {
    } else {
      result.enable(Capability.NUMERIC_CLASS);
    }

    // class
    result.enable(Capability.MISSING_CLASS_VALUES);

    // instances
    result.setMinimumNumberInstances(0);

    // set dependencies
    for (Capability cap : Capability.values())
      result.enableDependency(cap);

    return result;
  }

  /**
   * Unloads and then reloads MLR
   */
  protected void reloadMLR() {
    if (m_mlrAvailable) {
      RSession eng = null;
      try {
        eng = RSession.acquireSession(this);
        if (eng == null) {
          return;
        }
        eng.setLog(this, m_logger);
        eng.clearConsoleBuffer(this);

        String unload = "detach(\"package:mlr\", unload=TRUE)";
        eng.parseAndEval(this, unload);

        eng.loadLibrary(this, "mlr");
        eng.parseAndEval(this, "configureMlr(on.par.without.desc = \"warn\")");
      } catch (Exception ex) {
        ex.printStackTrace();
      } finally {
        if (eng != null) {
          eng.releaseSession(this);
        }
      }
    }
  }

  /**
   * Attempt to initialize the R environment. Sees if R is available, followed
   * by the MLR package. If MLR is not available it attempts to install and load
   * it for the user. It does the same for the XML package.
   * 
   * @throws Exception if a problem occurs
   */
  protected void init() throws Exception {

    if (!m_initialized) {
      m_logger = new RLoggerAPI() {

        @Override
        public void logMessage(String message) {
          if (m_logMessagesFromR) {
            System.err.println(message);
          } else if (m_errorsFromR != null) {
            m_errorsFromR.add(message);
          }
        }

        @Override
        public void statusMessage(String message) {
          // not needed.
        }
      };
    }

    m_initialized = true;
    RSession eng = null;

    eng = RSession.acquireSession(this);
    if (eng == null) {
      System.err.println("Could not acquire R session in MLR classifier.");
      return;
    }
    eng.setLog(this, m_logger);

    if (!eng.loadLibrary(this, "mlr")) {
      if (!eng.installLibrary(this, "mlr")) {
        RSession.releaseSession(this);
        return;
      }
    }
    eng.parseAndEval(this, "configureMlr(on.par.without.desc = \"warn\")");
    m_mlrAvailable = true;
    RSession.releaseSession(this);
  }

  /**
   * Attempts to load a base learner package in R. If this fails then it will
   * try to install the required package and then load again.
   * 
   * @throws Exception if a problem occurs
   */
  protected void loadBaseLearnerLibrary() throws Exception {
    String libString = MLRClassifier.TAGS_LEARNER[m_rLearner].getIDStr();
    if (!libString
      .equals("" + MLRClassifier.TAGS_LEARNER[m_rLearner].getID())) {
      if (libString.indexOf('.') > 0) {
        libString =
          libString.substring(libString.indexOf('.') + 1, libString.length());
      }

      String[] libs = libString.split(",");

      RSession eng = null;
      eng = RSession.acquireSession(this);
      if (eng == null) {
        return;
      }
      eng.setLog(this, m_logger);
      for (String lib : libs) {
        if (!eng.loadLibrary(this, lib)) {
          if (!eng.installLibrary(this, lib)) {
            RSession.releaseSession(this);
            return;
          }
        }
      }
    }

    m_baseLearnerLibraryAvailable = true;
    RSession.releaseSession(this);
  }

  /**
   * Returns an enumeration describing the available options.
   * 
   * @return an enumeration of all the available options.
   */
  @Override
  public Enumeration<Option> listOptions() {
    Vector<Option> newVector = new Vector<Option>();

    StringBuffer learners = new StringBuffer();
    boolean first = true;
    for (Tag t : MLRClassifier.TAGS_LEARNER) {
      String l = t.getReadable();
      learners.append((first ? "" : " ") + l);
      if (first) {
        first = false;
      }
    }

    newVector.addElement(new Option(
      "\tR learner to use (" + learners.toString() + ")\n\t(default = rpart)",
      "learner", 1, "-learner"));
    newVector.addElement(new Option(
      "\tLearner hyperparameters (comma separated)", "params", 1, "-params"));
    newVector
      .addElement(new Option("\tDon't replace missing values", "M", 0, "-M"));
    newVector.addElement(
      new Option("\tBatch size for batch prediction" + "\n\t(default = 100)",
        "batch", 1, "-batch"));
    newVector.addElement(new Option("\tLog messages from R", "L", 0, "-L"));
    newVector.addElement(new Option(
      "\tIf set, classifier is run in debug mode and\n"
        + "\tmay output additional info to the console",
      "output-debug-info", 0, "-output-debug-info"));
    newVector.addElement(new Option("\tRandom number seed.\n" + "\t(default 1)",
      "S", 1, "-S <num>"));

    return newVector.elements();
  }

  /**
   * Parses a given list of options.
   * <p/>
   * 
   * <!-- options-start --> <!-- options-end -->
   */
  @Override
  public void setOptions(String[] options) throws Exception {
    String learnerS = Utils.getOption("learner", options);
    if (learnerS.length() > 0) {
      boolean found = false;
      for (Tag t : MLRClassifier.TAGS_LEARNER) {
        if (t.getReadable().startsWith(learnerS)) {
          setRLearner(new SelectedTag(t.getID(), MLRClassifier.TAGS_LEARNER));
          found = true;
          break;
        }
      }
      if (!found) {
        throw new IllegalArgumentException(
          "MLR learner " + learnerS + " not found.");
      }
    }

    String paramsS = Utils.getOption("params", options);
    if (paramsS.length() > 0) {
      setLearnerParams(paramsS);
    }

    String batchSize = Utils.getOption("batch", options);
    if (batchSize.length() > 0) {
      setBatchSize(batchSize);
    }

    setDontReplaceMissingValues(Utils.getFlag('M', options));

    setDebug(Utils.getFlag("output-debug-info", options));

    setLogMessagesFromR(Utils.getFlag('L', options));

    String seed = Utils.getOption('S', options);
    if (seed.length() != 0) {
      setSeed(Integer.parseInt(seed));
    } else {
      setSeed(1);
    }

    Utils.checkForRemainingOptions(options);
  }

  /**
   * Gets the current settings of MLRClassifier.
   * 
   * @return an array of strings suitable for passing to setOptions()
   */
  @Override
  public String[] getOptions() {
    ArrayList<String> options = new ArrayList<String>();

    options.add("-learner");
    options.add(MLRClassifier.TAGS_LEARNER[m_rLearner].getReadable());
    if (m_schemeOptions != null && m_schemeOptions.length() > 0) {
      options.add("-params");
      options.add(m_schemeOptions);
    }

    if (getDontReplaceMissingValues()) {
      options.add("-M");
    }

    options.add("-batch");
    options.add(getBatchSize());

    if (getLogMessagesFromR()) {
      options.add("-L");
    }

    if (getDebug()) {
      options.add("-output-debug-info");
    }

    options.add("-S");
    options.add("" + getSeed());

    return options.toArray(new String[1]);
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String debugTipText() {
    return "Output debugging information";
  }

  /**
   * Set whether to output debugging info
   * 
   * @param d true if debugging info is to be output
   */
  public void setDebug(boolean d) {
    m_Debug = d;
  }

  /**
   * Get whether to output debugging info
   * 
   * @return true if debugging info is to be output
   */
  public boolean getDebug() {
    return m_Debug;
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String batchSizeTipText() {
    return "The preferred number of instances to push over to R at a time for "
      + "prediction (if operating in batch prediction mode). "
      + "More or fewer instances than this will be accepted.";
  }

  /**
   * Get the batch size for prediction (i.e. how many instances to push over
   * into an R data frame at a time).
   * 
   * @return the batch size for prediction
   */
  @Override
  public String getBatchSize() {
    return m_batchPredictSize;
  }

  /**
   * Set the batch size for prediction (i.e. how many instances to push over
   * into an R data frame at a time).
   * 
   * @return the batch size for prediction
   */
  @Override
  public void setBatchSize(String size) {
    m_batchPredictSize = size;
  }

  /**
   * Returns true, as R schemes have to predict in batches and we push entire
   * test sets over into R
   *
   * @return true
   */
  public boolean implementsMoreEfficientBatchPrediction() {
    return true;
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String RLearnerTipText() {
    return "R learner to use";
  }

  /**
   * Set the base R learner to use
   * 
   * @param learner the learner to use
   */
  public void setRLearner(SelectedTag learner) {
    if (learner.getTags() == MLRClassifier.TAGS_LEARNER) {
      m_rLearner = learner.getSelectedTag().getID();
    }
  }

  /**
   * Get the base R learner to use
   * 
   * @return the learner to use
   */
  public SelectedTag getRLearner() {
    return new SelectedTag(m_rLearner, MLRClassifier.TAGS_LEARNER);
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String learnerParamsTipText() {
    return "Parameters for the R learner";
  }

  /**
   * Set the parameters for the R learner. This should be specified in the same
   * way (i.e. comma separated) as they would be if using the R console.
   * 
   * @param learnerParams the parameters (comma separated) to pass to the R
   *          learner.
   */
  public void setLearnerParams(String learnerParams) {
    m_schemeOptions = learnerParams;
  }

  /**
   * Get the parameters for the R learner. This should be specified in the same
   * way (i.e. comma separated) as they would be if using the R console.
   * 
   * @return the parameters (comma separated) to pass to the R learner.
   */
  public String getLearnerParams() {
    return m_schemeOptions;
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String dontReplaceMissingValuesTipText() {
    return "Don't replace missing values";
  }

  /**
   * Set whether to turn off replacement of missing values in the data before it
   * is passed into R.
   * 
   * @param d true if missing values should not be replaced.
   */
  public void setDontReplaceMissingValues(boolean d) {
    m_dontReplaceMissingValues = d;
  }

  /**
   * Get whether to turn off replacement of missing values in the data before it
   * is passed into R.
   * 
   * @return true if missing values should not be replaced.
   */
  public boolean getDontReplaceMissingValues() {
    return m_dontReplaceMissingValues;
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String logMessagesFromRTipText() {
    return "Whether to output info/warning messages from R";
  }

  /**
   * Set whether to log info/warning messages from R to the console.
   * 
   * @param l true if info/warning messages should be logged to the console.
   */
  public void setLogMessagesFromR(boolean l) {
    m_logMessagesFromR = l;
  }

  /**
   * Get whether to log info/warning messages from R to the console.
   * 
   * @return true if info/warning messages should be logged to the console.
   */
  public boolean getLogMessagesFromR() {
    return m_logMessagesFromR;
  }

  /**
   * Returns the tip text for this property
   * 
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String seedTipText() {
    return "The random number seed to be used.";
  }

  /**
   * Set the seed for random number generation.
   *
   * @param seed the seed
   */
  public void setSeed(int seed) {
    m_Seed = seed;
  }

  /**
   * Gets the seed for the random number generations
   *
   * @return the seed for the random number generation
   */
  public int getSeed() {
    return m_Seed;
  }

  protected Instances handleZeroFrequencyNominalValues(Instances data) {
    // do we need to merge zero-frequency nominal values?
    ArrayList<Attribute> atts = new ArrayList<Attribute>();
    List<Integer> modifiedIndices = new ArrayList<Integer>();
    for (int i = 0; i < data.numAttributes(); i++) {
      if (data.attribute(i).isNominal()) {

        AttributeStats stats = data.attributeStats(i);
        int[] freqs = stats.nominalCounts;

        List<Integer> zeroFreqI = new ArrayList<Integer>();
        for (int j = 0; j < freqs.length; j++) {
          if (freqs[j] == 0) {
            zeroFreqI.add(j);
          }
        }

        if (zeroFreqI.size() > 0) {
          modifiedIndices.add(i);
          List<String> vals = new ArrayList<String>();
          for (int j = 0; j < data.attribute(i).numValues(); j++) {
            if (!zeroFreqI.contains(j)) {
              vals.add(data.attribute(i).value(j));
            }
          }
          Attribute a = new Attribute(data.attribute(i).name(), vals);
          atts.add(a);
        } else {
          atts.add((Attribute) data.attribute(i).copy());
        }
      } else {
        atts.add((Attribute) data.attribute(i).copy());
      }
    }

    m_originalTrainingHeader = null;
    if (modifiedIndices.size() > 0) {
      m_originalTrainingHeader = new Instances(data, 0);

      m_testHeader =
        new Instances(data.relationName(), atts, data.numInstances());

      m_testHeader.setClassIndex(data.classIndex());

      for (int i = 0; i < data.numInstances(); i++) {
        Instance current = data.instance(i);
        double[] vals = current.toDoubleArray();
        for (int j : modifiedIndices) {
          if (!current.isMissing(j)) {
            double newIndex =
              m_testHeader.attribute(j).indexOfValue(current.stringValue(j));

            vals[j] = newIndex;
          }
        }

        Instance newInst = null;
        if (current instanceof SparseInstance) {
          newInst = new SparseInstance(current.weight(), vals);
        } else {
          newInst = new DenseInstance(current.weight(), vals);
        }

        m_testHeader.add(newInst);
      }
      m_testHeader.compactify();

      data = m_testHeader;
      m_testHeader = new Instances(m_testHeader, 0);
    } else {
      m_testHeader = new Instances(data, 0);
    }

    return data;
  }

  /**
   * Checks to see if the supplied R scheme produces probabilities
   * 
   * @param mlrIdentifier the name of the scheme, e.g. classif.rpart
   * @param eng the R engine to use
   * @return true if the scheme produces probabilities
   * @throws Exception if a problem occurs
   */
  protected boolean schemeProducesProbabilities(String mlrIdentifier,
    RSession eng) throws Exception {

    boolean result = false; // assume false

    eng.clearConsoleBuffer(this);

    if (m_modelHash == null) {
      m_modelHash = "" + hashCode();
    }

    String learnString = "";
    learnString = "l" + m_modelHash + " <- makeLearner(\"" + mlrIdentifier + "\")";

    eng.parseAndEval(this, learnString);
    eng.parseAndEval(this, "print(l" + m_modelHash + ")");

    eng.parseAndEval(this, "remove(l" + m_modelHash + ")");

    String output = eng.getConsoleBuffer(this);

    // Are we working with MLR 2?
    if (output.indexOf("Properties:") >= 0) {
      // Get list of properties
      List<String> props =
        Arrays.asList(output.replaceFirst("(.|\\s)*Properties: ", "")
          .replaceFirst("\\s(.|\\s)*", "").split(","));
      return props.contains("prob");
    } else {

      // Need to parse MLR 1 output
      if (output.indexOf("Supports probabilities:") > 0) {
        result =
          output.substring(output.indexOf("Supports probabilities:") + 23,
            output.length()).trim().toLowerCase().startsWith("true");
      }

      return result;
    }
  }

  /**
   * Checks the R error stream for error messages and stores them in a buffer.
   * 
   * @throws Exception if an error message is detected.
   */
  protected void checkForErrors() throws Exception {
    StringBuilder b = new StringBuilder();

    if (m_errorsFromR != null) {
      for (String m : m_errorsFromR) {
        b.append(m);
      }

      if (b.toString().contains("Error in")) {
        throw new Exception(
          "An error occurred in the R environment:\n\n" + b.toString());
      }

      m_errorsFromR.clear();
    }
  }

  /**
   * Build the specified R learner on the incoming training data.
   * 
   * @param data the training data to be used for generating the R model.
   * @throws Exception if the classifier could not be built successfully.
   */
  public void buildClassifier(Instances data) throws Exception {
    // can classifier handle the data?
    getCapabilities().testWithFail(data);

    m_errorsFromR = new ArrayList<String>();

    if (m_modelHash == null) {
      m_modelHash = "" + hashCode();
    }

    data = new Instances(data);
    data.deleteWithMissingClass();

    if (data.numInstances() == 0 || data.numAttributes() == 1) {
      if (data.numInstances() == 0) {
        System.err
          .println("No instances with non-missing class - using ZeroR model");
      } else {
        System.err.println("Only the class attribute is present in "
          + "the data - using ZeroR model");
      }
      m_zeroR = new ZeroR();
      m_zeroR.buildClassifier(data);
      return;
    }

    // remove useless attributes
    m_removeUseless = new RemoveUseless();
    m_removeUseless.setInputFormat(data);
    data = Filter.useFilter(data, m_removeUseless);

    if (!m_dontReplaceMissingValues) {
      m_missingFilter = new ReplaceMissingValues();
      m_missingFilter.setInputFormat(data);
      data = Filter.useFilter(data, m_missingFilter);
    }

    data = handleZeroFrequencyNominalValues(data);

    m_serializedModel = null;

    if (!m_initialized) {
      init();

      if (!m_mlrAvailable) {
        throw new Exception(
          "MLR is not available for some reason - can't continue!");
      }
    } else {
      // unload and then reload MLR to try and clear any errors/inconsistent
      // state
      reloadMLR();
    }

    m_baseLearnerLibraryAvailable = false;
    loadBaseLearnerLibrary();
    if (!m_baseLearnerLibraryAvailable) {
      throw new Exception("Library "
        + MLRClassifier.TAGS_LEARNER[m_rLearner].getIDStr() + " for learner "
        + MLRClassifier.TAGS_LEARNER[m_rLearner].getReadable()
        + " is not available for some reason - can't continue!");
    }

    RSession eng = null;
    eng = RSession.acquireSession(this);
    if (eng == null) {
      return;
    }
    eng.setLog(this, m_logger);
    eng.clearConsoleBuffer(this);

    // Change to a temporary directory where we have write access
    // in case an mlr scheme tries to write a local file
    eng.parseAndEval(this, "setwd(tempdir())");

    // Set seed for random number generator in R in a data-dependent manner
    eng.parseAndEval(this,
      "set.seed(" + data.getRandomNumberGenerator(getSeed()).nextInt() + ")");

    // transfer training data into a data frame in R
    Object[] result = RUtils.instancesToDataFrame(eng, this, data, "mlr_data" + m_modelHash);
    m_cleanedAttNames = (String[])result[0];
    m_cleanedAttValues = (String[][])result[1];

    try {
      String mlrIdentifier =
        MLRClassifier.TAGS_LEARNER[m_rLearner].getReadable();

      if (data.classAttribute().isNumeric()
        && mlrIdentifier.startsWith("classif")) {
        throw new Exception("Training instances has a numeric class but "
          + "selected R learner is a classifier!");
      } else if (data.classAttribute().isNominal()
        && mlrIdentifier.startsWith("regr")) {
        throw new Exception("Training instances has a nominal class but "
          + "selected R learner is a regressor!");
      }

      m_errorsFromR.clear();
      // make classification/regression task
      String taskString = null;
      if (data.classAttribute().isNominal()) {
        taskString = "task"+ m_modelHash + " <- makeClassifTask(data = mlr_data"+ m_modelHash + ", target = \""
          + m_cleanedAttNames[data.classIndex()] + "\")";
      } else {
        taskString = "task"+ m_modelHash + " <- makeRegrTask(data = mlr_data"+ m_modelHash + ", target = \""
          + m_cleanedAttNames[data.classIndex()] + "\")";
      }

      if (m_Debug) {
        System.err.println("Prediction task: " + taskString);
      }

      eng.parseAndEval(this, taskString);
      // eng.parseAndEval(this, "print(task)");
      checkForErrors();

      m_schemeProducesProbs = schemeProducesProbabilities(mlrIdentifier, eng);

      String probs =
        (data.classAttribute().isNominal() && m_schemeProducesProbs)
          ? ", predict.type = \"prob\"" : "";
      String learnString = null;
      if (m_schemeOptions != null && m_schemeOptions.length() > 0) {
        learnString = "l"+ m_modelHash + " <- makeLearner(\"" + mlrIdentifier + "\"" + probs
          + ", " + m_schemeOptions + ")";
      } else {
        learnString =
          "l"+ m_modelHash + " <- makeLearner(\"" + mlrIdentifier + "\"" + probs + ")";
      }

      if (m_Debug) {
        System.err.println("Make a learner object: " + learnString);
      }

      eng.parseAndEval(this, learnString);
      checkForErrors();

      // eng.parseAndEval(this, "print(l)");

      // train model
      eng.parseAndEval(this,
        "weka_r_model" + m_modelHash + " <- train(l"+ m_modelHash + ", task"+ m_modelHash + ")");

      checkForErrors();

      // get the model for serialization
      REXP serializedRModel = eng.parseAndEval(this,
        "serialize(weka_r_model" + m_modelHash + ", NULL)");

      checkForErrors();

      m_modelText = new StringBuffer();

      // get the textual representation
      eng.parseAndEval(this,
        "print(getLearnerModel(weka_r_model" + m_modelHash + "))");
      m_modelText.append(eng.getConsoleBuffer(this));

      // now try and serialize the model
      XStream xs = new XStream();
      xs.addPermission(NoTypePermission.NONE);
      xs.allowTypeHierarchy(REXP.class);
      String xml = xs.toXML(serializedRModel);
      if (xml != null && xml.length() > 0) {
        m_serializedModel = new StringBuffer();
        m_serializedModel.append(xml);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      // remove R training data frame and model after completion
      eng.parseAndEval(this, "remove(mlr_data" + m_modelHash + ")");
      eng.parseAndEval(this, "remove(weka_r_model" + m_modelHash + ")");
      eng.parseAndEval(this, "remove(l" + m_modelHash + ")");
      eng.parseAndEval(this, "remove(task" + m_modelHash + ")");
      RSession.releaseSession(this);

      throw new Exception(ex.getMessage());
    }

    eng.parseAndEval(this, "remove(mlr_data" + m_modelHash + ")");
    eng.parseAndEval(this, "remove(weka_r_model" + m_modelHash + ")");
    eng.parseAndEval(this, "remove(l" + m_modelHash + ")");
    eng.parseAndEval(this, "remove(task" + m_modelHash + ")");
    RSession.releaseSession(this);
  }

  protected void pushModelToR(RSession eng) throws Exception {
    XStream xs = new XStream();
    xs.addPermission(NoTypePermission.NONE);
    xs.allowTypeHierarchy(REXP.class);
    REXP model = (REXP) xs.fromXML(m_serializedModel.toString());

    eng.assign(this, "weka_r_model" + m_modelHash, model);
    eng.parseAndEval(this, "weka_r_model" + m_modelHash
      + " <- unserialize(weka_r_model" + m_modelHash + ")");

    if (m_Debug) {
      eng.clearConsoleBuffer(this);
      eng.parseAndEval(this, "print(getLearnerModel(weka_r_model" + m_modelHash + "))");
      System.err.println("Printing pushed model....");
      System.err.println(eng.getConsoleBuffer(this));
    }
  }

  protected double[][] frameToPreds(RSession eng, REXP r, Attribute classAtt,
    int numRows) throws Exception {
    RList frame = r.asList();

    double[][] result = classAtt.isNumeric() ? new double[numRows][1]
      : new double[numRows][classAtt.numValues()];

    String attributeNames[] = null;

    attributeNames =
      ((REXPString) ((REXPGenericVector) r)._attr().asList().get("names"))
        .asStrings();

    if (classAtt.isNominal()) {
      if (m_schemeProducesProbs) {
        int indexInTestHeader = 0;
        for (int i = 0; i < classAtt.numValues(); i++) {
          if ((indexInTestHeader + 1) > m_testHeader.classAttribute().numValues()) {
            break; // Have run out of values;
          }
          if (!classAtt.value(i).equals(m_testHeader.classAttribute().value(indexInTestHeader))) {
            continue; // Skip empty classes
          }
          String classL = m_cleanedAttValues[m_testHeader.classAttribute().index()][indexInTestHeader++];

          int index = -1;
          for (int j = 0; j < attributeNames.length; j++) {
            if (attributeNames[j].equals("prob." + classL)) {
              index = j;
              break;
            }
          }
          Object columnObject = frame.get(index);
          REXPVector colVector = (REXPVector) columnObject;
          double[] colD = colVector.asDoubles();
          if (colD.length != numRows) {
            throw new Exception("Was expecting " + numRows + " predictions "
              + "but got " + colD.length + "!");
          }
          for (int j = 0; j < numRows; j++) {
            result[j][i] = colD[j];
          }
        }
      } else {
        // handle the "truth", "response" frame
        Attribute tempClassAtt = new Attribute(classAtt.name(), new ArrayList(Arrays.asList(m_cleanedAttValues[m_testHeader.classAttribute().index()])));
        Object columnObject = frame.get(1);
        REXPVector colVector = (REXPVector) columnObject;
        String[] labels = colVector.asStrings();

        if (labels.length != numRows) {
          throw new Exception("Was expecting " + numRows + " predictions "
            + "but got " + labels.length + "!");
        }

        // Take care of empty classes by computing index map
        int[] indexMap = new int[m_testHeader.classAttribute().numValues()];
        int indexInTestHeader = 0;
        for (int i = 0; i < classAtt.numValues(); i++) {
          if ((indexInTestHeader + 1) > m_testHeader.classAttribute().numValues()) {
            break; // Have run out of values;
          }
          if (!classAtt.value(i).equals(m_testHeader.classAttribute().value(indexInTestHeader))) {
            continue; // Skip empty classes
          }
          indexMap[indexInTestHeader++] = i;
        }

        // convert labels to 1/0 probs
        for (int i = 0; i < numRows; i++) {
          String pred = labels[i];

          int labelIndex = tempClassAtt.indexOfValue(pred.trim());

          if (labelIndex < 0) {
            System.err.println("Didn't find label: " + pred.trim());
          } else {
            result[i][indexMap[labelIndex]] = 1.0;
          }
        }
      }
    } else {
      Object columnObject = frame.get(1);
      REXPVector colVector = (REXPVector) columnObject;
      double[] colD = colVector.asDoubles();
      if (colD.length != numRows) {
        throw new Exception("Was expecting " + numRows + " predictions "
          + "but got " + colD.length + "!");
      }
      for (int j = 0; j < numRows; j++) {
        result[j][0] = colD[j];
      }
    }

    return result;
  }

  private double[][] batchScoreWithZeroR(Instances insts) throws Exception {
    double[][] result = new double[insts.numInstances()][];

    for (int i = 0; i < insts.numInstances(); i++) {
      Instance current = insts.instance(i);
      result[i] = m_zeroR.distributionForInstance(current);
    }

    return result;
  }

  /**
   * Batch scoring method
   * 
   * @param insts the instances to push over to R and get predictions for
   * @return an array of probability distributions, one for each instance
   * @throws Exception if a problem occurs
   */
  @Override
  public double[][] distributionsForInstances(Instances insts)
    throws Exception {
    double[][] probs = null;

    if (m_zeroR != null) {
      return batchScoreWithZeroR(insts);
    }

    if ((m_serializedModel == null || m_serializedModel.length() == 0)
      && m_zeroR == null) {
      throw new Exception("No model has been built yet!");
    }

    if (!m_initialized) {
      init();
    }

    if (!m_mlrAvailable) {
      throw new Exception(
        "MLR is not available for some reason - can't continue!");
    }

    if (m_errorsFromR == null) {
      m_errorsFromR = new ArrayList<String>();
    }

    m_testHeader.delete();

    for (int i = 0; i < insts.numInstances(); i++) {
      Instance inst = insts.instance(i);

      if (m_removeUseless != null) {
        m_removeUseless.input(inst);
        inst = m_removeUseless.output();
      }

      if (m_originalTrainingHeader != null) {
        // we removed some zero frequency nominal labels

        for (int j = 0; j < inst.numAttributes(); j++) {
          if (inst.attribute(j).isNominal() && !inst.isMissing(j)) {
            int index =
              m_testHeader.attribute(j).indexOfValue(inst.stringValue(j));
            if (index < 0) {
              // replace with missing value
              inst = (Instance) inst.copy();
              inst.setValue(j, Utils.missingValue());
            } else {
              if (index != (int) inst.value(j)) {
                // remap
                inst = (Instance) inst.copy();
                inst.setValue(j, index);
              }
            }
          }
        }
      }

      if (!m_dontReplaceMissingValues) {
        m_missingFilter.input(inst);
        inst = m_missingFilter.output();
      }

      m_testHeader.add(inst);
    }

    RSession eng = null;
    eng = RSession.acquireSession(this);
    if (eng == null) {
      return new double[insts.numInstances()][insts.numClasses()];
    }
    eng.setLog(this, m_logger);

    try {

      // Change to a temporary directory where we have write access
      // in case an mlr scheme tries to write a local file
      eng.parseAndEval(this, "setwd(tempdir())");

      if (m_Debug) {
        System.err.println("Pushing serialized model to R...");
      }

      // we need to push it over
      pushModelToR(eng);

      checkForErrors();

      // Instances toPush = m_dontReplaceMissingValues ? insts : m_testHeader;
      Instances toPush = m_testHeader;
      toPush.compactify();
      RUtils.instancesToDataFrame(eng, this, toPush, "weka_r_test" + m_modelHash);

      String testB =
        "p" + m_modelHash + " <- predict(weka_r_model" + m_modelHash + ", newdata = weka_r_test" + m_modelHash + ")";

      if (m_Debug) {
        System.err.println("Executing prediction: ");
        System.err.println(testB);
      }

      eng.parseAndEval(this, testB);

      checkForErrors();

      // cleanup the model in R
      if (m_Debug) {
        System.err.println("Cleaning up model in R...");
      }
      eng.parseAndEval(this, "remove(weka_r_model" + m_modelHash + ")");
      eng.parseAndEval(this, "remove(weka_r_test" + m_modelHash + ")");

      checkForErrors();

      testB = "p" + m_modelHash + " <- p" + m_modelHash + "$data";
      eng.parseAndEval(this, testB);

      REXP result = eng.parseAndEval(this, "p" + m_modelHash + "");

      probs =
        frameToPreds(eng, result, insts.classAttribute(), insts.numInstances());

      eng.parseAndEval(this, "remove(p" + m_modelHash + ")");
    } catch (Exception ex) {
      ex.printStackTrace();

      eng.parseAndEval(this, "remove(weka_r_model" + m_modelHash + ")");
      eng.parseAndEval(this, "remove(weka_r_test" + m_modelHash + ")");
      eng.parseAndEval(this, "remove(p" + m_modelHash + ")");

      RSession.releaseSession(this);
      throw new Exception(ex.getMessage());
    }

    checkForErrors();
    RSession.releaseSession(this);

    return probs;
  }

  /**
   * Calculates the class membership probabilities for the given test instance.
   * 
   * @param inst the instance to be classified
   * @return predicted class probability distribution
   * @throws Exception if distribution can't be computed successfully
   */
  public double[] distributionForInstance(Instance inst) throws Exception {

    Instances data = new Instances(inst.dataset(), 1);
    data.add(inst);
    double[][] result = distributionsForInstances(data);
    return result[0];
  }

  /**
   * Close the R environment down. This is useful when running from the command
   * line in order to get control back.
   */
  public void closeREngine() {
    RSession eng = null;
    try {
      eng = RSession.acquireSession(this);
      if (eng == null) {
        return;
      }
      eng.setLog(this, m_logger);

      eng.close();
    } catch (Exception ex) {
      ex.printStackTrace();
    }

  }

  /**
   * Returns the textual description of the R classifier.
   * 
   * @return description of the R classifier as a string.
   */
  @Override
  public String toString() {
    if (m_modelText != null && m_modelText.length() > 0) {
      return m_modelText.toString();
    }

    return "MLRClassifier: model not built yet!";
  }

  /**
   * Returns the revision string.
   * 
   * @return the revision
   */
  @Override
  public String getRevision() {
    return RevisionUtils.extract("$Revision$");
  }
}
