package forestsettlement;

import forestsettlement.camera.Camera;
import forestsettlement.properties.SystemProperties;
import forestsettlement.render.Mesh;
import forestsettlement.render.Shader;
import forestsettlement.time.FixedTimestep;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;
import org.lwjgl.system.MemoryStack;
import org.tinylog.Logger;

import java.nio.DoubleBuffer;
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

    private static final Vector3f[] CUBE_POSITIONS = {
            new Vector3f(-1.5f, 0.45f, -1f),
            new Vector3f(0.0f, 0.45f, 0f),
            new Vector3f(1.5f, 0.45f, 1f),
    };
    private static final float PAN_SPEED = 6f; // world units per second
    private static final double MAX_CAMERA_FRAME_TIME_SECONDS = 0.1; // guards a huge pan after a stall
    private static final float ROTATE_SENSITIVITY = 0.25f; // degrees per pixel of mouse drag
    private static final float ZOOM_SENSITIVITY = 1.5f; // distance units per scroll notch

    private double lastCursorX;
    private double lastCursorY;
    private boolean rotatingCamera = false;

    private long window;

    private Callback debugCallback = null;

    private long frameCount = 0;

    private final FixedTimestep clock = new FixedTimestep();

    private Shader shader;

    private Mesh quadMesh;
    private Mesh cubeMesh;
    private boolean cullingEnabled = true;

    private Matrix4f projection;

    private float fovDegrees = 60f;
    private float nearPlane = 0.1f;
    private float farPlane = 100f;

    private int framebufferWidth = 1280;
    private int framebufferHeight = 720;

    private final Camera camera = new Camera(new Vector3f(0f, 0f, 0f), 45f, 35f, 10f);

    private void run() {
        Logger.info("Forest Settlement — LWJGL {}", Version.getVersion());

        init();
        loop();

        quadMesh.destroy();
        cubeMesh.destroy();
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

        glfwSetScrollCallback(window, (win, xOffset, yOffset) ->
                camera.zoom((float) -yOffset * ZOOM_SENSITIVITY));
        GLFWErrorCallback.create((error, description) ->
                Logger.error("GLFW error {}: {}", error, GLFWErrorCallback.getDescription(description))
        ).set();

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(win, true);
            }

            if (key == GLFW_KEY_SPACE && action == GLFW_RELEASE) {
                clock.setPaused(!clock.isPaused());
                Logger.info("Simulation {}", clock.isPaused() ? "paused" : "resumed");
            }
            if (key == GLFW_KEY_F && action == GLFW_RELEASE) {
                cullingEnabled = !cullingEnabled;
                if (cullingEnabled) {
                    glEnable(GL_CULL_FACE);
                } else {
                    glDisable(GL_CULL_FACE);
                }
                Logger.info("Back-face culling {}", cullingEnabled ? "enabled" : "disabled");
            }

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
        });

        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            glViewport(0, 0, width, height);

            framebufferWidth = Math.max(width, 1);
            framebufferHeight = Math.max(height, 1);
            projection = buildProjection(framebufferWidth, framebufferHeight);
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

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        glEnable(GL_DEPTH_TEST);

        shader = new Shader("/shaders/basic.vert", "/shaders/basic.frag");

        quadMesh = Mesh.quad();
        cubeMesh = Mesh.cube();

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetFramebufferSize(window, pWidth, pHeight);

            framebufferWidth = pWidth.get(0);
            framebufferHeight = pHeight.get(0);
        }

        projection = buildProjection(framebufferWidth, framebufferHeight);


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

            handleCameraInput(frameTime);

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
        shader.setUniformMat4("projection", projection);

        Matrix4f groundModel = new Matrix4f()
                .identity()
                .rotateX((float) Math.toRadians(-90f))
                .scale(20f);
        shader.setUniformMat4("model", groundModel);
        quadMesh.draw();

        float angle = (float) glfwGetTime();

        shader.setUniformMat4("view", camera.viewMatrix());

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

    private void update(double dt) {
        // Nothing to simulate yet — R1 only builds the seam.
        // R16 onward hooks real simulation systems in here.
    }

    private Matrix4f buildProjection(int width, int height) {
        float aspect = (float) width / (float) height;
        return new Matrix4f().perspective(
                (float) Math.toRadians(fovDegrees), aspect, nearPlane, farPlane);
    }

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
    }

    public static void main(String[] args) {
        new Main().run();
    }
}
