# R3 — Indexed Geometry and Meshes

A line\-by\-line tutorial: what each step does, why it exists, and what breaks if you skip it

## What R3 actually builds

R2 gets you one hand\-typed triangle: three vertices, uploaded once directly in `Main.loop()`, drawn with the `Shader` class you wrote and linked yourself. That's the right first step — it forced you to understand the pipeline with nothing hidden — but it doesn't scale even slightly. A cube needs 8 corners shared by 6 faces; typing each face's vertices out separately means duplicating position data three or four times over, and there's still no reusable *thing* to represent a mesh — just one specific triangle's worth of raw VAO/VBO calls sitting directly in `Main`.

R3's job is to fix both problems at once: introduce **indexed geometry** (an element buffer that lets several triangles share vertices instead of duplicating them) and wrap the whole vertex/index/attribute dance in a small `Mesh` class with an explicit lifecycle — create it, draw it, destroy it. Two shapes prove it works: a flat quad (4 vertices, 2 triangles) and a cube (24 vertices, 12 triangles), replacing R2's triangle and rendered side by side in the same frame. Along the way you'll also turn on the two pieces of GL state that only start mattering once your geometry is actually three\-dimensional: back\-face culling (don't waste fill rate — or risk visual bugs — drawing triangles facing away from the camera) and depth testing (so overlapping fragments of the *same* mesh resolve correctly).

This is built directly against your actual merged R2 code — the same `Shader` class, the same `basic.vert`/`basic.frag` attribute layout (position at location 0, color at location 1) — not a rewrite. There's still no camera, no projection matrix, and no per\-object transform — R4 introduces JOML and the model/view/projection pipeline. R3's quad and cube sit at fixed, hand\-picked positions directly in clip space, the same way R2's triangle did.

## Prerequisites

- R2 merged and working — you should see a colored triangle on screen, and your log should show the `FPS, UPS` diagnostic line from R1 still ticking.
- Nothing new to install — R3 uses only what R0–R2 already set up.

## Step 1 — The interleaved vertex layout

### What we're doing

Confirming, in one place, exactly what one vertex looks like in memory — unchanged from R2's triangle, since `Mesh` reuses the same shaders:

```
one vertex = [ x, y, z,  r, g, b ]   ← 6 floats, 24 bytes
             └─ position ─┘  └─ color ─┘
```

### Why

This is the exact layout `basic.vert` already declares (`layout(location = 0) in vec3 position`, `layout(location = 1) in vec3 color`) and the exact stride/offset math R2's raw `glVertexAttribPointer` calls already used. "Interleaved" — every vertex's complete data sits contiguously in memory, in the order the GPU reads it — is what makes one `glVertexAttribPointer` call per attribute work, each sharing the same *stride* (24 bytes: the distance from one vertex to the next) with a different *offset* (0 for position, 12 bytes for color). Nothing about the shader or the layout changes in R3 — only *where* this setup code lives changes, moving from being typed once, inline, in `Main.loop()`, into a reusable class.

### What happens if you skip it

Getting stride or offset wrong is the single most common source of "the mesh renders as garbled triangles" bugs in any OpenGL project — the GPU doesn't know your data is malformed, it just reads 24 bytes at a time starting from whatever offset you told it and interprets the result as positions and colors, silently. Since R3 centralizes this calculation in `Mesh`'s constructor (Step 2) instead of leaving it typed out separately for every shape, getting it right once here is what keeps it right everywhere it's used afterward.

## Step 2 — The Mesh class: fields and constructor

### What we're doing

New file, `src/main/java/forestsettlement/render/Mesh.java` — the same package as `Shader`\:

```java
package forestsettlement.render;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

public class Mesh {

    private static final int FLOATS_PER_VERTEX = 6; // position(3) + color(3)
    private static final int STRIDE = FLOATS_PER_VERTEX * Float.BYTES;
    private static final long COLOR_OFFSET = 3L * Float.BYTES;

    private final int vao;
    private final int vbo;
    private final int ebo;
    private final int indexCount;

    public Mesh(float[] interleavedVertices, int[] indices) {
        this.indexCount = indices.length;

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, interleavedVertices, GL_STATIC_DRAW);

        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, STRIDE, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 3, GL_FLOAT, false, STRIDE, COLOR_OFFSET);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }
}
```

### Why

