package weka.filters.supervised.attribute.gpattributegeneration;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Random;
import weka.classifiers.Classifier;
import weka.core.Instances;

/**
 * Starts a complete run of the GP system
 * @author Colin Noakes
 */
public class GPSystem
{
    //CONSTANTS
    /**
     * Platform independent new line char. Rather this (where typos will be picked up) than the
     * System.getProperty("line.separator") call everywhere in the code....
     */
    private static final String NEW_LINE = System.getProperty("line.separator");

    //FIELDS
    private Instances data;  //Dataset to build classifier with
    private int popSize;
    private int maxNumGenerations;  //Maximum number of generations
    private int maxTime;  //Maximum amount of time (in seconds)
    private double targetFitness;  //Target fitness (algorithm will stop when this is reached); value between 0-1
    private ArrayList<String> functions;  //ArrayList of functions to use in GP
    private int maxDepth;  //Max depth of a program tree
    private double[] operatorProportion;  //The proportion of operator to use (should equal 1): crossover, mutation
    private EnumGenerationMethod popGenerationMethod;
    private Random rand;  //Stores Random object
    private Population population; //Stores current population object list; ensures the last population is still in memory after run() finishes
    private Timer timer;  //Stores a timer object to time execution time
    private int genCount;  //Stores number of generations actually ran
    private EnumFitnessEvaluationMethod evalMethod;  //Stores fitness evaluation method being used
    private EnumSelectionMethod selectionMethod;  //Stores selection method being used
    private Classifier classifier; //The classifier to use for fitness evaluation
    private String fittestTreeString;  //Stores the tree string with the best score to present back to the User at the end
    private double currentFittestTreeFitness;  //Max fitness so far to determine when to replace tree
    private long seed; //The seed the Rand object started with (only for results output)
    /**
     * String to output as result of 'toString()' call
     */
    private String resultString = NEW_LINE;

    /**
     * Constructor to pass in data and variables for the GP system
     *
     * @param instances Data instances to run thru GP system
     * @param popSize Initial population size
     * @param numOfGenerations Number of generations to go through before stopping
     * @param maxTime Max time the GP system can run for before stopping
     * @param targetFitness Target fitness to stop at
     * @param functions Functions that can be used as nodes of the program trees
     * @param maxDepth Maximum depth of program trees
     * @param operatorProp Operator proportion to use
     * @param popGenMethod Method to use to generate the population
     * @param evalMethod Fitness evaluation method to use
     * @param selMethod Selection method to use
     * @param classifier The classifier to use for fitness evaluation
     * @param seed The seed the Rand object started with (for results output)
     */
    public GPSystem(Instances instances,
            int popSize,
            int maxNumGenerations,
            int maxTime,
            double targetFitness,
            ArrayList<String> functions,
            int maxDepth,
            double[] operatorProp,
            EnumGenerationMethod popGenMethod,
            Random rand,
            EnumFitnessEvaluationMethod evalMethod,
            EnumSelectionMethod selMethod,
            Classifier classifier,
            long seed)
    {
        //Assign parameters to variables
        this.data = instances;
        this.popSize = popSize;
        this.maxNumGenerations = maxNumGenerations;
        this.maxTime = maxTime;
        this.targetFitness = targetFitness;
        this.functions = functions;
        this.maxDepth = maxDepth;
        this.operatorProportion = operatorProp;
        this.popGenerationMethod = popGenMethod;
        this.rand = rand;
        this.evalMethod = evalMethod;
        this.selectionMethod = selMethod;
        this.classifier = classifier;
        this.seed = seed;
    }

    /**
     * Runs the GP System using the previously specified data
     */
    public void run()
    {
        timer = new Timer();  //Start the timer
        timer.startSplit();  //Start a split to measure the first generation runtime

        //FIRST GP GENERATION
        //Generate a new population and calculate its fitness
        population = new Population(popGenerationMethod, rand, functions, popSize, maxDepth, validAttributes(data));
        population.calculateFitness(evalMethod, data, classifier);
        //Apply NSGA-II if selected
        if(selectionMethod == EnumSelectionMethod.NSGAII)
        {
            population.calculateNsgaii();
        }

        //Output the results of the first generation
        outputGenResult();
        //Check if maxFitness is greater than old maxFitness, if so, replace the tree string
        if(population.getMaxTreeAccuracy() > currentFittestTreeFitness)
        {
            fittestTreeString = classifier.toString();
            currentFittestTreeFitness = population.getMaxTreeAccuracy();
        }

        //SUBSEQUENT GENERATION LOOP
        //Main GP System loop; loops until any of the termination conditions are met
        while (!isTerminateCondMet())
        {
            timer.startSplit();  //Start a new split for this generation
            Epoch epoch = new Epoch();
            population = epoch.runEpoch(population, evalMethod, data, operatorProportion,
                    rand, functions, selectionMethod, classifier, validAttributes(data));
            genCount++;

            //Output results of the generation
            outputGenResult();

            //Check if maxFitness is greater than old maxFitness, if so, replace the tree string
            if(population.getMaxTreeAccuracy() > currentFittestTreeFitness)
            {
                fittestTreeString = classifier.toString();
                currentFittestTreeFitness = population.getMaxTreeAccuracy();
            }
        }

        timer.stop();  //Stop the timer
        addFinalInfoToResultStr();
    }

