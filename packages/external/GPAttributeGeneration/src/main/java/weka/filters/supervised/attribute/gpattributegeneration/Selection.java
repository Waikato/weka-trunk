package weka.filters.supervised.attribute.gpattributegeneration;

import java.util.Random;

/**
 * Class contains methods for helping with selection. Ported and modified from Luke Devonshire's C#.
 * @author Luke Devonshire
 */
public class Selection
{
    /**
     * Performs tournament selection on the given population list using fitness values
     * @param rand Random object to use to select a random individual
     * @param population Population to get random individuals from
     * @return A selected Genome
     */
    public static Genome<Gene> tournament(Random rand, Population population)
    {
        Genome<Gene> c1 = population.get(rand.nextInt(population.size() - 1));
        Genome<Gene> c2 = population.get(rand.nextInt(population.size() - 1));

        if (c1.getTreeAccuracy() > c2.getTreeAccuracy())
        {
            return c1;
        }
        else
        {
            return c2;
        }
    }

    /**
     * Performs tournament selection on the given population list using vector fitness values
     * @param rand Random object to use to select a random individual
     * @param population Population to get random individuals from
     * @return A selected Genome
     */
    public static Genome<Gene> vectorTournament(Random rand, Population population)
    {
        Genome<Gene> c1 = population.get(rand.nextInt(population.size() - 1));
        Genome<Gene> c2 = population.get(rand.nextInt(population.size() - 1));

        if (c1.getVectorFitness() > c2.getVectorFitness())
        {
            return c1;
        }
        else
        {
            return c2;
        }
    }

    /**
     * Performs tournament selection on the given population list using domination rank and crowding distance values (NSGAII)
     * @param rand Random object to use to select a random individual
     * @param population Population to get random individuals from
     * @return A selected Genome
     */
    public static Genome<Gene> crowdedTournament(Random rand, Population population)
    {
        Genome<Gene> c1 = population.get(rand.nextInt(population.size() - 1));
        Genome<Gene> c2 = population.get(rand.nextInt(population.size() - 1));

        if ((c1.getDominationRank() < c2.getDominationRank())
                || ((c1.getDominationRank() == c2.getDominationRank()) && (c1.getCrowdingDistance() > c2.getCrowdingDistance())))
        {
            return c1;
        }
        else
        {
            return c2;
        }
    }

    /**
     * Performs elitist selection on the given population. Returns the top 'numToCopy' individuals in the population.
     * @param population Population to perform elitist selection on
     * @param numToCopy Number of individuals to select. If numToCopy > population size; all the population will be returned
     * @param sortCriteria Sorting criteria to use
     * @return Selected population of the best numToCopy individuals
     */
    public static Population elitist(Population population, int numToCopy, EnumSortingCriteria sortCriteria)
    {
        population.sort(sortCriteria);  //Sort the population first using the specified sort criteria

        population.removeRange(numToCopy, population.size());

        return population;
    }
}
