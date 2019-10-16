package weka.filters.supervised.attribute.gpattributegeneration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Random;

/**
 * An individual genome for the GP system, made up of nodes.
 *  Adapted from Luke Devonshire's C# project
 * @author Colin Noakes & Luke Devonshire
 */
public class Genome<T> implements Comparable<Genome<T>>
{
    //FIELDS
    private GeneNode<Gene> genotype;
    private ArrayList<GeneNode<Gene>> children = new ArrayList<GeneNode<Gene>>();
    /**
     * Tree accuracy (1 is high; 0 is low)
     */
    private double treeAccuracy = 0;
    /**
     * NSGA-II: The domination rank
     */
    private int rank = 0;
    /**
     * NSGA-II: The crowding distance
     */
    private double crowdingDistance = 0.0;
    /**
     * Tree size that is associated with this genome;
     * Default is HIGH so that values closer to 0 are good, but it would be nonsensical to have a tree of size 0!
     */
    private int treeSize = Integer.MAX_VALUE;

    /**
     * Creates an empty Genome
     */
    public Genome()
    {
        genotype = null;
    }

    /**
     * Constructor to allow for a deep copy of a genome. Simply copies all fields using helper fields to copy data genotype structure.
     * @param genome Genome to copy
     */
    public Genome(Genome<T> genome)
    {
        this.genotype = copyGenotype(genome.genotype);
        repopulateChildren(this.genotype);
        this.treeAccuracy = genome.treeAccuracy;
        this.rank = genome.rank;
        this.crowdingDistance = genome.crowdingDistance;
    }

    /**
     * Constructs a Genome given a genotype
     * @param genotype Genotype to use for new Genome
     */
    public Genome(GeneNode<Gene> genotype)
    {
        this.genotype = copyGenotype(genotype);
        repopulateChildren(this.genotype);
    }

    /**
     * Create a genome from a PREFIX string, with nodes separated by a space
     * @param prefixGenome String to turn into a genome
     */
    public Genome(String prefixGenome)
    {
        this.genotype = buildChromosome(prefixGenome);
        repopulateChildren(this.genotype);
    }

    /**
     * Constructor to generate a random genome given the relevant parameters. Used when initialising populations
     *  or other times when a random genome is required
     * @param rand Random object to use for generating pseudo-random numbers
     * @param generationMethod The generation method to use
     * @param maxDepth The maximum depth of the tree created
     * @param funcSet Available functions to use for creating the tree
     * @param validAttributes the valid numeric attributes that can be used as part of equations
     */
    public Genome(Random rand, EnumGenerationMethod generationMethod, int maxDepth, ArrayList<String> funcSet, int[] validAttributes)
    {
        genotype = buildChromosome(rand, generationMethod, maxDepth, funcSet, validAttributes);
    }

    /**
     * Clears the genome of all its properties
     */
    public void Clear()
    {
        genotype = null;
        children.clear();
        treeAccuracy = 0.0;
    }

    /**
     * Returns the tree accuracy of the genome. 1 is the best value.
     * @return The tree accuracy of the genome
     */
    public double getTreeAccuracy()
    {
        return treeAccuracy;
    }
    /**
     * Sets tree accuracy. 1 is the best value.
     * @param fitness tree accuracy to give the Genome
     */
    public void setTreeAccuracy(double fitness)
    {
        this.treeAccuracy = fitness;
    }
    /**
     * Returns genotype
     * @return Genotype of genome
     */
    public GeneNode<Gene> getGenotype()
    {
        return genotype;
    }
    /**
     * Sets genotype of genome and re-populates children to make it correct
     * @param geno Genotype to give to gene
     */
    public void setGenotype(GeneNode<Gene> geno)
    {
        genotype = geno;
        repopulateChildren(genotype);
    }
    /**
     * Returns the children of the genome's root node
     * @return Children of the root node
     */
    public ArrayList<GeneNode<Gene>> getChildren()
    {
        return children;
    }
    /**
     * returns the length of the genome
     * @return Length of the genome
     */
    public int getLength()
    {
        String[] tokens = this.toString().trim().split(" ");
        return tokens.length;
    }
    /**
     * Calculate Vector fitness (1 is highest; but will never reach this).
     * Equation:
     * @return Vector fitness of this genome
     */
    public double getVectorFitness()
    {
        return (treeAccuracy - ((double)getLength() * (double)treeSize));
    }
    /**
     * Get the tree size of the genome
     * @return the tree size of the genome
     */
    public int getTreeSize()
    {
        return treeSize;
    }
    /**
     * Set the tree size of the genome
     * @param treeSize the tree size to set
     */
    public void setTreeSize(int treeSize)
    {
        this.treeSize = treeSize;
    }
    /**
     * Returns the domination rank of the genome
     * @return The domination rank
     */
    public int getDominationRank()
    {
        return rank;
    }
    /**
     * Sets the domination rank of the genome
     * @param rank The domination rank
     */
    public void setDominationRank(int rank)
    {
        this.rank = rank;
    }
    /**
     * Gets crowding distance of the genome
     * @return The crowding distance
     */
    public double getCrowdingDistance()
    {
        return crowdingDistance;
    }
    /**
     * Sets the crowding distance
     * @param dist The crowding distance
     */
    public void setCrowdingDistance(double dist)
    {
        crowdingDistance = dist;
    }

