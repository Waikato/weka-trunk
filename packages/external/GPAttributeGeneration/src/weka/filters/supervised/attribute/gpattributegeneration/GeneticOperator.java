package weka.filters.supervised.attribute.gpattributegeneration;

import java.util.ArrayList;
import java.util.Random;

/**
 * Class to provide genetic operators. Ported and adapted from Luke Devonshire's C#.
 * @author Colin Noakes & Luke Devonshire
 */
public class GeneticOperator
{
    /**
     * Crossover between two Genomes
     * @param rand Random object to use for generating pseudo-random numbers
     * @param parentOne Parent 1
     * @param parentTwo Parent 2
     * @return Two crossed over genomes
     */
    public static ArrayList<Genome<Gene>> crossOver(Random rand, Genome<Gene> parentOne, Genome<Gene> parentTwo)
    {
        ArrayList<Genome<Gene>> offSpring = new ArrayList<Genome<Gene>>();

        int crossPtOne;
        int crossPtTwo;
        //Generate two random cross over points, one for each genome
        crossPtOne = rand.nextInt(parentOne.getLength());
        crossPtTwo = rand.nextInt(parentTwo.getLength());

        //If crossover point was '0' for both then just return the genomes
        if ((crossPtOne == 0) && (crossPtTwo == 0))
        {
            offSpring.add(parentOne);
            offSpring.add(parentTwo);

            return offSpring;
        }
        else
        {
            //Make two fragments of the subtrees from the crossover points
            GeneNode<Gene> fragOne = parentOne.getChildren().get(crossPtOne);
            GeneNode<Gene> fragTwo = parentTwo.getChildren().get(crossPtTwo);

            //Actually perform the crossover and set the new genotype in the parents
            GeneNode<Gene> genotype = performCrossOver(parentOne.getGenotype(), fragTwo, new GeneticCrossoverParam(false, crossPtOne));
            parentOne.setGenotype(genotype);
            offSpring.add(parentOne);

            GeneNode<Gene> genotype2 = performCrossOver(parentTwo.getGenotype(), fragOne, new GeneticCrossoverParam(false, crossPtTwo));
            parentTwo.setGenotype(genotype2);
            offSpring.add(parentTwo);
        }

        return offSpring;
    }

    /**
     * Performs a crossover operation on a single genome, given a fragment, a genome and a crossover point
     * @param tree The genome to put the fragment into
     * @param frag The genetic fragment to insert into the genome
     * @param crossOverPoint Cross over point
     * @return A new genome offspring
     */
    private static GeneNode<Gene> performCrossOver(GeneNode<Gene> tree, GeneNode<Gene> frag, GeneticCrossoverParam param)
    {
        //Recrusively performs cross over on a subtree
        if (tree != null)
        {
            if (!param.success && param.crossOverPoint > 0)
            {
                if (tree.getLeft() != null)
                {
                    param.crossOverPoint--;
                    tree.getChildren().set(0, performCrossOver(tree.getLeft(), frag, param));
                }
            }

            if (!param.success && param.crossOverPoint > 0)
            {
                if (tree.getRight() != null)
                {
                    param.crossOverPoint--;
                    tree.getChildren().set(1, performCrossOver(tree.getRight(), frag, param));
                }
            }
        }

        if (!param.success)
        {
            if (param.crossOverPoint == 0)
            {
                param.success = true;
                return frag;
            }
        }

        return tree;
    }

    /**
     * Performs random number of point mutations on a genome
     * @param rand Random object to use for generating genomes
     * @param parent The genome to mutate
     * @param functions Function set being used in the GP System
     * @param validAttributes the valid numeric attributes that can be used as part of equations
     * @return A mutated genome
     */
    public static Genome<Gene> pointMutation(Random rand, Genome<Gene> parent, ArrayList<String> functions, int[] validAttributes)
    {
        //Randomises how many point mutations to do
        int numOfmutations = (int)(parent.getChildren().size() * 0.5);

        for (int i = 0; i <= numOfmutations; i++)
        {
            GeneNode<Gene> mutationPoint = parent.getChildren().get(rand.nextInt(parent.getChildren().size()));
            Gene mutantGene = new Gene();

            mutantGene.generateValue(rand, mutationPoint.getValue().getType(), functions, Function.arity(mutationPoint.toString()), validAttributes);
            mutationPoint.setValue(mutantGene);
        }
        return parent;
    }
}