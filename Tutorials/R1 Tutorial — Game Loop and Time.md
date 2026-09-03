# R1 — Game Loop and Time

A line\-by\-line tutorial: what each step does, why it exists, and what breaks if you skip it

## What R1 actually builds

R0 left you with a window that opens, clears itself to a solid color every frame, and closes cleanly. It has no notion of *time* — the `while` loop just runs once per `glfwPollEvents()`/`glfwSwapBuffers()` pair, as fast as v\-sync allows. That's fine when nothing is being simulated, but the moment you add anything that changes over time — physics, animation, a villager walking, a day/night cycle — "once per rendered frame" becomes a trap: your simulation's speed would depend on your framerate, which depends on the GPU, the window size, whether v\-sync is on, how many other windows are open. Two players on different machines would see different game behavior, not just different smoothness.

R1's job is to fix that, once, in one small, deliberately boring piece of code — a **fixed\-timestep accumulator** — and then wire it into the loop so that from here on, simulation time and render time are two separate things that just happen to run in the same loop. Nothing simulated yet: R1 is pure plumbing. The `update()` method it introduces does nothing — R16 onward is what actually puts logic inside it. What R1 gives you is the *seam* itself, built correctly, tested in isolation, and proven out with a pause key you can press right now.

We'll build this on top of the actual merged R0 code — `Main.java` as it exists on `development` after your PR, not the original tutorial's idealized version — so what's below matches your repository exactly.

## Prerequisites

- R0 merged and working — you should be able to run the app (from the IDE or `mvn compile exec:java "-Dexec.mainClass=forestsettlement.Main"`), see the window, and close it with Escape or the close button.
- Nothing new to install beyond what R0 already set up (Java 21, Maven, LWJGL, tinylog) — R1 adds one new test\-scope dependency, covered in Step 1.

## Step 1 — Adding JUnit 5 to the build

### What we're doing

Add a test\-scoped dependency and pin a Surefire version that understands it:

```xml
<dependencies>
    <!-- ... existing LWJGL and tinylog dependencies stay as they are ... -->

    <!-- Testing -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>6.1.3</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- ... existing exec-maven-plugin stays as it is ... -->

        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.5.6</version>
        </plugin>
    </plugins>
</build>
```

### Why

R1's acceptance criteria explicitly require the timing code to be "isolated enough to unit test without a window" — that's not a suggestion, it's the thing this release is graded on, so we need an actual test runner before writing the class it's testing. `junit-jupiter` is the aggregate artifact that pulls in the JUnit 5 API, engine, and parameterized\-test support in one dependency — the standard, unglamorous choice for a new Java project in 2026, with no meaningful competing option worth considering here. `scope=test` keeps it (and everything it pulls in) off your actual runtime classpath — it exists only while compiling and running tests, never ships in the game itself.

Surefire is the Maven plugin that actually runs `mvn test`. Recent versions (2.22.0\+) auto\-detect the JUnit Platform and need no separate provider dependency, but your project doesn't declare *any* Surefire version yet — it would silently fall back to whatever version ships with your local Maven installation, which on an older Maven install can predate JUnit 5 support entirely. Pinning `3.5.6` explicitly is the same reproducibility argument you already applied to `exec-maven-plugin` in R0: anyone building this project, on any machine, gets the same behavior.

### What happens if you skip it

Without the dependency, a test class using `@Test` simply won't compile — `import org.junit.jupiter.api.Test;` resolves to nothing. Without pinning Surefire, the failure is quieter and worse: on a machine with an old bundled Surefire, `mvn test` can report "no tests to run" and exit successfully, silently skipping every test you write — the acceptance criterion looks satisfied by inspection but isn't actually enforced by your build.

## Step 2 — The FixedTimestep class: fields and constructor

### What we're doing

New file, `src/main/java/forestsettlement/time/FixedTimestep.java`\:

```java
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
}
```

### Why

This class lives in its own package, `forestsettlement.time`, with **zero imports from `org.lwjgl.*`** — no GLFW, no OpenGL, nothing native. That's deliberate and it's the whole point: R1's acceptance criterion says this code must be testable without a window, and the only way to guarantee that is to make it structurally impossible for it to depend on one. It doesn't know what a window is. It takes a number (seconds elapsed) and produces a number (how many simulation steps to run) — that's the entire contract.

