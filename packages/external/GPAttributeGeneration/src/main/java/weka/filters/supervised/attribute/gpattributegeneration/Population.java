package weka.filters.supervised.attribute.gpattributegeneration;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import weka.attributeSelection.AttributeSelection;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Add;

/**
 * Population class to store a list of individuals in an ArrayList as well as other fields
 *
 * @author Colin Noakes
 */
public class Population
{
    //FIELDS
    /**
     * Int to store the current maxDepth when generating trees; gets changed when using ramped half and half.
     */
    private int maxDepth = 5;
    /**
     * Max (best) tree accuracy of the population.
     */
    private double maxTreeAccuracy = 0.0;
    /**
     * Min (worst) tree accuracy of the population.
     */
    private double minTreeAccuracy = 0.0;
    /**
     * Max length of any genome in the population.
     */
    private int maxLength = 0;
    /**
     * Min length of any genome in the population.
     */
    private int minLength = 0;

    private double maxVectorFitness = 0;

    private double minVectorFitness = 0;

    private int maxTreeSize = 0;

    private int minTreeSize = 0;


    /**
     * The arraylist containing the actual population genomes
     */
    private ArrayList<Genome<Gene>> population;
    /**
     * Stores the NSGA-II object for this population
     */
    private NSGAII nsgaii;

    /**
     * Create an empty population
     */
    public Population()
    {
        this.population = new ArrayList<Genome<Gene>>();
    }
    /**
     * Clones the given population in the process creating a new one. This is a DEEP CLONE, and copies all array list elements.
     * @param popClonee Population object to clone
     */
    public Population(Population popClonee)
    {
        //Copies fields
        this.maxDepth = popClonee.maxDepth;
        this.maxTreeAccuracy = popClonee.maxTreeAccuracy;
        this.maxLength = popClonee.maxLength;
        this.minTreeAccuracy = popClonee.minTreeAccuracy;
        this.minLength = popClonee.minLength;
        this.minTreeSize = popClonee.minTreeSize;
        this.maxTreeSize = popClonee.maxTreeSize;
        this.maxVectorFitness = popClonee.maxVectorFitness;
        this.minVectorFitness = popClonee.minVectorFitness;

        //Deep copy the population array
        this.population = new ArrayList<Genome<Gene>>();
        //Iterate thru the given population and create a cloned individual in this object
        for (Genome<Gene> genome : popClonee.population)
        {
            this.population.add(new Genome<Gene>(genome));
        }
    }
    /**
     * Copy (SHALLOW) the given ArrayList into a new population
     * @param popList Population list to copy into the new population
     */
    public Population(ArrayList<Genome<Gene>> popList)
    {
        //shallow copy the population array
        this.population = popList;
    }
    /**
     * Generates a population with the specified generation method and size, using the specified Random object and funcSet.
     * @param genMethod Generation method to use to create the population
     * @param rand Random object to create pseudo-random numbers
     * @param funcSet Function set to use for initialising the population
     * @param popSize The size to make the population
     * @param validAttributes the valid numeric attributes that can be used as part of equations
     */
    public Population(EnumGenerationMethod genMethod, Random rand, ArrayList<String> funcSet, int popSize, int maxDepth, int[] validAttributes)
    {
        this.maxDepth = maxDepth;
        if (genMethod == EnumGenerationMethod.RAMPED_HALF_HALF)
        {
            this.population = rampedHalfandHalf(rand, popSize, funcSet, validAttributes);
        }
        else
        {
            this.population = fullGrow(rand, popSize, funcSet, genMethod, validAttributes);
        }
    }
    /**
     * Appends all of the given individuals in the ArrayList to this population.
     * Updates Min/Max fitness and length fields
     * @param pop Individuals to combine with this population
     */
    public void addAll(Population pop)
    {
        this.population.addAll(pop.population);
        sort(EnumSortingCriteria.LENGTH);
        sort(EnumSortingCriteria.FITNESS);
    }
    /**
     * Return the fittest individuals (as a string) in an ArrayList
     * @return The fittest genomes in an ArrayList
     */
    public ArrayList<String> getFittestIndividuals()
    {
        //Sort on fitness first
        sort(EnumSortingCriteria.FITNESS);
        //Put all the genomes that are equal to the minFitness in the ArrayList
        ArrayList<String> bestIndividuals = new ArrayList<String>();
        for(Genome<Gene> genome : population)
        {
            if(genome.getTreeAccuracy() == maxTreeAccuracy)
            {
                bestIndividuals.add(genome.toString());
            }
        }

        return bestIndividuals;
    }
    /**
     * Return the tree accuracy of the best individual(s) in the population.
     * @return The maximum tree accuracy of the population
     */
    public double getMaxTreeAccuracy()
    {
        return maxTreeAccuracy;
    }
    /**
     * Return the tree accuracy of the worst individual(s) in the population.
     * @return The minimum tree accuracy of the population
     */
    public double getMinTreeAccuracy()
    {
        return minTreeAccuracy;
    }
    /**
     * Max length of the genomes in the population
     * @return the maxLength
     */
    public int getMaxLength()
    {
        return maxLength;
    }
    /**
     * Min length of the genomes in the population
     * @return the minLength
     */
    public int getMinLength()
    {
        return minLength;
    }
    /**
     * Return max tree size
     * @return max tree size
     */
    public int getMaxTreeSize()
    {
        return maxTreeSize;
    }
    /**
     * Return min tree size
     * @return min tree size
     */
    public int getMinTreeSize()
    {
        return minTreeSize;
    }
    /**
     * Return max tree size
     * @return max tree size
     */
    public double getMaxVectorFitness()
    {
        return maxVectorFitness;
    }
    /**
     * Return min tree size
     * @return min tree size
     */
    public double getMinVectorFitness()
    {
        return minVectorFitness;
    }