Compare this constructor line by line against the raw setup block R2 put directly in `Main.loop()` (`triangleVao = glGenVertexArrays(); ... glBindVertexArray(0);`) — it's the same five operations in the same order: create and bind a VAO first (everything bound afterward gets *recorded into* that VAO), upload vertex data to a VBO, upload index data to a new EBO (the one genuinely new piece — R2's triangle had no indices at all), describe the layout with two `glVertexAttribPointer` calls, then unbind the VAO. The EBO binding happens while the VAO is still bound, so the VAO remembers "this is my index buffer" and nothing has to re\-bind it before drawing.

This lives in `forestsettlement.render`, alongside `Shader` — a deliberate separation from both `forestsettlement` (orchestration) and `forestsettlement.time` (pure simulation timing, R1). `Mesh` knows about vertices, buffers and attributes; it does not know what a villager or a resource is, which is exactly what R3's acceptance criteria mean by "no game\-domain logic."

### What happens if you skip it

Binding the EBO *before* creating the VAO, or unbinding the VAO before the EBO binding is recorded, is a classic and confusing failure mode: the mesh appears to have no index buffer at all (`glDrawElements` reads garbage or draws nothing), even though the EBO itself was created and populated correctly — the bug isn't in the buffer, it's in what was and wasn't associated with the VAO at bind time.

## Step 3 — draw() and destroy(): the lifecycle contract

### What we're doing

Add to `Mesh`\:

```java
public void draw() {
    glBindVertexArray(vao);
    glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
    glBindVertexArray(0);
}

public void destroy() {
    glDeleteBuffers(vbo);
    glDeleteBuffers(ebo);
    glDeleteVertexArrays(vao);
}
```

(`GL_TRIANGLES` and `glDrawElements` come from `org.lwjgl.opengl.GL11`; add that import alongside the others.)

### Why

This mirrors `Shader`'s own `use()`/`destroy()` contract from R2 — bind it before drawing, release it exactly once on shutdown. `draw()` rebinds the VAO and issues `glDrawElements`, the indexed counterpart to R2's `glDrawArrays`\: instead of "read the next N vertices in order," it means "read N indices from the EBO, and use each one to look up a vertex" — which is what lets the cube's 8 corners be reused across 6 faces instead of being typed out 24 separate times with no sharing.

`destroy()` exists for the same reason R2's cleanup step deleted the triangle's VBO/VAO and the shader program: none of these GL objects are garbage\-collected. R3's acceptance criteria are explicit that "all GPU resources created this release are released on shutdown," which means `destroy()` has to actually get called somewhere, not just exist (Step 8 wires this in).

### What happens if you skip it

Skip `destroy()` entirely and nothing breaks visibly during a short debug session — the resources just leak until the process exits. But it's the kind of gap that compounds: R11 starts loading real models, R35 starts instancing thousands of objects, and a codebase with no habit of releasing GPU resources on teardown eventually leaks memory in a way that's much harder to track down than it would have been to just call `destroy()` correctly from the start.

## Step 4 — Building a quad from indexed geometry

### What we're doing

A static factory method on `Mesh`\:

```java
public static Mesh quad() {
    float[] vertices = {
        // x,     y,     z,    r,  g,  b
        -0.8f, -0.3f, 0.0f,  1f, 0f, 0f,
        -0.2f, -0.3f, 0.0f,  0f, 1f, 0f,
        -0.2f,  0.3f, 0.0f,  0f, 0f, 1f,
        -0.8f,  0.3f, 0.0f,  1f, 1f, 0f,
    };

    int[] indices = {
        0, 1, 2,
        2, 3, 0,
    };

    return new Mesh(vertices, indices);
}
```

### Why

