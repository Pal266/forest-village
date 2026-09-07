package forestsettlement.time;

public class FixedTimestep {

    public static final double DEFAULT_STEP_SECONDS = 1.0 / 60.0;
    private static final double MAX_FRAME_TIME_SECONDS = 0.25;

    private final double stepSeconds;

    private double accumulator = 0.0;
    private long updateCount = 0;

    private boolean paused = false;
    private double timeScale = 1.0;

    public FixedTimestep() {
        this(DEFAULT_STEP_SECONDS);
    }

    public FixedTimestep(double stepSeconds) {
        this.stepSeconds = stepSeconds;
    }

    public int advance(double frameTimeSeconds) {
        if (frameTimeSeconds < 0.0) {
            frameTimeSeconds = 0.0;
        }
        if (frameTimeSeconds > MAX_FRAME_TIME_SECONDS) {
            frameTimeSeconds = MAX_FRAME_TIME_SECONDS;
        }

        accumulator += frameTimeSeconds * (paused ? 0.0 : timeScale);

        int steps = 0;
        while (accumulator >= stepSeconds) {
            accumulator -= stepSeconds;
            steps++;
            updateCount++;
        }
        return steps;
    }

    public double stepSeconds() {
        return stepSeconds;
    }

    public double accumulatorSeconds() {
        return accumulator;
    }

    public double alpha() {
        return accumulator / stepSeconds;
    }

    public long updateCount() {
        return updateCount;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public double timeScale() {
        return timeScale;
    }

    public void setTimeScale(double timeScale) {
        this.timeScale = timeScale;
    }
}
