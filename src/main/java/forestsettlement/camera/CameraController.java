package forestsettlement.camera;

import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Drives a {@link Camera} from raw window input: continuous WASD panning,
 * right-mouse-drag orbiting, and scroll-wheel zoom. Kept separate from
 * {@code Main} so camera controls aren't entangled with the game loop or
 * window setup, and so the polling logic lives next to the camera math it
 * drives.
 */
public class CameraController {

    private static final float PAN_SPEED = 6f; // world units per second
    private static final double MAX_FRAME_TIME_SECONDS = 0.1; // guards a huge pan after a stall
    private static final float ROTATE_SENSITIVITY = 0.25f; // degrees per pixel of mouse drag
    private static final float ZOOM_SENSITIVITY = 1.5f; // distance units per scroll notch

    private final Camera camera;
    private final long windowHandle;

    private double lastCursorX;
    private double lastCursorY;
    private boolean rotating = false;

    public CameraController(Camera camera, long windowHandle) {
        this.camera = camera;
        this.windowHandle = windowHandle;
    }

    /** Wire this to the window's scroll callback. */
    public void onScroll(double yOffset) {
        camera.zoom((float) -yOffset * ZOOM_SENSITIVITY);
    }

    /** Call once per frame with the (unclamped) real frame time. */
    public void update(double frameTime) {
        double clampedFrameTime = Math.min(frameTime, MAX_FRAME_TIME_SECONDS);
        float panDistance = (float) (PAN_SPEED * clampedFrameTime);

        float forwardAmount = 0f;
        float rightAmount = 0f;

        if (glfwGetKey(windowHandle, GLFW_KEY_W) == GLFW_PRESS) {
            forwardAmount += panDistance;
        }
        if (glfwGetKey(windowHandle, GLFW_KEY_S) == GLFW_PRESS) {
            forwardAmount -= panDistance;
        }
        if (glfwGetKey(windowHandle, GLFW_KEY_D) == GLFW_PRESS) {
            rightAmount += panDistance;
        }
        if (glfwGetKey(windowHandle, GLFW_KEY_A) == GLFW_PRESS) {
            rightAmount -= panDistance;
        }

        if (forwardAmount != 0f || rightAmount != 0f) {
            camera.pan(rightAmount, forwardAmount);
        }

        try (MemoryStack stack = stackPush()) {
            DoubleBuffer cursorX = stack.mallocDouble(1);
            DoubleBuffer cursorY = stack.mallocDouble(1);
            glfwGetCursorPos(windowHandle, cursorX, cursorY);

            double currentCursorX = cursorX.get(0);
            double currentCursorY = cursorY.get(0);

            boolean rightButtonDown = glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;

            if (rightButtonDown && rotating) {
                float deltaYaw = (float) (currentCursorX - lastCursorX) * ROTATE_SENSITIVITY;
                float deltaPitch = (float) (currentCursorY - lastCursorY) * ROTATE_SENSITIVITY;
                camera.rotate(deltaYaw, deltaPitch);
            }

            rotating = rightButtonDown;
            lastCursorX = currentCursorX;
            lastCursorY = currentCursorY;
        }
    }
}
