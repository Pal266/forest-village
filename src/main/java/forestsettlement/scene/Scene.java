package forestsettlement.scene;

import forestsettlement.render.Mesh;
import forestsettlement.render.Shader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Owns everything currently drawn in the world: the ground plane and the
 * placeholder cubes. This is temporary scaffolding for the render/mesh
 * tutorials (R2-R5) rather than a real content system - it exists mainly so
 * {@code Main} doesn't have to know what a "renderable object" is, and so
 * that adding or replacing content later doesn't mean editing the game loop.
 */
public class Scene {

    private static final Vector3f[] CUBE_POSITIONS = {
            new Vector3f(-1.5f, 0.45f, -1f),
            new Vector3f(0.0f, 0.45f, 0f),
            new Vector3f(1.5f, 0.45f, 1f),
    };

    // Radians per second, matching the previous wall-clock-driven rotation
    // (angle == elapsed seconds) when the simulation is running unpaused.
    private static final float CUBE_ROTATION_SPEED = 1f;

    private final Mesh groundMesh;
    private final Mesh cubeMesh;
    private final Matrix4f groundModel;

    private float cubeRotationAngle = 0f;

    public Scene() {
        groundMesh = Mesh.quad();
        cubeMesh = Mesh.cube();

        groundModel = new Matrix4f()
                .identity()
                .rotateX((float) Math.toRadians(-90f))
                .scale(20f);
    }

    /**
     * Advances scene animation by one simulation step. Called only from the
     * fixed-timestep update, not every render frame, so the cubes correctly
     * stop turning when the simulation is paused - unlike the previous
     * wall-clock-based rotation, which kept animating through a pause.
     */
    public void update(double dt) {
        cubeRotationAngle += (float) (dt * CUBE_ROTATION_SPEED);
    }

    public void render(Shader shader) {
        shader.setUniformMat4("model", groundModel);
        groundMesh.draw();

        for (Vector3f position : CUBE_POSITIONS) {
            Matrix4f cubeModel = new Matrix4f()
                    .identity()
                    .translate(position)
                    .rotateY(cubeRotationAngle)
                    .scale(1.5f);

            shader.setUniformMat4("model", cubeModel);
            cubeMesh.draw();
        }
    }

    public void destroy() {
        groundMesh.destroy();
        cubeMesh.destroy();
    }
}
