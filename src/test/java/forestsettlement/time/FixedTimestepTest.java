package forestsettlement.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedTimestepTest {

    @Test
    void oneStepOfFrameTimeProducesExactlyOneUpdate() {
        FixedTimestep clock = new FixedTimestep(1.0 / 60.0);

        int steps = clock.advance(1.0 / 60.0);

        assertEquals(1, steps);
        assertEquals(1, clock.updateCount());
    }

    @Test
    void partialStepAccumulatesWithoutProducingAnUpdate() {
        FixedTimestep clock = new FixedTimestep(1.0 / 60.0);

        int steps = clock.advance(1.0 / 120.0);

        assertEquals(0, steps);
        assertEquals(0, clock.updateCount());
        assertTrue(clock.accumulatorSeconds() > 0.0);
    }

    @Test
    void aSlowFrameCatchesUpWithMultipleSteps() {
        FixedTimestep clock = new FixedTimestep(1.0 / 60.0);

        int steps = clock.advance(2.5 * (1.0 / 60.0));

        assertEquals(2, steps);
        assertEquals(2, clock.updateCount());
    }

    @Test
    void aHugeFrameSpikeIsClampedInsteadOfRequestingHundredsOfSteps() {
        FixedTimestep clock = new FixedTimestep(1.0 / 60.0);

        // simulates a 10-second debugger breakpoint, OS-level stall, or a long window-resize drag
        int steps = clock.advance(10.0);

        assertTrue(steps < 20, "expected the frame-time clamp to cap the catch-up, got " + steps + " steps");
    }

    @Test
    void pausingStopsAccumulationWithoutResettingIt() {
        FixedTimestep clock = new FixedTimestep(1.0 / 60.0);

        clock.advance(1.0 / 120.0); // half a step, still un-consumed
        double accumulatorWhilePaused = clock.accumulatorSeconds();

        clock.setPaused(true);
        clock.advance(5.0); // whole seconds pass, but the clock is paused

        assertEquals(accumulatorWhilePaused, clock.accumulatorSeconds(), 1e-12,
                "a paused clock must not accumulate simulation time");
    }

    @Test
    void resumingContinuesFromWhereItLeftOff() {
        FixedTimestep clock = new FixedTimestep(1.0 / 60.0);

        clock.advance(1.0 / 120.0); // half a step
        clock.setPaused(true);
        clock.advance(5.0);        // ignored while paused
        clock.setPaused(false);

        int steps = clock.advance(1.0 / 120.0); // the other half-step

        assertEquals(1, steps,
                "the accumulator should pick up exactly where it paused, needing only the remaining half-step");
    }
}