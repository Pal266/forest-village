# R0 — Bootstrap and a Living Window

A line\-by\-line tutorial: what each step does, why it exists, and what breaks if you skip it

## What R0 actually builds

By the end of this tutorial you'll have a Java 21 \+ Maven project that opens a native, resizable window with a working OpenGL context, and shuts down without leaking anything. Nothing is drawn yet — that's R2. R0's entire job is to get the *lifecycle* right: init → run → cleanup, done deterministically, with diagnostics you can trust — including a small tinylog\-based logging setup that writes to both the console and a rolling log file, so a failure is still readable after you've closed the window. Every later release stacks on top of this window, so mistakes here (a leaked callback, a context created on the wrong thread, a swallowed error) tend to resurface much later as confusing, hard\-to\-trace bugs. It's worth doing carefully once.

We'll build the file incrementally, explaining each piece, then show the complete listing at the end.

## Prerequisites

- **JDK 21** installed, with `java -version` and `javac -version` both reporting it
- **Maven** on your `PATH` (`mvn -version`)
- An IDE (IntelliJ IDEA is the most common choice for LWJGL projects, but any IDE with Maven support works)

If you're on **macOS**, keep one fact in mind for later: Cocoa (the OS windowing layer GLFW talks to) requires all windowing calls to happen on the process's *first* thread. We'll come back to this in the "Running it" section — skipping it is the single most common reason a first LWJGL app crashes instantly on a Mac.

## Step 1 — The pom.xml: your build definition

### What we're doing

