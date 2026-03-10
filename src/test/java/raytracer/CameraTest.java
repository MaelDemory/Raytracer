package raytracer;

import geometry.Point;
import geometry.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraTest {

    @Test
    void testConstructorAndGetters() {
        Point lookFrom = new Point(0, 0, 0);
        Point lookAt = new Point(0, 0, -1);
        Vector up = new Vector(0, 1, 0);
        int fov = 90;

        Camera camera = new Camera(lookFrom, lookAt, up, fov);

        assertEquals(lookFrom, camera.getLookFrom());
        assertEquals(lookAt, camera.getLookAt());
        assertEquals(up, camera.getUp());
        assertEquals(fov, camera.getFov());
    }
}
