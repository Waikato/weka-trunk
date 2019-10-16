package weka.filters.supervised.attribute.gpattributegeneration;

import java.util.HashMap;
import static org.junit.Assert.assertEquals;
import org.junit.*;

/**
 *
 * @author Colin
 */
public class ParseTest {

    public ParseTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of parseJ48 method, of class Parse.
     */
    @Test
    public void testParseJ48() {
        System.out.println("parseJ48");
        String treeResult = "J48 pruned tree\r\n------------------\r\n\r\n"
            + "GP: physician-fee-freeze = n: democrat (253.41/3.75)\r\n"
            + "GP: physician-fee-freeze = y\r\n"
            + "|   GP: synfuels-corporation-cutback = n: republican (145.71/4.0)\r\n"
            + "|   GP: synfuels-corporation-cutback = y\r\n"
            + "|   |   GP: mx-missile = n (6.03/1.03)\r\n"
            + "|   |   GP: mx-missile = y: democrat (6.03/1.03)\r\n\r\n"
            + "Number of Leaves  : 	22\r\n\r\n"
            + "Size of the tree : 	33";

        //Fill the expected result hashmap
        HashMap expResult = new HashMap<String, Integer>();
        expResult.put("GP: physician-fee-freeze", new Integer(0));
        expResult.put("GP: synfuels-corporation-cutback", new Integer(1));
        expResult.put("GP: mx-missile", new Integer(2));

        HashMap result = Parse.parseJ48(treeResult);
        assertEquals(expResult, result);
        System.out.println("parseJ48: Success");
    }
}
