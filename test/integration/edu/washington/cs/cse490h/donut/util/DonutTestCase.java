package edu.washington.cs.cse490h.donut.util;

/**
 * Donut closure for running tests
 * @author alevy
 *
 */
public abstract class DonutTestCase extends DonutClosure {
    public abstract void test() throws Exception;
    
    @Override
    public void run() throws Exception {
        test();
    }
}