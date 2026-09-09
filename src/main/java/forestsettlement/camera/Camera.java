package forestsettlement.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {

    private static final float MIN_PITCH_DEGREES = 15f;
    private static final float MAX_PITCH_DEGREES = 80f;

    private static final float MIN_DISTANCE = 3f;
    private static final float MAX_DISTANCE = 30f;

    private static final float DEFAULT_FOV_DEGREES = 60f;
    private static final float DEFAULT_NEAR_PLANE = 0.1f;
    private static final float DEFAULT_FAR_PLANE = 100f;

    private final Vector3f target;
    private float yawDegrees;
    private float pitchDegrees;
    private float distance;

    // The camera owns the projection parameters as well as the view, since
    // "where the camera is looking from" and "how it sees the world" are the
    // same concept rather than two unrelated pieces of state.
    private float fovDegrees;
    private float nearPlane;
    private float farPlane;

    public Camera(Vector3f target, float yawDegrees, float pitchDegrees, float distance) {
        this(target, yawDegrees, pitchDegrees, distance, DEFAULT_FOV_DEGREES, DEFAULT_NEAR_PLANE, DEFAULT_FAR_PLANE);
    }

    public Camera(Vector3f target, float yawDegrees, float pitchDegrees, float distance,
                  float fovDegrees, float nearPlane, float farPlane) {
        this.target = new Vector3f(target);
        this.yawDegrees = yawDegrees;
        this.pitchDegrees = clampPitch(pitchDegrees);
        this.distance = clampDistance(distance);
        this.fovDegrees = fovDegrees;
        this.nearPlane = nearPlane;
        this.farPlane = farPlane;
    }

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

    public float fovDegrees() {
        return fovDegrees;
    }

    public float nearPlane() {
        return nearPlane;
    }

    public float farPlane() {
        return farPlane;
    }

    /**
     * Builds the perspective projection matrix for the given framebuffer size,
     * using this camera's field of view and clip planes.
     */
    public Matrix4f projectionMatrix(int framebufferWidth, int framebufferHeight) {
        float aspect = (float) framebufferWidth / (float) framebufferHeight;
        return new Matrix4f().perspective(
                (float) Math.toRadians(fovDegrees), aspect, nearPlane, farPlane);
    }

    /**
     * Moves the far clip plane by the given amount, never letting it cross the
     * near plane.
     */
    public void adjustFarPlane(float delta) {
        farPlane = Math.max(nearPlane + 0.1f, farPlane + delta);
    }

    private static float clampPitch(float pitchDegrees) {
        return Math.clamp(pitchDegrees, MIN_PITCH_DEGREES, MAX_PITCH_DEGREES);
    }

    private static float clampDistance(float distance) {
        return Math.clamp(distance, MIN_DISTANCE, MAX_DISTANCE);
    }
}