`stepSeconds` defaults to `1/60` — sixty fixed updates per simulated second, a conventional choice that's fine\-grained enough for smooth\-feeling gameplay logic without being wastefully small. It's `final` and set once in the constructor rather than configurable at runtime, because changing your simulation's tick rate mid\-run is a much bigger design decision (it changes the meaning of every duration your gameplay code has hard\-coded) than anything R1 needs to support. `MAX_FRAME_TIME_SECONDS` shows up in Step 3 — it's the fix for a specific, real failure mode called "spiral of death," explained there.

### What happens if you skip it

Nothing yet — this step only declares state. The actual behavior, and the actual risk, is in Step 3's `advance()` method. Skipping the *separate package* part specifically (putting this class directly in `forestsettlement` next to `Main`, or worse, as an inner class of `Main`) doesn't break anything today either — but it makes Step 5's unit tests awkward to justify structurally, and it's a standing invitation for a future you to reach for `Main.window` or a GLFW call from inside what's supposed to be pure timing logic, once the two are sitting in the same file.

## Step 3 — advance(): the accumulator, and the spiral\-of\-death clamp

### What we're doing

```java
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
```

### Why

This is the classic fixed\-timestep pattern (Glenn Fiedler's "Fix Your Timestep\!" is the usual reference, and it hasn't materially changed in two decades because the underlying problem hasn't changed): you feed it however much real time just elapsed, it banks that time in `accumulator`, and it hands back "run the simulation this many times" — draining the accumulator one fixed\-size `stepSeconds` chunk at a time. If a frame takes exactly one step's worth of time, you get exactly one update. If a frame runs slow and two and a half steps' worth of time elapse, you get two updates and the leftover half\-step stays banked for next time — nothing is lost, nothing is double\-counted. `alpha()` (the fraction of a step still sitting in the accumulator) isn't used yet in R1, but it's there because R2\+ rendering will want it eventually, for interpolating the visual position of things *between* the last two simulated states, so motion still looks smooth even though updates run at a fixed, coarser rate than the display.

The clamp is the part worth understanding rather than copying. Without it, imagine you pause at a debugger breakpoint for ten seconds, or the OS briefly suspends your process, or — concretely, something you already have direct experience with from R0 — the user drags an edge of the window to resize it. Your loop's `while` body doesn't run during a Windows live\-resize drag (that's exactly the modal\-loop issue R0's `glfwSetWindowRefreshCallback` works around for rendering), so when the drag ends and the loop resumes, `frameTimeSeconds` for that one frame is however long the entire drag took — potentially seconds. Fed straight into the accumulator, that would demand hundreds of catch\-up steps in a single frame; each one takes real time to run, which produces the *next* frame's `frameTimeSeconds`, which demands even more catch\-up steps than before — a runaway feedback loop that never recovers, called the "spiral of death." Clamping the input to at most a quarter\-second means the worst case is "the simulation visibly hitches and picks up roughly where it should," not "the app locks up forever." A quarter\-second is generous enough to never clip a normal frame (even 15 FPS is 0.067s/frame) while still bounding the worst case to a handful of steps.

### What happens if you skip it

Skip the clamp specifically, and the class works perfectly in every quick manual test you run — until the first time someone resizes the window, or your IDE pauses the JVM for a GC log, or a laptop goes to sleep mid\-run. Then the game appears to hang: it's not actually frozen, it's burning CPU trying to run thousands of queued\-up simulation steps as fast as possible before it can render another frame, and depending on how slow each step is, "eventually catches up" can mean anywhere from a visible stutter to something indistinguishable from a crash. This is a well\-known enough failure mode that it has a name, and it's specifically the kind of bug that never shows up in your first week of testing and then reliably shows up the first time someone else runs your game.

## Step 4 — Pause and time\-scale

### What we're doing

```java
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
```

(These sit alongside the getters from Step 3 — the full class is assembled at the end of Step 5.)

### Why

R1's requirement is specifically "pause and a basic time\-scale hook (even if only `0x`/`1x` for now)" — and the implementation above gives you both from one mechanism. Look back at Step 3's accumulation line: `accumulator += frameTimeSeconds * (paused ? 0.0 : timeScale)`. Pausing isn't a separate code path that skips the accumulator — it's `timeScale` forced to `0` for that frame, expressed as its own explicit flag rather than making callers remember to call `setTimeScale(0)` and separately remember what value to restore. That distinction matters for *why* it's implemented as a boolean rather than just leaning on `timeScale` alone: "paused" is a clear, named intent a keybinding or a menu can toggle without needing to remember what speed to resume at, while `timeScale` stays free for R1's other half of the requirement — a `2x`/`0.5x` hook you're not using yet, but isn't precluded either.

Crucially, pausing this way never touches `accumulator` itself. Compare that to a naive pause implementation that just skips calling `advance()` entirely while paused — that would work too, *except* your loop still needs to measure `frameTime` every frame to keep `glfwGetTime()` deltas correct (Step 6), so you'd end up with special\-cased branching in the loop itself instead of one clean, always\-called method. Keeping `advance()` unconditionally called, with pause handled *inside* it, is what R1's acceptance criterion means by "pausing halts simulation time without corrupting the accumulator" — nothing gets reset, nothing gets skipped incorrectly, the accumulator is just frozen in place.

### What happens if you skip it

Without pause, there's no way to verify the accumulator ever holds state correctly across a gap in real time — which is precisely what R1 asks you to prove, and precisely what a debugger breakpoint, an OS\-level stall, or (again) a window resize will do to your real, unpaused game whether or not you ever meant to test for it. Building the pause hook now means you have a manual, on\-demand way to reproduce "time passed without simulation running" *safely*, on purpose, whenever you want — rather than the first time you observe it being an actual bug you didn't expect.

## Step 5 — Unit\-testing FixedTimestep without a window

### What we're doing

New file, `src/test/java/forestsettlement/time/FixedTimestepTest.java`\:

```java
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
```

Run it with:

```bash
mvn test
```

### Why

Each test targets one specific claim from R1's acceptance criteria, deliberately narrow rather than one big end\-to\-end test: exact\-step alignment, sub\-step accumulation, multi\-step catch\-up, the spiral\-of\-death clamp, and the two pause behaviors (freezes without resetting, resumes without losing the banked time). None of them touch `Main`, GLFW, or a window — `FixedTimestep` is constructed directly with hand\-picked numbers standing in for "time since last frame," which is exactly what "isolated enough to unit test without a window" means in practice, not just in principle. This is also the fastest possible feedback loop for a bug in this logic: these six tests run in well under a second, versus manually launching the game, dragging the window around, and eyeballing whether the pause key behaved correctly.

### What happens if you skip it

The class still works — R1 doesn't strictly require the tests to exist for the *game* to run correctly. But the acceptance criterion explicitly asks for it, and there's a sharper practical reason beyond satisfying a checklist: the spiral\-of\-death clamp is the kind of bug that is nearly invisible by inspection (the math looks fine at a glance) and only manifests under a specific real\-world condition you might not hit again for weeks — right up until a player's machine stalls for a few seconds and the game appears to hang. A five\-line test that directly exercises "what happens with a 10\-second frame time" catches that in under a second, every time you run `mvn test`, instead of relying on remembering to manually reproduce it.

## Step 6 — Measuring real elapsed time with glfwGetTime()

### What we're doing

In `Main.java`, add the field and the two lines that open `loop()`\:

```java
private final FixedTimestep clock = new FixedTimestep();
```

```java
private void loop() {
    GL.createCapabilities();

    if (SystemProperties.DEBUG_MODE) {
        debugCallback = GLUtil.setupDebugMessageCallback(System.err);
    }

    glClearColor(0.10f, 0.11f, 0.13f, 1.0f);
    glfwSetWindowRefreshCallback(window, win -> render());

    double previousTime = glfwGetTime();

    // ... the while loop from Step 7 goes here
}
```

Add the import: `import forestsettlement.time.FixedTimestep;`

### Why

`glfwGetTime()` returns a `double`\: seconds elapsed since `glfwInit()` was called, backed by whatever high\-resolution timer the platform provides (GLFW's own docs describe micro\- or nanosecond resolution on the platforms it supports) — far finer\-grained than `System.currentTimeMillis()`, and unaffected by the system clock being adjusted mid\-run the way wall\-clock time can be. That's exactly what `FixedTimestep.advance()` needs as its input: not "what time is it," but "how much time passed since I last checked," measured consistently.

`clock` is declared as a `Main` field — the same pattern R0 already established for `window` and `debugCallback` — because Step 9 needs to reach it from the key callback registered in `init()`, and `loop()` needs it too; a local variable inside `loop()` alone couldn't be shared between the two. It's `final` because the *instance* never needs to change, only its internal state (via the pause/time\-scale setters from Step 4) — the same reasoning as `window` being a plain field rather than something re\-assigned per frame.

`previousTime` is captured once, right before the loop starts, as the baseline the very first frame's elapsed time is measured against.

### What happens if you skip it

Without a real time source, `FixedTimestep.advance()` has nothing meaningful to advance by — you'd either have to fake a fixed value every frame (which defeats the entire point: your simulation would run at a rate tied to render framerate again, exactly what R1 exists to prevent) or reach for `System.currentTimeMillis()`, which is coarser (millisecond resolution, sometimes worse depending on OS) and can jump backward if something adjusts the system clock — a rare but real source of a negative `frameTimeSeconds` that Step 3's clamp only partially protects against (it clamps too\-large values, but a negative one would need its own check, which is exactly why `advance()` clamps a negative input to `0.0` too).

## Step 7 — Wiring FixedTimestep into the render loop

### What we're doing

The full `loop()` method, replacing R0's version:

```java
private void loop() {
    GL.createCapabilities();

    if (SystemProperties.DEBUG_MODE) {
        debugCallback = GLUtil.setupDebugMessageCallback(System.err);
    }

    glClearColor(0.10f, 0.11f, 0.13f, 1.0f);
    glfwSetWindowRefreshCallback(window, win -> render());

    double previousTime = glfwGetTime();

    while (!glfwWindowShouldClose(window)) {
        double currentTime = glfwGetTime();
        double frameTime = currentTime - previousTime;
        previousTime = currentTime;

        int steps = clock.advance(frameTime);
        for (int i = 0; i < steps; i++) {
            update(clock.stepSeconds());
        }

        render();
        glfwPollEvents();

        frameCount++;
    }
}
```

### Why

This is the seam R1 exists to build, made concrete: every iteration measures real elapsed time (`frameTime`), hands it to the accumulator, and runs `update()` however many times `advance()` says to — zero times on a fast frame with time left over, once on a typical frame, more than once on a slow one, and at most a handful even on the worst stall, thanks to Step 3's clamp. `render()` still runs exactly once per loop iteration regardless — rendering stays tied to your actual display refresh rate (v\-sync, from R0), while simulation now runs at its own fixed, framerate\-independent rate. That decoupling is the entire deliverable of R1.

Notice what *doesn't* change: `glfwSetWindowRefreshCallback` still calls `render()` directly, exactly as R0 left it, with no `update()` call anywhere near it. That's intentional, not an oversight — a mid\-drag refresh during a window resize should redraw the *current* state, not advance the simulation, and the accumulator will correctly catch up on its own the moment the outer loop resumes (see Step 3's explanation of exactly this scenario). You get a very concrete, free verification of the clamp working correctly the next time you resize the window: watch the console for the diagnostic line Step 10 adds, and you'll see a brief burst of updates immediately after you release the mouse, not a hang.

### What happens if you skip a piece

- Skip capturing `frameTime` each iteration (reuse a stale value, or hardcode one) → you're back to framerate\-coupled simulation, silently, with no error — just increasingly wrong behavior as your actual framerate drifts from whatever you assumed.
- Call `update()` unconditionally once per loop iteration instead of `steps` times → this is the single most common way people accidentally reinvent the exact bug R1 is designed to eliminate; it looks identical to correct code until you test on a machine with a different refresh rate.
- Forget the `for` loop and just call `update()` when `steps > 0` → simulation runs at *render* rate whenever more than a step is due, and silently drops updates whenever multiple steps accumulate (a slow frame simulates only one step's worth of game time instead of catching up) — a subtler version of the same framerate\-coupling bug.

## Step 8 — The update() seam

### What we're doing

```java
private void update(double dt) {
    // Nothing to simulate yet — R1 only builds the seam.
    // R16 onward hooks real simulation systems in here.
}
```

### Why

This method is deliberately empty. R1's Explicitly\-out\-of\-scope list is direct about this: no in\-game calendar yet, this is raw engine time, not game time. Everything from R2 through R15 is rendering, assets, input and world\-building work that doesn't need a per\-tick simulation callback yet — `update()` sits here, unused, until R16 introduces the first system that actually needs it. What matters for R1 is that the *signature* is right: it takes `dt` (always `clock.stepSeconds()`, a fixed value — never the raw, variable `frameTime`) so that whatever eventually goes in this method can assume a constant, predictable timestep no matter how erratic real framerates get. That guarantee — every call to `update()` represents exactly the same amount of simulated time — is the actual payoff of everything built in Steps 2–7.

### What happens if you skip it

You could inline the (currently empty) body directly into Step 7's `for` loop instead of extracting a method, and nothing would behave differently today. The method exists now, before there's anything to put in it, specifically so R16 has an obvious, unambiguous, already\-correctly\-wired place to add real logic — rather than someone (quite possibly you, months from now, having forgotten these details) needing to rediscover where in `loop()` a per\-tick hook belongs and re\-deriving why it has to be called `steps` times with a fixed `dt` rather than once with a variable one.

## Step 9 — Pausing from the keyboard

### What we're doing

Extend R0's key callback (from its Step 7) in `init()`\:

```java
glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
    if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
        glfwSetWindowShouldClose(win, true);
    }

    if (key == GLFW_KEY_SPACE && action == GLFW_RELEASE) {
        clock.setPaused(!clock.isPaused());
        Logger.info("Simulation {}", clock.isPaused() ? "paused" : "resumed");
    }
});
```

### Why

This is the manual, on\-demand way to exercise R1's pause acceptance criterion against the *real* running app, not just the unit tests from Step 5 — press Space, and `clock.isPaused()` flips; Step 10's diagnostic logging will show the update count stop climbing while the frame count keeps going, which is the concrete, observable proof that rendering and simulation really are decoupled. Routing the confirmation through `Logger.info(...)` rather than skipping it is the same habit R0 already established: every state change worth knowing about goes through tinylog, so it's in your log file too, not just something you had to be watching the console at the right moment to see.

This callback closure can reach `clock` directly because it's a `Main` field (Step 6) rather than a local variable inside `loop()` — the callback is registered in `init()`, which runs *before* `loop()` even starts, so a local variable wouldn't exist yet for the closure to capture.

### What happens if you skip it

`FixedTimestep`'s pause behavior stays fully correct and fully tested (Step 5 doesn't depend on this at all) — but you'd have no way to see it working in the actual running application without writing a throwaway test harness or waiting for a real stall to happen. Not a functional gap, but a verification one: R1's acceptance criteria ask you to confirm this behavior, and confirming it by eye, on demand, is a lot more convincing than trusting six passing unit tests you can't currently observe against the real game loop.

## Step 10 — Diagnostics: FPS/UPS logging

### What we're doing

The complete `loop()` method, final form for R1:

```java
private void loop() {
    GL.createCapabilities();

    if (SystemProperties.DEBUG_MODE) {
        debugCallback = GLUtil.setupDebugMessageCallback(System.err);
    }

    glClearColor(0.10f, 0.11f, 0.13f, 1.0f);
    glfwSetWindowRefreshCallback(window, win -> render());

    double previousTime = glfwGetTime();
    double diagnosticsTimer = 0.0;
    long framesThisSecond = 0;
    long updatesThisSecond = 0;

    while (!glfwWindowShouldClose(window)) {
        double currentTime = glfwGetTime();
        double frameTime = currentTime - previousTime;
        previousTime = currentTime;

        int steps = clock.advance(frameTime);
        for (int i = 0; i < steps; i++) {
            update(clock.stepSeconds());
            updatesThisSecond++;
        }

        render();
        glfwPollEvents();

        frameCount++;
        framesThisSecond++;

        diagnosticsTimer += frameTime;
        if (diagnosticsTimer >= 1.0) {
            Logger.debug("{} FPS, {} UPS, paused={}", framesThisSecond, updatesThisSecond, clock.isPaused());
            diagnosticsTimer = 0.0;
            framesThisSecond = 0;
            updatesThisSecond = 0;
        }
    }
}
```

### Why

R1's Requirements list "frame and update counters exposed for diagnostics" as its own line item, distinct from R0's `frameCount` (which R0 added purely so its loop had *something* satisfying that same wording, before there was any real update concept to count). This is that requirement done properly: once per real second, log both counters together, then reset them for the next second — a live, readable FPS/UPS line in your console and log file, at `debug` level so it doesn't clutter a normal `info`\-level run but is there the moment you need it (`tinylog.properties`, from R0, already routes `debug` to the rolling file writer). `frameCount` — R0's lifetime counter — is untouched and kept for exactly the diagnostic purpose it was already serving; `framesThisSecond`/`updatesThisSecond` are new, local, and reset every second specifically to produce a *rate*, which is the number actually useful for "is this running smoothly."

This single log line is also your practical verification tool for two things at once: pausing (watch UPS drop to 0 while FPS keeps climbing — see Step 9), and the spiral\-of\-death clamp from Step 3 (resize the window, hold the drag a couple of seconds, release it, and watch for a brief spike in UPS as the accumulator catches up — bounded, not a hang).

### What happens if you skip it

The game runs identically — this step is pure observability, no behavior depends on it. But you lose the cheapest possible way to notice, live, if something's gone wrong with the timing code: a UPS that's stuck at 0 (pause got toggled and you didn't notice, or a bug), a UPS that's climbing without bound (the clamp isn't working), or an FPS that's dropped far below your monitor's refresh rate (a performance problem worth investigating before it gets worse in a later, busier release). Without it, all three of those look identical from the outside: "the window is open and doesn't look wrong."

## Running it

```bash
mvn compile exec:java "-Dexec.mainClass=forestsettlement.Main"
```

(On Windows PowerShell specifically, quote the whole `-D` argument as shown above — see R0's tutorial if you hit an "Unknown lifecycle phase" error, which is a PowerShell quoting issue, not a build problem.)

Watch the console (or the newest file under `logs/`, if you're not running with debug output visible) for a line like:

```
60 FPS, 60 UPS, paused=false
```

once per second. Press **Space** — the next line should show `paused=true` and `0 UPS`, with FPS continuing to climb normally. Press Space again to resume, and confirm UPS picks back up.

Run the unit tests separately:

```bash
mvn test
```

All six `FixedTimestepTest` cases should pass.

## Verifying against R1's acceptance criteria

- **Simulation updates stay stable and reproducible when render frame rate varies.** Covered structurally by the accumulator itself (Step 3) and directly by `oneStepOfFrameTimeProducesExactlyOneUpdate` / `aSlowFrameCatchesUpWithMultipleSteps` (Step 5) — the same `advance()` call produces the same update count for the same elapsed time, regardless of how that time was divided into frames.
- **Pausing halts simulation time without corrupting the accumulator.** `pausingStopsAccumulationWithoutResettingIt` and `resumingContinuesFromWhereItLeftOff` (Step 5) prove this directly; Step 9's Space key lets you confirm it by eye against the real running app via Step 10's UPS counter.
- **The timing/accumulator code is isolated enough to unit test without a window.** `FixedTimestep` (Step 2) has no LWJGL imports at all, and `FixedTimestepTest` (Step 5) never touches `Main`, GLFW, or a window — `mvn test` runs and passes with no window ever created.

## Troubleshooting

**`mvn test` reports "no tests found" or "0 tests run."** Check that `maven-surefire-plugin` is actually declared in `pom.xml` (Step 1) — an old, un\-pinned Surefire version bundled with your local Maven install can silently fail to discover JUnit 5 tests.

**UPS climbs far above \~60 right after resizing, instead of a brief bounded catch\-up.** The clamp in `FixedTimestep.advance()` (Step 3) is missing or was set too high — re\-check `MAX_FRAME_TIME_SECONDS` and that both the `if (frameTimeSeconds < 0.0)` and `if (frameTimeSeconds > MAX_FRAME_TIME_SECONDS)` checks run *before* `accumulator` is updated, not after.

**Pressing Space does nothing.** Confirm the key callback in `init()` actually has the new `GLFW_KEY_SPACE` block (Step 9) — it's easy to accidentally replace R0's `if` for Escape rather than adding a second one alongside it.

**FPS and UPS are wildly different and neither looks like \~60.** If v\-sync (R0's `glfwSwapInterval(1)`) is working, FPS should track your monitor's refresh rate and UPS should track \~60 independently — if FPS is uncapped and very high, double check `glfwSwapInterval(1)` is still being called in `init()`; it's unrelated to R1 but easy to have accidentally removed while editing nearby code.

**`update()` never seems to get called (UPS stays 0 without pausing).** Almost always means `steps` from `clock.advance(frameTime)` is coming back `0` every frame — check that `frameTime` is actually being measured from `glfwGetTime()` each iteration (Step 6/7) rather than a stale or zero value, and that `stepSeconds` wasn't accidentally constructed as something much larger than `1.0/60.0`.

## What's next

R2 — First Programmable Render is where something finally appears on screen: your first GLSL shader pair, VAO/VBO setup, and the first triangle. `update()` stays empty until R16, but `render()` is about to get a lot more interesting.