    /**
     * A string representation of the genome
     * @return The genome represented as a string
     */
    @Override
    public String toString()
    {
        Deque<String> stack = new ArrayDeque<String>();
        String indicator = "#";
        String postfixExpression = "";

        for (int i = 0; i < this.children.size(); i++)
        {
            GeneNode<Gene> gene = this.children.get(i);

            if (this.children.size() > 0 || stack.size() != 0)
            {
                //Check if gene is a function (if not it is a terminal)
                if (Function.isValid(gene.toString()))
                {
                    if (Function.isUnary(gene.toString()))
                    {
                        stack.push(gene.getValue().toString());
                        stack.push(indicator);
                    }
                    else
                    {
                        stack.push(gene.getValue().toString());
                    }
                }
                else
                {
                    postfixExpression += " " + gene.getValue().toString();

                    while ((stack.size() != 0) && (stack.peek().equals(indicator)))
                    {
                        stack.pop();

                        postfixExpression += " " + stack.peek();
                        stack.pop();
                    }
                    stack.push(indicator);
                }
            }
        }

        return postfixExpression;
    }

    /**
     * Implements comparison method from 'Comparable'. Compares based on genome fitness so Java can show a 'natural ordering' of genomes
     * @param o The genome to compare to
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
     */
    @Override
    public int compareTo(Genome<T> o)
    {
        if (treeAccuracy == o.getTreeAccuracy())
        {
            return 0;
        }
        else if (treeAccuracy < o.getTreeAccuracy())
        {
            return 1;
        }
        else
        {
            return -1;
        }
    }

    /**
     * Equates this genome with the given parameter genome. Returns true if genome is identical
     * @param obj Genome to compare to
     * @return True for same genome, false for not
     */
    @Override
    public boolean equals(Object obj)
    {
        //Do standard object checks
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        //cast the object to compare
        @SuppressWarnings({"unchecked"})
        Genome<T> equatingToGenome = (Genome<T>)obj;
        //Compare the string representations of the objects
        if (equatingToGenome.toString().equals(this.toString()))
        {
            return true;
        }
        return false;
    }

    /**
     * Calculates a hashcode for Genome for use in HashMap
     * @return Hash code
     */
    @Override
    public int hashCode()
    {
        int hash = 7;
        return hash;
    }

    /**
     * Method to recursively build the chromosomes comprising a genome. Called by the overloaded class constructor to use when
     *  generating genomes at random; such as when initialising a population.
     *  Method is outside of constructor to allow for recursion required to build trees of n depth.
     * @param rand Random object to use for generating pseudo-random numbers
     * @param generationMethod The generation method to use
     * @param maxDepth The maximum depth of the tree created
     * @param funcSet Available functions to use for creating the tree
     * @param validAttributes the valid numeric attributes that can be used as part of equations
     * @return A genotype
     */
    private GeneNode<Gene> buildChromosome(Random rand, EnumGenerationMethod generationMethod,
            int maxDepth, ArrayList<String> funcSet, int[] validAttributes)
    {
        GeneNode<Gene> geneNode;
        Gene gene = new Gene();

        // Establish if the next gene should be a terminal or a function
        double randomRate = 2.0 / (2.0 + (double)funcSet.size());
        if ((maxDepth == 0) || ((generationMethod == EnumGenerationMethod.GROW) && (rand.nextDouble() < randomRate)))
        {
            gene.generateValue(rand, EnumGeneType.TERMINAL, funcSet, validAttributes);
            geneNode = new GeneNode<Gene>(gene);
        }
        else
        {
            gene.generateValue(rand, EnumGeneType.FUNCTION, funcSet, validAttributes);
            geneNode = new GeneNode<Gene>(gene);
        }

        // add the gene to the genomes children list
        this.children.add(geneNode);

        //If the gene is an operator then it will require some operands
        if (Function.isValid(geneNode.toString()))
        {
            //The arity of an operator dentoes how many operands are required
            for (int i = 0; i < Function.arity(geneNode.toString()); i++)
            {
                geneNode.add(buildChromosome(rand, generationMethod, maxDepth - 1, funcSet, validAttributes));
            }
        }
        return geneNode;
    }

