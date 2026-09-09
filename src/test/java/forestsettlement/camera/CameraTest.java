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
