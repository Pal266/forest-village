# R4 — 3D Mathematics and Transformations

A line\-by\-line tutorial: what each step does, why it exists, and what breaks if you skip it

## What R4 actually builds

R3 leaves you with a quad and a cube sitting at fixed, hand\-picked positions written directly into their vertex data — because there was no other way to place them yet. That doesn't scale past "two static shapes chosen once": moving an object means re\-uploading its entire vertex buffer, and there's no way to have the same cube mesh appear at ten different positions without ten copies of its geometry.

R4 fixes this with the standard real\-time\-graphics answer: separate *what a mesh looks like* (its vertex data, essentially unchanged since R3) from *where it is* (a 4×4 transform matrix, uploaded to the shader as a uniform and applied fresh every draw call). This is the model/view/projection split — three matrices, three different jobs: **model** places one specific object in the world (translate, rotate, scale), **view** repositions the entire world relative to a camera, and **projection** turns 3D coordinates into the 2D, perspective\-correct image actually drawn to the screen. JOML — the math library this project has been pointed at since the very first roadmap conversation — provides the vector and matrix types and the operations to build all three.

By the end of R4, `Mesh.cube()` (recentered at the origin) renders several times per frame at genuinely different positions, a real perspective camera replaces "coordinates that happen to already be in clip space," and resizing the window correctly keeps objects from stretching. There's still no *interactive* camera — R4's own scope explicitly excludes that — the view is fixed, just no longer trivial.

This is built directly against your actual merged `Main.java`, `Mesh.java` and `Shader.java` from R3 — not a rewrite.

## Prerequisites

- R3 merged and working — you should see a quad and a flat\-red cube face on screen, and **F** should toggle culling (watch the log for `Back-face culling enabled/disabled`).
- JOML is new to the project as of this release — nothing else to install beyond what R0–R3 already set up.

## Step 1 — Recentering the meshes at the origin

### What we're doing

In `Mesh.java`, `quad()` and `cube()` currently place their shapes off\-center (the quad around `x ≈ -0.5`, the cube around `x ≈ +0.5`) purely so R3 could show both without overlapping, with no other way to position them. Shift every coordinate back so each shape is centered on its own local origin — for the quad, that just means dropping the `-0.5` baked into every X coordinate:

```java
public static Mesh quad() {
    float[] vertices = {
        // x,     y,     z,    r,  g,  b
        -0.3f, -0.3f, 0.0f,  1f, 0f, 0f,
         0.3f, -0.3f, 0.0f,  0f, 1f, 0f,
         0.3f,  0.3f, 0.0f,  0f, 0f, 1f,
        -0.3f,  0.3f, 0.0f,  1f, 1f, 0f,
    };
    // indices unchanged
}
```

For the cube, every `0.2f` becomes `-0.3f` and every `0.8f` becomes `0.3f` — a uniform `-0.5` shift on X, everything else (Y, Z, colors, index generation) untouched:

```java
public static Mesh cube() {
    float[] vertices = {
        // Front (red)       x      y      z    r   g   b
                            -0.3f, -0.3f,  0.3f, 1f, 0f, 0f,
                             0.3f, -0.3f,  0.3f, 1f, 0f, 0f,
                             0.3f,  0.3f,  0.3f, 1f, 0f, 0f,
                            -0.3f,  0.3f,  0.3f, 1f, 0f, 0f,
        // Back (green)
                             0.3f, -0.3f, -0.3f, 0f, 1f, 0f,
                            -0.3f, -0.3f, -0.3f, 0f, 1f, 0f,
                            -0.3f,  0.3f, -0.3f, 0f, 1f, 0f,
                             0.3f,  0.3f, -0.3f, 0f, 1f, 0f,
        // Left (blue)
                            -0.3f, -0.3f, -0.3f, 0f, 0f, 1f,
                            -0.3f, -0.3f,  0.3f, 0f, 0f, 1f,
                            -0.3f,  0.3f,  0.3f, 0f, 0f, 1f,
                            -0.3f,  0.3f, -0.3f, 0f, 0f, 1f,
        // Right (yellow)
                             0.3f, -0.3f,  0.3f, 1f, 1f, 0f,
                             0.3f, -0.3f, -0.3f, 1f, 1f, 0f,
                             0.3f,  0.3f, -0.3f, 1f, 1f, 0f,
                             0.3f,  0.3f,  0.3f, 1f, 1f, 0f,
        // Top (cyan)
                            -0.3f,  0.3f,  0.3f, 0f, 1f, 1f,
                             0.3f,  0.3f,  0.3f, 0f, 1f, 1f,
                             0.3f,  0.3f, -0.3f, 0f, 1f, 1f,
                            -0.3f,  0.3f, -0.3f, 0f, 1f, 1f,
        // Bottom (magenta)
                            -0.3f, -0.3f, -0.3f, 1f, 0f, 1f,
                             0.3f, -0.3f, -0.3f, 1f, 0f, 1f,
                             0.3f, -0.3f,  0.3f, 1f, 0f, 1f,
                            -0.3f, -0.3f,  0.3f, 1f, 0f, 1f,
    };
    // index-generation loop unchanged
}
```

