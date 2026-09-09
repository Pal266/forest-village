# R5 Tutorial — Strategy Camera

A line\-by\-line tutorial: what each step does, why it exists, and what breaks if you skip it

## What R5 actually builds

R4 left the camera as a single hardcoded `lookAt` call — `eye` fixed at `(0, 3, 6)`, always looking at the origin, never changing after setup. That was deliberately out of scope for R4 ("no interactive camera yet — a fixed or scripted view suffices"), but it means there's currently no way to look at the scene from anywhere else, and nothing in `Main` treats the camera as a thing with its own state.

R5's job is to replace that one fixed matrix with a real orbit camera: a focus point (`target`) the camera always looks at, and `yaw`/`pitch`/`distance` describing where the camera sits relative to that point. Panning moves `target` across the ground; dragging rotates `yaw`/`pitch` around it; scrolling changes `distance`. Every frame, those four numbers get turned into an eye position and fed through the same `Matrix4f.lookAt` R4 already used — the camera doesn't introduce a new way of building a view matrix, just a reason for that matrix to change.

The roadmap's acceptance criteria call for "a placeholder world" to navigate — "a flat, textured plane suffices." Textures don't exist yet; that's R6's entire job (`stb_image`, texture coordinates, samplers). R5 uses a plain, vertex\-colored ground plane instead — same placeholder\-world purpose, without reaching into work that hasn't happened yet. R4's three cubes stay, now resting on top of that plane instead of floating at `y = 0`.

This is built directly against your actual merged `Main.java`, `Mesh.java` and `Shader.java` from R4 — not a rewrite.

## Prerequisites

