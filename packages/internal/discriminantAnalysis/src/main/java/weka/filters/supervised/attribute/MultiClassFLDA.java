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
 *    MultiClassFLDA.java
 *    Copyright (C) 2016-2021 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.filters.supervised.attribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;

import no.uib.cipr.matrix.*;
import no.uib.cipr.matrix.Matrix;
import weka.core.*;
import weka.filters.SimpleBatchFilter;

/**
 <!-- globalinfo-start -->
 * Implements Fisher's linear discriminant analysis for dimensionality reduction. Note that this implementation
 * adds the value of the ridge parameter to the diagonal of the within-class scatter matrix.
 <!-- globalinfo-end -->
 *
 <!-- options-start -->
 * Valid options are: <p/>
 *
 * <pre> -R
 *  The ridge parameter to add to the diagonal of the within-class scatter matrix.
 *  (default is 1e-6)</pre>
 *
 <!-- options-end -->
 *
 * @author Eibe Frank (eibe@cs.waikato.ac.nz)
 * @version $Revision: 12341 $
 */
public class MultiClassFLDA extends SimpleBatchFilter implements OptionHandler, WeightedInstancesHandler {

  /** for serialization */
  static final long serialVersionUID = -291536442147283133L;

  /** Stores the weighting matrix. */
  protected Matrix m_WeightingMatrix;

  /** Ridge parameter */
  protected double m_Ridge = 1e-6;

  /**
   * Returns the Capabilities of this filter.
   *
   * @return the capabilities of this object
   * @see Capabilities
   */
  @Override
  public Capabilities getCapabilities() {

    Capabilities result = new Capabilities(this);
    result.disableAll();

    result.setMinimumNumberInstances(0);

    // attributes
    result.enable(Capabilities.Capability.NUMERIC_ATTRIBUTES);

    // class
    result.enable(Capabilities.Capability.NOMINAL_CLASS);
    result.enable(Capabilities.Capability.MISSING_CLASS_VALUES);

    return result;
  }

  /**
   * Provides information regarding this class.
   *
   * @return string describing the method that this class implements
   */
  @Override
  public String globalInfo() {

    return "Implements Fisher's linear discriminant analysis for dimensionality reduction. Note that this implementation " +
            "adds the value of the ridge parameter to the diagonal of the within-class scatter matrix.";
  }

  /**
   * Returns the tip text for this property
   *
   * @return tip text for this property suitable for displaying in the
   *         explorer/experimenter gui
   */
  public String ridgeTipText() {

    return "The ridge parameter to add to the diagonal of the within-class scatter matrix.";
  }

  /**
   * Get the value of Ridge.
   *
   * @return Value of Ridge.
   */
  public double getRidge() {

    return m_Ridge;
  }

  /**
   * Set the value of Ridge.
   *
   * @param newRidge Value to assign to Ridge.
   */
  public void setRidge(double newRidge) {

    m_Ridge = newRidge;
  }

  /**
   * Returns an enumeration describing the available options.
   *
   * @return an enumeration of all the available options.
   */
  public Enumeration<Option> listOptions() {

    java.util.Vector<Option> newVector = new java.util.Vector<Option>(7);

    newVector.addElement(new Option(
            "\tThe ridge parameter to add to the diagonal of the within-class scatter matrix.\n"+
                    "\t(default is 1e-6)",
            "R", 0, "-R"));

    newVector.addAll(Collections.list(super.listOptions()));

    return newVector.elements();
  }

  /**
   * Parses a given list of options. <p/>
   *
   * <!-- options-start -->
   * Valid options are: <p/>
   *
   * <pre> -R
   *  The ridge parameter to add to the diagonal of the within-class scatter matrix.
   *  (default is 1e-6)</pre>
  *
   * <!-- options-end -->
   *
   * @param options the list of options as an array of strings
   * @throws Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception {

    String ridgeString = Utils.getOption('R', options);
    if (ridgeString.length() != 0) {
      setRidge(Double.parseDouble(ridgeString));
    } else {
      setRidge(1e-6);
    }

    super.setOptions(options);

    Utils.checkForRemainingOptions(options);
  }

  /**
   * Gets the current settings of IBk.
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String [] getOptions() {

    java.util.Vector<String> options = new java.util.Vector<String>();
    options.add("-R"); options.add("" + getRidge());

    Collections.addAll(options, super.getOptions());

    return options.toArray(new String[0]);
  }

  /**
   * Returns whether to allow the determineOutputFormat(Instances) method access
   * to the full dataset rather than just the header.
   * <p/>
   * Default implementation returns false.
   *
   * @return whether determineOutputFormat has access to the full input dataset
   */
  public boolean allowAccessToFullInputFormat() {
    return true;
  }

