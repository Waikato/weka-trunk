package weka.filters.supervised.attribute.gpattributegeneration;

/**
 * Timer class to replicate some of the C# Stopwatch functionality
 * @author Colin Noakes
 */
public class Timer
{
    //FIELDS
    private final long startTime;
    private long stopTime;
    private long split;

    /**
     * Starts the timer
     */
    public Timer()
    {
        startTime = System.currentTimeMillis();
    }

    /**
     * Gets time elapsed since the timer was started in milliseconds.
     * @return Time since timer was started in milliseconds
     */
    public long getElapsed()
    {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Gets time elapsed since the timer was started, in seconds.
     * @return Time since timer was started in seconds
     */
    public double getElapsedSeconds()
    {
        return (double)getElapsed() / 1000.0;
    }

    /**
     * Gets time elapsed since the timer's split function was used in milliseconds.
     * @return Time since the startSplit() method was called in milliseconds. If the startSplit() method was not called, returns 0;
     */
    public long getElapsedSinceSplit()
    {
        if(split == 0)
        {
            return 0;
        }

        return System.currentTimeMillis() - split;
    }

    /**
     * Gets time elapsed since the timer's split function was used, in seconds.
     * @return Time since the startSplit() method was called in seconds. If the startSplit() method was not called, returns 0;
     */
    public double getElapsedSinceSplitSeconds()
    {
        return (double)getElapsedSinceSplit() / 1000.0;
    }

    /**
     * Starts a split timer
     */
    public void startSplit()
    {
        split = System.currentTimeMillis();
    }

    /**
     * Stops the timer
     */
    public void stop()
    {
        stopTime = System.currentTimeMillis();
    }

    /**
     *
     * @return Total time the timer ran for, in milliseconds
     */
    public long getTotalTime()
    {
        return stopTime - startTime;
    }

    /**
     *
     * @return Total time the timer ran for, in seconds
     */
    public double getTotalTimeSeconds()
    {
        return (double)getTotalTime() / 1000.0;
    }

}
