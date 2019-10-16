package weka.filters.supervised.attribute.gpattributegeneration;

import java.util.*;
import weka.classifiers.Classifier;
import weka.core.*;
import weka.filters.Filter;
import weka.filters.SupervisedFilter;


/**
 * Implementation of a genetic programming system in the WEKA input space
 *
 * @author Colin Noakes
 */
public class GPAttributeGeneration extends Filter
    implements OptionHandler, SupervisedFilter
{
    //CONSTANTS
    //Default option constants
    private static final int DEFAULT_POP_SIZE = 100;
    private static final int DEFAULT_MAX_NUM_GENERATIONS = 100;
    private static final int DEFAULT_MAX_TIME = 600;
    private static final double DEFAULT_TARGET_TREE_ACCURACY = 1.0;
    private static final int DEFAULT_MAX_DEPTH = 5;
    private static final double[] DEFAULT_OPERATOR_PROP = new double[] {0.9, 0.1};
    private static final String[] DEFAULT_FUNCTIONS = new String[] {"+", "-", "*", "/", "^", "sin", "cos", "ln", "exp", "sqrt"};
    private static final EnumGenerationMethod DEFAULT_POP_GEN_METHOD = EnumGenerationMethod.RAMPED_HALF_HALF;
    private static final EnumFitnessEvaluationMethod DEFAULT_FITNESS_EVAL_METHOD = EnumFitnessEvaluationMethod.J48;
    private static final EnumSelectionMethod DEFAULT_SELECTION_METHOD = EnumSelectionMethod.SINGLE_FITNESS;
    private static final Classifier DEFAULT_CLASSIFIER = initialiseJ48();
    private static final long DEFAULT_SEED = 1;

    /**
     * Platform independent new line char. Rather this (where typos will be picked up) than the
     * System.getProperty("line.separator") call everywhere in the code....
     */
    private static final String NEW_LINE = System.getProperty("line.separator");

    //Population generation method options
    private static final String OPT_POP_GEN_HAH = "H";
    private static final String OPT_POP_GEN_GROW = "G";
    private static final String OPT_POP_GEN_FULL = "F";
    private static final Tag[] OPT_TAGS_POP_GEN = {
        new Tag(0, OPT_POP_GEN_HAH, "Ramped half and half"),
        new Tag(1, OPT_POP_GEN_GROW, "Grow"),
        new Tag(2, OPT_POP_GEN_FULL, "Full")};

    //Fitness evaluation options
    private static final String OPT_FIT_EVAL_CLASSIFIER = "C";
    private static final Tag[] OPT_TAGS_FIT_EVAL = {
        new Tag(0, OPT_FIT_EVAL_CLASSIFIER, "J48")};

    //Selection method options
    private static final String OPT_SEL_METHOD_SINGLE_FITNESS = "S";
    private static final String OPT_SEL_METHOD_NSGAII = "N";
    private static final String OPT_SEL_METHOD_VECTOR_FITNESS = "V";
    private static final Tag[] OPT_TAGS_SEL_METHOD = {
        new Tag(0, OPT_SEL_METHOD_SINGLE_FITNESS, "Single Fitness"),
        new Tag(1, OPT_SEL_METHOD_VECTOR_FITNESS, "Vector Fitness"),
        new Tag(2, OPT_SEL_METHOD_NSGAII, "NSGA-II")};

    //Option command line names
    private static final String CMD_POP_SIZE = "P";
    private static final String CMD_MAX_NUM_GENERATIONS = "G";
    private static final String CMD_MAX_TIME = "MT";
    private static final String CMD_TARGET_TREE_ACCURACY = "F";
    private static final String CMD_FUNCTIONS = "func";
    private static final String CMD_MAX_DEPTH = "MD";
    private static final String CMD_OPERATOR_PROP = "OP";
    private static final String CMD_POP_GEN_METHOD = "PG";
    private static final String CMD_FITNESS_EVAL = "FE";
    private static final String CMD_SELECTION_METHOD = "SM";
    private static final String CMD_SEED = "S";

    //Help text (for both CMD line and GUI)
    private static final String HELP_POP_SIZE = "Population Size";
    private static final String HELP_MAX_NUM_GENERATIONS = "Maximum Number of generations to run";
    private static final String HELP_MAX_TIME = "Maximum time (in seconds) to run";
    private static final String HELP_TARGET_TREE_ACCURACY = "Target tree accuracy";
    private static final String HELP_FUNCTIONS = "Function operators to use in the program,\n\t seperated by commas; "
            + "available functions:\n\t " + Function.HELP_ALL_FUNCTIONS;
    private static final String HELP_MAX_DEPTH = "Maximum depth of the trees to generate";
    private static final String HELP_OPERATOR_PROP = "Proportion of genetic operators to use:\n\t crossover, mutation";
    private static final String HELP_POP_GEN_METHOD = "Method of generating the initial population:\n\t H-Ramped H&H; F-Full; G-Grow";
    private static final String HELP_FITNESS_EVAL = "Fitness evaluation method to use:\n\t "
            + "C-J48";
    private static final String HELP_SELECTION_METHOD = "Selection method to use:\n\t S-Single Fitness; N-NSGAII; "
            + "V-Vector Fitness";
    private static final String HELP_SEED = "Seed to use for the random number generator";

    //FIELDS
    //Initialise Option variables and sets defaults
    private int popSize = DEFAULT_POP_SIZE;
    private int maxNumGenerations = DEFAULT_MAX_NUM_GENERATIONS;  //Maximum number of generations
    private int maxTime = DEFAULT_MAX_TIME;  //Maximum amount of time (in seconds)
    private double targetTreeAccuracy = DEFAULT_TARGET_TREE_ACCURACY;  //Target fitness (algorithm will stop when this is reached); value between 0-1
    private ArrayList<String> f_Functions = new ArrayList<String>(Arrays.asList(DEFAULT_FUNCTIONS));  //HashSet of functions to use in GP; NOT ORDERBLE
    private int maxDepth = DEFAULT_MAX_DEPTH;  //Max depth of a program tree
    private double[] operatorProportion = DEFAULT_OPERATOR_PROP.clone();  //The proportion of operator to use (should equal 1): crossover, mutation
    private EnumGenerationMethod popGenerationMethod = DEFAULT_POP_GEN_METHOD;  //Stores an int denoting an option to initialise the population: 0-H&H; 1-Grow, 2-Full
    private EnumFitnessEvaluationMethod fitnessEvaluationMethod = DEFAULT_FITNESS_EVAL_METHOD;
    private EnumSelectionMethod selectionMethod = DEFAULT_SELECTION_METHOD;
    private ArrayList<String> fittestIndividuals;  //The fittest individuals (as a string)
    private Classifier m_Classifier = DEFAULT_CLASSIFIER;
    private long seed = DEFAULT_SEED;

    // PUBLIC METHODS

    /**
     * Main method
     *
     * @param args the options, use "-h" to display options
     */
    public static void main(String[] args)
    {
        runFilter(new GPAttributeGeneration(), args);
    }

    @Override
    public boolean batchFinished() throws Exception
    {
        //Make sure input format is set
        if (getInputFormat() == null)
        {
            throw new NullPointerException("No input instance format defined.");
        }
        //Check operator proportion
        if (operatorProportion[0] + operatorProportion[1] != 1)
        {
            throwExp("The total value of the operator proportions specified must equal 1");
        }

        //Get the input instances
        Instances inputInstances = getInputFormat();

        //Create a java.util.Random object to guarantee the same pseudo-random numbers for a given seed;
        // this gives repeatable experiments (required by WEKA and is more scientific)
        Random rand = new Random(seed);

        //Print some output to the console (For info)
        String outputStr = "----------------------------------------------------------------------------"
            + NEW_LINE + "GP System Run" + NEW_LINE + NEW_LINE;

        //Add the option params used to the output str
        if (this instanceof OptionHandler)
        {
            String [] options = ((OptionHandler)this).getOptions();
            for (int i = 0; i < options.length; i++)
            {
                outputStr += options[i].trim() + " ";
            }
        }
        outputStr = outputStr + NEW_LINE + NEW_LINE + "Gen.\tTree Acc.\t\tRuntime";
        System.out.print(outputStr);

        //Create a new GP System object then run it to do the GP run
        GPSystem gpSystem =
                new GPSystem(inputInstances, popSize, maxNumGenerations, maxTime, targetTreeAccuracy, f_Functions, maxDepth,
                        operatorProportion, popGenerationMethod, rand, fitnessEvaluationMethod, selectionMethod, m_Classifier, seed);

        gpSystem.run();

        //Get the results of the run (Instances and OutputFormat) and set them to the correct methods
        //Set the output format; Adds fittest individuals as an attribute to the output format
        Instances outFormat = new Instances(getInputFormat(), 0);
        fittestIndividuals = gpSystem.getSelectedIndividuals();
        for(String genome : fittestIndividuals)
        {
            outFormat.insertAttributeAt(new Attribute(Parse.formatGenomeString(genome)), outFormat.numAttributes());
        }
        setOutputFormat(outFormat);

        //Add all the new instances to the output queue
        for (int i = 0; i < inputInstances.numInstances(); i++)
        {
            //Place to store new array and get old array values from the inputInstances
            double[] newValues = new double[outFormat.numAttributes()];
            double[] oldValues = inputInstances.get(i).toDoubleArray();

            //copy the old values to the new values
            System.arraycopy(oldValues, 0, newValues, 0, oldValues.length);

            //Add the new values of all of the new attributes (genomes) using the RPN calculator
            // Attributes are inserted AFTER the current set; so must be inserted at c + getInputFormat().numAttributes()
            for(int c = 0; c < fittestIndividuals.size(); c++)
            {
                newValues[c + getInputFormat().numAttributes()] = RPNCalculator.evaluate(fittestIndividuals.get(c), inputInstances.get(i));
            }

            //Push the new instance onto the output dataset
            push(new DenseInstance(1.0, newValues));
        }

        flushInput();
        return (numPendingOutput() != 0);
    }

    public String globalInfo()
    {
        return "This filter implements a genetic programming system to generate attributes suitable for a decision system"
                + " such as J48. J48 is itself used to evolve polynomials of the original attributes that aid J48 in correctly classifying the"
                + " dataset. Multi Objective Optimisation methods NSGA-II and Vector fitness that select on polynomial length, tree size and tree quality"
                + " are also available."
                + " Based on previous work by Koza, Chris Hinde and Kalyanmoy Deb.\n\n"
                + "Koza, J.R. (1992), Genetic Programming: On the Programming of Computers "
		+ "by Means of Natural Selection, MIT Press\n\n"
                + "Deb, K., Agrawal, S., Pratap, A. & Meyarivan, T., (2000). A Fast Elitist Non-Dominated Sorting Genetic Algorithm "
                + "for Multi-Objective Optimization: NSGA-II. Springer.\n\n"
                + "Hinde, C.J., Bani-Hani, A.I., Jackson, T.W., Cheung, Y.P. (2012) Evolving Polynomials of the Inputs for Decision Tree Building."
                + " Journal of Emerging Technologies in Web Intelligence, Vol. 4, No. 2";
    }

    @Override
    public Capabilities getCapabilities()
    {
        Capabilities caps = new Capabilities(this);

        caps.enable(Capabilities.Capability.NOMINAL_CLASS);
        caps.enable(Capabilities.Capability.NUMERIC_ATTRIBUTES);
        caps.enable(Capabilities.Capability.NOMINAL_ATTRIBUTES);

        return caps;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------------------------------
    //  COMMAND LINE OPTION HANDLING
    //------------------------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     *
     * @return Returns an enumeration of all options
     */
    @Override
    public Enumeration listOptions()
    {
        Vector optionList = new Vector<Option>();

        optionList.addElement(new Option("\t" + HELP_POP_SIZE, CMD_POP_SIZE, 1, "-" + CMD_POP_SIZE
                + " <population size>"));
        optionList.addElement(new Option("\t" + HELP_MAX_NUM_GENERATIONS, CMD_MAX_NUM_GENERATIONS, 1,
                "-" + CMD_MAX_NUM_GENERATIONS + " <number of generations>"));
        optionList.addElement(new Option("\t" + HELP_MAX_TIME, CMD_MAX_TIME, 1, "-" + CMD_MAX_TIME
                + " <max time in seconds>"));
        optionList.addElement(new Option("\t" + HELP_TARGET_TREE_ACCURACY, CMD_TARGET_TREE_ACCURACY, 1, "-"
                + CMD_TARGET_TREE_ACCURACY + " <target tree accuracy between 0-1>"));
        optionList.addElement(new Option("\t" + HELP_FUNCTIONS, CMD_FUNCTIONS, 1, "-" + CMD_FUNCTIONS
                + " <f1, f2, f3>"));
        optionList.addElement(new Option("\t" + HELP_MAX_DEPTH, CMD_MAX_DEPTH, 1, "-" + CMD_MAX_DEPTH
                + " <max depth of program trees>"));
        optionList.addElement(new Option("\t" + HELP_OPERATOR_PROP, CMD_OPERATOR_PROP, 1, "-"
                + CMD_OPERATOR_PROP + " <proportion of operators>"));
        optionList.addElement(new Option("\t" + HELP_POP_GEN_METHOD, CMD_POP_GEN_METHOD, 1, "-"
                + CMD_POP_GEN_METHOD + " <H-H&H; G-Grow, F-Full>"));
        optionList.addElement(new Option("\t" + HELP_FITNESS_EVAL, CMD_FITNESS_EVAL, 1, "-"
                + CMD_FITNESS_EVAL + " <C-J48>"));
        optionList.addElement(new Option("\t" + HELP_SELECTION_METHOD, CMD_SELECTION_METHOD, 1, "-"
                + CMD_SELECTION_METHOD + " <S-Single Fitness; N-NSGAII; V-Vector Fitness>"));
        optionList.addElement(new Option("\t" + HELP_SEED, CMD_SEED, 1, "-"
                + CMD_SEED + " <seed>"));

        return optionList.elements();
    }

    /**
     * Sets the options via the command line
     *
     * @param options A parameter and an argument are always two elements in the array
     * @throws Exception If an option is not supported
     */
    @Override
    public void setOptions(String[] options) throws Exception
    {
        //Population size
        String optionString = Utils.getOption(CMD_POP_SIZE, options);
        if (optionString.length() != 0)
        {
            setPopulationSize(Integer.parseInt(optionString));
        }

        //Number of generations
        optionString = Utils.getOption(CMD_MAX_NUM_GENERATIONS, options);
        if(optionString.length() != 0)
        {
            setNumberOfGenerations(Integer.parseInt(optionString));
        }

        //Max processing time
        optionString = Utils.getOption(CMD_MAX_TIME, options);
        if(optionString.length() != 0)
        {
            setMaxTime(Integer.parseInt(optionString));
        }

        //Target fitness (double)
        optionString = Utils.getOption(CMD_TARGET_TREE_ACCURACY, options);
        if(optionString.length() != 0)
        {
            setTargetTreeAccuracy(Double.parseDouble(optionString));
        }

        //Functions
        optionString = Utils.getOption(CMD_FUNCTIONS, options);
        if(optionString.length() != 0)
        {
            setFunctions(optionString);
        }

        //Max depth
        optionString = Utils.getOption(CMD_MAX_DEPTH, options);
        if(optionString.length() != 0)
        {
            setMaxDepth(Integer.parseInt(optionString));
        }

        //Operator proportion
        optionString = Utils.getOption(CMD_OPERATOR_PROP, options);
        if(optionString.length() != 0)
        {
            setOperatorProportion(optionString);
        }

        //Population generation method
        optionString = Utils.getOption(CMD_POP_GEN_METHOD, options);
        if(optionString.length() != 0)
        {
            setPopulationGenerationMethod(new SelectedTag(optionString, OPT_TAGS_POP_GEN));
        }

        //Fitness evaluation
        optionString = Utils.getOption(CMD_FITNESS_EVAL, options);
        if(optionString.length() != 0)
        {
            setFitnessEvaluationMethod(new SelectedTag(optionString, OPT_TAGS_FIT_EVAL));
        }

        //Selection Method
        optionString = Utils.getOption(CMD_SELECTION_METHOD, options);
        if(optionString.length() != 0)
        {
            setSelectionMethod(new SelectedTag(optionString, OPT_TAGS_SEL_METHOD));
        }

        //Seed
        optionString = Utils.getOption(CMD_SEED, options);
        if(optionString.length() != 0)
        {
            setSeed(Long.parseLong(optionString));
        }

        //Inform the filter (superclass) about changes to the options
        if (getInputFormat() != null)
        {
            setInputFormat(getInputFormat());
        }
    }

    /**
     * Gets the options currently set for the class
     * @return A string array of command line options for the filter and the super-class
     */
    @Override
    public String[] getOptions()
    {
        ArrayList<String> optionList = new ArrayList<String>();
        optionList.add("-" + CMD_POP_SIZE);
        optionList.add("" + getPopulationSize());
        optionList.add("-" + CMD_MAX_NUM_GENERATIONS);
        optionList.add("" + getNumberOfGenerations());
        optionList.add("-" + CMD_MAX_TIME);
        optionList.add("" + getMaxTime());
        optionList.add("-" + CMD_TARGET_TREE_ACCURACY);
        optionList.add("" + getTargetTreeAccuracy());
        optionList.add("-" + CMD_FUNCTIONS);
        optionList.add("" + getFunctions());
        optionList.add("-" + CMD_MAX_DEPTH);
        optionList.add("" + getMaxDepth());
        optionList.add("-" + CMD_OPERATOR_PROP);
        optionList.add("" + getOperatorProportion());
        optionList.add("-" + CMD_POP_GEN_METHOD);
        optionList.add("" + getPopulationGenerationMethod().getSelectedTag().getIDStr());
        optionList.add("-" + CMD_FITNESS_EVAL);
        optionList.add("" + getFitnessEvaluationMethod().getSelectedTag().getIDStr());
        optionList.add("-" + CMD_SELECTION_METHOD);
        optionList.add("" + getSelectionMethod().getSelectedTag().getIDStr());
        optionList.add("-" + CMD_SEED);
        optionList.add("" + getSeed());

        return optionList.toArray(new String[optionList.size()]);
    }

    //-----------------------------------------------------------------------------------------------------------------------------------------------
    // GUI OPTION HANDLING
    //-----------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Checks whether supplied value is valid and updates popSize if it is; throws exception if not
     * @param pSize The chosen population size
     * @throws Exception The specified value is invalid
     */
    public void setPopulationSize(int pSize) throws Exception
    {
        popSize = checkNum(pSize, DEFAULT_POP_SIZE, 1, 100000, "Population size");
    }

    /**
     * Returns the property
     * @return The population size
     */
    public int getPopulationSize()
    {
        return popSize;
    }

    /**
     * Returns the help text for the GUI.
     * @return PopulationSize help text
     */
    public String populationSizeTipText()
    {
        return HELP_POP_SIZE;
    }

    /**
     * Checks whether supplied value is valid and updates maxNumGenerations if it is; throws exception if not
     * @param gens The chosen number of generations to run
     * @throws Exception The specified value is invalid
     */
    public void setNumberOfGenerations(int gens) throws Exception
    {
        maxNumGenerations = checkNum(gens, DEFAULT_MAX_NUM_GENERATIONS, 1, 100000000, "Number of generations");
    }
    /**
     * Returns the property
     * @return The number of generations
     */
    public int getNumberOfGenerations()
    {
        return maxNumGenerations;
    }
    /**
     * Returns the help text for the GUI.
     * @return the help text for the GUI.
     */
    public String numberOfGenerationsTipText()
    {
        return HELP_MAX_NUM_GENERATIONS;
    }

    /**
     * Checks whether supplied value is valid and updates maxTime if it is; throws exception if not
     * @param maxtime The max time
     * @throws Exception The specified value is invalid
     */
    public void setMaxTime(int maxtime) throws Exception
    {
        maxTime = checkNum(maxtime, DEFAULT_MAX_TIME, 1, 100000000, "Max processing time");
    }
    /**
     * Returns the property
     * @return The max time
     */
    public int getMaxTime()
    {
        return maxTime;
    }
    /**
     * Returns the help text for the GUI.
     * @return the help text for the GUI.
     */
    public String maxTimeTipText()
    {
        return HELP_MAX_TIME;
    }

    /**
     * Checks whether supplied value is valid and updates targetTreeAccuracy if it is; throws exception if not
     * @param treeAccuracy The target accuracy
     * @throws Exception The specified value is invalid
     */
    public void setTargetTreeAccuracy(double treeAccuracy) throws Exception
    {
        targetTreeAccuracy = checkNum(treeAccuracy, DEFAULT_TARGET_TREE_ACCURACY, 0.0, 1.1, "Target Tree Accuracy");
    }
    /**
     * Returns the property
     * @return The target fitness
     */
    public double getTargetTreeAccuracy()
    {
        return targetTreeAccuracy;
    }
    /**
     * Returns the help text for the GUI.
     * @return the help text for the GUI.
     */
    public String targetTreeAccuracyTipText()
    {
        return HELP_TARGET_TREE_ACCURACY;
    }
    /**
     * Sets the property
     * @param func The functions
     */
    public void setFunctions(String func)
    {
        //Gets chosen functions from string, ensures they are on the ALL_FUNCTIONS list and puts them in the functions Set
        // (duplicates are ignored by the java.util.Set object)
        String[] funcArr = func.split(",");
        //Only add "supported" functions, and ignore duplicates
        f_Functions.removeAll(f_Functions);
        for(String f : funcArr)
        {
            if(Function.isValid(f) && !f_Functions.contains(f))
            {
                f_Functions.add(f);
            }
            else
            {
                System.err.println("The function is not supported or you have entered a duplicate function");
            }
        }
    }
    /**
     * Returns the property
     * @return The functions
     */
    public String getFunctions()
    {
        String retStr = "";

        for (String func : f_Functions)
        {
            retStr += func + ",";
        }
        retStr = retStr.substring(0, retStr.length() - 1);  //Remove the last comma
        return retStr;
    }
    /**
     * Returns the help text for the GUI.
     * @return the help text for the GUI.
     */
    public String functionsTipText()
    {
        return HELP_FUNCTIONS;
    }
    /**
     * Sets the property
     * @param func The max depth
     */
    public void setMaxDepth(int depth) throws Exception
    {
        maxDepth = checkNum(depth, DEFAULT_MAX_DEPTH, 1, 10000, "max depth");
    }
    /**
     * Returns the property
     * @return The max depth
     */
    public int getMaxDepth()
    {
        return maxDepth;
    }
    /**
     * Returns the help text for the GUI.
     * @return the help text for the GUI.
     */
    public String maxDepthTipText()
    {
        return HELP_MAX_DEPTH;
    }

    /**
     * Sets the property
     * @param ops The operators to set
     */
    public void setOperatorProportion(String ops) throws Exception
    {
        //split the string to get it's component parts
        String tempArr[] = ops.split(",");
        //Check numbers are valid
        double crossNum = checkNum(Double.parseDouble(tempArr[0]), DEFAULT_OPERATOR_PROP[0], 0.0, 1.0, "Crossover proportion");
        double mutNum = checkNum(Double.parseDouble(tempArr[1]), DEFAULT_OPERATOR_PROP[1], 0.0, 1.0, "Mutation proportion");

        //put them into the array containing the proportions
        operatorProportion[0] = crossNum;
        operatorProportion[1] = mutNum;
    }
    /**
     * Returns the property
     * @return The max depth
     */
    public String getOperatorProportion()
    {
        String retStr = operatorProportion[0] + "," + operatorProportion[1];

        return retStr;
    }
    /**
     * Returns the help text for the GUI.
     * @return the help text for the GUI.
     */
    public String operatorProportionTipText()
    {
        return HELP_OPERATOR_PROP;
    }

    /**
     * Sets the property
     * @param popGenTag The initial generation to use
     */
    public void setPopulationGenerationMethod(SelectedTag popGenTag)
    {
        if (popGenTag.getTags() == OPT_TAGS_POP_GEN)
        {
            if(popGenTag.getSelectedTag().getIDStr().equalsIgnoreCase("H")) {
                popGenerationMethod = EnumGenerationMethod.RAMPED_HALF_HALF; }
            else if(popGenTag.getSelectedTag().getIDStr().equalsIgnoreCase("F")) {
                popGenerationMethod = EnumGenerationMethod.FULL; }
            else if(popGenTag.getSelectedTag().getIDStr().equalsIgnoreCase("G")) {
                popGenerationMethod = EnumGenerationMethod.GROW; }
            else { popGenerationMethod = DEFAULT_POP_GEN_METHOD; }
        }
    }
    /**
     * Returns the property
     * @return The population generation method
     */
    public SelectedTag getPopulationGenerationMethod()
    {
        switch(popGenerationMethod)
        {
            case GROW: return new SelectedTag(OPT_POP_GEN_GROW, OPT_TAGS_POP_GEN);
            case FULL: return new SelectedTag(OPT_POP_GEN_FULL, OPT_TAGS_POP_GEN);
            default: return new SelectedTag(OPT_POP_GEN_HAH, OPT_TAGS_POP_GEN);
        }
    }
    /**
     * Returns the help text for the GUI
     * @return help text for the GUI
     */
    public String populationGenerationMethodTipText()
    {
        return HELP_POP_GEN_METHOD;
    }

    /**
     * @return the fitnessEvaluationMethod as a string
     */
    public SelectedTag getFitnessEvaluationMethod()
    {
        switch(fitnessEvaluationMethod)
        {
            default: return new SelectedTag(OPT_FIT_EVAL_CLASSIFIER, OPT_TAGS_FIT_EVAL);
        }
    }

    /**
     * @param fitnessEvaluationMethod the fitnessEvaluationMethod to set
     */
    public void setFitnessEvaluationMethod(SelectedTag evalMethod)
    {
        if (evalMethod.getTags() == OPT_TAGS_FIT_EVAL)
        {
            if(evalMethod.getSelectedTag().getIDStr().equalsIgnoreCase("C")) {
                fitnessEvaluationMethod = EnumFitnessEvaluationMethod.J48; }
            else { fitnessEvaluationMethod = DEFAULT_FITNESS_EVAL_METHOD; }
        }
    }
    /**
     * Fitness evaluation method help text
     * @return Fitness evaluation method help text
     */
    public String fitnessEvaluationMethodTipText()
    {
        return HELP_FITNESS_EVAL;
    }

    /**
     * @return the selectionMethod
     */
    public SelectedTag getSelectionMethod()
    {
        switch(selectionMethod)
        {
            case NSGAII: return new SelectedTag(OPT_SEL_METHOD_NSGAII, OPT_TAGS_SEL_METHOD);
            case VECTOR_FITNESS: return new SelectedTag(OPT_SEL_METHOD_VECTOR_FITNESS, OPT_TAGS_SEL_METHOD);
            default: return new SelectedTag(OPT_SEL_METHOD_SINGLE_FITNESS, OPT_TAGS_SEL_METHOD);
        }
    }

    /**
     * @param selMethod the selectionMethod to set
     */
    public void setSelectionMethod(SelectedTag selMethod)
    {
        if(selMethod.getTags() == OPT_TAGS_SEL_METHOD)
        {
            if(selMethod.getSelectedTag().getIDStr().equalsIgnoreCase("S")) {
                selectionMethod = EnumSelectionMethod.SINGLE_FITNESS; }
            else if(selMethod.getSelectedTag().getIDStr().equalsIgnoreCase("N")) {
                selectionMethod = EnumSelectionMethod.NSGAII; }
            else if(selMethod.getSelectedTag().getIDStr().equalsIgnoreCase("V")) {
                selectionMethod = EnumSelectionMethod.VECTOR_FITNESS; }
            else { selectionMethod = DEFAULT_SELECTION_METHOD; }
        }
    }
    /**
     * selection method tip text
     * @return selection method tip text
     */
    public String selectionMethodTipText()
    {
        return HELP_SELECTION_METHOD;
    }
    /**
     * Get the seed
     * @return the seed
     */
    public long getSeed()
    {
        return seed;
    }
    /**
     * Set the seed value for the random number generator
     * @param seed The specified seed value
     */
    public void setSeed(long seed)
    {
        this.seed = seed;
    }
    /**
     * Seed tip text for the GUI
     * @return Seed tip text
     */
    public String seedTipText()
    {
        return HELP_SEED;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------------------------------
    // PRIVATE METHODS
    //------------------------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Checks an integer is between min and max numbers and returns default if not
     *
     * @param value The value to test
     * @param defaultVal The default value
     * @param min The minimum value
     * @param max The maximum value
     * @param optionName The parameter name
     * @return The value, or the default if the given value is invalid
     * @throws Exception Thrown by throwExp
     */
    private int checkNum(int value, int defaultVal, int min, int max, String optionName) throws Exception
    {
        if(value > max || value < min)
        {
            throwExp("Value for " + optionName + " must be between " + min + " and " + max);
            return defaultVal;
        }
        else
        {
            return value;
        }
    }

     /**
     * Checks a double is between min and max numbers and returns default if not
     *
     * @param value The value to test
     * @param defaultVal The default value
     * @param min The minimum value
     * @param max The maximum value
     * @param optionName The parameter name
     * @return The value, or the default if the given value is invalid
     * @throws Exception Thrown by throwExp
     */
    private double checkNum(double value, double defaultVal, double min, double max, String optionName) throws Exception
    {
        if(value > max || value < min)
        {
            throwExp("Value for " + optionName + " must be between " + min + " and " + max);
            return defaultVal;
        }
        else
        {
            return value;
        }
    }

    /**
     * Method to throw exceptions
     *
     * @param exp The exception string
     * @throws Exception General exception thrown with informative error message
     */
    private void throwExp(String exp) throws Exception
    {
        throw new Exception(exp);
    }

    /**
     * Initialises and returns a J48 tree cast as a Classifier with required options
     * @return J48 classifier with required options
     */
    private static Classifier initialiseJ48()
    {
        weka.classifiers.trees.J48 j48 = new weka.classifiers.trees.J48();
        //j48.setMinNumObj(1);
        return (Classifier)j48;
    }

}
