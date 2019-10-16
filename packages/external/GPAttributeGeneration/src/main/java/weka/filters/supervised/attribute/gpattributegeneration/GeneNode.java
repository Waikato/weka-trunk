package weka.filters.supervised.attribute.gpattributegeneration;

/**
 * Node type for Genes. Ported from C#.
 * @author Luke Devonshire & Colin Noakes
 */
public class GeneNode<T> extends Node<T>
{
    /**
     * Creates an empty node
     */
    public GeneNode()
    {
        super();
    }

    /**
     * Creates a gene node with the given data
     * @param data data to pass to the node
     */
    public GeneNode(T data)
    {
        super(data, null);
    }

    /**
     * Creates a gene node
     * @param data data to pass to the node
     * @param left left child
     * @param right right child
     */
    public GeneNode(T data, GeneNode<T> left, GeneNode<T> right)
    {
        super.setValue(data);
        GeneNodeList<T> children = new GeneNodeList<T>(2);
        children.set(0, left);
        children.set(1, right);

        super.setChildren(children);
    }

    /**
     * add a child to the node (up to a maximum of 2)
     * @param node Node to add to the current node as a child
     */
    public void add(GeneNode<T> node)
    {
        //If there are no children, add the given node as the 'left' child; else add it as 'right'
        if (this.getChildren() == null)
        {
            if (super.getChildren() == null)
            {
                this.setChildren(new GeneNodeList<T>(2));
            }

            node.setParent(this);
            GeneNodeList<T> temp = this.getChildren();
            temp.set(0, node);
            this.setChildren(temp);
        }
        else
        {
            if (super.getChildren() == null)
            {
                this.setChildren(new GeneNodeList<T>(2));
            }

            node.setParent(this);
            GeneNodeList<T> temp = this.getChildren();
            temp.set(1, node);
            this.setChildren(temp);
        }
    }

    /**
     * Get Left child
     * @return The left child of the node
     */
    public GeneNode<T> getLeft()
    {
        if (super.getChildren() == null)
        {
            return null;
        }
        else
        {
            return (GeneNode<T>)super.getChildren().get(0);
        }
    }

    /**
     * Get Right child
     * @return The right child of the node
     */
    public GeneNode<T> getRight()
    {
        if (super.getChildren() == null)
        {
            return null;
        }
        else
        {
            return (GeneNode<T>)super.getChildren().get(1);
        }
    }

    /**
     * Gets the node's parent
     * @return The parent
     */
    public GeneNode<T> getParent()
    {
        return (GeneNode<T>)super.parent;
    }

    /**
     * Set the parent
     * @param parent Parent to set
     */
    public void setParent(GeneNode<T> parent)
    {
        super.parent = parent;
    }

    /**
     * Converts node to a string
     * @return The node as a string
     */
    @Override
    public String toString()
    {
        return super.getValue().toString();
    }
}