### Why

The offsets currently in `Mesh.java` existed purely so a quad and a cube, both authored directly in clip space, wouldn't overlap — the only positioning tool available before this release. Now that a model matrix (Step 4) can translate a mesh anywhere, baking a position directly into the vertex data would just fight with it: a cube already offset by `+0.5` on X, then translated another `+1.5` by its model matrix, ends up at `+2.0` — not wrong exactly, but confusing, and it means "where is this mesh, really" has two different, easy\-to\-forget answers depending on which file you're looking at. Centering every mesh on its own local origin is the standard convention specifically because it makes the model matrix the *only* source of truth for position.

The winding order, face colors, and index\-generation loop are all unchanged — only the coordinates moved, by the same amount, in the same pattern, so nothing about R3's culling or depth testing needs revisiting.

### What happens if you skip it

The cubes in Step 8 render at the wrong positions — each one offset from where its model matrix says it should be by the leftover `+0.5`, which is a subtle, easy\-to\-misdiagnose bug rather than an obvious one: things look *approximately* right, just not exactly where the math says they should be.

## Step 2 — Adding JOML to the build

### What we're doing

In `pom.xml`\:

```xml
<dependency>
    <groupId>org.joml</groupId>
    <artifactId>joml</artifactId>
    <version>1.10.9</version>
</dependency>
```

### Why

JOML (Java OpenGL Math Library) is a pure\-Java vector/matrix library built specifically for real\-time graphics — no native bindings, no allocation\-heavy general\-purpose math library repurposed for a job it wasn't designed for. R4's requirements are explicit that JOML becomes "the project's only math types" from here on: every position, direction, and transform in the entire codebase — not just rendering — should be a JOML `Vector3f`/`Matrix4f`, so R5's camera, R10's mouse picking, and everything after aren't each reaching for a different, incompatible way to represent "a point in 3D space."

### What happens if you skip it

Hand\-rolling your own 4×4 matrix math is a well\-trodden, extremely bug\-prone path — row\-major versus column\-major convention mistakes alone are common enough that essentially no real project does this from scratch anymore. Skipping a real math library doesn't save time; it just moves the time into debugging matrix multiplication order later.

## Step 3 — Uploading matrices as shader uniforms

### What we're doing

In `Shader.java`, add two imports and one method:

```java
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
```

```java
public void setUniformMat4(String name, Matrix4f matrix) {
    int location = glGetUniformLocation(programId, name);
    try (MemoryStack stack = MemoryStack.stackPush()) {
        FloatBuffer buffer = stack.mallocFloat(16);
        matrix.get(buffer);
        glUniformMatrix4fv(location, false, buffer);
    }
}
```