  /**
   * Computes the mean vector for the given dataset.
   */
  protected Vector computeMean(Instances data, double[] totalWeight, int aI) {

    int m = data.numAttributes() - 1;
    double[] mean = new double[m];
    totalWeight[aI] = 0;
    int classIndex = (int) data.classIndex();
    for (Instance inst : data) {
      double weight = inst.weight();
      for (int i = 0; i < classIndex; i++) {
        mean[i] += weight * inst.value(i);
      }
      for (int i = classIndex; i < m; i++) {
        mean[i] += weight * inst.value(i + 1);
      }
      totalWeight[aI] += weight;
    }
    Vector meanVector = new DenseVector(mean);
    meanVector.scale(1.0 / totalWeight[aI]);
    return meanVector;
  }

  /**
   * Turns instances into a matrix without the class attribute.
   */
  protected Matrix instancesToMatrix(Instances insts) {

    int m = insts.numAttributes() - 1;
    int n = insts.numInstances();
    Matrix matrix = new DenseMatrix(m, n);
    int classIndex = insts.classIndex();
    for (int j = 0; j < n; j++) {
      Instance inst = insts.instance(j);
      for (int i = 0; i < classIndex; i++) {
        matrix.set(i, j, inst.value(i));
      }
      for (int i = classIndex; i < m; i++) {
        matrix.set(i, j, inst.value(i + 1));
      }
    }
    return matrix;
  }

