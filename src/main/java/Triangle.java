import com.sun.istack.internal.NotNull;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class Triangle {
    static int counter = 0;

    int id;
    public enum data_type {
        NORMAL,
        VERTEX
    }

    Vector4f[] vertices = new Vector4f[3];
    Vector4f[] normals = new Vector4f[3];

    Triangle(Vector3f[] points, Vector3f[] normals) {
        this.id = ++counter;

        for (int i = 0; i < 3; i++) {
            this.normals[i] = new Vector4f(normals[i], 1.0f);
            this.vertices[i] = new Vector4f(points[i], 1.0f);
        }
    }

    float[] getData(data_type type) {
        List<Float> result = new ArrayList<>();
        Vector4f[] list = type.equals(data_type.NORMAL) ? normals : vertices;

        for (Vector4f vertex : list) {
            for (int i = 0; i < 3; i++) {
                result.add(vertex.get(i));
            }
        }

        float[] real_result = new float[result.size()];

        for (int i = 0; i < result.size(); i++) {
            real_result[i] = result.get(i);
        }

        return real_result;
    }

    float[] getAllData() {
        List<Float> result = new ArrayList<>();
        Vector4f[] list1 = vertices;
        Vector4f[] list2 = normals;

        for (int i = 0; i < list1.length; i++) {
            for (int j = 0; j < 3; j++) {
                result.add(list1[i].get(j));
            }

            for (int j = 0; j < 3; j++) {
                result.add(list2[i].get(j));
            }
        }

        float[] real_result = new float[result.size()];

        for (int i = 0; i < result.size(); i++) {
            real_result[i] = result.get(i);
        }

        return real_result;

    }

    @Override
    @NotNull
    public String toString() {
        return String.format("Trig #%d: (%f, %f, %f), (%f, %f, %f), (%f, %f, %f)", id, vertices[0].x, vertices[0].y, vertices[0].z, vertices[1].x, vertices[1].y, vertices[1].z, vertices[2].x, vertices[2].y, vertices[2].z);
    }
}
