package weka.filters.supervised.attribute.gpattributegeneration;

import java.util.ArrayList;

/**
 * Class to provide NSGA-II features and methods.
 * Uses some code adapted from Luke Devonshire's project
 * @author Colin Noakes & Luke Devonshire
 */
public class NSGAII
{
    /**
     * Store the population attributes we need to calculate crowding distance
     */
    private double maxFitness;
    private double minFitness;
    private double maxLength;
    private double minLength;
    private double maxTreeSize;
    private double minTreeSize;
    /**
     * Store the arraylist of the population currently being worked on
     * (So that we can manipulate it without worrying about the original population object)
     */
    private ArrayList<Genome<Gene>> popList;
    /**
     * Store the non dominated fronts
     */
    private ArrayList<ArrayList<Genome<Gene>>> nonDominatedFronts;

    /**
     * Instantiate a new NSGAII class with the specified population.
     * Automatically calculates non dominated fronts and crowding distance
     * @param pop The population to instantiate the class with
     */
    public NSGAII(Population pop)
    {
        //Store the populations max and min fitness values
        maxFitness = pop.getMaxTreeAccuracy();
        minFitness = pop.getMinTreeAccuracy();
        maxLength = pop.getMaxLength();
        minLength = pop.getMinLength();
        maxTreeSize = pop.getMaxTreeSize();
        minTreeSize = pop.getMinTreeSize();

        //Shallow copy the Genomes into a new ArrayList (so we don't affect the actual population list)
        this.popList = new ArrayList<Genome<Gene>>();
        for(int i = 0; i < pop.size(); i++)
        {
            this.popList.add(pop.get(i));
        }

        //Find the non dominated fronts
        findNonDominatedFronts();
        //Assign rank and crowding distance to genomes
        assignGenomeProperties();
        //Sort each of the fronts by crowding distance
        sortFronts();
    }

    /**
     * Return the non dominated fronts
     * @return non dominated fronts
     */
    public ArrayList<ArrayList<Genome<Gene>>> getNonDomFronts()
    {
        return nonDominatedFronts;
    }

    /**
     * Assigns the rank and crowding distance of each of the genomes in the non dominated fronts
     */
    private void assignGenomeProperties()
    {
        //Loop thru each front assigning rank and crowding distance for each of the genomes in each front
        int rank = 1;
        for(ArrayList<Genome<Gene>> front : nonDominatedFronts)
        {
            for(Genome<Gene> genome : front)
            {
                //Set the domination rank
                genome.setDominationRank(rank);
            }
            //Set the crowding distances of the genomes in the front using the crowd distance calculator
            calculateCrowdingDistance(front);
            rank++;
        }
    }

    /**
     * Calculates the crowding distance using Fitness and length for the genomes in the nonDominatedSet specified
     * @param nonDominatedSet The non dominated set to use for calculating the crowding distance
     * @param pop Original population from which the genomes come from; Only used for getting min and max fitness / length
     */
    private void calculateCrowdingDistance(ArrayList<Genome<Gene>> nonDominatedSet)
    {
        //Make a new population to store our front (so we can sort it using the sorting)
        Population sortedNonDominatedSet = new Population(nonDominatedSet);

        //Calculate crowding distance for fitness
        sortedNonDominatedSet.sort(EnumSortingCriteria.FITNESS);
        for(int i = 0; i < sortedNonDominatedSet.size(); i++)
        {
            //If i is not an edge case, calculate a crowding distance. If it is and edge case it gets positive infinity
            if ((i != 0) && (i != nonDominatedSet.size() - 1))
            {
                sortedNonDominatedSet.get(i).setCrowdingDistance(
                        (sortedNonDominatedSet.get(i - 1).getTreeAccuracy() - sortedNonDominatedSet.get(i + 1).getTreeAccuracy())
                        / (maxFitness - minFitness)   );
            }
            else
            {
                sortedNonDominatedSet.get(i).setCrowdingDistance(Double.POSITIVE_INFINITY);
            }
        }

        //calculate for length
        sortedNonDominatedSet.sort(EnumSortingCriteria.LENGTH);
        for(int c = 0; c < sortedNonDominatedSet.size(); c++)
        {
            //If the crowd distance is already positive infinity, don't calculate the crowding distance using length
            // Also assign edge points infinite crowd distance as well
            if(sortedNonDominatedSet.get(c).getCrowdingDistance() != Double.POSITIVE_INFINITY
                    && c != 0 && c != sortedNonDominatedSet.size() - 1)
            {
                sortedNonDominatedSet.get(c).setCrowdingDistance(
                    sortedNonDominatedSet.get(c).getCrowdingDistance() +
                        ((sortedNonDominatedSet.get(c + 1).getLength() - sortedNonDominatedSet.get(c - 1).getLength())
                        / (maxLength - minLength))   );
            }
            else
            {
                sortedNonDominatedSet.get(c).setCrowdingDistance(Double.POSITIVE_INFINITY);
            }
        }

        //Calculate for tree size
        sortedNonDominatedSet.sort(EnumSortingCriteria.TREE_SIZE);
        for(int c = 0; c < sortedNonDominatedSet.size(); c++)
        {
            //If the crowd distance is already positive infinity, don't calculate the crowding distance using length
            // Also assign edge points infinite crowd distance as well
            if(sortedNonDominatedSet.get(c).getCrowdingDistance() != Double.POSITIVE_INFINITY
                    && c != 0 && c != sortedNonDominatedSet.size() - 1)
            {
                sortedNonDominatedSet.get(c).setCrowdingDistance(
                    sortedNonDominatedSet.get(c).getCrowdingDistance() +
                        ((sortedNonDominatedSet.get(c + 1).getTreeSize() - sortedNonDominatedSet.get(c - 1).getTreeSize())
                        / (maxTreeSize - minTreeSize))   );
            }
            else
            {
                sortedNonDominatedSet.get(c).setCrowdingDistance(Double.POSITIVE_INFINITY);
            }
        }
    }

