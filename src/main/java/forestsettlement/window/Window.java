package forestsettlement.window;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWScrollCallbackI;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;
import org.tinylog.Logger;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Owns the GLFW window's lifecycle: creation, callback wiring, and cleanup.
 * Rendering and game logic stay out of this class on purpose - it only knows
 * about the window itself, not what gets drawn into it.
 */
public class Window {

    public interface ResizeListener {
        void onResize(int framebufferWidth, int framebufferHeight);
    }

    private long handle = NULL;

    private int framebufferWidth;
    private int framebufferHeight;

    private ResizeListener resizeListener;

    public Window(int width, int height, String title, boolean debugContext) {
        GLFWErrorCallback.create((error, description) ->
                Logger.error("GLFW error {}: {}", error, GLFWErrorCallback.getDescription(description))
        ).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

            if (debugContext) {
                glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
            }

            handle = glfwCreateWindow(width, height, title, NULL, NULL);
            if (handle == NULL) {
                throw new RuntimeException("Failed to create the GLFW window");
            }

            framebufferWidth = width;
            framebufferHeight = height;

            glfwSetFramebufferSizeCallback(handle, (win, w, h) -> {
                framebufferWidth = Math.max(w, 1);
                framebufferHeight = Math.max(h, 1);
                if (resizeListener != null) {
                    resizeListener.onResize(framebufferWidth, framebufferHeight);
                }
            });

            centerOnPrimaryMonitor();

            glfwMakeContextCurrent(handle);
            glfwSwapInterval(1);
        } catch (RuntimeException e) {
            // glfwInit() already succeeded by this point, so on any failure below we
            // must still tear down whatever GLFW state was created - otherwise a
            // failed startup (e.g. no window could be created) leaks the GLFW library
            // instance and any partially-created window.
            if (handle != NULL) {
                glfwFreeCallbacks(handle);
                glfwDestroyWindow(handle);
            }
            glfwTerminate();
            glfwSetErrorCallback(null).free();
            throw e;
        }
    }

    private void centerOnPrimaryMonitor() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(handle, pWidth, pHeight);

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(
                    handle,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        }
    }

    public void onResize(ResizeListener listener) {
        this.resizeListener = listener;
    }

    public void onKey(GLFWKeyCallbackI callback) {
        glfwSetKeyCallback(handle, callback);
    }

    public void onScroll(GLFWScrollCallbackI callback) {
        glfwSetScrollCallback(handle, callback);
    }

    public void onRefresh(Runnable callback) {
        glfwSetWindowRefreshCallback(handle, win -> callback.run());
    }

    public void show() {
        glfwShowWindow(handle);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public void close() {
        glfwSetWindowShouldClose(handle, true);
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public void swapBuffers() {
        glfwSwapBuffers(handle);
    }

    public long handle() {
        return handle;
    }

    public int framebufferWidth() {
        return framebufferWidth;
    }

    public int framebufferHeight() {
        return framebufferHeight;
    }

    public void destroy() {
        glfwFreeCallbacks(handle);
        glfwDestroyWindow(handle);

        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
}
