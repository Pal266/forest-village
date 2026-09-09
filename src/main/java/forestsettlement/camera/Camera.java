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

    public void pan(float rightAmount, float forwardAmount) {
        float yawRadians = (float) Math.toRadians(yawDegrees);

        float forwardX = (float) Math.sin(yawRadians);
        float forwardZ = (float) Math.cos(yawRadians);
        float rightX = forwardZ;
        float rightZ = -forwardX;

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

    private static float clampPitch(float pitchDegrees) {
        return Math.clamp(pitchDegrees, MIN_PITCH_DEGREES, MAX_PITCH_DEGREES);
    }

    private static float clampDistance(float distance) {
        return Math.clamp(distance, MIN_DISTANCE, MAX_DISTANCE);
    }
}