  /**
     * Determines the output format for the data that is produced by this filter.
     *
     * @param inputFormat the input format to base the output format on
     * @return the output format
     * @throws Exception if a problem occurs when the output format is generated
     */
    @Override
    protected Instances determineOutputFormat(Instances inputFormat) throws Exception {

      // Delete instances with missing class values
      inputFormat = new Instances(inputFormat);
      inputFormat.deleteWithMissingClass();

      // Determine number of attributes
      int m = inputFormat.numAttributes() - 1;

      // Determine number of instances
      int n = inputFormat.numInstances();

      // Determine number of classes
      int k = inputFormat.numClasses();

      // Compute global mean
      Vector globalMean = computeMean(inputFormat, new double[1], 0);

      // Compute subset for each class
      Instances[] subsets = new Instances[k];
      for (int j = 0; j < k; j++) {
        subsets[j] = new Instances(inputFormat, n);
      }
      for (Instance inst : inputFormat) {
        subsets[(int) inst.classValue()].add(inst);
      }
      for (int i = 0; i < k; i++) {
        subsets[i].compactify();
      }

      // Compute mean vector and weight for each class
      Vector[] perClassMeans = new DenseVector[k];
      double[] perClassWeights = new double[k];
      for (int i = 0; i < k; i++) {
        perClassMeans[i] = computeMean(subsets[i], perClassWeights, i);
      }

      // Compute within-class scatter matrix
      Matrix tempMatrix = new DenseMatrix(m, n);
      int classIndex = inputFormat.classIndex();
      for (int j = 0; j < n; j++) {
        Instance inst = inputFormat.instance(j);
        double sqrtWeight = Math.sqrt(inst.weight());
        Vector classMean = perClassMeans[(int) inst.classValue()];
        for (int i = 0; i < classIndex; i++) {
          tempMatrix.set(i, j, sqrtWeight * (inst.value(i) - classMean.get(i)));
        }
        for (int i = classIndex; i < m; i++) {
          tempMatrix.set(i, j, sqrtWeight * (inst.value(i + 1) - classMean.get(i)));
        }
      }
      Matrix Cw = (new UpperSPDDenseMatrix(m)).rank1(tempMatrix);

      // Add ridge to pooled within-class scatter matrix
      for (int i = 0; i < m; i++) {
        Cw.add(i, i, m_Ridge);
      }

      // Compute between-class scatter matrix
      int k_actual = 0;
      for (int j = 0; j < k; j++) {
        if (perClassWeights[j] > 0) {
          k_actual++;
        }
      }
      tempMatrix = new DenseMatrix(m, k_actual);
      int j_actual = 0;
      for (int j = 0; j < k; j++) {
        if (perClassWeights[j] > 0) {
          for (int i = 0; i < m; i++) {
            tempMatrix.set(i, j_actual,
                    Math.sqrt(perClassWeights[j]) * (perClassMeans[j].get(i) - globalMean.get(i)));
          }
          j_actual++;
        }
      }
      Matrix Cb = (new UpperSPDDenseMatrix(m)).rank1(tempMatrix);

      if (m_Debug) {
        System.err.println("Within-class scatter matrix :\n" + Cw);
        System.err.println("Between-class scatter matrix :\n" + Cb);
      }

      // Compute square root of inverse within-class scatter matrix
      SymmDenseEVD evd = SymmDenseEVD.factorize(Cw);
      Matrix ev = evd.getEigenvectors();
      double[] evs = evd.getEigenvalues();

      if (m_Debug) {
        System.err.println("ev : \n" + ev);
	System.err.println("evs : \n");
        for (int i = 0; i < evs.length; i++) {
          System.err.print(evs[i] + " ");
        }
	System.err.println();
        System.err.println("ev times evCwTransposed : \n" + ev.mult(ev.transpose(new DenseMatrix(m, m)), new DenseMatrix(m, m)));
      }

      // Eigenvectors for Cw and its inverse are the same. Eigenvalues of inverse are reciprocal of evs of original.
      for (int i = 0; i < ev.numColumns(); i++) {
        if (evs[i] > 0) {
          double multiplier = 1.0 / Math.pow(evs[i], 0.25);
          for (int j = 0; j < ev.numRows(); j++) {
            ev.set(j, i, ev.get(j, i) * multiplier);
          }
        } else {
          throw new IllegalArgumentException("Found non-positive eigenvalue of within-class scatter matrix.");
        }
      }
      Matrix sqrtCwInverse = new UpperSPDDenseMatrix(m).rank1(ev);

      if (m_Debug) {
        System.err.println("sqrtCwInverse : \n");
        for (int i = 0; i < sqrtCwInverse.numRows(); i++) {
          for (int j = 0; j < sqrtCwInverse.numColumns(); j++) {
            System.err.print(sqrtCwInverse.get(i, j) + "\t");
          }
          System.err.println();
        }
        System.err.println("sqrtCwInverse times sqrtCwInverse : \n" + sqrtCwInverse.mult(sqrtCwInverse, new DenseMatrix(m, m)));
        DenseMatrix I = Matrices.identity(m);
        DenseMatrix CwInverse = I.copy();
        System.err.println("CwInverse : \n" + Cw.solve(I, CwInverse));
      }

      // Compute eigen decomposition of between-class scatter matrix
      evd = SymmDenseEVD.factorize(Cb);
      ev = evd.getEigenvectors();
      evs = evd.getEigenvalues();

      if (m_Debug) {
        System.err.println("ev : \n" + ev);
        System.err.println("evs : \n");
        for (int i = 0; i < evs.length; i++) {
          System.err.print(evs[i] + " ");
        }
        System.err.println();
        System.err.println("ev times evCwTransposed : \n" + ev.mult(ev.transpose(new DenseMatrix(m, m)), new DenseMatrix(m, m)));
      }

      // Use eigenvalue decomposition to compute square root of Cb
      for (int i = 0; i < ev.numColumns(); i++) {
	  double multiplier = 0;
	  if (evs[i] > 0) {
	      multiplier = Math.pow(evs[i], 0.25);
	  }
	  for (int j = 0; j < ev.numRows(); j++) {
	      ev.set(j, i, ev.get(j, i) * multiplier);
	  }
      }
      Matrix sqrtCb = new UpperSPDDenseMatrix(m).rank1(ev);

      // Compute symmetric matrix using square root
      Matrix temp = sqrtCwInverse.mult(sqrtCb, new DenseMatrix(m, m));
      Matrix symmMatrix = new UpperSymmDenseMatrix(m).rank1(temp);

      /*System.err.println("Computing symmetric matrix");
      Matrix temp = sqrtCwInverse.mult(Cb, new DenseMatrix(m, m));
      Matrix symmMatrix = temp.mult(sqrtCwInverse, new UpperSymmDenseMatrix(m));*/

      if (m_Debug) {
        System.err.println("Symmetric matrix : \n" + symmMatrix);
      }

      // Perform eigendecomposition on symmetric matrix
      evd = SymmDenseEVD.factorize(symmMatrix);

      if (m_Debug) {
        System.err.println("Eigenvectors of symmetric matrix :\n" + evd.getEigenvectors());
        System.err.println("Eigenvalues of symmetric matrix :\n" + Utils.arrayToString(evd.getEigenvalues()) + "\n");
      }

      // Only keep eigenvectors with positive eigenvalues
      ArrayList<Integer> indices = new ArrayList<Integer>();
      for (int i = 0; i < evd.getEigenvalues().length; i++) {
        if (Utils.gr(evd.getEigenvalues()[i], 0)) {
          indices.add(i);
        }
      }
      int[] cols = new int[indices.size()];
      int index = 0;
      for (int i = indices.size() - 1; i >= 0; i--) {
        cols[index++] = indices.get(i);
      }
      int[] rows = new int[evd.getEigenvectors().numRows()];
      for (int i = 0; i < rows.length; i++) {
        rows[i] = i;
      }
      Matrix reducedMatrix = Matrices.getSubMatrix(evd.getEigenvectors(), rows, cols);

      if (m_Debug) {
        System.err.println("Eigenvectors with eigenvalues > eps :\n" + reducedMatrix);
      }

      //
      // Compute weighting Matrix
      //
      // Note: we do not scale the matrix so that the new attributes have (unbiased) variance 1, like R's lda does.
      // In our case, the *scatter matrix* of the new data is I (if no regularization is used).
      // Also, R's lda always centers the data.
      //
      m_WeightingMatrix = sqrtCwInverse.mult(reducedMatrix, new DenseMatrix(rows.length, cols.length)).
              transpose(new DenseMatrix(cols.length, rows.length));

      if (m_Debug) {
        System.err.println("Weighting matrix: \n");
        for (int i = 0; i < m_WeightingMatrix.numRows(); i++) {
          for (int j = 0; j < m_WeightingMatrix.numColumns(); j++) {
            System.err.print(m_WeightingMatrix.get(i, j) + "\t");
          }
          System.err.println();
        }
      }

      // Construct header for output format
      ArrayList<Attribute> atts = new ArrayList<Attribute>(cols.length + 1);
      for (int i = 0; i < cols.length; i++) {
        atts.add(new Attribute("z" + (i + 1)));
      }
      atts.add((Attribute) inputFormat.classAttribute().copy());
      Instances d = new Instances(inputFormat.relationName(), atts, 0);
      d.setClassIndex(d.numAttributes() - 1);

      return d;
    }

