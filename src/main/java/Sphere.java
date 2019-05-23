import org.joml.Vector3f;

public class Sphere {
    Vector3f center;
    float radius;
    Vector3f color;
    float shininess;

    Sphere(Vector3f center, float radius, Vector3f color, float shininess) {
        this.center = center;
        this.color = color;
        this.radius = radius;
        this.shininess = shininess;
    }
}
