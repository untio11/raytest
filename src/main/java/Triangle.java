import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class Triangle {
    public enum data_type {
        NORMAL,
        VERTEX
    }

    Vector3f[] vertices;
    Vector3f[] normals;

    Triangle(Vector3f[] points, Vector3f[] normals) {
        this.normals = normals;
        this.vertices = points;
    }

    float[] getData(data_type type) {
        List<Float> result = new ArrayList<>();
        Vector3f[] list = type.equals(data_type.NORMAL) ? normals : vertices;
        for (Vector3f vertex : list) {
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
        Vector3f[] list1 = vertices;
        Vector3f[] list2 = normals;

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
}