    /**
     * Returns the size of the population
     * @return Size of the population
     */
    public int size()
    {
        return population.size();
    }
    /**
     * Returns the genome in the population at that index.
     * @param index Index of the element to return
     * @return Genome currently stored at index
     */
    public Genome<Gene> get(int index)
    {
        return population.get(index);
    }

    /**
     * Removes from the population all of the elements whose index is between fromIndex, inclusive, and toIndex, exclusive.
     * Shifts any succeeding individuals to the left (reduces their index).
     * This call shortens the list by (toIndex - fromIndex) individuals. (If toIndex==fromIndex, this operation has no effect.)
     * @param fromIndex index of the first individual to be removed
     * @param toIndex index after the last individual to be removed
     */
    public void removeRange(int fromIndex, int toIndex)
    {
        try
        {
            population.subList(fromIndex, toIndex).clear();
        } catch (Exception ex) {Logger.getLogger(Population.class.getName()).log(Level.SEVERE, null, ex);  }
    }

    /**
     * Sorts the elements of the population list by the specified sorting criteria. Uses QuickSort.
     * Updates relevant max/min fields after a sort is complete.
     * @param sortBy Sorting Criteria to use when sorting
     */
    public void sort(EnumSortingCriteria sortBy)
    {
        if (sortBy == EnumSortingCriteria.FITNESS)
        {
            if (!population.isEmpty())
            {
                quickSortTreeAccuracy(0, population.size() - 1);
            }
            //Reverse the arraylist so it's in the right order
            Collections.reverse(population);
            //Update fields
            maxTreeAccuracy = population.get(0).getTreeAccuracy();  //Get first element fitness - best individual
            minTreeAccuracy = population.get(population.size() - 1).getTreeAccuracy();  //Get last element fitness - worst individual
        }
        else if (sortBy == EnumSortingCriteria.LENGTH)
        {
            if (!population.isEmpty())
            {
                quickSortLength(0, population.size() - 1);
            }
            //Update fields
            minLength = population.get(0).getLength();  //Get first element length - shortest/GOOD
            maxLength = population.get(population.size() - 1).getLength();  //Get last element length - longest/BAD
        }
        else if(sortBy == EnumSortingCriteria.CROWDING_DISTANCE)
        {
            if (!population.isEmpty())
            {
                quickSortCrowdingDistance(0, population.size() - 1);
            }
            //Reverse the arraylist so it's in the right order
            Collections.reverse(population);
        }
        else if (sortBy == EnumSortingCriteria.NSGAII)
        {
            population.clear();  //Clear the population first
            //Loop thru all the fronts and add them to the population
            for(ArrayList<Genome<Gene>> front : nsgaii.getNonDomFronts())
            {
                population.addAll(front);
            }
        }
        else if (sortBy == EnumSortingCriteria.VECTOR_FITNESS)
        {
            if (!population.isEmpty())
            {
                quickSortVectorFitness(0, population.size() - 1);
            }
            //Reverse the arraylist so it's in the right order
            Collections.reverse(population);
            //Update fields
            maxVectorFitness = population.get(0).getVectorFitness();  //Get first element fitness - best individual
            minVectorFitness = population.get(population.size() - 1).getVectorFitness();  //Get last element fitness - worst individual
        }
        else if (sortBy == EnumSortingCriteria.TREE_SIZE)
        {
            if (!population.isEmpty())
            {
                quickSortTreeSize(0, population.size() - 1);
            }
            //Update fields
            minTreeSize = population.get(0).getTreeSize();  //Get first element length - shortest/GOOD
            maxTreeSize = population.get(population.size() - 1).getTreeSize();  //Get last element length - longest/BAD
        }
    }