Four corners, two triangles, six indices — and every corner is stored exactly once. Without an index buffer, drawing a quad as two triangles with plain `glDrawArrays` (R2's approach) would mean listing 6 vertices, with the shared diagonal corners (index 0 and index 2 above) typed out twice, verbatim — trivial to get away with for a quad, but the duplication scales with mesh complexity, and duplicated vertex data that should be identical is a real source of bugs when it drifts.

The quad is placed at `x ≈ -0.5` (via the `-0.8`/`-0.2` coordinates) specifically so it doesn't overlap the cube from Step 5, which sits at `x ≈ +0.5` — with no camera or projection yet (R4), "several distinct meshes in one frame" has to mean "at different hand\-picked positions directly in clip space," the same constraint R2's single triangle didn't have to deal with because there was only ever one shape.

### What happens if you skip it

Get the index order wrong — say, `0, 1, 3` for the first triangle instead of `0, 1, 2` — and you get a visibly wrong shape (a sliver or a self\-overlapping triangle) rather than a quad, because the indices are the only thing telling the GPU which three corners form each triangle.

## Step 5 — Building a cube from indexed geometry

### What we're doing

Another static factory on `Mesh`\:

```java
public static Mesh cube() {
    float[] vertices = {
        // Front (red)      x     y     z    r   g   b
                            0.2f, -0.3f,  0.3f, 1f, 0f, 0f,
                            0.8f, -0.3f,  0.3f, 1f, 0f, 0f,
                            0.8f,  0.3f,  0.3f, 1f, 0f, 0f,
                            0.2f,  0.3f,  0.3f, 1f, 0f, 0f,
        // Back (green)
                            0.8f, -0.3f, -0.3f, 0f, 1f, 0f,
                            0.2f, -0.3f, -0.3f, 0f, 1f, 0f,
                            0.2f,  0.3f, -0.3f, 0f, 1f, 0f,
                            0.8f,  0.3f, -0.3f, 0f, 1f, 0f,
        // Left (blue)
                            0.2f, -0.3f, -0.3f, 0f, 0f, 1f,
                            0.2f, -0.3f,  0.3f, 0f, 0f, 1f,
                            0.2f,  0.3f,  0.3f, 0f, 0f, 1f,
                            0.2f,  0.3f, -0.3f, 0f, 0f, 1f,
        // Right (yellow)
                            0.8f, -0.3f,  0.3f, 1f, 1f, 0f,
                            0.8f, -0.3f, -0.3f, 1f, 1f, 0f,
                            0.8f,  0.3f, -0.3f, 1f, 1f, 0f,
                            0.8f,  0.3f,  0.3f, 1f, 1f, 0f,
        // Top (cyan)
                            0.2f,  0.3f,  0.3f, 0f, 1f, 1f,
                            0.8f,  0.3f,  0.3f, 0f, 1f, 1f,
                            0.8f,  0.3f, -0.3f, 0f, 1f, 1f,
                            0.2f,  0.3f, -0.3f, 0f, 1f, 1f,
        // Bottom (magenta)
                            0.2f, -0.3f, -0.3f, 1f, 0f, 1f,
                            0.8f, -0.3f, -0.3f, 1f, 0f, 1f,
                            0.8f, -0.3f,  0.3f, 1f, 0f, 1f,
                            0.2f, -0.3f,  0.3f, 1f, 0f, 1f,
    };

    int[] indices = new int[36];
    for (int face = 0; face < 6; face++) {
        int vertexOffset = face * 4;
        int indexOffset = face * 6;

        indices[indexOffset]     = vertexOffset;
        indices[indexOffset + 1] = vertexOffset + 1;
        indices[indexOffset + 2] = vertexOffset + 2;
        indices[indexOffset + 3] = vertexOffset + 2;
        indices[indexOffset + 4] = vertexOffset + 3;
        indices[indexOffset + 5] = vertexOffset;
    }

    return new Mesh(vertices, indices);
}
```

### Why

A cube geometrically has only 8 corners, but this uses 24 vertices — 4 per face, one set per face rather than one set shared across all six. That's a deliberate trade, not a mistake: sharing all 8 corners across faces would also force sharing *color*, since color is part of each vertex's data, and there'd be no way to make each face a distinct color the way this version does (useful here so Step 6's culling is visually obvious). Real per\-face data — most commonly *normals* for lighting, arriving in R7 — has exactly the same requirement: a corner shared by three faces needs three different normals depending on which face you're asking about, only representable by duplicating that corner once per face.

The index\-generation loop encodes one repeating pattern — "4 vertices per face, split into two triangles as `(0,1,2)` and `(2,3,0)`, same as the quad" — six times, rather than 36 individual literals.

Each face's four vertices are listed in a specific rotational order (not arbitrary) so that, read as `(v0, v1, v2)`, they wind counter\-clockwise **as seen from outside the cube, looking at that face**. That's not a cosmetic choice — it's exactly what Step 6 depends on.

### What happens if you skip it

Get one face's vertex order backwards (clockwise instead of counter\-clockwise, as seen from outside) and everything still compiles and mostly still looks right — until Step 6 turns on back\-face culling, at which point that one face vanishes: the GPU now believes it's facing away from the camera, because winding order is the only signal it uses to decide, and this particular face's winding disagrees with the other five.

