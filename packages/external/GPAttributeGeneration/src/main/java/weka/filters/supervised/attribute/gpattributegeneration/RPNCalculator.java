package weka.filters.supervised.attribute.gpattributegeneration;

import java.util.ArrayDeque;
import java.util.Deque;
import weka.core.Instance;

/**
 * A calculator for postfix expressions. Ported from Luke's C#.
 * @author Luke Devonshire
 */
public class RPNCalculator
{
    /**
     * Evaluates a postfix expression.
     * @param expression Expression to evaluate as a space separated string
     * @param instance the instance (row) to
     * @return The answer to the expression given with the specified xValue
     */
    public static double evaluate(String expression, Instance instance)
    {
        // split expression to separate tokens, which represent functions ans variables
        String[] tokens = expression.trim().split(" ");

        // arguments stack
        Deque arguments = new ArrayDeque();

        // walk through all tokens
        for (String token : tokens)
        {
            // check for token type
            //Temp variable to hold the potentially parsed Double
            double[] parsedDouble = tryParse(token);

            if (token.substring(0, 1).equalsIgnoreCase("a"))
            {
                //If the token is an attribute variable ('aN') push the instance value for the Nth attribute onto the stack
                arguments.push(instance.value(Integer.parseInt(token.substring(1, 2))));
            }
            else if (parsedDouble[0] == 1)
            {
                //If the parsed double variable contains a double, push it onto the stack
                arguments.push(parsedDouble[1]);
            }
            else
            {
                // each function has at least one argument, so let's get the top one
                // argument from stack
                double v = (Double)arguments.pop();

                // check for function and apply it to the arguments on the stack
                if("+".equals(token)) { arguments.push((Double)arguments.pop() + v); }
                else if("-".equals(token)) { arguments.push((Double)arguments.pop() - v); }
                else if("*".equals(token)) { arguments.push((Double)arguments.pop() * v); }
                else if("/".equals(token)) { arguments.push((Double)arguments.pop() / v); }
                else if("^".equals(token)) { arguments.push(Math.pow(v, (Double)arguments.pop())); }
                else if("sin".equals(token)) { arguments.push(Math.sin(v)); }
                else if("cos".equals(token)) { arguments.push(Math.cos(v)); }
                else if("ln".equals(token)) { arguments.push(Math.log(v)); }
                else if("exp".equals(token)) { arguments.push(Math.exp(v)); }
                else if("sqrt".equals(token)) { arguments.push(Math.sqrt(v)); }
                else
                {
                    // throw exception informing about undefined function
                    throw new IllegalArgumentException("Unsupported function: " + token);
                }
            }
        }

        // check stack size
        if (arguments.size() != 1)
        {
            throw new IllegalArgumentException("Incorrect expression.");
        }

        // return the only value from stack

        //check for infinity occurence return 0 in the case of infinity or NaN
        if ((Double.isInfinite((Double)arguments.peek()) || Double.isNaN((Double)arguments.peek())))
        {
            return 0.0;
        }
        else
        {
            //The answer should be on top of the stack; return it
            return (Double)arguments.pop();
        }
    }

    /**
     * Attempts to parse a double. Array[0] contains 1.0 if operation was successful and 0 if it wasn't.
     * If successful returns an array in which the value in array[1] is the double
     * @param token String to attempt to parse
     * @return Array[0] is result of operation; array[1] is parsed double (if op was successful)
     */
    private static double[] tryParse(String token)
    {
        double[] arr = new double[2];

        try
        {
            arr[1] = Double.parseDouble(token);
        }
        catch (NumberFormatException ex)
        {
            arr[0] = 0;
            return arr;
        }

        arr[0] = 1;
        return arr;
    }
}