- R4 merged and working — three cubes at distinct depths, a resizable window that keeps its aspect ratio, and **\-**/**\=** visibly clipping cubes at the far plane.
- JOML is already in `pom.xml` since R4 — nothing new to install for R5.

## Step 1 — The Camera class: fields, constructor, and clamps

### What we're doing

New file, `src/main/java/forestsettlement/camera/Camera.java`\:

```java
package forestsettlement.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {

    private static final float MIN_PITCH_DEGREES = 15f;
    private static final float MAX_PITCH_DEGREES = 80f;

    private static final float MIN_DISTANCE = 3f;
    private static final float MAX_DISTANCE = 30f;

    private final Vector3f target;
    private float yawDegrees;
    private float pitchDegrees;
    private float distance;

    public Camera(Vector3f target, float yawDegrees, float pitchDegrees, float distance) {
        this.target = new Vector3f(target);
        this.yawDegrees = yawDegrees;
        this.pitchDegrees = clampPitch(pitchDegrees);
        this.distance = clampDistance(distance);
    }

    private static float clampPitch(float pitchDegrees) {
        return Math.max(MIN_PITCH_DEGREES, Math.min(MAX_PITCH_DEGREES, pitchDegrees));
    }

    private static float clampDistance(float distance) {
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
    }
}
```

### Why

`forestsettlement.camera` is a new package, deliberately separate from `forestsettlement.render` — the same reasoning R1 applied to `forestsettlement.time`\: this class has **zero imports from `org.lwjgl.*`**, only JOML types, so nothing about it depends on a window existing. That's what the roadmap means by "the camera exposes no OpenGL concepts to code outside the render layer" — taken further than strictly required, since `Camera` itself has no OpenGL concepts *at all*, not just none leaking past its own boundary.

`target` is stored as a defensive copy (`new Vector3f(target)`) rather than the reference the caller passed in, because `Vector3f` is mutable — without the copy, code elsewhere holding the original vector could silently move the camera's focus point out from under it.

The two clamp helpers exist for a reason that matters more once Step 2 introduces the methods that call them repeatedly: `pitchDegrees` is clamped between 15° and 80° so the camera can never flatten to looking dead level (15°, which would clip through the ground plane at a grazing angle) or snap to looking straight down (80°, just short of 90°, which would make yaw meaningless — orbiting around a point you're staring straight down at doesn't visibly rotate anything). `distance` is clamped between 3 and 30 so the camera can't zoom inside the cubes or zoom out far enough to lose the scene entirely.

### What happens if you skip it

Nothing yet — this step only declares state, the same as R1's Step 2 for `FixedTimestep`. Skip the *separate package* part specifically, and nothing breaks today either, but it's the same standing invitation R1 warned about: once `Camera` and `Main` share a file or package, it's an easy reach to pull a GLFW constant or a window handle into what's supposed to be pure orbit math.

## Step 2 — pan(), rotate(), zoom(): the mutators

### What we're doing

```java
public void pan(float rightAmount, float forwardAmount) {
    float yawRadians = (float) Math.toRadians(yawDegrees);

    float forwardX = -(float) Math.sin(yawRadians);
    float forwardZ = -(float) Math.cos(yawRadians);
    float rightX = -forwardZ;
    float rightZ = forwardX;

    target.x += rightX * rightAmount + forwardX * forwardAmount;
    target.z += rightZ * rightAmount + forwardZ * forwardAmount;
}

public void rotate(float deltaYawDegrees, float deltaPitchDegrees) {
    yawDegrees += deltaYawDegrees;
    pitchDegrees = clampPitch(pitchDegrees + deltaPitchDegrees);
}

public void zoom(float deltaDistance) {
    distance = clampDistance(distance + deltaDistance);
}
```

### Why

`pan()` moves `target` in the ground plane relative to which way the camera is currently facing, not relative to fixed world axes — `forwardAmount` moves toward whatever `yaw` is currently looking at, `rightAmount` strafes perpendicular to it. `forwardX`/`forwardZ` are the *negation* of the offset `viewMatrix()` (Step 3) uses to place `eye` relative to `target` — `(sin(yaw), cos(yaw))` points from the focus point *toward* the camera, so the direction the camera actually looks (`eye → target`) is the negative of that. `right` is then derived from the corrected `forward` via the same rotate\-90° relationship as before (`(-forwardZ, forwardX)`, equivalent to the cross product of `forward` and world\-up), so strafing stays correct once `forward` is.

`rotate()` only ever changes `pitchDegrees` through the clamp — `yawDegrees` is unbounded on purpose, since spinning all the way around a focus point is normal and there's no reason to stop it, while pitch has the hard physical limits Step 1 explained.

`zoom()` is the same shape as `rotate()`'s pitch handling: add a delta, clamp the result, store it back. Neither method takes an absolute value — both take a *delta*, so the caller (Step 6\-8, in `Main`) decides how much per frame or per input event, and `Camera` only enforces the limits.

### What happens if you skip it

Skip the `right`/`forward` decomposition in `pan()` and move `target` along raw world X/Z instead, and the camera works correctly only at `yaw = 0` — rotate the view at all, and pressing "forward" starts moving the camera sideways relative to what's on screen, which is disorienting in a way that's obvious the moment you actually rotate and try to pan.

Get the negation on `forwardX`/`forwardZ` backwards — using `viewMatrix()`'s eye\-relative offset directly instead of its negation — and panning still looks plausible on paper (the math runs, nothing throws), but W visibly backs the camera away from the scene and S advances it: forward and backward are swapped, while strafing (A/D) still feels correct, since `right` happens to come out the same either way. That asymmetry — one axis wrong, the other fine — is exactly what makes it easy to miss until you actually try moving forward.

## Step 3 — viewMatrix(): turning yaw/pitch/distance into an eye position

### What we're doing

```java
public Matrix4f viewMatrix() {
    float yawRadians = (float) Math.toRadians(yawDegrees);
    float pitchRadians = (float) Math.toRadians(pitchDegrees);

    float horizontalDistance = distance * (float) Math.cos(pitchRadians);
    float height = distance * (float) Math.sin(pitchRadians);

    Vector3f eye = new Vector3f(
            target.x + horizontalDistance * (float) Math.sin(yawRadians),
            target.y + height,
            target.z + horizontalDistance * (float) Math.cos(yawRadians)
    );

    return new Matrix4f().lookAt(eye, target, new Vector3f(0f, 1f, 0f));
}
```

### Why

This is spherical coordinates, the same idea as latitude/longitude/altitude but centered on `target` instead of the Earth: `pitch` splits `distance` into a horizontal component and a vertical one (`cos`/`sin` of the same angle, so they always recombine to exactly `distance` — Pythagoras, not a coincidence), and `yaw` spins that horizontal component around `target` in the XZ plane. The result, `eye`, is a genuine world\-space position — everything downstream (`lookAt`) is exactly what R4 already used, just fed a computed `eye` instead of a hardcoded one.

`up` stays `(0, 1, 0)` unconditionally, which only works because `pitch` is clamped away from 90° (Step 1) — a camera looking exactly straight down has no well\-defined "up" in world space, and `lookAt` degenerates. That's the concrete reason the pitch clamp matters, not just an abstract "feels wrong" argument.

### What happens if you skip it

Get `sin`/`cos` swapped between `horizontalDistance` and `height` and the camera's pitch behaves backwards — dragging to look more downward instead makes the view more level, which is a subtle\-looking bug that's actually a simple copy\-paste transposition, easy to introduce and, watching the camera respond backwards to a drag, easy to spot too.

## Step 4 — Accessors, and unit\-testing Camera without a window

### What we're doing

Add read\-only accessors to `Camera`\:

```java
public Vector3f target() {
    return new Vector3f(target);
}

public float yawDegrees() {
    return yawDegrees;
}

public float pitchDegrees() {
    return pitchDegrees;
}

public float distance() {
    return distance;
}
```

New file, `src/test/java/forestsettlement/camera/CameraTest.java`\:

```java
package forestsettlement.camera;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraTest {

    @Test
    void pitchIsClampedOnConstruction() {
        Camera camera = new Camera(new Vector3f(0f, 0f, 0f), 0f, 200f, 10f);

        assertEquals(80f, camera.pitchDegrees(), 1e-6);
    }

    @Test
    void pitchIsClampedWhenRotatingPastTheMaximum() {
        Camera camera = new Camera(new Vector3f(0f, 0f, 0f), 0f, 70f, 10f);

        camera.rotate(0f, 50f);

        assertEquals(80f, camera.pitchDegrees(), 1e-6);
    }

    @Test
    void pitchNeverGoesBelowTheMinimum() {
        Camera camera = new Camera(new Vector3f(0f, 0f, 0f), 0f, 20f, 10f);

        camera.rotate(0f, -50f);

        assertEquals(15f, camera.pitchDegrees(), 1e-6);
    }

    @Test
    void distanceIsClampedOnConstruction() {
        Camera camera = new Camera(new Vector3f(0f, 0f, 0f), 0f, 45f, 1000f);

        assertEquals(30f, camera.distance(), 1e-6);
    }

    @Test
    void zoomNeverGoesBelowTheMinimumDistance() {
        Camera camera = new Camera(new Vector3f(0f, 0f, 0f), 0f, 45f, 5f);

        camera.zoom(-50f);

        assertEquals(3f, camera.distance(), 1e-6);
    }

    @Test
    void panMovesTargetWithoutTouchingHeight() {
        Camera camera = new Camera(new Vector3f(0f, 0f, 0f), 0f, 45f, 10f);

        camera.pan(2f, 3f);

        assertEquals(0f, camera.target().y, 1e-6);
        assertTrue(camera.target().x != 0f || camera.target().z != 0f);
    }
}
```

Run it with:

```bash
mvn test
```

### Why

Same motivation as R1's `FixedTimestepTest`\: `Camera` has no LWJGL imports, so it can be constructed directly with hand\-picked numbers and asserted against, with no window, no GLFW, no OpenGL context — six narrow tests, each targeting one specific claim (pitch clamps at both ends, distance clamps at both ends, panning never touches world\-space height). `target()` returns a defensive copy for the same reason the constructor takes one — a test (or, later, other code) holding onto that vector should never be able to mutate the camera's real state through it.

### What happens if you skip it

The camera still works correctly if the clamp math is right — but the clamps are exactly the kind of off\-by\-a\-sign or swapped\-operand mistake that's easy to get subtly wrong (`Math.min` where you meant `Math.max`, say) and that a manual play\-test might not catch, since dragging "too far" during casual testing looks the same whether the clamp is working or just happens to not have been hit yet.

## Step 5 — Wiring Camera into Main

### What we're doing

Add the import and replace the `view` field:

```java
import forestsettlement.camera.Camera;
```

```java
private final Camera camera = new Camera(new Vector3f(0f, 0f, 0f), 45f, 35f, 10f);
```

Delete the `private Matrix4f view;` field, and delete the fixed `lookAt` block from `loop()`\:

```java
view = new Matrix4f().lookAt(
        new Vector3f(0f, 3f, 6f),
        new Vector3f(0f, 0f, 0f),
        new Vector3f(0f, 1f, 0f)
);
```

In `render()`, replace the `view` uniform upload:

```java
shader.setUniformMat4("view", camera.viewMatrix());
```

### Why

`camera` is a field initialized inline, the same pattern R1 used for `clock` — it exists the moment `Main` is constructed, before `init()` or `loop()` run, so Step 7's scroll callback (registered in `init()`) has something real to call `.zoom()` on. The initial `(yaw=45°, pitch=35°, distance=10)` puts the camera at a comparable angle to R4's old fixed `eye`, so the very first frame after this change looks similar, not jarringly different — Step 9's ground plane is what actually changes the picture.

`view` disappears entirely as a `Main` field — it was never anything but "the last matrix `lookAt` produced," and now that value is computed fresh from `camera` every frame instead of stored.

### What happens if you skip it

Leave the old `view` field and its `lookAt` call in place alongside `camera`, and the scene keeps rendering from the fixed R4 viewpoint no matter what the camera's `target`/`yaw`/`pitch`/`distance` say — every input handler from Step 6 onward would appear to do nothing, because `render()` would still be reading the unused fixed matrix.

## Step 6 — Continuous keyboard panning (WASD)

### What we're doing

A new private method in `Main`\:

```java
private static final float PAN_SPEED = 6f; // world units per second
private static final double MAX_CAMERA_FRAME_TIME_SECONDS = 0.1; // guards a huge pan after a stall

private void handleCameraInput(double frameTime) {
    double clampedFrameTime = Math.min(frameTime, MAX_CAMERA_FRAME_TIME_SECONDS);
    float panDistance = (float) (PAN_SPEED * clampedFrameTime);

    float forwardAmount = 0f;
    float rightAmount = 0f;

    if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
        forwardAmount += panDistance;
    }
    if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
        forwardAmount -= panDistance;
    }
    if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
        rightAmount += panDistance;
    }
    if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
        rightAmount -= panDistance;
    }

    if (forwardAmount != 0f || rightAmount != 0f) {
        camera.pan(rightAmount, forwardAmount);
    }
}
```

Call it once per real frame, in `loop()`'s `while` body:

```java
double currentTime = glfwGetTime();
double frameTime = currentTime - previousTime;
previousTime = currentTime;

handleCameraInput(frameTime);

int steps = clock.advance(frameTime);
```

### Why

Every keyboard handler so far (Escape, Space, F, **\-**/**\=**) lives in the key callback registered in `init()`, and fires once on `GLFW_RELEASE` — correct for toggles, wrong for "move while held," since a callback that only fires on press/release has no way to know the key is *still* down between those two events. Panning needs to run every frame the key is held, so `handleCameraInput` polls `glfwGetKey(...) == GLFW_PRESS` directly inside the loop instead — a second, deliberately different input pattern living alongside the first, not a replacement for it.

