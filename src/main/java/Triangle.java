import org.joml.Vector3f;

public class Triangle {
    Vector3f[] vertices;
    Vector3f[] normals;

    Triangle(Vector3f[] points, Vector3f[] normals) {
        this.normals = normals;
        this.vertices = points;
    }
}