    /**
     * Takes a batch of data and transforms it.
     *
     * @param instances the data to process
     * @return the processed instances
     * @throws Exception is thrown if a problem occurs
     */
    @Override
    protected Instances process(Instances instances) throws Exception {

      Instances transformed = getOutputFormat();
      Matrix newInsts = m_WeightingMatrix.mult(instancesToMatrix(instances),
              new DenseMatrix(m_WeightingMatrix.numRows(), instances.numInstances()));
      for (int j = 0; j < instances.numInstances(); j++) {
        Instance inst = instances.instance(j);
        double[] newVals = new double[m_WeightingMatrix.numRows() + 1];
        for (int i = 0; i < m_WeightingMatrix.numRows(); i++) {
          newVals[i] = newInsts.get(i, j);
        }
        newVals[transformed.classIndex()] = inst.classValue();
        transformed.add(new DenseInstance(inst.weight(), newVals));
      }
      return transformed;
    }

    /**
     * Returns the revision string.
     *
     * @return the revision
     */
    @Override
    public String getRevision() {
        return RevisionUtils.extract("$Revision: 12037 $");
    }

    /**
     * Main method for testing this class.
     *
     * @param argv should contain arguments to the filter: use -h for help
     */
    public static void main(String[] argv) {
        runFilter(new MultiClassFLDA(), argv);
    }
}