    /**
     * Calculates this population's fitness using the specified Classifier and fitness evaluation method.
     * Also sorts the population in fitness order. Uses standardised fitness value, where 0 is the best fitness.
     * @param evalMethod Fitness evaluation method to use
     * @param data Data to use when calculating fitness
     * @param classifier Classifier to use for fitness evaluation
     */
    public void calculateFitness(EnumFitnessEvaluationMethod evalMethod, Instances data, Classifier classifier)
    {
        //Add each individual GP as an 'attribute' to the dataset;
        // applying the GP equation/polynomial to the attributes
        Instances gpDataset = addAttributes(data);

        if(evalMethod == EnumFitnessEvaluationMethod.J48)
        {
            calculateJ48Fitness(gpDataset, classifier);
        }

        //Set max/min fitness fields for the population using the sort algorithms
        //LENGTH
        this.sort(EnumSortingCriteria.LENGTH);

        //FITNESS
        this.sort(EnumSortingCriteria.FITNESS);

        //Leave the array sorted by fitness - this is probably what we want anyway!
    }

    /**
     * Calculates NSGA-II fronts for this population and updates genomes with domination rank and crowding distance
     */
    public void calculateNsgaii()
    {
        this.nsgaii = new NSGAII(this);
    }

    /**
     * Return the non dominated fronts (calculateNsgaii() must have been run beforehand)
     * @return non dominated fronts
     */
    public ArrayList<ArrayList<Genome<Gene>>> getNonDomFronts()
    {
        return this.nsgaii.getNonDomFronts();
    }

    //----------------------------------------------------------------------------------------------------------------------------------------
    //      PRIVATE METHODS
    //----------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Calculates the fitness of each genome in this population using the J48 classifier.
     * @param gpDataset The dataset with the additional GP attributes
     * @param classifier The classifier to use (should be of type J48)
     */
    private void calculateJ48Fitness(Instances gpDataset, Classifier classifier)
    {
        try
        {
            //Run dataset thru classifier using cross-validation
            Evaluation eval = new Evaluation(gpDataset);
            eval.crossValidateModel(classifier, gpDataset, 10, new Random(1));

            //Get the stats we need from the evaluator (as actual %age)
            double pctCorrect = eval.pctCorrect() / 100;

            //Get a hashmap of attributes and levels from the tree
            classifier.buildClassifier(gpDataset);  //Classifier must be rebuilt (again) for this step as we need our own model
            HashMap<String, Integer> nodes = Parse.parseJ48(classifier.toString());
            int treeSize = Parse.getTreeSize(classifier.toString());  //Get the tree size

            //If individual GP appears in classifier then assign the GP the %age correct classifications of the tree
            for(Genome<Gene> genome : population)
            {
                if(nodes.containsKey("GP: " + genome.toString()))
                {
                    genome.setTreeAccuracy(pctCorrect);
                    genome.setTreeSize(treeSize);
                }
                else
                {
                    //If individual is NOT used in classifier, give it a bad fitness
                    // Even an individual included in a bad tree will have a better fitness than this
                    genome.setTreeAccuracy(0);
                    genome.setTreeSize(Integer.MAX_VALUE);
                }
            }

        } catch (Exception ex) {Logger.getLogger(Population.class.getName()).log(Level.SEVERE, null, ex);  }
    }

