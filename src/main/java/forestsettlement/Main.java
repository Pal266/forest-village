package forestsettlement;

import forestsettlement.properties.SystemProperties;
import forestsettlement.render.Shader;
import forestsettlement.time.FixedTimestep;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;
import org.lwjgl.system.MemoryStack;
import org.tinylog.Logger;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Main {

    private long window;

    private Callback debugCallback = null;

    private long frameCount = 0;

    private final FixedTimestep clock = new FixedTimestep();

    private Shader shader;
    private int triangleVao;
    private int triangleVbo;

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

    private void init() {
        GLFWErrorCallback.create((error, description) ->
                Logger.error("GLFW error {}: {}", error, GLFWErrorCallback.getDescription(description))
        ).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        boolean debugBuild = SystemProperties.DEBUG_MODE;
        if (debugBuild) {
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
        }

        window = glfwCreateWindow(1280, 720, "Forest Settlement", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(win, true);
            }

            if (key == GLFW_KEY_SPACE && action == GLFW_RELEASE) {
                clock.setPaused(!clock.isPaused());
                Logger.info("Simulation {}", clock.isPaused() ? "paused" : "resumed");
            }
        });

        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            glViewport(0, 0, width, height);
        });

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

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);

        glfwShowWindow(window);
    }

    private void loop() {
        GL.createCapabilities();

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

    private void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        shader.use();
        glBindVertexArray(triangleVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);

        glfwSwapBuffers(window);
    }

    private void update(double dt) {
        // Nothing to simulate yet — R1 only builds the seam.
        // R16 onward hooks real simulation systems in here.
    }

    public static void main(String[] args) {
        new Main().run();
    }
}
