import org.joml.Vector3f;

public class Sphere {
    private Vector3f center;
    private float radius;
    private Vector3f color;

    Sphere(Vector3f center, float radius, Vector3f color) {
        this.center = center;
        this.color = color;
        this.radius = radius;
    }
}