    /**
     * Builds a chromosome from a PREFIX string
     * @param genome Genome represented as a prefix string separated by spaces
     * @return A complete genotype
     */
    private GeneNode<Gene> buildChromosome(String genome)
    {
        String[] genomeArray = genome.split(" ");
        //Loop thru the array string and add the correct gene type to the queue
        Deque<Gene> prefix = new ArrayDeque<Gene>();
        for (String gene : genomeArray)
        {
            if (Function.isValid(gene))
            {
                prefix.add(new Gene(gene, EnumGeneType.FUNCTION));
            }
            else
            {
                prefix.add(new Gene(gene, EnumGeneType.TERMINAL));
            }
        }

        return recreateGenotype(prefix);
    }

    /**
     * Deep copies a GeneNode object and returns the copy
     * @param geneNode GeneNode to copy
     * @return the clone of the GeneNode object
     */
    private GeneNode<Gene> copyGenotype(GeneNode<Gene> subTree)
    {
        Deque<Gene> genes = extractGenes(new ArrayDeque<Gene>(), subTree);

        return recreateGenotype(genes);
    }

    /**
     * Extracts the genes from a GeneNode tree to a queue. Uses recursion.
     * @param prefix Prefix queue to store representation of the GeneNode tree
     * @param subTree Tree to extract genes from
     * @return A prefix queue representation of the tree
     */
    private Deque<Gene> extractGenes(Deque<Gene> prefix, GeneNode<Gene> subTree)
    {
        if (subTree != null)
        {
            if (subTree.getLeft() != null)
            {
                prefix.add(subTree.getValue());

                extractGenes(prefix, subTree.getLeft());
            }

            if (subTree.getRight() != null)
            {
                extractGenes(prefix, subTree.getRight());
            }

            if (subTree.getLeft() == null && subTree.getRight() == null)
            {
                prefix.add(subTree.getValue());
            }
        }
        return prefix;
    }

    /**
     * Recreates a genotype data structure from a queue representation of genes
     * @param queue The queue to create the genotype from
     * @return A recreated GeneNode genotype
     */
    private GeneNode<Gene> recreateGenotype(Deque<Gene> queue)
    {
        GeneNode<Gene> node = new GeneNode<Gene>(queue.remove());

        if (Function.isValid(node.toString()))
        {
            for (int i = 0; i < Function.arity(node.toString()); i++)
            {
                node.add(recreateGenotype(queue));
            }
        }

        return node;
    }
    /**
     * Re populate this classes children from the genotype subtree
     * @param subTree The tree to get the children from
     */
    private void repopulateChildren(GeneNode<Gene> subTree)
    {
        //Empty the list first
        this.children.clear();
        //Actually repopulate the children recursively
        repopulateChildrenRecursive(subTree);
    }

    /**
    * Actually does the job of populating the children field recursively
    * @param subTree The tree to get the children from
    */
    private void repopulateChildrenRecursive(GeneNode<Gene> subTree)
    {
        if (subTree != null)
        {
            if (subTree.getLeft() != null)
            {
                this.children.add(subTree);

                repopulateChildrenRecursive(subTree.getLeft());
            }

            if (subTree.getRight() != null)
            {
                repopulateChildrenRecursive(subTree.getRight());
            }

            if (subTree.getLeft() == null && subTree.getRight() == null)
            {
                this.children.add(subTree);
            }
        }
    }

}