`panDistance` scales by `frameTime` — real elapsed seconds, the same value `clock.advance()` receives — so panning covers the same world distance per second regardless of framerate, which is exactly what the roadmap's "movement is frame\-rate independent" asks for. `MAX_CAMERA_FRAME_TIME_SECONDS` is the same idea as `FixedTimestep`'s spiral\-of\-death clamp from R1, applied here for a much smaller reason: without it, resuming after a multi\-second stall (a window drag, a debugger pause) would produce one single, huge `panDistance` and visibly teleport the camera if a pan key happened to be held at that moment. Capping the input at a tenth of a second bounds that to "a barely visible extra nudge," not a jump.

### What happens if you skip it

Try to implement WASD panning inside the existing key callback instead of polling, and it only moves the camera once per press — holding the key down produces one step of movement, then nothing, because `GLFW_RELEASE` (the only edge the existing handlers check) doesn't fire again until you actually let go.

## Step 7 — Mouse\-drag rotation around the focus point

### What we're doing

New `Main` fields:

```java
private static final float ROTATE_SENSITIVITY = 0.25f; // degrees per pixel of mouse drag

private double lastCursorX;
private double lastCursorY;
private boolean rotatingCamera = false;
```

Extend `handleCameraInput`\:

```java
try (MemoryStack stack = stackPush()) {
    DoubleBuffer cursorX = stack.mallocDouble(1);
    DoubleBuffer cursorY = stack.mallocDouble(1);
    glfwGetCursorPos(window, cursorX, cursorY);

    double currentCursorX = cursorX.get(0);
    double currentCursorY = cursorY.get(0);

    boolean rightButtonDown = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;

    if (rightButtonDown && rotatingCamera) {
        float deltaYaw = (float) (currentCursorX - lastCursorX) * ROTATE_SENSITIVITY;
        float deltaPitch = (float) (currentCursorY - lastCursorY) * ROTATE_SENSITIVITY;
        camera.rotate(deltaYaw, deltaPitch);
    }

    rotatingCamera = rightButtonDown;
    lastCursorX = currentCursorX;
    lastCursorY = currentCursorY;
}
```

