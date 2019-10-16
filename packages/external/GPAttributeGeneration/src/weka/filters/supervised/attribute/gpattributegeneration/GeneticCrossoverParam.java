package weka.filters.supervised.attribute.gpattributegeneration;

/**
 * Provides a 'struct' to store control variables for Genetic crossover
 * @author Colin Noakes
 */
public class GeneticCrossoverParam
{
    /**
     * Denotes whether the crossover has been successful yet. (Has reached the bottom node on the sub-tree being crossed)
     */
    public boolean success;
    /**
     * The point at which to perform crossover.
     */
    public int crossOverPoint;

    /**
     * Initialise the struct
     * @param success Denotes whether the crossover has been successful yet. (Has reached the bottom node on the sub-tree being crossed)
     * @param crossOverPoint The point at which to perform crossover.
     */
    public GeneticCrossoverParam(boolean success, int crossOverPoint)
    {
        this.success = success;
        this.crossOverPoint = crossOverPoint;
    }
}