Maven needs to know three things: which LWJGL modules to put on the classpath, which *native* binaries (the actual C libraries GLFW/OpenGL bindings call into) to bundle for your OS and CPU architecture, and which logging library to pull in so we're never reduced to `System.out.println` for diagnostics. LWJGL ships native code per\-platform, so the dependency list is slightly longer than a typical pure\-Java library; logging adds just two small, dependency\-free jars.

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.forestsettlement</groupId>
  <artifactId>forest-settlement</artifactId>
  <version>0.1.0-R0</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <lwjgl.version>3.4.3</lwjgl.version>
    <tinylog.version>2.7.0</tinylog.version>

    <!-- Set this to match your machine. One of:
         natives-windows, natives-windows-x86, natives-windows-arm64,
         natives-linux, natives-linux-arm64,
         natives-macos, natives-macos-arm64 -->
    <lwjgl.natives>natives-linux</lwjgl.natives>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.lwjgl</groupId>
        <artifactId>lwjgl-bom</artifactId>
        <version>${lwjgl.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <!-- Java-side API jars -->
    <dependency><groupId>org.lwjgl</groupId><artifactId>lwjgl</artifactId></dependency>
    <dependency><groupId>org.lwjgl</groupId><artifactId>lwjgl-glfw</artifactId></dependency>
    <dependency><groupId>org.lwjgl</groupId><artifactId>lwjgl-opengl</artifactId></dependency>

    <!-- Native binaries for your platform -->
    <dependency><groupId>org.lwjgl</groupId><artifactId>lwjgl</artifactId><classifier>${lwjgl.natives}</classifier></dependency>
    <dependency><groupId>org.lwjgl</groupId><artifactId>lwjgl-glfw</artifactId><classifier>${lwjgl.natives}</classifier></dependency>
    <dependency><groupId>org.lwjgl</groupId><artifactId>lwjgl-opengl</artifactId><classifier>${lwjgl.natives}</classifier></dependency>

    <!-- Logging: tinylog -->
    <dependency><groupId>org.tinylog</groupId><artifactId>tinylog-api</artifactId><version>${tinylog.version}</version></dependency>
    <dependency><groupId>org.tinylog</groupId><artifactId>tinylog-impl</artifactId><version>${tinylog.version}</version></dependency>
  </dependencies>
</project>
```

Then create `src/main/resources/tinylog.properties` — tinylog reads this at startup with no code required:

```properties
writer1                 = console
writer1.level            = info
writer1.format           = {date: HH:mm:ss.SSS} {level}: {message}

writer2                  = rolling file
writer2.level             = debug
writer2.file              = logs/forest-settlement_{date:yyyy-MM-dd_HH-mm-ss}.log
writer2.policies          = startup
writer2.format            = {date: yyyy-MM-dd HH:mm:ss.SSS} [{thread}] {level}: {class}.{method}() - {message}
```

This gives you two independent outputs from the same log calls: a short, human\-readable line on the console while you're developing, and a fuller, timestamped, per\-run file under `logs/` for anything you need to look back at — including runs that happened when you weren't watching the console at all.

### Why it's built this way

The `lwjgl-bom` import pins every LWJGL artifact to the same version, so you never end up with `lwjgl-glfw` and `lwjgl-opengl` silently drifting to different releases — a real source of native\-crash bugs in larger projects. Splitting each module into a plain jar plus a classified `natives-*` jar is how LWJGL ships pre\-compiled native libraries for six\-plus platforms without forcing every developer to download all of them.

tinylog is split the same conceptual way LWJGL and SLF4J are: `tinylog-api` is the small, stable set of `Logger.info(...)`/`Logger.error(...)` calls your code makes, and `tinylog-impl` is the backend that actually writes output. That split isn't decorative here — point 3 in "What happens if you skip or misconfigure this" is a real failure mode, not a hypothetical. tinylog was chosen over SLF4J\+Logback and Log4j2 specifically for R0: it's the fastest of the three in its own published benchmarks (especially when logging is disabled at a given level — the check compiles down to a cached boolean, not a method call), it ships as two small, dependency\-free jars instead of pulling in a facade plus a separate backend plus (for Log4j2's async mode) the LMAX Disruptor, and `writer = rolling file` with a `{date}`–stamped filename gets you a separate log file per run with zero custom code — no `RollingFileAppender` XML, no manual file\-naming logic. Log4j2's async loggers have higher peak throughput, but that's a real\-time\-server\-under\-load feature, not something a single\-player game's window bootstrap needs, and it comes with its own footguns (error handling on the async thread, mutable log messages) that aren't worth taking on this early.

If you'll eventually build for multiple operating systems, don't hand\-maintain the LWJGL dependency list — generate it from the official [LWJGL customizer](https://www.lwjgl.org/customize), which produces a correct Maven or Gradle block for whichever modules and platforms you select.

### What happens if you skip or misconfigure this

- **Wrong or missing `natives-*` classifier** → the app compiles fine, then throws `UnsatisfiedLinkError` the moment GLFW tries to load its native library at runtime. This is the single most common "it worked on the tutorial but not for me" issue, and it's always a natives mismatch.
- **No BOM, mismatched versions by hand** → usually still works today, but is a landmine: a future `mvn versions:use-latest-releases` or a copy\-pasted dependency block from an old tutorial can quietly desync the modules, producing native crashes that look nothing like a version problem.
- **`tinylog-api` without `tinylog-impl`** → the app compiles and runs fine, but every `Logger.info(...)`/`Logger.error(...)` call silently does nothing — no console output, no file, no error. This is the logging equivalent of the natives mismatch above: it's not a crash, it's a slow\-motion "why do I have no logs at all" bug the first time you actually need one.
- **No `tinylog.properties` on the classpath** → tinylog falls back to its own built\-in defaults (console only, no file), so the app still runs but you silently lose the per\-run log file this step exists to give you.

## Step 2 — Main.java: the application's shape

### What we're doing

Before writing any GLFW calls, decide the lifecycle shape: an `init()` phase, a `loop()` phase, and cleanup — all inside one method that's easy to read top\-to\-bottom.

```java
package forestsettlement;

import org.lwjgl.Version;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import org.tinylog.Logger;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main {

    private long window;

    public void run() {
        Logger.info("Forest Settlement — LWJGL {}", Version.getVersion());

        init();
        loop();

        // cleanup goes here — Step 15
    }

    private void init() {
        // Steps 3–13 go here
    }

    private void loop() {
        // Step 14 goes here
    }

    public static void main(String[] args) {
        new Main().run();
    }
}
```

### Why it's built this way

`window` is a `long` because GLFW is a C library — LWJGL exposes native window objects as raw handles (memory addresses), not Java objects. This is a pattern you'll see throughout LWJGL: anything backed by native state is an opaque `long` handle you pass back into GLFW/OpenGL functions, never an object you can inspect from the Java side. Separating `init()` from `loop()` from the start also gives you a natural place to put R1's update/render split later — you are not fighting a monolithic `main()` method by then. The startup line uses `Logger.info` with a `{}` placeholder rather than string concatenation — tinylog only builds the final string if the info level is actually enabled for this run, and it's the same call style you'll use everywhere else from now on, so nothing later needs to be retrofitted off `System.out`.

### What happens if you skip it

Nothing breaks immediately — you could write everything inline in `main()`, and you could keep using `System.out.println` instead of `Logger.info`. But every subsequent release adds more setup (asset loading in R11, UI init in R32, and so on) and more places that need diagnostics, and an unstructured `main()` mixed with raw console prints becomes a genuine liability within a few releases — no timestamps, no log levels, no file you can look back at after the console is gone. This costs nothing to do right now and saves real pain later.

## Step 3 — The GLFW error callback

### What we're doing

Add this as the very first line of `init()`\:

```java
GLFWErrorCallback.create((error, description) ->
    Logger.error("GLFW error {}: {}", error, GLFWErrorCallback.getDescription(description))
).set();
```

### Why

GLFW is a C library; it can't throw a Java exception when something goes wrong internally. Instead, it reports errors through a callback function you register. LWJGL's `GLFWErrorCallback.createPrint(PrintStream)` is the usual quickstart version of this — it formats and prints GLFW's error code and description straight to a stream like `System.err`. We use the slightly longer `create(...)` form instead so the same error goes through `Logger.error(...)`\: it lands in the console *and* in the rolling log file from Step 1, with a timestamp and the `ERROR` level, right alongside every other diagnostic the app produces — not on a separate, unlogged channel that disappears the moment you close the console window.

### What happens if you skip it

GLFW failures — an unsupported window hint combination, a monitor query that fails, a context creation error — happen *silently*. You'll see confusing downstream symptoms (a `NULL` window handle, a black screen, an exception three calls later with no obvious cause) with no indication of what GLFW itself actually complained about, and nothing in your log file to look back on afterward. Setting this callback first is cheap insurance that pays for itself the first time something goes wrong — and routing it through the same logger as everything else means that insurance is still readable after the fact, not just visible in a console you happened to be watching at the time.

## Step 4 — Initializing GLFW

### What we're doing

```java
if (!glfwInit()) {
    throw new IllegalStateException("Unable to initialize GLFW");
}
```

### Why

`glfwInit()` sets up GLFW's internal state — it must run, and succeed, before any other GLFW function is called. It can fail on a machine with no display server, no graphics driver, or an unsupported platform, so it returns a boolean rather than assuming success.

### What happens if you skip it

Every subsequent GLFW call either crashes the JVM outright or produces undefined behavior, because GLFW's internal state was never established. This is not a "sometimes" failure — it fails every time, and checking the return value is what turns an obscure native crash into a clear, actionable Java exception at the exact point of failure.

## Step 5 — Window hints

### What we're doing

```java
glfwDefaultWindowHints();
glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

boolean debugBuild = true; // flip to false, or read from a system property, for release builds
if (debugBuild) {
    glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
}
```

### Why

Window hints configure the window *and* the OpenGL context GLFW will create with it, and they must be set before `glfwCreateWindow` — there's no way to change most of them afterward. `GLFW_VISIBLE = false` keeps the window hidden until we've finished configuring it (Step 13 shows it deliberately) — this avoids a visible flash of an unstyled or unpositioned window. `GLFW_RESIZABLE = true` satisfies R0's explicit "resize the window without errors" requirement. `GLFW_OPENGL_DEBUG_CONTEXT` requests a context that supports the `KHR_debug` extension, which Step 11 needs to install readable OpenGL error diagnostics — but you generally want it *off* in a shipped build, since debug contexts carry a small performance cost.

### What happens if you skip it

- Skip `GLFW_VISIBLE = false` → the window appears immediately, un\-positioned (Step 9 hasn't centered it yet), then visibly jumps once you do — a small but very noticeable rough edge.
- Skip `GLFW_RESIZABLE` → the window is fixed\-size by default, silently failing R0's "window can be resized" acceptance criterion.
- Skip the debug\-context hint → Step 11's debug callback either won't fire or will fall back to a much less useful polling\-based mechanism, depending on your driver — you lose the readable diagnostics R0 asks for.

## Step 6 — Creating the window

### What we're doing

```java
window = glfwCreateWindow(1280, 720, "Forest Settlement", NULL, NULL);
if (window == NULL) {
    throw new RuntimeException("Failed to create the GLFW window");
}
```

### Why

This is the call that actually creates the native window and its associated OpenGL context, using every hint set in Step 5. The two `NULL` arguments mean "not fullscreen on a specific monitor" and "don't share resources with another context" — both irrelevant to R0. Checking for `NULL` matters because window/context creation is exactly the kind of call that can fail on a machine with an unsupported or misconfigured graphics driver — precisely the failure Step 3's error callback will explain.

### What happens if you skip the null check

Every GLFW call from here on takes `window` as an argument. Passing a `NULL` handle into them doesn't fail cleanly — you get undefined native\-level behavior, typically a hard JVM crash with a native stack trace instead of a clean Java exception naming the actual problem.

## Step 7 — The key callback (closing on Escape)

### What we're doing

```java
glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
    if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
        glfwSetWindowShouldClose(win, true);
    }
});
```

### Why

`glfwSetWindowShouldClose` doesn't close the window itself — it sets a flag that our render loop (Step 14) checks each iteration to decide whether to keep running. This callback is the input side of that: it listens for key events and sets the flag when Escape is released. R0's acceptance criteria explicitly require both the window's close button *and* a clean programmatic exit path — this callback is the latter.

### What happens if you skip it

The window can still be closed via its OS\-level close button (the X, or Cmd/Alt\+F4), because GLFW sets the should\-close flag for that automatically. But there's no keyboard\-driven way to exit, which is a real usability gap during development — you'll be creating and closing this window hundreds of times as you iterate.

## Step 8 — The framebuffer\-size callback

### What we're doing

```java
glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
    glViewport(0, 0, width, height);
});
```

### Why

The *window* size (in screen coordinates) and the *framebuffer* size (in actual pixels) are not the same thing on high\-DPI displays — a 1280×720 window can back a 2560×1440 framebuffer on a Retina/HiDPI screen. OpenGL's viewport must be set in framebuffer pixels, so we listen for framebuffer\-size changes specifically, not window\-size changes, and update `glViewport` every time the window is resized. This is exactly what R0's "window can be resized without errors" criterion is checking.

### What happens if you skip it

The OpenGL viewport stays fixed at whatever size it was when the context was created. Resize the window and — once you're actually drawing something from R2 onward — the rendered image stretches, squashes, or only fills part of the window instead of matching it. It's a classic "why does my triangle look wrong after I resized the window" bug, and it's entirely avoidable by wiring this callback now, before there's anything to render.

## Step 9 — Centering the window

### What we're doing

```java
try (MemoryStack stack = stackPush()) {
    IntBuffer pWidth = stack.mallocInt(1);
    IntBuffer pHeight = stack.mallocInt(1);
    glfwGetWindowSize(window, pWidth, pHeight);

    GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
    glfwSetWindowPos(
        window,
        (vidmode.width() - pWidth.get(0)) / 2,
        (vidmode.height() - pHeight.get(0)) / 2
    );
}
```

### Why

This is the first place we touch LWJGL's `MemoryStack` — worth understanding now because you'll use it constantly. GLFW's C functions return values like width/height through output parameters (pointers), not Java return values. LWJGL represents those as small native buffers. `MemoryStack.stackPush()` allocates temporary native memory for those buffers off a thread\-local stack and automatically frees it when the `try`\-with\-resources block ends — no manual `free()` call, no leak, no garbage\-collector involvement, because this memory is off the native heap entirely. We use it here to ask GLFW for the window's current size and the primary monitor's resolution, then center the window between them.

### What happens if you skip it

Nothing breaks — this step is cosmetic. GLFW places new windows at a platform\-decided default position, which is often fine, but can also default to a corner or an awkward spot depending on OS and window manager. It's optional polish, included here mainly because it's the cleanest first place to introduce `MemoryStack`, a pattern that stops being optional once you're querying things every frame later on.

## Step 10 — Making the context current, and v\-sync

### What we're doing

```java
glfwMakeContextCurrent(window);
glfwSwapInterval(1);
```

### Why

A machine can have multiple OpenGL contexts (imagine several windows), but OpenGL calls are implicit about *which* context they target — they act on whatever context is "current" on the calling thread. `glfwMakeContextCurrent` binds this window's context to the current thread so that every OpenGL call from here on affects this window. `glfwSwapInterval(1)` requests v\-sync: the driver will wait for the display's vertical blank before swapping buffers, capping the frame rate to the monitor's refresh rate and eliminating tearing. This satisfies R0's explicit "v\-sync configuration" requirement.

### What happens if you skip it

- Skip `glfwMakeContextCurrent` → every OpenGL call in Step 11 onward fails, because there is no current context for them to act on. This typically manifests as calls silently doing nothing, or LWJGL throwing an `IllegalStateException` complaining that no `GLCapabilities` instance is current for the calling thread (which is directly caused by skipping this step, since capabilities are tied to the current context).
- Skip `glfwSwapInterval` → the render loop runs completely uncapped once real rendering starts in R2\+, burning CPU/GPU for no visual benefit and often tearing visibly. Harmless at R0 (nothing is drawn yet) but worth setting correctly now rather than chasing a "why is my fan spinning up" question later.

## Step 11 — Creating OpenGL capabilities

### What we're doing

At the top of `loop()` (or right after Step 10, either works — we'll put it in `loop()` to keep `init()` focused on window setup):

```java
GLCapabilities caps = GL.createCapabilities();
```

### Why

LWJGL doesn't statically link against a specific OpenGL implementation — it discovers, at runtime, which OpenGL functions your graphics driver actually supports, and dynamically binds Java methods to their native function pointers. `GL.createCapabilities()` does that discovery for the context made current in Step 10 and returns a `GLCapabilities` object describing what's available. Every `GL11`/`GL30`/etc. static method you call afterward relies on this having run first.

### What happens if you skip it

Every OpenGL function call — `glClear`, `glViewport`, everything — throws an `IllegalStateException` stating that no `GLCapabilities` instance is current. This is one of the most common first\-run errors in LWJGL, and the fix is always the same: make sure `GL.createCapabilities()` ran, on the same thread, after `glfwMakeContextCurrent`.

## Step 12 — The OpenGL debug callback

### What we're doing

Right after creating capabilities, only in development builds:

```java
Callback debugCallback = null;
if (debugBuild) {
    debugCallback = GLUtil.setupDebugMessageCallback(System.err);
}
```

(Keep a reference to `debugCallback` — you'll free it in Step 15.)

### Why

Historically, OpenGL error checking meant manually calling `glGetError()` after every single call and decoding a bare integer — tedious enough that most tutorials skip it, and most bugs go undiagnosed as a result. `GLUtil.setupDebugMessageCallback` uses the `KHR_debug` extension (available because of the context hint from Step 5) to have the driver *push* human\-readable messages to you automatically — for performance warnings, deprecated\-behavior notices, and genuine errors alike — with no per\-call overhead in your own code. This is exactly R0's "OpenGL debug context/callback in development builds" requirement, and it will save you significant time from R2 onward, once shaders and buffers are in the picture.

### What happens if you skip it

OpenGL doesn't throw exceptions — a mistake (an invalid enum, a buffer bound to the wrong target, a shader compiled with the wrong version) typically just does nothing, or renders incorrectly, with zero indication of what went wrong. Without this callback you're back to manually sprinkling `glGetError()` calls everywhere, or — far more commonly — not checking at all and spending an hour on a black screen that a driver message would have explained in one line.

## Step 13 — Showing the window

### What we're doing

Back in `init()`, as the last line:

```java
glfwShowWindow(window);
```

### Why

Recall Step 5 created the window hidden (`GLFW_VISIBLE = false`). Now that it's positioned (Step 9), has a current context (Step 10), and is otherwise fully configured, we make it visible in one clean step — instead of the user seeing it appear, then jump to its centered position, then (eventually, once R2 renders something) flash from whatever garbage was in an uninitialized framebuffer to your first real frame.

### What happens if you skip it

If you never call `glfwShowWindow`, the window stays hidden forever — the application runs, the loop executes, but nothing is ever visible on screen. It's a common "why is nothing happening" mistake specifically *because* Step 5 deliberately created the window invisible.

## Step 14 — The render loop

### What we're doing

```java
private void loop() {
    GL.createCapabilities();
    if (debugBuild) {
        GLUtil.setupDebugMessageCallback(System.err);
    }

    glClearColor(0.10f, 0.11f, 0.13f, 1.0f);

    long frameCount = 0;
    while (!glfwWindowShouldClose(window)) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glfwSwapBuffers(window);
        glfwPollEvents();

        frameCount++;
    }
}
```

### Why

This is GLFW's standard loop shape, and it matters that the two calls happen in this order for a reason: `glfwSwapBuffers` presents the frame we just drew by swapping the front and back buffers (we render into a hidden "back" buffer so the user never sees a half\-drawn frame), and `glfwPollEvents` processes the OS event queue — mouse, keyboard, window messages — dispatching to whichever callbacks you registered in Steps 7 and 8. Skipping `glfwPollEvents` for even a few seconds makes the OS consider the application unresponsive. `frameCount` is here only so this loop satisfies R0's "frame/update counters for diagnostics" requirement — R1 replaces this with real fixed\-step update/render accounting.

### What happens if you skip a piece

- Skip `glfwPollEvents()` → the window immediately becomes unresponsive: no input is processed, the OS shows "Not Responding," and the close button stops working (though Alt\+F4/force\-quit still works at the OS level).
- Skip `glfwSwapBuffers()` → you never see anything you draw; the back buffer fills correctly but is never presented to the screen.
- Skip the `glClear` call → once real rendering starts (R2\+), each frame draws on top of the previous one instead of a clean slate — a classic smearing/trailing artifact.

## Step 15 — Cleanup

### What we're doing

Back in `run()`, after `loop()` returns:

```java
public void run() {
    Logger.info("Forest Settlement — LWJGL {}", Version.getVersion());

    init();
    loop();

    if (debugCallback != null) {
        debugCallback.free();
    }

    glfwFreeCallbacks(window);
    glfwDestroyWindow(window);

    glfwTerminate();
    glfwSetErrorCallback(null).free();
}
```

### Why

Everything created in this tutorial lives outside the JVM's garbage\-collected heap: the window, its OpenGL context, every callback, GLFW's own internal state, and the debug callback are all native resources. The JVM garbage collector doesn't know about them and can't clean them up — leaving them means leaked native memory and, for callbacks specifically, dangling function pointers that can crash the process if native code ever calls into freed Java\-side memory. `glfwFreeCallbacks` releases every callback registered on this window in one call (key callback, framebuffer\-size callback, and any others), `glfwDestroyWindow` releases the window and its context, `glfwTerminate` releases everything GLFW itself allocated in Step 4, and finally we free the error callback we set in Step 3 — the tinylog\-routing one — deliberately last, so that if anything in the preceding cleanup calls fails, we still get an error message about it, logged the same way as everything else.

### What happens if you skip it

Nothing dramatic on a single run — the OS reclaims process memory when the JVM exits regardless. The real cost shows up during *development*\: repeatedly running the app from an IDE without clean shutdown accumulates native handles across runs in some configurations, and — more importantly — this discipline is what R0's "deterministic cleanup" requirement exists to establish before later releases add far more native resources (textures in R6, buffers in R2/R3, audio in R39) where leaks genuinely do accumulate into real problems, including on a single long session.

## Running it

From the project root:

```bash
mvn compile exec:java -Dexec.mainClass="forestsettlement.Main"
```

(Add the `exec-maven-plugin` to your `pom.xml` if `exec:java` isn't recognized — or simply run `Main.main()` from your IDE, which needs no extra plugin.)

**On macOS specifically**, GLFW requires windowing calls on the JVM's first thread. Add this JVM argument or the app will crash on launch with a message about `NSWindow` / main thread:

```
-XstartOnFirstThread
```

In an IDE, add it to the run configuration's VM options. From the command line: `java -XstartOnFirstThread -cp ... forestsettlement.Main`. This has no effect on Windows or Linux — it's safe to always include it in your run configuration if you want one setup that works across platforms.

Each run also creates a new file under `logs/` (per the `tinylog.properties` from Step 1), named after the run's start time. Check it after the first successful run — it should contain the startup banner from Step 2 at minimum, which is a quick way to confirm logging is wired up correctly before you need it for something that actually went wrong.

## Verifying against R0's acceptance criteria

Before calling R0 done, check each one explicitly:

- **Application starts from both the IDE and Maven.** Run it both ways.
- **Window can be resized without errors.** Drag an edge; confirm no exceptions in the console and (once R2 exists) no stretched image — for now, just confirm no crash and that `glViewport` is being called (a log line in the framebuffer\-size callback is a quick way to check).
- **Escape and the close button both exit normally.** Try both; confirm the process actually terminates rather than hanging.
- **No native\-resource or OpenGL errors during a normal run.** Watch the console for anything from the Step 3 or Step 12 callbacks during ordinary startup/shutdown.

## Troubleshooting

**`UnsatisfiedLinkError` mentioning a `.dll`/`.so`/`.dylib`.** Your `lwjgl.natives` classifier doesn't match your actual OS/architecture. Double check against the list in Step 1, or regenerate your dependency block from the [LWJGL customizer](https://www.lwjgl.org/customize).

**`IllegalStateException: No GLCapabilities instance was created`.** `GL.createCapabilities()` either wasn't called, or was called on a different thread than the one that called `glfwMakeContextCurrent`, or ran before `glfwMakeContextCurrent`. Check ordering and thread.

**Window never appears, no errors.** Almost certainly a missing `glfwShowWindow(window)` call — see Step 13.

**Instant crash on launch, macOS only.** Missing `-XstartOnFirstThread` — see "Running it" above.

**Nothing printed by the debug callback even when you'd expect a warning.** Confirm `GLFW_OPENGL_DEBUG_CONTEXT` was set *before* `glfwCreateWindow` (Step 5) — it cannot be applied after the context exists.

**No `logs/` folder, or it's empty, even though the console shows output.** `tinylog-impl` is missing from `pom.xml` (only `tinylog-api` is present), or `tinylog.properties` isn't on the classpath — see Step 1's "What happens if you skip or misconfigure this".

## What's next

R1 — Game Loop and Time replaces this tutorial's simple `while` loop with a proper fixed\-timestep accumulator, decoupling simulation updates from render frame rate — the seam every future gameplay system in R16 onward depends on.
