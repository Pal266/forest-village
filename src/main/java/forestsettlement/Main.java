package forestsettlement;

import forestsettlement.camera.Camera;
import forestsettlement.camera.CameraController;
import forestsettlement.properties.SystemProperties;
import forestsettlement.render.Shader;
import forestsettlement.scene.Scene;
import forestsettlement.time.FixedTimestep;
import forestsettlement.window.Window;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.Version;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;
import org.tinylog.Logger;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Main {

    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;

    private Window window;
    private CameraController cameraController;

    private final FixedTimestep clock = new FixedTimestep();
    private final Camera camera = new Camera(new Vector3f(0f, 0f, 0f), 45f, 35f, 10f);

    private Callback debugCallback = null;

    private Shader shader;
    private Scene scene;
    private boolean cullingEnabled = true;

    private Matrix4f projection;

    private void run() {
        Logger.info("Forest Settlement — LWJGL {}", Version.getVersion());

        try {
            init();
            loop();
        } finally {
            cleanup();
        }
    }

    private void init() {
        window = new Window(WINDOW_WIDTH, WINDOW_HEIGHT, "Forest Settlement", SystemProperties.DEBUG_MODE);
        cameraController = new CameraController(camera, window.handle());

        window.onKey((win, key, scancode, action, mods) -> handleKey(key, action));
        window.onScroll((win, xOffset, yOffset) -> cameraController.onScroll(yOffset));
        window.onResize((width, height) -> {
            glViewport(0, 0, width, height);
            projection = camera.projectionMatrix(width, height);
        });

        window.show();
    }

    private void handleKey(int key, int action) {
        if (action != GLFW_RELEASE) {
            return;
        }

        if (key == GLFW_KEY_ESCAPE) {
            window.close();
        }

        if (key == GLFW_KEY_SPACE) {
            clock.setPaused(!clock.isPaused());
            Logger.info("Simulation {}", clock.isPaused() ? "paused" : "resumed");
        }

        if (SystemProperties.DEBUG_MODE) {
            handleDebugKey(key);
        }
    }

    /**
     * Keys that only exist to demonstrate an engine feature for a specific
     * tutorial (back-face culling from R3, far-plane clipping from R4)
     * rather than being real game controls. Gated behind debug mode so they
     * never end up as real keybinds once this stops being a tech demo.
     */
    private void handleDebugKey(int key) {
        if (key == GLFW_KEY_F) {
            cullingEnabled = !cullingEnabled;
            if (cullingEnabled) {
                glEnable(GL_CULL_FACE);
            } else {
                glDisable(GL_CULL_FACE);
            }
            Logger.info("Back-face culling {}", cullingEnabled ? "enabled" : "disabled");
        }

        if (key == GLFW_KEY_MINUS) {
            camera.adjustFarPlane(-1f);
            projection = camera.projectionMatrix(window.framebufferWidth(), window.framebufferHeight());
            Logger.info("Far plane now {}", camera.farPlane());
        }

        if (key == GLFW_KEY_EQUAL) {
            camera.adjustFarPlane(1f);
            projection = camera.projectionMatrix(window.framebufferWidth(), window.framebufferHeight());
            Logger.info("Far plane now {}", camera.farPlane());
        }
    }

    private void loop() {
        GL.createCapabilities();
        setupGraphicsState();

        shader = new Shader("/shaders/basic.vert", "/shaders/basic.frag");
        scene = new Scene();

        projection = camera.projectionMatrix(window.framebufferWidth(), window.framebufferHeight());

        if (SystemProperties.DEBUG_MODE) {
            debugCallback = GLUtil.setupDebugMessageCallback(System.err);
        }

        window.onRefresh(this::render);

        double previousTime = glfwGetTime();
        double diagnosticsTimer = 0.0;
        long framesThisSecond = 0;
        long updatesThisSecond = 0;

        while (!window.shouldClose()) {

            double currentTime = glfwGetTime();
            double frameTime = currentTime - previousTime;
            previousTime = currentTime;

            cameraController.update(frameTime);

            int steps = clock.advance(frameTime);
            for (int i = 0; i < steps; i++) {
                update(clock.stepSeconds());
                updatesThisSecond++;
            }

            render();
            window.pollEvents();

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

    private void setupGraphicsState() {
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        glEnable(GL_DEPTH_TEST);

        glClearColor(0.10f, 0.11f, 0.13f, 1.0f);
    }

    private void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        shader.use();
        shader.setUniformMat4("projection", projection);
        shader.setUniformMat4("view", camera.viewMatrix());

        scene.render(shader);

        window.swapBuffers();
    }

    private void update(double dt) {
        scene.update(dt);
    }

    private void cleanup() {
        if (scene != null) {
            scene.destroy();
        }
        if (shader != null) {
            shader.destroy();
        }
        if (debugCallback != null) {
            debugCallback.free();
        }
        if (window != null) {
            window.destroy();
        }
    }

    public static void main(String[] args) {
        new Main().run();
    }
}