Add the import: `import java.nio.DoubleBuffer;`

### Why

Dragging with the right mouse button held rotates the camera by however far the cursor moved since last frame — `deltaYaw` from horizontal movement, `deltaPitch` from vertical, both just a pixel delta times a sensitivity constant, no accumulation logic beyond that. The `rotatingCamera` flag exists specifically to skip the very first frame the button goes down: on that frame, `lastCursorX`/`lastCursorY` still hold wherever the mouse was *before* the click, which has nothing to do with the drag that's about to start, so computing a delta against it would produce one spurious jump the instant you click. Only computing a delta when the button was *already* down last frame — `rightButtonDown && rotatingCamera` — means the first frame of a click just records a clean starting position instead.

Screen Y increases downward, so dragging the mouse down (`currentCursorY - lastCursorY > 0`) increases `pitchDegrees` — tilting toward a steeper, more top\-down view. If that reads backwards once you actually try it, negate `deltaPitch` — it's a preference, not a correctness issue, the same kind of note as R0's PowerShell quoting caveat.

Right mouse button was free to use — nothing in the project has touched mouse buttons before this release.

### What happens if you skip it

Skip the `rotatingCamera` guard and compute a delta unconditionally whenever the button is down, and every click produces one sharp, uncontrolled jump in the camera's orientation the instant you press the button — before you've actually dragged anywhere — because that first delta is measured against a stale cursor position from before the click.