    /**
     * Returns a new dataset which has all of the GPs as additional attributes. Also calculates the new values for new attributes.
     *  Uses the 'add' filter built into WEKA to add the attributes.
     * Source: {@link http://weka.wikispaces.com/Adding+attributes+to+a+dataset}
     * @param data The dataset to add attributes to
     * @return A new dataset with the GPs as attributes
     */
    private Instances addAttributes(Instances data)
    {
        //declare a new Add filter which will add the attributes
        Add addFilter;

        //loop thru all of the GPs in the population adding them to the dataset
        for(Genome<Gene> genome : population)
        {
            String attrName = "GP: " + genome.toString(); //String to store the name of the attribute to be created

            //check for duplicate attributes so only 1 of each gets added; all attributes with duplicates will still have correct fitness assigned
            if(data.attribute(attrName) == null)
            {
                //Assign a new filter for the new genome
                addFilter = new Add();
                //Set the filter's properties
                addFilter.setAttributeIndex("last");
                addFilter.setAttributeName(attrName);

                //Set the input format for the filter and use the filter on the dataset (adds the new attribute)
                try
                {
                    addFilter.setInputFormat(data);
                    data = Filter.useFilter(data, addFilter);
                } catch (Exception ex) {
                    Logger.getLogger(Population.class.getName()).log(Level.SEVERE, null, ex);
                }

                //Evaluate the expression for each instance and add it to the dataset using the RPN Calculator
                for (int i = 0; i < data.numInstances(); i++)
                {
                    data.instance(i).setValue(data.numAttributes() - 1, RPNCalculator.evaluate(genome.toString(), data.instance(i)));
                }
            }
        }

        return data;
    }

    /**
     * Quick sort on tree accuracy. Higher is better.
     * @param left 'Left' half of population indices
     * @param right 'Right' half of population indices
     */
    private void quickSortTreeAccuracy(int left, int right)
    {
        int i = left;
        int j = right;
        //temporary genomes
        Genome<Gene> c1;
        Genome<Gene> c2;

        c1 = population.get((left + right) / 2);

        do
        {
            while ((population.get(i).getTreeAccuracy() < c1.getTreeAccuracy()) && (i < right))
            {
                i++;
            }
            while ((c1.getTreeAccuracy() < population.get(j).getTreeAccuracy()) && (j > left))
            {
                j--;
            }

            if (i <= j)
            {
                c2 = population.get(i);
                population.set(i, population.get(j));
                population.set(j, c2);
                i++;
                j--;
            }

        } while (i <= j);

        if (left < j) quickSortTreeAccuracy(left, j);
        if (i < right) quickSortTreeAccuracy(i, right);
    }

    /**
     * Quick sort on adjusted VectorFitness. Higher is better.
     * @param left 'Left' half of population indices
     * @param right 'Right' half of population indices
     */
    private void quickSortVectorFitness(int left, int right)
    {
        int i = left;
        int j = right;
        //temporary genomes
        Genome<Gene> c1;
        Genome<Gene> c2;

        c1 = population.get((left + right) / 2);

        do
        {
            while ((population.get(i).getVectorFitness() < c1.getVectorFitness()) && (i < right))
            {
                i++;
            }
            while ((c1.getVectorFitness() < population.get(j).getVectorFitness()) && (j > left))
            {
                j--;
            }

            if (i <= j)
            {
                c2 = population.get(i);
                population.set(i, population.get(j));
                population.set(j, c2);
                i++;
                j--;
            }

        } while (i <= j);

        if (left < j) quickSortVectorFitness(left, j);
        if (i < right) quickSortVectorFitness(i, right);
    }

    /**
     * Quick sort on length. Smaller is better.
     * @param left 'Left' half of population indices
     * @param right 'Right' half of population indices
     */
    private void quickSortLength(int left, int right)
    {
        int i = left;
        int j = right;
        //temporary genomes
        Genome<Gene> c1;
        Genome<Gene> c2;

        c1 = population.get((left + right) / 2);

        do
        {
            while ((population.get(i).getLength() < c1.getLength()) && (i < right))
            {
                i++;
            }
            while ((c1.getLength() < population.get(j).getLength()) && (j > left))
            {
                j--;
            }

            if (i <= j)
            {
                c2 = population.get(i);
                population.set(i, population.get(j));
                population.set(j, c2);
                i++;
                j--;
            }

        } while (i <= j);

        if (left < j) quickSortLength(left, j);
        if (i < right) quickSortLength(i, right);
    }

