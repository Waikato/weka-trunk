
package weka.filters.supervised.attribute.gpattributegeneration;

import java.util.HashMap;

/**
 * Provides methods which can parse results and strings throughout the program
 * @author Colin Noakes
 */
class Parse
{
    /**
     * Parses a J48 result string and returns a hash map of nodes and their level
     * @param treeResult J48 tree string to parse
     * @return a hash map of nodes and their level
     */
    public static HashMap<String, Integer> parseJ48(String treeResult)
    {
        HashMap<String, Integer> hm = new HashMap<String, Integer>();
        //Split the string at each new line char (Regex to take into account different line end chars depending on OS)
        String[] lines = treeResult.split("\\r\\n?|\\n");

        //loop thru all lines adding new nodes as they come with their level
        for(String line : lines)
        {
            //Check if the line has 'GP' in it at any stage, this denotes the presence of a GP node
            if(line.matches("[\\|\\ ]*GP:\\ [\\x20-\\x7E]+"))
            {
                String[] lineSplit = line.split("\\ [\\<\\=\\>]");
                int level = countOccurrences(lineSplit[0], '|');  //Get the level from counting the '|' chars
                if (level == 0)
                {
                    //If level == 0 then the string in [0] is the name of the node, add it to the hashmap with the level as the value
                    hm.put(lineSplit[0], level);
                }
                else
                {
                    //If level > 0 then split string to get name of the node, add it to the hashmap with the level as the value
                    String[] split = lineSplit[0].split("\\|");
                    hm.put(split[split.length - 1].trim(), level);
                }
            }
        }

        return hm;
    }

    /**
     * Gets the tree size number from the string output
     * @param j48Tree J48 string output
     * @return The tree size
     */
    public static int getTreeSize(String j48Tree)
    {
        String[] splitStr = j48Tree.split("Size of the tree : ");
        //In the second element, parse the number out
        return Integer.parseInt(splitStr[1].trim());
    }

    /**
     * Converts attribute indexes ('aN') from a genome string from N to N + 1 to fit into WEKA style.
     *  Attributes are labeled by numbers beginning at 1 in the GUI so this method makes the GP generated
     *  attributes fit that.
     * @param genome Genome string
     * @return a genome string with attribute indexes which fit WEKA
     */
    public static String formatGenomeString(String genome)
    {
        String retStr = ""; //Initialise the return string
        String[] splitStr = genome.split(" ");  //Split the string into tokens
        //Look through each token and check if it is an attribute identifier; also rebuild the string as we go
        for(String token : splitStr)
        {
            if(token.matches("a[0-9]+"))
            {
                //If number is an attribute token; add 1 to the number
                int attrIndex = Integer.parseInt(token.substring(1));
                attrIndex++;
                token = "a" + attrIndex;
            }
            retStr = retStr + " " + token;
        }

        return retStr.trim();
    }

    /**
     * Counts occurences of a character in a string.
     * Source: {@link http://stackoverflow.com/questions/275944/how-do-i-count-the-number-of-occurrences-of-a-char-in-a-string}
     * @param string string to search in
     * @param ch char to search for
     * @return Number of occurences of ch in string
     */
    private static int countOccurrences(String string, char ch)
    {
        int count = 0;
        for (int i=0; i < string.length(); i++)
        {
            if (string.charAt(i) == ch)
            {
                count++;
            }
        }
        return count;
    }

}