## Step 8 — Mouse\-wheel zoom

### What we're doing

A constant and a new callback, registered in `init()` alongside the existing key and framebuffer\-size callbacks:

```java
private static final float ZOOM_SENSITIVITY = 1.5f; // distance units per scroll notch
```

```java
glfwSetScrollCallback(window, (win, xOffset, yOffset) ->
        camera.zoom((float) -yOffset * ZOOM_SENSITIVITY));
```

### Why

Each scroll\-wheel notch delivers one discrete `yOffset` event — positive when scrolling up/forward, negative scrolling down/back — so, unlike panning, zoom needs no `frameTime` scaling: one notch is one step, regardless of how long a frame happens to take. Negating `yOffset` makes scrolling up zoom in (`distance` decreases), the near\-universal convention. `camera.zoom()` already clamps internally (Step 1), so this callback doesn't need its own bounds check — scrolling past either limit just stops changing `distance`, silently and correctly.

`camera` being a field initialized at construction (Step 5) is what makes this callback legal to register here — it's set up in `init()`, which runs before `camera` would otherwise exist if it were built later, inside `loop()`.

### What happens if you skip it

Without a scroll callback, `distance` never changes after construction — the camera can pan and rotate around its focus point at a fixed 10 units away forever, with no way to get a closer look at one cube or pull back to see the whole placeholder world at once.

## Step 9 — A ground plane to navigate, and resting the cubes on it

### What we're doing

Change `CUBE_POSITIONS`' Y coordinate from `0f` to `0.45f`\:

```java
private static final Vector3f[] CUBE_POSITIONS = {
        new Vector3f(-1.5f, 0.45f, -1f),
        new Vector3f(0.0f, 0.45f, 0f),
        new Vector3f(1.5f, 0.45f, 1f),
};
```

In `render()`, replace R4's parked\-quad block with a ground plane, reusing the same `quadMesh`\:

```java
Matrix4f groundModel = new Matrix4f()
        .identity()
        .rotateX((float) Math.toRadians(-90f))
        .scale(20f);
shader.setUniformMat4("model", groundModel);
quadMesh.draw();
```

### Why

R4's quad was parked at a fixed position purely to prove every mesh needs its own `model` uniform, not just the cubes — scaffolding with no in\-world meaning. R5 needs an actual placeholder world to navigate, so that scaffolding is replaced with a real one: the same quad geometry, rotated \-90° around X to lie flat instead of standing upright, scaled 20× so its half\-extent (`0.3`) becomes 6 world units — a 12×12 ground big enough to pan and zoom around. No new mesh code, and no new vertex data — this is the model matrix doing exactly what R4 built it to do, applied to an existing mesh instead of a new one.

