package weka.filters.supervised.attribute.gpattributegeneration;

import java.util.ArrayList;

/**
 * Extends ArrayList to make a GeneNodeList so it's easier to use GP nodes
 * @author Luke Devonshire
 */
public class GeneNodeList<T> extends ArrayList<Node<T>>
{
    public GeneNodeList()
    {
        super();
    }

    public GeneNodeList(int initialSize)
    {
        for (int i = 0; i < initialSize; i++)
        {
            super.add(null);
        }
    }
}