## Step 6 — Winding order and back\-face culling

### What we're doing

In `Main.java`'s `loop()`, after `GL.createCapabilities()` (this is new GL state — R0–R2 never turned it on):

```java
glEnable(GL_CULL_FACE);
glCullFace(GL_BACK);
glFrontFace(GL_CCW);
```

### Why

OpenGL decides whether a triangle is "front\-facing" or "back\-facing" purely from the order its three vertices appear in, as projected onto the screen — no normal vector, no extra data, just the winding direction. `glFrontFace(GL_CCW)` (counter\-clockwise, also OpenGL's default) declares the convention this project uses: a triangle whose vertices go counter\-clockwise, viewed from the side you want visible, is the front. `glCullFace(GL_BACK)` says to discard back\-facing triangles; `glEnable(GL_CULL_FACE)` actually turns the feature on.

For a convex, fully\-enclosed shape like the cube, culling isn't just a performance optimization (though it is one — roughly half the cube's triangles never need shading): it's the reason you don't need any lighting or transparency to already have a coherent picture. Every face's winding was chosen in Step 5 specifically so its outward\-facing side reads as counter\-clockwise — get the geometry and the culling convention to agree, and the three faces of the cube facing away from the camera at any given moment simply aren't drawn.

### What happens if you skip it

Without culling, every triangle draws regardless of which way it faces — for a solid cube with opaque colors and correct depth testing, you likely won't notice anything wrong by eye (the correctly depth\-tested front faces just paint over the back ones), which is exactly why this is easy to accidentally skip and not notice for a while. The cost shows up as wasted GPU work now, and as a real visual bug the moment anything becomes even slightly transparent — a scenario this project will hit for real once trees, foliage or UI elements need alpha blending.

## Step 7 — Depth testing for real 3D geometry

### What we're doing

Also new, alongside Step 6 in `loop()`\:

```java
glEnable(GL_DEPTH_TEST);
```

`glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)` in `render()` is already correct and unchanged — R0 already clears the depth buffer bit every frame, even though nothing used depth testing until now.

### Why

R0 through R2's flat triangle never needed depth to speak of — one shape, no overlapping geometry. The moment actual 3D geometry exists, OpenGL needs a place to record, per pixel, how far away the nearest fragment drawn there so far was, so a farther fragment drawn afterward doesn't incorrectly paint over a nearer one. `glEnable(GL_DEPTH_TEST)` turns that check on; the existing `GL_DEPTH_BUFFER_BIT` clear (already in your `render()` since R0) is what keeps it from comparing against stale data left over from the previous frame.

This isn't explicitly named in R3's requirements list the way culling is, but it's a direct, unavoidable consequence of "a quad and a cube both render from indexed geometry" once that geometry has real depth extent.

### What happens if you skip it

For this cube specifically, with culling already on, you might not see anything obviously wrong at first — culling alone already hides most of the triangles that would otherwise cause visible errors. But it's fragile: a viewing angle, a future non\-convex mesh, or two overlapping objects will immediately show the real symptom — nearer geometry disappearing behind farther geometry that happened to be drawn later, because "later in the draw order" silently wins instead of "actually closer to the camera."

## Step 8 — Replacing R2's triangle with Mesh\-based rendering

### What we're doing

Remove the two triangle\-specific fields R2 added:

```java
private int triangleVao;
private int triangleVbo;
```

Replace them with:

```java
private Mesh quadMesh;
private Mesh cubeMesh;
private boolean cullingEnabled = true;
```

In `loop()`, remove R2's raw vertex\-array/vertex\-buffer setup block (the `float[] vertices = {...}` triangle data through the final `glBindVertexArray(0);`), and replace it with:

```java
quadMesh = Mesh.quad();
cubeMesh = Mesh.cube();
```

Update `render()`\:

```java
private void render() {
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    shader.use();
    quadMesh.draw();
    cubeMesh.draw();

    glfwSwapBuffers(window);
}
```

And update `run()`'s cleanup — replace `glDeleteBuffers(triangleVbo); glDeleteVertexArrays(triangleVao);` with:

```java
quadMesh.destroy();
cubeMesh.destroy();
```

### Why

