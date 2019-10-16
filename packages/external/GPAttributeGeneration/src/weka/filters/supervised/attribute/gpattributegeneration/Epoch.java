package weka.filters.supervised.attribute.gpattributegeneration;

import java.util.ArrayList;
import java.util.Random;
import weka.classifiers.Classifier;
import weka.core.Instances;

/**
 * Provides methods that control the running of an epoch.
 * @author Colin Noakes
 */
public class Epoch
{
    //FIELDS
    private Random rand;
    private ArrayList<String> functions;

    /**
     * Runs an epoch. Takes a population and runs a complete epoch on it; returning a new population.
     *  An Epoch 'run' starts at selection, genetic operation and fitness evaluation.
     * @param population the population to run an epoch on
     * @param evalMethod Fitness evaluation method to use
     * @param data Data to use to evaluate the fitness of individuals
     * @param operatorProp Operator proportion to use when evolving populations
     * @param rand Random number generator
     * @param functions Function nodes
     * @param selectionMethod Selection method to use when evolving individuals
     * @return A new population on which an epoch has been run
     */
    public Population runEpoch(
            Population population,
            EnumFitnessEvaluationMethod evalMethod,
            Instances data,
            double[] operatorProp,
            Random rand,
            ArrayList<String> functions,
            EnumSelectionMethod selectionMethod,
            Classifier classifier,
            int[] validAttributes)
    {
        //Set fields
        this.rand = rand;
        this.functions = functions;

        //Get a 'new' evolved population from the evolver
        Population newPop = evolve(population, operatorProp, selectionMethod, validAttributes);

        //Calculate fitness for the new population
        newPop.calculateFitness(evalMethod, data, classifier);

        //Add the old population to the new population
        newPop.addAll(population);

        //Return newPop after selection
        newPop = elitism(newPop, selectionMethod);


        return newPop;
    }

    /**
     * Evolves a population once, using selection and then genetic operators to evolve individuals.
     * @param pop Population to evolve
     * @param operatorProportion Operator proportions to use when evolving (should equal 1): crossover, mutation
     * @param selMethod Selection method to use to select individuals
     * @param validAttributes the valid numeric attributes that can be used as part of equations
     * @return An evolved population
     */
    private Population evolve(Population pop, double[] operatorProportion, EnumSelectionMethod selMethod, int[] validAttributes)
    {

        ArrayList<Genome<Gene>> newPopArrayList = new ArrayList<Genome<Gene>>();

        //Convert operator proportions to raw integers specifying the numbers of offspring that will fill the new population
        int crossoverAmount = (int)Math.round(pop.size() * operatorProportion[0]);
        int mutationAmount = pop.size() - crossoverAmount;

        //Do all the crossover operations and fill the new population list
        for (int i = 0; i < crossoverAmount; i++)
        {
            //select two parents and copy them
            Genome<Gene> p1 = new Genome<Gene>(select(pop, selMethod));
            Genome<Gene> p2 = new Genome<Gene>(select(pop, selMethod));

            //cross them
            ArrayList<Genome<Gene>> offSpring = GeneticOperator.crossOver(rand, p1, p2);
            //Check for i being 1 less than crossOver amount (in this case we only insert 1 offSpring)
            if(crossoverAmount - i == 1)
            {
                newPopArrayList.add(offSpring.get(0));
            }
            else
            {
                //Add both offspring to new list; add one more to i counter to take this into account
                newPopArrayList.addAll(offSpring);
                i++;
            }
        }

        //Do mutations
        for (int i = 0; i < mutationAmount; i++)
        {
            //Select a parent and copy it
            Genome<Gene> p = new Genome<Gene>(select(pop, selMethod));
            //mutate it
            p = GeneticOperator.pointMutation(rand, p, functions, validAttributes);
            //Add it to new list
            newPopArrayList.add(p);
        }

        //Put the new individuals in a new population and return it
        return new Population(newPopArrayList);
    }

    /**
     * Selects a genome based on the selection method specified
     * @param pop Population to select from
     * @param selMethod Selection method to use
     * @param elitistSize The size of the elitist collection (ignored if not using elitist selection)
     * @return A genome
     */
    private Genome<Gene> select(Population pop, EnumSelectionMethod selMethod)
    {
        if(selMethod == EnumSelectionMethod.SINGLE_FITNESS)
        {
            return Selection.tournament(rand, pop);
        }
        else if(selMethod == EnumSelectionMethod.NSGAII)
        {
            return Selection.crowdedTournament(rand, pop);
        }
        else if(selMethod == EnumSelectionMethod.VECTOR_FITNESS)
        {
            return Selection.vectorTournament(rand, pop);
        }
        return null;  //Just to please compiler; program should never reach this line!
    }

    /**
     * Returns the top 50% of the given fullPop, using either straight fitness or NSGA-II
     * @param fullPop population to perform elitism on
     * @param nsgaii True - use NSGA-II; False - Use fitness
     * @return A population with the top 50% of individuals
     */
    private Population elitism(Population fullPop, EnumSelectionMethod selectionMethod)
    {
        if(selectionMethod == EnumSelectionMethod.NSGAII)
        {
            fullPop.calculateNsgaii(); //Apply NSGA-II if selected
            return Selection.elitist(fullPop, (fullPop.size()/2), EnumSortingCriteria.NSGAII);
        } else if (selectionMethod == EnumSelectionMethod.SINGLE_FITNESS) {
            return Selection.elitist(fullPop, (fullPop.size()/2), EnumSortingCriteria.FITNESS);
        } else if (selectionMethod == EnumSelectionMethod.VECTOR_FITNESS) {
            return Selection.elitist(fullPop, (fullPop.size()/2), EnumSortingCriteria.VECTOR_FITNESS);
        }
        return null;  //Just to please compiler
    }
}
