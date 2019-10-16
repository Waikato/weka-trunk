package weka.filters.supervised.attribute.gpattributegeneration;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Class to contain list of supported functions and their properties (such as arity)
 * @author Colin Noakes
 */
public class Function
{
    //List of available functions
    private static final ArrayList<String> ALL_FUNCTIONS = new ArrayList<String>(Arrays.asList("+", "-", "*", "/", "^", "sin", "cos", "ln", "exp", "sqrt" ));
    //Stores unary functions as a list
    private static final ArrayList<String> UNARY_FUNCTIONS = new ArrayList<String>(Arrays.asList("sin", "cos", "ln", "exp", "sqrt" ));  //FIND WAY TO INCLUDE + AND - HERE
    //A list of the supported functions (for the help)
    public static final String HELP_ALL_FUNCTIONS = "+, -, *, /, ^, sin, cos, ln, exp, sqrt";

    /**
     * Checks the function is on the allowed list of functions.
     * @param func The function to test
     * @return True if function is valid; false if not
     */
    public static boolean isValid(String func)
    {
        if(ALL_FUNCTIONS.contains(func))
        {
            return true;
        }
        return false;
    }

    /**
     * Checks whether the function is unary or binary
     * @param func The function to test
     * @return True if function is Unary; false if not
     */
    public static boolean isUnary(String func)
    {
        if(UNARY_FUNCTIONS.contains(func))
        {
            return true;
        }
        return false;
    }

    /**
     * An integer denoting the arity of the given function
     * @param func The function to find out the arity of
     * @return The arity of the specified function
     */
    public static int arity(String func)
    {
        if(isUnary(func))
        {
            return 1;
        }
        return 2;
    }

    /**
     * Generates and returns a unary ArrayList from a complete array list
     * @param funcSet The complete array list to make a unary function list out of
     * @return Unary ArrayList of functions
     */
    public static ArrayList<String> getUnaryFuncSet(ArrayList<String> funcSet)
    {
        ArrayList<String> unaryFuncSet = new ArrayList<String>();
        //Check all functions in function set to see if they are unary or binary
        for (String func : funcSet)
        {
            if(Function.isUnary(func))
            {
                unaryFuncSet.add(func);
            }
        }
        return unaryFuncSet;
    }

    /**
     * Generates and returns a binary ArrayList from a complete array list
     * @param funcSet The complete array list to make a binary function list out of
     * @return Binary ArrayList of functions
     */
    public static ArrayList<String> getBinaryFuncSet(ArrayList<String> funcSet)
    {
        ArrayList<String> binaryFuncSet = new ArrayList<String>();
        //Check all functions in function set to see if they are unary or binary
        for (String func : funcSet)
        {
            if(!Function.isUnary(func))
            {
                binaryFuncSet.add(func);
            }
        }
        return binaryFuncSet;
    }



}
