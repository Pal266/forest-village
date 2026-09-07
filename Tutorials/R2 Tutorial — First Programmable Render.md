# R2 — First Programmable Render

A line\-by\-line tutorial: what each step does, why it exists, and what breaks if you skip it

## What R2 actually builds

R1 gave you a running heartbeat — real elapsed time, a fixed\-step accumulator, `update()` and `render()` called the right number of times per frame — but `render()` itself still just clears the screen to a flat color. Nothing has ever actually been drawn by this project. R2's job is to draw exactly one thing — a single triangle — by hand, through a shader program you write, compile and link yourself, with nothing hidden behind a helper class yet. That's deliberate: R3 introduces a reusable `Mesh` abstraction specifically to stop you from re\-typing this VAO/VBO/attribute dance for every new shape, but you only get to appreciate what that abstraction is doing for you once you've done the raw version once, in `Main`, with your own hands.

Two things this release is really about, beyond "a triangle appears": first, the actual modern\-OpenGL pipeline — vertex data uploaded to the GPU, a vertex shader that positions each vertex, a fragment shader that colors each pixel, and the plumbing (VAO, VBO, attribute pointers) that connects your data to those shaders. Second, and just as important per R2's acceptance criteria: what happens when a shader is *wrong*. A broken shader has to fail loudly and readably — a clear compile error in your log file — never as a silent black window that gives you no idea what went wrong.

This is built directly against your actual merged `Main.java` — the one with R1's `FixedTimestep`, `clock` field, and diagnostics already in place — not an idealized rewrite.

## Prerequisites

- R1 merged and working — you should see the `FPS, UPS` diagnostic line in your console/log, and Space should pause/resume the simulation.
- Nothing new to install — R2 uses only GLFW/OpenGL bindings already in `pom.xml` since R0.

## Step 1 — The shader source files

### What we're doing

Two new files under `src/main/resources/shaders/`\:

`basic.vert`\:

```glsl
#version 330 core

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 color;

out vec3 vertexColor;

void main() {
    vertexColor = color;
    gl_Position = vec4(position, 1.0);
}
```

`basic.frag`\:

```glsl
#version 330 core

in vec3 vertexColor;
out vec4 fragColor;

void main() {
    fragColor = vec4(vertexColor, 1.0);
}
```

### Why

GLSL (OpenGL Shading Language) source lives in its own files rather than as Java string literals, for the same reason SQL or HTML usually doesn't live embedded in Java strings either: no syntax highlighting, no line numbers that match what the compiler reports, and it makes the shader hard to read at a glance. Living under `src/main/resources` means it ships on the classpath exactly like `system.properties` and `tinylog.properties` already do.

