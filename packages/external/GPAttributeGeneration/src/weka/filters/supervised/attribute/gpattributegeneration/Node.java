package weka.filters.supervised.attribute.gpattributegeneration;

/**
 * Defines a node and its children (if the node is a function)
 *  Ported from Luke Devonshire's C# project
 * @author Luke Devonshire
 */
public class Node<T>
{
    private T data;
    private GeneNodeList<T> children;

    /**
     * A Node's parent
     */
    protected Node<T> parent;

    /**
     * Creates an empty node
     */
    public Node()
    {
        super();
    }

    /**
     * Creates a node with the given data
     * @param data Data to create node with
     */
    public Node(T data)
    {
        this.data = data;
    }

    /**
     * Creates a node with the given data and children
     * @param data Data to create node with
     * @param children Children to also add to the node
     */
    public Node(T data, GeneNodeList<T> children)
    {
        this.data = data;
        this.children = children;
    }

    /**
     * Returns the value of the node
     * @return The value of the node
     */
    public T getValue()
    {
        return data;
    }

    /**
     * Set the value of the node
     * @param data The value
     */
    public void setValue(T data)
    {
        this.data = data;
    }

    /**
     * Returns the children
     * @return The Node's children
     */
    public GeneNodeList<T> getChildren()
    {
        return children;
    }

    /**
     * Sets the children
     * @param children Children to set for the node
     */
    public void setChildren(GeneNodeList<T> children)
    {
        this.children = children;
    }
}