    /**
     * Outputs the results of a GP System run
     * @return Results of a GP System run as a string
     */
    @Override
    public String toString()
    {
        return resultString;
    }

    /**
     * Returns the selected individuals from the population. Returns only unique elements.
     * @return The selected individuals from the population
     */
    public ArrayList<String> getSelectedIndividuals()
    {
        //Parse the fittest tree and return the elements in it
        ArrayList<String> individuals = new ArrayList<String>(Parse.parseJ48(fittestTreeString).keySet());
        //Loop thru keys to remove "GP:  " prepend
        for(int i = 0; i < individuals.size(); i++)
        {
            individuals.set(i, individuals.get(i).substring(3, individuals.get(i).length()).trim());
        }
        return individuals;
    }

    /**
     * Checks if termination conditions (target fitness, maximum time, maximum number of generations) have been met
     * @return True if they have; False if not
     */
    private boolean isTerminateCondMet()
    {
        //boolean to determine if the termination conditions have been met
        if(population.getMaxTreeAccuracy() >= targetFitness || timer.getElapsedSeconds() > (double)maxTime || genCount >= maxNumGenerations)
        {
            return true;
        }
        return false;
    }

    /**
     * Outputs generation results to the console.
     */
    private void outputGenResult()
    {
        NumberFormat formatter = new DecimalFormat("#0.000000");
        System.out.print(NEW_LINE + genCount + "\t" + formatter.format(population.getMaxTreeAccuracy())
                + "\t" + timer.getElapsedSinceSplitSeconds());
    }

    /**
     * Adds last set of info the result string at the end of a GP run.
     * Includes a print out of the last tree if J48 was chosen as the fitness evaluation method
     */
    private void addFinalInfoToResultStr()
    {

        resultString = resultString + NEW_LINE + "Total Time: " + timer.getElapsedSeconds() + "s" + NEW_LINE;

        int numInds = getSelectedIndividuals().size();  //Number of selected individuals

        //Format string for an easy copy and paste into excel
        resultString = resultString + NEW_LINE + "Excel Row: seed, genCount, treeAcc, num sel ind, mean Ind length, tree size, runtime"
                + NEW_LINE + "ExcelRes:\t" + seed + "\t \t" + genCount + "\t" + population.getMaxTreeAccuracy() + "\t"
                + numInds + "\t" + meanFitIndividualLength() + "\t" + Parse.getTreeSize(fittestTreeString)
                + "\t" + timer.getElapsedSeconds() + NEW_LINE;

        System.out.print(resultString);
    }

    /**
     * Calculate the mean individual length
     * @return double mean length
     */
    private double meanFitIndividualLength()
    {
        int sumLength = 0;
        //add the length of all the individuals
        for(String individual : getSelectedIndividuals())
        {
            sumLength = sumLength + individual.trim().split(" ").length;
        }

        return (double) sumLength / (double) getSelectedIndividuals().size();
    }

    /**
     * Returns a list of numeric attribute indeces that can be used for the GP run, as an array.
     * This removes the class attribute and other attributes that cannot be used in polynomials.
     * @return An array of valid attributes
     */
    private static int[] validAttributes(Instances data)
    {
        //largest possible number of valid attributes is number of attributes already there
        int[] tempValidAttrs = new int[data.numAttributes()];
        int freeElement = 0;  //stores the next free element

        //Get every attribute, if they are numeric add the index to the validAttribute array
        for(int i = 0; i < data.numAttributes(); i++)
        {
            if(data.attribute(i).isNumeric())
            {
                tempValidAttrs[freeElement] = i;
                freeElement++;
            }
        }

        //resize the array to be the right length (so that array length property is accurate)
        int[] valAttrs = new int[freeElement];
        System.arraycopy(tempValidAttrs, 0, valAttrs, 0, freeElement);

        return valAttrs;
    }
}