The vertex shader declares two per\-vertex inputs at fixed locations — `position` at `0`, `color` at `1` — matching exactly how Step 6's vertex buffer will be laid out. `vertexColor` is an `out` variable: GLSL automatically interpolates it across each triangle's surface between the three vertices' colors, which is what makes a triangle with three differently\-colored corners render as a smooth gradient rather than three flat\-colored thirds. `gl_Position` is a required built\-in — every vertex shader must write to it, and it's what ultimately decides where each vertex lands on screen (here, unmodified — R4 is what introduces model/view/projection matrices; for now, vertex positions are used directly as clip\-space coordinates, the same way R1's window just clears to a flat color with no transform involved at all).

The fragment shader receives the interpolated `vertexColor` and writes it straight to `fragColor` — the simplest possible fragment shader that still does something visible.

### What happens if you skip it

Get the attribute locations here out of sync with Step 6's `glVertexAttribPointer` calls (say, swap `position` to location `1` and `color` to location `0` in one file but not the other), and the triangle still renders — just with position data being interpreted as color and vice versa, producing a shape in the wrong place with wrong colors rather than an error. This class of bug is silent by nature, which is exactly why Step 9 walks through what a *loud* shader failure looks like, so you can tell the two apart.

## Step 2 — The Shader class: compiling one stage

### What we're doing

New file, `src/main/java/forestsettlement/render/Shader.java`\:

```java
package forestsettlement.render;

import org.tinylog.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL20.*;

public class Shader {

    private static String loadSource(String resourcePath) {
        try (InputStream in = Shader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Shader source not found on classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read shader source: " + resourcePath, e);
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            String stageName = type == GL_VERTEX_SHADER ? "vertex" : "fragment";
            Logger.error("Shader compilation failed ({} stage):\n{}", stageName, log);
            glDeleteShader(shader);
            throw new IllegalStateException("Shader compilation failed — see the log above for details.");
        }

        return shader;
    }
}
```

### Why

`loadSource` mirrors the exact pattern `SystemProperties.loadDebugMode()` already established in R0: read a classpath resource via `getResourceAsStream`, handle a missing file and an I/O failure as two distinct, clearly\-logged cases, rather than letting either one surface as an unexplained `NullPointerException` deep inside OpenGL setup.

`compile` is where R2's "shader compilation and linking with readable, log\-surfaced error reporting" requirement actually lives. `glCompileShader` never throws — like most of the OpenGL API, it fails silently by design, recording a pass/fail status you have to explicitly check with `glGetShaderi(shader, GL_COMPILE_STATUS)`. Skipping that check is how "a typo in your GLSL" turns into "the app runs but nothing appears, with zero indication why." Logging the *full* compiler\-provided info log via `Logger.error` before throwing means the actual line number and reason (GLSL compilers report both) end up in your log file, not just "something went wrong."

Throwing `IllegalStateException` after logging — rather than swallowing the failure and continuing — is deliberate: a broken shader means the application genuinely cannot render as designed, and continuing on with an unusable shader object would just relocate today's clear, immediate failure into a much more confusing one later.

### What happens if you skip it

Skip the `GL_COMPILE_STATUS` check entirely and call `glUseProgram` on a program built from a shader that failed to compile, and OpenGL's behavior here is exactly the failure mode R2's acceptance criteria call out by name: nothing renders, no exception, no log line — just a black or unchanged window, with the actual cause (a GLSL syntax error) completely invisible unless you already knew to go check `glGetShaderInfoLog` by hand.

## Step 3 — Linking the program

### What we're doing

Add to `Shader`\:

```java
private static int link(int vertexShader, int fragmentShader) {
    int program = glCreateProgram();
    glAttachShader(program, vertexShader);
    glAttachShader(program, fragmentShader);
    glLinkProgram(program);

    if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
        String log = glGetProgramInfoLog(program);
        Logger.error("Shader program linking failed:\n{}", log);
        glDeleteProgram(program);
        throw new IllegalStateException("Shader program linking failed — see the log above for details.");
    }

    glDetachShader(program, vertexShader);
    glDetachShader(program, fragmentShader);

    return program;
}
```

### Why

Compiling and linking are two separate steps with two separate failure modes: a shader can compile perfectly in isolation (valid GLSL on its own) and still fail to *link* — the classic case being a vertex shader's `out` variable whose name or type doesn't match the fragment shader's corresponding `in` variable. `glLinkProgram` is where that mismatch actually gets caught, which is why it needs its own status check and its own log\-and\-throw, mirroring Step 2's pattern exactly rather than assuming "both stages compiled, so linking must be fine."

Detaching the shader stages after a successful link (`glDetachShader`) is a correctness detail, not just tidiness: the compiled shader objects (`vertexShader`/`fragmentShader`) are only needed *during* linking — once linked, the program has its own internal copy of what it needs, and the standalone shader objects can be deleted (Step 4 does exactly that, back in `Shader`'s constructor) without affecting the now\-linked program at all.

### What happens if you skip it

Without the link\-status check, a mismatched vertex/fragment interface produces the same category of silent failure as Step 2's missing compile check — a program handle that looks valid (it's a real, non\-zero integer) but produces no correct output when used, with the real cause buried in a log you never asked OpenGL for.

## Step 4 — Tying it together: the constructor, use(), and destroy()

### What we're doing

The rest of `Shader`\:

```java
private final int programId;

public Shader(String vertexResourcePath, String fragmentResourcePath) {
    int vertexShader = compile(GL_VERTEX_SHADER, loadSource(vertexResourcePath));
    int fragmentShader = compile(GL_FRAGMENT_SHADER, loadSource(fragmentResourcePath));

    programId = link(vertexShader, fragmentShader);

    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);
}

public void use() {
    glUseProgram(programId);
}

public void destroy() {
    glDeleteProgram(programId);
}
```

### Why

The constructor reads top to bottom as the whole shader\-build pipeline in five lines: load and compile each stage, link them into a program, then delete the now\-redundant individual shader objects (Step 3's detach made this safe). Everything before this point either succeeded completely or threw — there's no partially\-constructed `Shader` left half\-built if something failed partway through, since a thrown exception from `compile` or `link` unwinds the constructor entirely.

`use()` and `destroy()` mirror the exact two\-method public contract R3's `Mesh` class will use — bind it before drawing, release it exactly once on shutdown — establishing the pattern the rest of the render layer follows.

### What happens if you skip it

Skip `glDeleteShader` on the individual vertex/fragment shader objects after linking, and each one leaks — a smaller leak than an un\-destroyed VAO/VBO (Step 8), but the same class of bug: a GPU resource with a handle nothing ever cleans up.

## Step 5 — The triangle's vertex data

### What we're doing

In `Main.java`, new fields and imports:

```java
import forestsettlement.render.Shader;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
```

```java
private Shader shader;
private int triangleVao;
private int triangleVbo;
```

### Why

`Shader`, `triangleVao` and `triangleVbo` join `window`, `debugCallback` and `clock` as `Main` fields for the same reason those already are: they're created once during setup and need to be reachable from both `loop()` (to draw every frame) and the shutdown sequence (Step 8, to release them). The three new static imports bring in `glGenBuffers`/`glBindBuffer`/`glBufferData`/`glDeleteBuffers` (`GL15`), `glVertexAttribPointer`/`glEnableVertexAttribArray` (`GL20`), and `glGenVertexArrays`/`glBindVertexArray`/`glDeleteVertexArrays` (`GL30`) — `GL_TRIANGLES` and `glDrawArrays` are already available via R0's existing `import static org.lwjgl.opengl.GL11.*;`.

### What happens if you skip it

Nothing yet — this step is pure declaration. The consequence of getting the *type* of these fields wrong (using a Java array or list instead of raw `int` handles, say) would only show up once Step 6 tries to actually use them as OpenGL object names, which are always plain integers.

## Step 6 — Uploading the triangle and describing its layout

### What we're doing

In `loop()`, after `GL.createCapabilities()` and the debug\-callback setup, before the `while` loop:

```java
shader = new Shader("/shaders/basic.vert", "/shaders/basic.frag");

float[] vertices = {
        // x,     y,    z,    r,  g,  b
         0.0f,  0.5f, 0.0f,  1f, 0f, 0f,
        -0.5f, -0.5f, 0.0f,  0f, 1f, 0f,
         0.5f, -0.5f, 0.0f,  0f, 0f, 1f,
};

triangleVao = glGenVertexArrays();
glBindVertexArray(triangleVao);

triangleVbo = glGenBuffers();
glBindBuffer(GL_ARRAY_BUFFER, triangleVbo);
glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

int stride = 6 * Float.BYTES;
glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
glEnableVertexAttribArray(0);

glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
glEnableVertexAttribArray(1);

glBindVertexArray(0);
```

### Why

Three vertices, six floats each — position (`x, y, z`) immediately followed by color (`r, g, b`), interleaved in one array exactly as Step 1's shader attribute locations expect. `-0.5`/`0.5` coordinates are already in *clip space* (the `-1..1` cube OpenGL renders directly, with no camera or projection yet to translate real\-world units into it — that's R4), which is why this triangle is small and centered without any extra math: it's the simplest coordinates that produce a visible, centered shape given the vertex shader's current `gl_Position = vec4(position, 1.0)`.

`glGenVertexArrays`/`glBindVertexArray` create and activate a VAO first, so every call afterward — the VBO binding, the buffer upload, both attribute\-pointer calls — gets recorded into it; binding `0` at the end deactivates it so later, unrelated GL calls elsewhere can't accidentally modify this triangle's setup. `GL_STATIC_DRAW` is a usage hint telling the driver this data is uploaded once and never modified — accurate here, and the correct hint for any mesh that doesn't change after creation (as opposed to `GL_DYNAMIC_DRAW`, for buffers you intend to update frequently, which nothing in this project needs yet). The stride/offset math is the same interleaved\-attribute calculation R3's `Mesh` class will later centralize in one place — here, spelled out inline once, by hand, is exactly the point of this release.

### What happens if you skip it

Forget `glEnableVertexAttribArray` for either attribute (a very easy line to accidentally omit, since `glVertexAttribPointer` alone silently does nothing without it) and that attribute reads as whatever default value GLSL assigns — typically all zeros — so a missing `color` attribute enable produces a fully black triangle, and a missing `position` enable collapses every vertex to the same point, rendering nothing visible at all.

## Step 7 — Drawing the triangle

### What we're doing

The updated `render()`\:

```java
private void render() {
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    shader.use();
    glBindVertexArray(triangleVao);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindVertexArray(0);

    glfwSwapBuffers(window);
}
```

### Why

Four calls, each doing one specific job: bind the shader program so subsequent draw calls use it, bind the VAO so the GPU knows which vertex data and attribute layout to read, `glDrawArrays(GL_TRIANGLES, 0, 3)` to actually issue the draw — "starting at vertex 0, read 3 vertices, group them into triangles" — and unbind the VAO afterward for the same reason Step 6 did. `glDrawArrays` (not `glDrawElements`) is the right call here specifically because there's no index buffer yet — every vertex is used exactly once, in order, which is all a single triangle ever needs; R3's indexed geometry is what `glDrawElements` is for.

### What happens if you skip it

Forget `shader.use()` specifically, and whatever shader program happened to be bound last (none, on the very first frame) is what's active — with no shader program bound at all, `glDrawArrays` either renders nothing or produces undefined, driver\-dependent output, rather than a clear error.

## Step 8 — Cleanup: releasing every GPU object on shutdown

### What we're doing

In `run()`, right after `loop()` returns and before the existing GLFW teardown:

```java
private void run() {
    Logger.info("Forest Settlement — LWJGL {}", Version.getVersion());

    init();
    loop();

    glDeleteBuffers(triangleVbo);
    glDeleteVertexArrays(triangleVao);
    shader.destroy();

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

GPU resources have to be released while their OpenGL context is still current — `glfwDestroyWindow`/`glfwTerminate` further down tear down that context, so the new cleanup lines have to run before them, not after. This is the direct fulfillment of "explicit cleanup of every OpenGL object you created" and "all GPU resources created this release are released on shutdown": the VBO, the VAO, and the linked shader program (which itself, inside `destroy()`, is the only GPU object `Shader` owns — the individual vertex/fragment shader objects were already released back in Step 4's constructor).

### What happens if you skip it

Exactly the leak this project has now guarded against twice — first for the timing code's non\-issue (nothing to leak there), now for real GPU handles: run the app, close it, and the VAO/VBO/program are reclaimed only when the whole process exits, invisible in any short manual test, and exactly the kind of gap R3 will repeat this same fix for on a larger scale (a quad and a cube instead of one triangle) if it isn't made a habit starting here.

## Step 9 — Proving the failure is loud: deliberately breaking a shader

### What we're doing

Temporarily introduce a syntax error into `basic.vert` — for example, delete the semicolon after `gl_Position = vec4(position, 1.0)`\:

```glsl
void main() {
    vertexColor = color;
    gl_Position = vec4(position, 1.0)
}
```

Run the app. Then revert the change.

### Why

This is the concrete, hands\-on verification of R2's second acceptance criterion — "deliberately broken shader source produces a clear, readable failure — not a silent black screen." With Steps 2–3's status checks in place, running the app with this typo should immediately log a `Shader compilation failed (vertex stage):` line via `Logger.error`, containing the GLSL compiler's own message (typically naming the exact line and "syntax error" or similar), followed by the application throwing and exiting — no window ever even opens, because the exception propagates out of `loop()`'s setup before the render loop starts. That's the entire point: a broken shader should be impossible to miss, not something you only discover by noticing the window looks wrong.

### What happens if you skip it

Skipping this step doesn't break anything in the shipped code — it's a verification exercise, not a feature. But without actually doing it once, you're trusting that Steps 2–3's error handling works correctly rather than having confirmed it — and a bug in error\-handling code is a particularly easy one to ship unnoticed, precisely because the code path only runs when something has already gone wrong.

## Running it

```bash
mvn compile exec:java "-Dexec.mainClass=forestsettlement.Main"
```

Expect a single triangle — red top corner, green bottom\-left, blue bottom\-right, blending smoothly between them — centered in the window. Everything from R1 still works underneath it: the `FPS, UPS` diagnostic line keeps logging once a second, and Space still pauses/resumes the simulation (with no visible effect on the still\-static triangle, since nothing about its position depends on `update()` yet).

## Verifying against R2's acceptance criteria

- **A triangle renders using a shader program you wrote and linked yourself.** Steps 1–7: `basic.vert`/`basic.frag`, compiled and linked by `Shader` (Steps 2–4), driving the triangle drawn in Step 7.
- **Deliberately broken shader source produces a clear, readable failure — not a silent black screen.** Step 9's walkthrough, backed by Steps 2–3's explicit `GL_COMPILE_STATUS`/`GL_LINK_STATUS` checks and log\-and\-throw handling.
- **All GPU resources created this release are released on shutdown.** Step 8: the VBO, VAO, and shader program all destroyed before the GLFW context goes away.

## Troubleshooting

**The window is black, no triangle, no errors logged.** Almost always a missing `glEnableVertexAttribArray` call, or `shader.use()` not being called before the draw — both produce silent failures rather than logged ones, since they're not compile/link errors.

**The triangle is the wrong shape or in the wrong place, but not obviously "broken."** Check the stride/offset math in Step 6 against the attribute layout in Step 1's shaders — a mismatch here doesn't error, it just misinterprets the data.

**The app crashes immediately on startup with a shader compile/link error you didn't intend.** Check for a stray character or encoding issue in `basic.vert`/`basic.frag` — the log message from Step 2/3 names the exact problem; read it before assuming something else is wrong.

**`Shader source not found on classpath` at startup.** The resource path passed to `Shader`'s constructor has to start with `/` and match the file's actual location under `src/main/resources` exactly (`/shaders/basic.vert`, not `shaders/basic.vert` or `/basic.vert`).

## What's next

R3 — Indexed Geometry and Meshes replaces this release's raw, hand\-typed VAO/VBO calls with a reusable `Mesh` class, introduces index buffers so shapes can share vertices instead of duplicating them, and renders a quad and a cube instead of one static triangle.