The cubes moving from `y = 0` to `y = 0.45` fixes a problem that R4 never exposed: a cube scaled 1.5× has a half\-height of `0.3 * 1.5 = 0.45`, so at `y = 0` it always extended from `-0.45` to `+0.45` — invisible before, because nothing else occupied that space. Now that a ground plane sits at `y = 0`, the old position would bury each cube halfway into it. Lifting them by exactly their own half\-height means their bottom face sits flush on the ground instead of through it.

The ground plane inherits the quad's existing per\-corner vertex colors (the same red/green/blue/yellow gradient R2 and R3 already used) rather than a single flat color — a real texture is R6's job, not R5's; this is a deliberate placeholder, not an oversight.

### What happens if you skip it

Skip the cube Y\-position fix specifically, and the scene still renders and the camera still works correctly — but every cube visibly clips through the new ground plane by almost half its height, which reads as a bug the moment the ground plane exists to notice it against, even though nothing about the camera itself is wrong.

## Running it

```bash
mvn compile exec:java "-Dexec.mainClass=forestsettlement.Main"
```

Hold **W**/**A**/**S**/**D** to pan across the ground plane — movement should feel the same speed regardless of window size or resize activity. Hold the **right mouse button** and drag to rotate around the three cubes; release and click again to confirm there's no jump on the initial click. Scroll the **mouse wheel** to zoom in and out, and confirm it stops changing distance at both the close and far limits instead of overshooting. Press **\-**/**\=** (from R4) to confirm far\-plane clipping still works against the new camera.

Run the unit tests separately:

```bash
mvn test
```

All `CameraTest` cases (and the existing `FixedTimestepTest` cases) should pass.

## Verifying against R5's acceptance criteria

- **The camera navigates a placeholder world comfortably.** Step 9's ground plane gives WASD panning, drag\-rotation, and scroll\-zoom (Steps 6–8) an actual world to move around in, rather than open space with nothing to judge movement against.
- **Movement is frame\-rate independent.** Step 6's `panDistance` scales by real `frameTime`, the same quantity `FixedTimestep` consumes; drag\-rotation and scroll\-zoom are event\-driven (a pixel delta, a scroll notch) rather than time\-driven, so framerate doesn't enter into them at all.
- **The camera exposes no OpenGL concepts to code outside the render layer.** `Camera` (Step 1) has zero `org.lwjgl.*` imports — it only accepts and returns JOML types (`Vector3f`, `Matrix4f`) and plain floats.

## Troubleshooting

**Holding W/A/S/D does nothing.** Confirm `handleCameraInput(frameTime)` is actually being called inside the `while` loop in `loop()` (Step 6) — it's easy to define the method and forget to call it every frame.

**W moves the camera backward and S moves it forward (A/D feel fine).** `forwardX`/`forwardZ` in `pan()` (Step 2) are using `(sin(yaw), cos(yaw))` directly instead of its negation — that value is the offset from `target` to `eye` (which way the camera sits), not the direction the camera looks. Negate both.

**The camera jumps sharply the instant you right\-click, before you've dragged anywhere.** The `rotatingCamera` guard from Step 7 is missing or was placed after the delta calculation instead of before it — the first frame of a click must skip computing a delta entirely.

**Zoom keeps going past where it should stop, or does nothing at all.** If it doesn't stop, check that `Camera.zoom()` (Step 1) is actually clamping via `clampDistance` rather than assigning `distance` directly. If it does nothing, confirm the scroll callback (Step 8) is registered in `init()`, not accidentally left out.

**Cubes are half\-buried in the ground.** `CUBE_POSITIONS`' Y coordinate (Step 9) is still `0f` instead of `0.45f`.

**Panning speed visibly changes when the window is resized or during heavy load.** Check that `panDistance` is computed from `clampedFrameTime` (Step 6), not a hardcoded per\-frame constant — the entire point of scaling by real elapsed time is that it stays correct regardless of how long each frame takes.

## What's next

R6 — Textures and Materials replaces the ground plane's flat vertex\-color gradient with an actual loaded image, via `stb_image`, texture coordinates, and a minimal `Material` type — the placeholder world Step 9 built finally gets to look like something.