    /**
     * Quick sort on TreeSize. Smaller is better.
     * @param left 'Left' half of population indices
     * @param right 'Right' half of population indices
     */
    private void quickSortTreeSize(int left, int right)
    {
        int i = left;
        int j = right;
        //temporary genomes
        Genome<Gene> c1;
        Genome<Gene> c2;

        c1 = population.get((left + right) / 2);

        do
        {
            while ((population.get(i).getTreeSize() < c1.getTreeSize()) && (i < right))
            {
                i++;
            }
            while ((c1.getTreeSize() < population.get(j).getTreeSize()) && (j > left))
            {
                j--;
            }

            if (i <= j)
            {
                c2 = population.get(i);
                population.set(i, population.get(j));
                population.set(j, c2);
                i++;
                j--;
            }

        } while (i <= j);

        if (left < j) quickSortTreeSize(left, j);
        if (i < right) quickSortTreeSize(i, right);
    }

    /**
     * Quick sort on crowding distance
     * @param left 'Left' half of population indices
     * @param right 'Right' half of population indices
     */
    private void quickSortCrowdingDistance(int left, int right)
    {
        int i = left;
        int j = right;
        //temporary genomes
        Genome<Gene> c1;
        Genome<Gene> c2;

        c1 = population.get((left + right) / 2);

        do
        {
            while ((population.get(i).getCrowdingDistance() < c1.getCrowdingDistance()) && (i < right))
            {
                i++;
            }
            while ((c1.getCrowdingDistance() < population.get(j).getCrowdingDistance()) && (j > left))
            {
                j--;
            }

            if (i <= j)
            {
                c2 = population.get(i);
                population.set(i, population.get(j));
                population.set(j, c2);
                i++;
                j--;
            }

        } while (i <= j);

        if (left < j) quickSortCrowdingDistance(left, j);
        if (i < right) quickSortCrowdingDistance(i, right);
    }

    /**
     * Generates a population using ramped half and half method.
     * @param rand Random object to use for generating pseudo random numbers
     * @param populationSize size of the tPopulation to set
     * @param funcSet Function set to use for generating population
     * @param validAttributes the valid numeric attributes that can be used as part of equations
     * @return a population generated using ramped half and half
     */
    private ArrayList<Genome<Gene>> rampedHalfandHalf(Random rand, int populationSize, ArrayList<String> funcSet, int[] validAttributes)
    {
        ArrayList<Genome<Gene>> tPopulation = new ArrayList<Genome<Gene>>();

        int subSetSize = populationSize / 5;
        int counter = 0;

        for (int i = 0; i < populationSize; i++)
        {
            if (counter == subSetSize)
            {
                if (maxDepth > 1) maxDepth--;  //Protect against rare cases where maxDepth becomes negative
                counter = 0;
            }

            if(i % 2 == 0)
            {
                Genome<Gene> genome = new Genome<Gene>(rand, EnumGenerationMethod.FULL, maxDepth, funcSet, validAttributes);

                if(!tPopulation.contains(genome))
                {
                    tPopulation.add(genome);
                    counter++;
                }
                else
                {
                    i--;
                }
            }
            else
            {
                Genome<Gene> genome = new Genome<Gene>(rand, EnumGenerationMethod.GROW, maxDepth, funcSet, validAttributes);

                if (!tPopulation.contains(genome))
                {
                    tPopulation.add(genome);
                    counter++;
                }
                else
                {
                    i--;
                }
            }
        }
        return tPopulation;
    }

    /**
     * Generates a population using full or grow method.
     * @param rand Random object to use for generating pseudo random numbers
     * @param populationSize size of the tPopulation to set
     * @param funcSet Function set to use for generating population
     * @param validAttributes the valid numeric attributes that can be used as part of equations
     * @return a population generated using full or grow
     */
    private ArrayList<Genome<Gene>> fullGrow(Random rand, int populationSize, ArrayList<String> funcSet,
            EnumGenerationMethod genMethod, int[] validAttributes)
    {
        //If we got here with the wrong method, go to the right place
        if(genMethod == EnumGenerationMethod.RAMPED_HALF_HALF)
        {
            return rampedHalfandHalf(rand, populationSize, funcSet, validAttributes);
        }

        //Generate the population with the specified method
        ArrayList<Genome<Gene>> tPopulation = new ArrayList<Genome<Gene>>();
        for (int i =0; i < populationSize; i++)
        {
            Genome<Gene> genome = new Genome<Gene>(rand, genMethod, maxDepth, funcSet, validAttributes);

            if(!tPopulation.contains(genome))
            {
                tPopulation.add(genome);
            }
            else
            {
                i--;
            }
        }
        return tPopulation;
    }
}