`shader.use()` still binds the compiled program once per frame, exactly as R2 left it — both meshes share the one shader, since R3 explicitly doesn't introduce per\-mesh materials (that's R6). Drawing `quadMesh` and `cubeMesh` back to back in the same `render()` call is the literal fulfillment of "support for drawing several distinct meshes in one frame" — nothing more elaborate is needed at this scale, and R12's `RenderInstance` abstraction is what eventually replaces "a fixed field per mesh" once there are more than a couple of objects.

Removing `triangleVao`/`triangleVbo` rather than leaving them alongside the new meshes matters for R3's "no game\-domain logic" and general cleanliness — R2's triangle was a deliberately temporary, hand\-typed proof that the pipeline worked; `Mesh` is what replaces it going forward, not something that coexists with it.

### What happens if you skip it

Leave R2's raw triangle fields and setup code in place alongside the new `Mesh` calls, and you'd either render three shapes instead of two (harmless but confusing, and contrary to what R3 is actually demonstrating) or end up with dead, unused fields and buffer calls that make the codebase harder to follow — worth cleaning up now while the diff is small, rather than as a separate pass later.

## Step 9 — A culling toggle key

### What we're doing

Extend R1's key callback in `init()`\:

```java
if (key == GLFW_KEY_F && action == GLFW_RELEASE) {
    cullingEnabled = !cullingEnabled;
    if (cullingEnabled) {
        glEnable(GL_CULL_FACE);
    } else {
        glDisable(GL_CULL_FACE);
    }
    Logger.info("Back-face culling {}", cullingEnabled ? "enabled" : "disabled");
}
```

### Why

R3's acceptance criteria specifically call for culling to be "toggled and its effect explained, not just observed" — flipping it live and watching a cube face pop in or out is a far more convincing, on\-demand demonstration than a static screenshot, and it costs only a few lines given R1 already established the pattern (a boolean flag, a key callback branch, a `Logger.info` confirmation) for the Space pause key.

### What happens if you skip it

Without the toggle, culling either works or doesn't, with no easy way to *prove* which — a viewer has to trust that what they're seeing is correctly culled rather than, say, a cube with only three faces ever defined. The toggle turns "trust me" into "watch this face appear the instant I disable culling," which is a meaningfully stronger demonstration for the exact acceptance criterion R3 asks for.

## Running it

```bash
mvn compile exec:java "-Dexec.mainClass=forestsettlement.Main"
```

Expect a small quad on the left half of the window and a cube on the right, each face a different flat color, both rendered with no lighting (that's R7). Press **F** and watch one or more cube faces vanish or reappear as culling toggles — that's your live confirmation that winding order, culling, and depth testing are all agreeing with each other.

## Verifying against R3's acceptance criteria

- **A quad and a cube both render from indexed geometry.** Step 4 and Step 5's factory methods, drawn together in Step 8's `render()`.
- **`Mesh` contains no game\-domain logic — it is purely a rendering primitive.** Steps 2–3: the class only ever talks about vertices, buffers, attributes and draw calls, never anything specific to Forest Settlement's game rules.
- **Culling can be toggled and its effect explained, not just observed.** Step 6 explains the winding\-order mechanism directly; Step 9's `F`\-key toggle makes the effect observable on demand.

## Troubleshooting

**The cube looks entirely wrong or inside\-out from every angle.** Almost always a `glFrontFace`/winding mismatch — double check that `GL_CCW` in Step 6 actually matches the vertex order used in Step 5.

**One specific cube face disappears only when culling is on.** That face's vertex order in Step 5 winds the opposite direction from the other five — re\-derive it: list its four corners in the order that appears counter\-clockwise when looking at that face from outside the cube.

**Nothing draws at all, or the app crashes on startup.** Almost always a stride/offset mismatch — but since Step 1's layout is unchanged from R2's already\-working triangle, this is more likely a leftover reference to the removed `triangleVao`/`triangleVbo` fields (Step 8) that didn't get cleaned up.

**The quad and cube overlap or one is clipped off\-screen.** Both shapes' coordinates were hand\-picked to fit within the \-1..1 clip\-space range with no projection matrix (R4 hasn't happened yet) — if you change either shape's size or offset, keep every vertex coordinate within that range.

## What's next

R4 — 3D Mathematics and Transformations brings in JOML and the model/view/projection pipeline — the point where "hand\-picked clip\-space coordinates" finally goes away, replaced by real world positions, a real camera, and several cubes rendered at genuinely different transforms rather than just offset by hand.