    /**
     * Finds the non dominated fronts for the population stored in this class
     */
    private void findNonDominatedFronts()
    {
        ArrayList<ArrayList<Genome<Gene>>> fronts = new ArrayList<ArrayList<Genome<Gene>>>();

        ArrayList<Genome<Gene>> nonDominatedSet = findNonDominatedSet(popList);

        fronts.add(nonDominatedSet);

        while (popList.size() > 0)
        {
            for (Genome<Gene> genome : nonDominatedSet)
            {
                popList.remove(genome);
            }

            nonDominatedSet = findNonDominatedSet(popList);

            if (!nonDominatedSet.isEmpty())
            {
                fronts.add(nonDominatedSet);
            }
        }

        this.nonDominatedFronts = fronts;
    }

    /**
     * Find a non dominated set from the current population. (Contents of higher ranked fronts should be removed before this is run)
     * @param popList The arraylist of individuals
     * @return a non dominated set
     */
    private ArrayList<Genome<Gene>> findNonDominatedSet(ArrayList<Genome<Gene>> popList)
    {
        ArrayList<Genome<Gene>> nonDominatedSet = new ArrayList<Genome<Gene>>();

        for (int i = 0; i < popList.size(); i++)
        {
            for (int j = 0; j < popList.size(); j++)
            {
                if (i != j)
                {
                    //if i is dominated, break out the inner loop
                    if (dominates(popList.get(j), popList.get(i)))
                    {
                        break;
                    }
                }
                //if no individual dominates i then add it to the front
                if (j + 1 == popList.size())
                {
                    nonDominatedSet.add(popList.get(i));
                }
            }
        }
        return nonDominatedSet;
    }

    /**
     * Works out if c1 dominates c2 or not.
     * A solution x(1) is said to dominate the other solution x(2) if the following conditions are met:
     *
     * 1: The solution x(1) is no worse than x(2) in all objectives.
     * 2: The solution x(1) is strictly better that x(2) in at least one objective.
     *
     * Where we are wanting to maximise the genome's Fitness while minimising the genome length.
     *
     * Is no worse for maximisation means that x(1) is at least equal to or bigger than x(2)
     *
     * Is no worse for minimisation means that x(1) is at least equal to or smaller than x(2)
     *
     * @param c1 Genome to test if dominates
     * @param c2 Genome to test if dominated
     * @return True if c1 dominates c2; false otherwise
     */
    private boolean dominates(Genome<Gene> c1, Genome<Gene> c2)
    {
        if ((c1.getTreeAccuracy() >= c2.getTreeAccuracy() && c1.getLength() <= c2.getLength() && c1.getTreeSize() <= c2.getTreeSize())
                && (c1.getTreeAccuracy() > c2.getTreeAccuracy() || c1.getLength() < c2.getLength() || c1.getTreeSize() < c2.getTreeSize()))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Sort all of the fronts by crowding distance
     */
    private void sortFronts()
    {
        for(ArrayList<Genome<Gene>> front : nonDominatedFronts)
        {
            Population frontPop = new Population(front);
            frontPop.sort(EnumSortingCriteria.CROWDING_DISTANCE);
        }
    }
}