(`glGetUniformLocation` and `glUniformMatrix4fv` are already available via `Shader.java`'s existing `import static org.lwjgl.opengl.GL20.*;` — no new static import needed for those two.)

### Why

A "uniform" is a shader input that stays constant across every vertex in a single draw call (as opposed to per\-vertex *attributes*, like `position` and `color` since R2) — exactly the right fit for a transform matrix, since every vertex of one cube shares the same model matrix. `glGetUniformLocation` looks up where in the compiled program (`programId`, the field `Shader`'s constructor already sets) a named uniform lives; `glUniformMatrix4fv` uploads 16 floats there.

`MemoryStack.stackPush()` borrows a small, short\-lived block of off\-heap memory rather than allocating a new `FloatBuffer` on the Java heap every call — with a matrix uploaded several times per frame per object, that adds up fast. The `try`\-with\-resources block returns the memory the moment the method exits.

### What happens if you skip it

Without a real uniform\-upload path, the model/view/projection matrices built in the next few steps would have nowhere to go — they'd exist as Java objects with no way to reach the GPU, making the rest of R4 impossible to actually wire up.

## Step 4 — The model matrix

### What we're doing

A pattern you'll use for every object, first seen in Step 8:

```java
Matrix4f model = new Matrix4f()
        .identity()
        .translate(position)
        .rotateY(angle)
        .scale(1.5f);
```

### Why

This is composed, not computed as three separate matrices multiplied by hand — JOML's fluent API applies operations right\-to\-left in the order that matches how you'd describe the transform in English ("scale it, then rotate it, then move it"), even though the calls read top\-to\-bottom. `translate` → `rotateY` → `scale` in that call order means: start from the object's own local space, scale it first, then rotate around its own center, then move the whole result to its world position — the conventional order for "where is this thing, facing which way, how big," and the order that keeps rotation happening around the object's own center rather than around the world origin (which is what you'd get if translate came first).

`identity()` at the start isn't optional ceremony — a freshly constructed JOML `Matrix4f` already *is* the identity matrix, but calling it explicitly makes the starting point visible in the code.

### What happens if you skip it

Get the operation order backwards — `rotateY` after `translate`, say — and objects rotate around the world origin instead of their own center, which for anything not already sitting at `(0,0,0)` looks like the object orbiting some invisible point rather than spinning in place.

## Step 5 — The view matrix: a fixed camera

### What we're doing

A new `Main` field:

```java
private Matrix4f view;
```

Built once, in `loop()`, alongside the existing culling/depth\-test setup:

```java
view = new Matrix4f().lookAt(
        new Vector3f(0f, 3f, 6f),   // eye: where the camera sits
        new Vector3f(0f, 0f, 0f),   // center: what it's looking at
        new Vector3f(0f, 1f, 0f)    // up: which way is "up" for the camera
);
```

### Why

`lookAt` is the conventional way to build a view matrix from three intuitive inputs: an eye position, a point to look at, and an up direction (almost always world\-up, `(0,1,0)`, unless the camera is deliberately tilted). Conceptually, the view matrix does the opposite of what it sounds like — instead of moving the camera, it moves the *entire world* in the opposite direction, so the math can pretend the camera always sits at the origin looking down one fixed axis.

`view` is a field (not a local variable inside `loop()`) because Step 8's `render()` needs to read it every frame, and it's built once during setup rather than every frame since nothing about it changes yet. R4's explicit scope says "no interactive camera yet — a fixed or scripted view is fine," which is exactly this — R5 replaces it with a camera that responds to input.

### What happens if you skip it

Without a view matrix, the camera is implicitly sitting at the world origin, facing down the default axis, with no way to look at a scene from any other angle — every object would need to be positioned assuming that fixed, restrictive viewpoint.

## Step 6 — The projection matrix: perspective

### What we're doing

More `Main` fields:

```java
private Matrix4f projection;

private float fovDegrees = 60f;
private float nearPlane = 0.1f;
private float farPlane = 100f;

private int framebufferWidth = 1280;
private int framebufferHeight = 720;
```

A builder method:

```java
private Matrix4f buildProjection(int width, int height) {
    float aspect = (float) width / (float) height;
    return new Matrix4f().perspective(
            (float) Math.toRadians(fovDegrees), aspect, nearPlane, farPlane);
}
```

Called once during `loop()`'s setup, using the same `MemoryStack` pattern `init()` already uses for the window\-size query:

```java
try (MemoryStack stack = stackPush()) {
    IntBuffer pWidth = stack.mallocInt(1);
    IntBuffer pHeight = stack.mallocInt(1);
    glfwGetFramebufferSize(window, pWidth, pHeight);

    framebufferWidth = pWidth.get(0);
    framebufferHeight = pHeight.get(0);
}

projection = buildProjection(framebufferWidth, framebufferHeight);
```

### Why

Perspective projection is what makes distant objects appear smaller — the geometric property that makes a 3D scene actually read as three\-dimensional, as opposed to *orthographic* projection (parallel lines stay parallel; not needed here). `perspective()` takes a vertical field of view in radians (hence `Math.toRadians`, since FOV is conventionally authored in degrees), an aspect ratio, and the near/far clip planes: anything closer than `nearPlane` or farther than `farPlane` is discarded before rasterization.

Querying the actual framebuffer size via `glfwGetFramebufferSize` (rather than hardcoding `1280`/`720`, the window's creation size) matters because the framebuffer can differ from the window size on displays with content scaling — using the real, current size is what "configurable field of view and clip planes" needs to build on correctly from the very first frame, before any resize ever happens.

### What happens if you skip it

Skip perspective and use an identity matrix instead, and every cube renders the same size regardless of distance from the camera — technically still real 3D positions, but visually flat and wrong the moment two objects are meant to read as being at different distances.

## Step 7 — Wiring model, view and projection through the shader

### What we're doing

Extend `basic.vert`\:

```glsl
#version 330 core

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 color;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

out vec3 vertexColor;

void main() {
    vertexColor = color;
    gl_Position = projection * view * model * vec4(position, 1.0);
}
```

(`basic.frag` is unchanged.)

### Why

Matrix multiplication order matters here for a specific, memorizable reason: GLSL matrices multiply a column vector on the right, so `projection * view * model * position` applies `model` first (local space → world space), then `view` (world space → camera\-relative space), then `projection` (camera\-relative space → clip space) — read right\-to\-left, the same "innermost operation happens first" rule as ordinary function composition. R4's requirement that "model, view and projection matrices \[are\] uploaded as shader uniforms" (three separate uniforms, not pre\-multiplied on the CPU) is deliberate: `view` and `projection` are the same for every object in a frame and only need setting once, while `model` changes per object.

### What happens if you skip it

Get the multiplication order backwards (`model * view * projection`, say) and the result is either nonsensical geometry or, confusingly, sometimes something that looks almost plausible depending on the specific matrices involved — a mistake that's easy to introduce and genuinely hard to spot by inspection.

## Step 8 — Updating render(): the quad, and several cubes at distinct transforms

### What we're doing

A new field alongside `quadMesh`/`cubeMesh`\:

```java
private static final Vector3f[] CUBE_POSITIONS = {
        new Vector3f(-1.5f, 0f, -1f),
        new Vector3f( 0.0f, 0f,  0f),
        new Vector3f( 1.5f, 0f,  1f),
};
```

The updated `render()`\:

```java
private void render() {
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    shader.use();
    shader.setUniformMat4("view", view);
    shader.setUniformMat4("projection", projection);

    Matrix4f quadModel = new Matrix4f().identity().translate(0f, 1.5f, -2f);
    shader.setUniformMat4("model", quadModel);
    quadMesh.draw();

    float angle = (float) glfwGetTime();

    for (Vector3f position : CUBE_POSITIONS) {
        Matrix4f cubeModel = new Matrix4f()
                .identity()
                .translate(position)
                .rotateY(angle)
                .scale(1.5f);

        shader.setUniformMat4("model", cubeModel);
        cubeMesh.draw();
    }

    glfwSwapBuffers(window);
}
```

### Why

`view` and `projection` are uploaded once per frame, before any mesh draws, because neither depends on which object is currently being drawn — only `model` changes per iteration. The quad gets a fixed, parked position (`(0, 1.5, -2)`) with an identity rotation, mostly to prove the point that *every* mesh needs a `model` uniform now, not just the cubes — R4's actual acceptance criterion only asks for "several cubes... at distinct world transforms," which the three `CUBE_POSITIONS` fulfill directly.

With cubes at genuinely different depths from the camera (`z = -1`, `0`, `1`) rather than R3's side\-by\-side, non\-overlapping placement, R3's depth test is doing real, necessary work for the first time — resolving which cube's fragments win wherever their silhouettes happen to overlap on screen as they rotate. `glfwGetTime()` (not R1's `FixedTimestep`) drives the rotation angle deliberately — this is a purely cosmetic visual flourish with no simulation meaning yet; once R16 introduces real per\-tick simulation state, anything affecting *gameplay* goes through the fixed\-timestep path, while "does it look alive" can keep reading wall\-clock time directly.

### What happens if you skip it

Reuse the same `Matrix4f` instance across iterations without a fresh `.identity()` call each time is one specific, easy way to get this wrong: JOML's `Matrix4f` methods mutate in place and return `this`, so the second cube's transform would end up composed *on top of* the first one's rather than replacing it.

## Step 9 — Recomputing projection on window resize

### What we're doing

Extend the existing framebuffer\-size callback in `init()`\:

```java
glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
    glViewport(0, 0, width, height);

    framebufferWidth = Math.max(width, 1);
    framebufferHeight = Math.max(height, 1);
    projection = buildProjection(framebufferWidth, framebufferHeight);
});
```

### Why

`buildProjection`'s aspect ratio (Step 6) has to match the window's actual current shape, or every rendered object stretches the moment the window resizes to a different aspect ratio. Recomputing it right inside the existing callback, alongside the already\-present `glViewport` call, keeps both pieces of resize\-handling logic next to each other.

`Math.max(width, 1)` guards a real, reachable edge case: minimizing the window on some platforms delivers a framebuffer\-size event with `height == 0`, and `width / 0` as a floating\-point division doesn't throw in Java — it silently produces `Infinity`, which then propagates into a projection matrix full of `NaN`/`Infinity` and a corrupted render once the window is restored.

### What happens if you skip it

R4's acceptance criterion "window resize updates the projection matrix's aspect ratio correctly" fails visibly — resize the window to something tall and narrow, and every cube squashes or stretches, because the projection matrix still assumes whatever aspect ratio the window had at startup.

## Step 10 — Demonstrating near/far clipping

### What we're doing

Extend the existing key callback in `init()`, alongside the Escape/Space/F branches already there:

```java
if (key == GLFW_KEY_MINUS && action == GLFW_RELEASE) {
    farPlane = Math.max(nearPlane + 0.1f, farPlane - 1f);
    projection = buildProjection(framebufferWidth, framebufferHeight);
    Logger.info("Far plane now {}", farPlane);
}

if (key == GLFW_KEY_EQUAL && action == GLFW_RELEASE) {
    farPlane += 1f;
    projection = buildProjection(framebufferWidth, framebufferHeight);
    Logger.info("Far plane now {}", farPlane);
}
```

### Why

This is the direct, hands\-on fulfillment of "near/far clipping and depth\-buffer behavior can be demonstrated and explained." With the camera roughly 6–7 units from the cubes (Step 5) and a starting `farPlane` of `100`, pressing **\-** repeatedly shrinks the far plane; once it drops below the cubes' actual distance from the camera, they simply vanish — not fade, not clip at an edge, disappear entirely — concrete proof that "farther than the far plane" means "not rendered at all." Pressing **\=** grows the far plane back and the cubes reappear the instant it crosses their distance again.

### What happens if you skip it

The clip planes still work correctly without this — clipping happens regardless of whether you've built a way to observe it — but R4's acceptance criterion specifically asks for a *demonstration*, the same reason R3's culling toggle did.

## Running it

```bash
mvn compile exec:java "-Dexec.mainClass=forestsettlement.Main"
```

Expect three rotating cubes at different depths, and the quad parked above them. Press **F** to confirm culling still works against real 3D transforms now, not just a static view. Press **\-**/**\=** to watch cubes disappear and reappear as the far plane crosses their distance from the camera.

## Verifying against R4's acceptance criteria

- **Several cubes render simultaneously at distinct world transforms.** Step 8's three `CUBE_POSITIONS`, each producing its own `model` matrix from the same shared `cubeMesh`.
- **Window resize updates the projection matrix's aspect ratio correctly.** Step 9's extended framebuffer\-size callback rebuilds `projection` with the new width/height every time.
- **Near/far clipping and depth\-buffer behavior can be demonstrated and explained.** Step 10's `-`/`=` far\-plane toggle, plus Step 8's genuinely depth\-separated cubes putting R3's depth test to real use for the first time.

## Troubleshooting

**Everything is black — nothing renders at all.** Double\-check Step 5's `eye`/`center` against Step 8's `CUBE_POSITIONS` — if the eye and center are too close together, or objects sit behind the eye, `lookAt` produces a valid matrix that simply never shows anything.

**Cubes render but look stretched or squashed.** Check that Step 9's resize callback is actually wired up, and that the very first `buildProjection` call during setup uses the real framebuffer size from `glfwGetFramebufferSize`, not a hardcoded guess.

**One cube looks correct but the others are in the wrong place, or all three end up stacked together.** Check for a reused `Matrix4f` instance across loop iterations without a fresh `.identity()` call each time (Step 8).

**The near/far toggle from Step 10 does nothing.** Confirm `projection` is actually being rebuilt after changing `farPlane` — changing the field alone does nothing until `buildProjection` runs again and the result is re\-uploaded as the `projection` uniform next frame.

## What's next

R5 — Strategy Camera replaces Step 5's fixed `lookAt` with a real, keyboard/mouse\-driven camera constrained the way a settlement\-builder's overhead view should be — the first release where "no interactive camera yet" stops being true.
