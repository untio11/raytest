import org.joml.Vector3f;

public class Sphere {
    Vector3f center;
    float radius;
    Vector3f color;

    Sphere(Vector3f center, float radius, Vector3f color) {
        this.center = center;
        this.color = color;
        this.radius = radius;
    }
}
