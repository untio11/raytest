import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Main {
    private static final int VSYNCH = 0;

    private long window; // The window handle
    private int width = 1280;
    private int height = 720;
    private double movement_param = 0d;
    private double last_time, current_time, delta;
    private boolean showfps = true;
    private boolean updated = false;

    private int vaoId, VertexVBO, IndexVBO, ColorVBO; // VAO and VBO's
    private int vertexSSBO, normalSSBO; // Shader buffer objects for passing triangle data
    private int quadProgram, rayProgram; // Shader programs
    private int rayTexture; // Image for the raytracing
    private int cameraSSBO;

    private static float[] vertices = {
            -1f,  1f, 0f, 1f, // 1/6 -> ID:0
            -1f, -1f, 0f, 1f, // 2   -> ID:1
             1f, -1f, 0f, 1f, // 3/4 -> ID:2
             1f,  1f, 0f, 1f, // 5   -> ID:3
    };

    private static byte[] indices = {
            0, 1, 2,
            2, 3, 0
    };

    float[] colors = {
            1f, 0f, 0f, 1f,
            0f, 1f, 0f, 1f,
            0f, 0f, 1f, 1f,
            1f, 1f, 1f, 1f,
    };

    private static final int tringle_amount = 1;
    private Triangle[] tringles = generateTringles();

    private Sphere[] scene = generateSpheres();

    private float[] lights = {
            -10f,  10f, 0f,
             10f,  10f, 0f,
              0f,  10f, 20f
    };

    private boolean[] lightswitch = {true, true, true}; // Toggle light activity
    private boolean moving_spheres, move_lights = false;

    private Vector3f camera = new Vector3f(0f, 0f, -2f);
    private static float fov = 1.2f; // Camera to viewport distance. smaller fov => wider viewangle
    private static Vector3f forward = new Vector3f(0f, 0f, 1f);
    private static Vector3f up = new Vector3f(0f, 1f, 0f);
    private static Vector3f right = new Vector3f(1f, 0f, 0f);


    private Set<Integer> pressedKeys = new HashSet<>(); // To collect all pressed keys for processing

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        init();
        loop();
        clean();
    }

    private void init() {
        // Redirect errors to System.error for debugging
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) // Initialize GLFW
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable
        glfwWindowHint(GLFW_AUTO_ICONIFY, GLFW_TRUE); // The window will minimize when out of focus and in full screen

        // We need at least openGL version 4.3 for the compute shaders.
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);

        // Create the window in windowed mode
        window = glfwCreateWindow(width, height, "4", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }
        glfwSetWindowAspectRatio(window, 16, 9);

        // Remember key state until it has been handled (AKA doesn't miss a key press)
        //glfwSetInputMode(window, GLFW_STICKY_KEYS, GLFW_TRUE);
        glfwSetKeyCallback(window, this::KeyCallback);
        glfwSetWindowSizeCallback(window, this::windowSizeCallback);

        // Get the video mode to fetch the screen resolution
        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        glfwSetWindowPos( // Center the window inside the screen
                window,
                (vidmode.width() - width) / 2,
                (vidmode.height() - height) / 2
        );

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(VSYNCH);

        System.out.println("Compute shader " + glfwExtensionSupported("ARB_compute_shader"));
    }

    private void loop() {
        GL.createCapabilities();
        GL11.glClearColor(0.4f, 0.3f, 0.9f, 0f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        (new Timer()).schedule((new InputHandler()), 0, 1);

        setupQuad();
        createQuadProgram();
        setupTexture();
        setupTriangle();
        createRayProgram();

        last_time = glfwGetTime();
        int frames = 0;

        while (!glfwWindowShouldClose(window)) {
            current_time = glfwGetTime();
            if (moving_spheres)
                moveSpheres(current_time);
            if (move_lights)
                moveLights(current_time);
            frames++;

            if (current_time - last_time > 1.0) { // Print average framerate every 5 seconds
                if (showfps) System.out.println(String.format("%f ms/frame <=> %f frames/s", 1000d/((double) frames), (double)frames / 1d));
                frames = 0;
                last_time = glfwGetTime();
            }

            render();
            glfwPollEvents();
        }
    }

    private Sphere[] generateSpheres() {
        Sphere[] spheres = new Sphere[10];
        Random generator = new Random();

        for(int i = 0; i < 10; i++) {
            spheres[i] = new Sphere(
                    new Vector3f(
                            (generator.nextFloat() - 0.5f) * 10f,
                            (generator.nextFloat() - 0.5f) * 10f,
                            (generator.nextFloat() - 0.5f) * 5f + 7f
                    ),
                    generator.nextFloat() * 0.2f + 0.6f,
                    new Vector3f(
                            generator.nextFloat(),
                            generator.nextFloat(),
                            generator.nextFloat()
                    ),
                    generator.nextFloat() * 75f + 1f
            );
        }

        return spheres;
    }

    private Triangle[] generateTringles() {
        Triangle[] result = new Triangle[tringle_amount];
        Random generator = new Random();

        for (int i = 0; i < tringle_amount; i++) {
            Vector3f start = new Vector3f(
                    (generator.nextFloat() - 0.5f) * 10f,
                    (generator.nextFloat() - 0.5f) * 10f,
                    (generator.nextFloat() - 0.5f) * 7f + 3f
            );

            Vector3f vertex1 = new Vector3f(
                    start.x + (generator.nextFloat() - 0.5f) *  2f,
                    start.y + (generator.nextFloat() - 0.5f) *  2f,
                    start.z + (generator.nextFloat() - 0.5f) *  2f
            );

            Vector3f vertex2 = new Vector3f(
                    start.x + (generator.nextFloat() - 0.5f) *  2f,
                    start.y + (generator.nextFloat() - 0.5f) *  2f,
                    start.z + (generator.nextFloat() - 0.5f) *  2f
            );

            result[i] = new Triangle(
                    new Vector3f[] {
                            start,
                            vertex1,
                            vertex2
                    }, new Vector3f[] {
                    new Vector3f(0f, 0f, -1f),
                    new Vector3f(0f, 0f, -1f),
                    new Vector3f(0f, 0f, -1f)
            });
        }
        updated = true;
        return result;
    }

    private void moveSpheres(double time) {
        for (Sphere sphere : scene) {
            sphere.center.x = (float) (sphere.shininess * Math.sin(time + sphere.shininess) * 0.005f + sphere.center.x);
            sphere.center.z = (float) (sphere.shininess * Math.cos(time + sphere.shininess) * 0.005f + sphere.center.z);
        }
    }

    private void moveLights(double time) {
        for (int i = 0; i < lights.length/3; i++) {
            lights[i * 3] += (float) ((-Math.sin(time * (i + 1)) * 0.2f));
            lights[(i * 3) + 2] += (float) ((-Math.cos(time * (i + 1)) * 0.2f));
        }
    }

    private void setupTriangle() {
        float[] vertex_data_raw = new float[tringles.length * 12];
        float[] normal_data_raw = new float[tringles.length * 12];

        for (int i = 0; i < tringles.length; i++) {
            for (int j = 0; j < tringles[i].getData(Triangle.data_type.VERTEX).length; j++) {
                vertex_data_raw[(i * 12) + j] = tringles[i].getData(Triangle.data_type.VERTEX)[j];
            }
            for (int j = 0; j < tringles[i].getData(Triangle.data_type.NORMAL).length; j++) {
                normal_data_raw[(i * 12) + j] = tringles[i].getData(Triangle.data_type.NORMAL)[j];
            }
        }

        FloatBuffer vertex_data = createBuffer(vertex_data_raw);
        FloatBuffer normal_data = createBuffer(normal_data_raw);

        vertexSSBO = GL43.glGenBuffers();
        GL43.glBindBuffer(GL43.GL_ARRAY_BUFFER, vertexSSBO);
        GL43.glBufferData(GL43.GL_ARRAY_BUFFER, vertex_data, GL43.GL_STATIC_DRAW);

        normalSSBO = GL43.glGenBuffers();
        GL43.glBindBuffer(GL43.GL_ARRAY_BUFFER, normalSSBO);
        GL43.glBufferData(GL43.GL_ARRAY_BUFFER, normal_data, GL43.GL_STATIC_DRAW);
    }

    private void setupQuad() {
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        FloatBuffer verticesBuffer = createBuffer(vertices);
        ByteBuffer indexBuffer = createBuffer(indices);
        FloatBuffer colorBuffer = createBuffer(colors);

        VertexVBO = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, VertexVBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);

        GL30.glVertexAttribPointer(0, 4, GL30.GL_FLOAT, false, 0, 0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        ColorVBO = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, ColorVBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, colorBuffer, GL15.GL_STATIC_DRAW);

        GL30.glVertexAttribPointer(1, 4, GL30.GL_FLOAT, false, 0, 0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        IndexVBO = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, IndexVBO);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        GL30.glBindVertexArray(0);
    }

    private void createQuadProgram() {
        int vertexshader = loadShader("src/main/resources/vertexshader.glsl", GL_VERTEX_SHADER);
        int fragmentshader = loadShader("src/main/resources/fragmentshader.glsl", GL_FRAGMENT_SHADER);

        quadProgram = glCreateProgram();
        glAttachShader(quadProgram, vertexshader);
        glAttachShader(quadProgram, fragmentshader);

        GL20.glBindAttribLocation(quadProgram, 0, "position_in"); // Position in (x,y,z,w)

        glLinkProgram(quadProgram);
        glValidateProgram(quadProgram);
        System.out.println("[QuadProgram]: " + GL20.glGetProgramInfoLog(quadProgram));
    }

    private void setupTexture() {
        rayTexture = glGenTextures();
        GL15.glActiveTexture(GL13.GL_TEXTURE0);
        GL15.glBindTexture(GL_TEXTURE_2D, rayTexture);
        GL15.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL15.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL15.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL15.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GL15.glTexImage2D(GL_TEXTURE_2D, 0, GL30C.GL_RGBA32F, width, height, 0, GL_RGBA, GL_FLOAT, NULL);
        GL43C.glBindImageTexture(0, rayTexture, 0, false, 0, GL15C.GL_WRITE_ONLY, GL30C.GL_RGBA32F);
    }

    private void createRayProgram() {
        int ray_shader = loadShader("src/main/resources/triangleTracer.glsl", GL_COMPUTE_SHADER);
        System.out.println("[RayTracerShader]: " + GL43.glGetShaderInfoLog(ray_shader));

        rayProgram = glCreateProgram();
        glAttachShader(rayProgram, ray_shader);
        glLinkProgram(rayProgram);
        glValidateProgram(rayProgram);

        System.out.println("[RayTracerProgram]: " + GL43.glGetProgramInfoLog(rayProgram));
    }

    private void render() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        executeRay();
        renderQuad();

        glfwSwapBuffers(window); // swap the color buffers
    }

    private void executeRay() {
        int[] work_group_size = new int[3];
        GL20.glGetProgramiv(rayProgram, GL_COMPUTE_WORK_GROUP_SIZE, work_group_size);

        int work_x = getNextPowerOfTwo(width  / work_group_size[0]);
        int work_y = getNextPowerOfTwo(height / work_group_size[1]);

        GL41.glProgramUniform3f(rayProgram, 0, camera.x, camera.y, camera.z);
        GL41.glProgramUniform1f(rayProgram, 1, fov);
        GL41.glProgramUniformMatrix3fv(rayProgram, 2, false, new float[] {
                right.x, right.y, right.z,
                up.x, up.y, up.z,
                forward.x, forward.y, forward.z
        });

        GL43.glProgramUniform1i(rayProgram, 3, tringle_amount);

        glUseProgram(rayProgram);

        if (updated) {
            setupTriangle();
            updated = false;
        }

        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, vertexSSBO);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, normalSSBO);

        glDispatchCompute(work_x, work_y, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    private void setupExecRay() {
        for (int i = 0; i < scene.length; i++) {
            Sphere sphere = scene[i];
            GL41.glProgramUniform3f(rayProgram, GL41.glGetUniformLocation(rayProgram, String.format("spheres[%d].location", i)), sphere.center.x, sphere.center.y, sphere.center.z);
            GL41.glProgramUniform3f(rayProgram, GL41.glGetUniformLocation(rayProgram, String.format("spheres[%d].color", i)), sphere.color.x, sphere.color.y, sphere.color.z);
            GL41.glProgramUniform1f(rayProgram, GL41.glGetUniformLocation(rayProgram, String.format("spheres[%d].radius", i)), sphere.radius);
            GL41.glProgramUniform1f(rayProgram, GL41.glGetUniformLocation(rayProgram, String.format("spheres[%d].shininess", i)), sphere.shininess);
        }

        for (int i = 0; i < lights.length / 3; i++) {
            GL41.glProgramUniform3f(rayProgram, GL41.glGetUniformLocation(rayProgram, String.format("lights[%d].location", i)), lights[(3 * i)], lights[(3 * i) + 1], lights[(3 * i) + 2]);
            GL41.glProgramUniform1i(rayProgram, GL41.glGetUniformLocation(rayProgram, String.format("lights[%d].toggle", i)), lightswitch[i] ? 1 : 0);
        }
    }

    private void renderQuad() {
        glUseProgram(quadProgram);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, rayTexture);

        GL30.glBindVertexArray(vaoId);
        GL20.glEnableVertexAttribArray(0); // Vertex position data
        GL20.glEnableVertexAttribArray(1); // Vertex color data
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, IndexVBO); // Index data

        // Draw the vertices
        GL11.glDrawElements(GL11.GL_TRIANGLES, indices.length, GL11.GL_UNSIGNED_BYTE, 0);

        // Put everything back to default (deselect)
        glUseProgram(0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL30.glBindVertexArray(0);
    }

    private void clean() {
        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();

        // Disable the VBO index from the VAO attributes list
        GL20.glDisableVertexAttribArray(0);

        // Delete the VBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glDeleteBuffers(VertexVBO);

        // Delete the VAO
        GL30.glBindVertexArray(0);
        GL30.glDeleteVertexArrays(vaoId);
    }

    private void KeyCallback(long window, int key, int scancode, int action, int mods) {
        if (action == GLFW_PRESS) {
            pressedKeys.add(key);
        } else if (action == GLFW_RELEASE) {
            pressedKeys.remove(key);
        }

        //handleKeys(pressedKeys);
    }

    private void windowSizeCallback(long window, int width, int height) {
        GL11.glViewport(0, 0, width, height);
        this.width = width;
        this.height = height;
        setupTexture();
    }

    private FloatBuffer createBuffer(float[] data) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length);
        buffer.put(data);
        buffer.flip();
        return buffer;
    }

    private ByteBuffer createBuffer(byte[] data) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(data.length);
        buffer.put(data);
        buffer.flip();
        return buffer;
    }

    private int loadShader(String filename, int type) {
        StringBuilder shader_source = new StringBuilder();
        String line = null;

        try {
            BufferedReader reader = new BufferedReader(new FileReader(filename));
            while( (line = reader.readLine()) != null ) {
                shader_source.append(line);
                shader_source.append('\n');
            }
        } catch(IOException e) {
            throw new IllegalArgumentException("unable to load shader from file ["+filename+"]");
        }

        int shaderID = GL43C.glCreateShader(type);
        GL43C.glShaderSource(shaderID, shader_source);
        GL43C.glCompileShader(shaderID);
        return shaderID;
    }

    private static int getNextPowerOfTwo(int value) {
        int result = value;
        result -= 1;
        result |= result >> 16;
        result |= result >> 8;
        result |= result >> 4;
        result |= result >> 2;
        result |= result >> 1;
        return result + 1;
    }

    private class InputHandler extends TimerTask {
        @Override
        public void run() {
            handleKeys();
        }

        private void handleKeys() {
            Set<Integer> toRemove = new HashSet<>();

            for (int keyPressed : pressedKeys) {
                switch (keyPressed) {
                    case GLFW_KEY_ESCAPE:
                        glfwSetWindowShouldClose(window, true);
                        break;
                    case GLFW_KEY_F5:
                        scene = generateSpheres();
                        tringles = generateTringles();
                        toRemove.add(keyPressed);
                        break;
                    case GLFW_KEY_DOWN:
                        camera.y = Math.max(camera.y - 0.05f, -5.9f);
                        break;
                    case  GLFW_KEY_UP:
                        camera.y += 0.05f;
                        break;
                    case GLFW_KEY_LEFT:
                        camera.x -= 0.05f;
                        break;
                    case  GLFW_KEY_RIGHT:
                        camera.x += 0.05f;
                        break;
                    case GLFW_KEY_S:
                        camera.z -= 0.05f;
                        break;
                    case GLFW_KEY_W:
                        camera.z += 0.05f ;
                        break;
                    case GLFW_KEY_F1:
                        lightswitch[0] = !lightswitch[0];
                        toRemove.add(keyPressed);
                        break;
                    case GLFW_KEY_F2:
                        lightswitch[1] = !lightswitch[1];
                        toRemove.add(keyPressed);
                        break;
                    case GLFW_KEY_F3:
                        lightswitch[2] = !lightswitch[2];
                        toRemove.add(keyPressed);
                        break;
                    case GLFW_KEY_M:
                        moving_spheres = !moving_spheres;
                        toRemove.add(keyPressed);
                        break;
                    case GLFW_KEY_L:
                        move_lights = !move_lights;
                        toRemove.add(keyPressed);
                        break;
                    case GLFW_KEY_KP_4:
                        forward = forward.rotateY(0.01f);
                        up =      up.rotateY(0.01f);
                        right =   right.rotateY(0.01f);
                        break;
                    case GLFW_KEY_KP_6:
                        forward = forward.rotateY(-0.01f);
                        up =      up.rotateY(-0.01f);
                        right =   right.rotateY(-0.01f);
                        break;
                    case GLFW_KEY_KP_8:
                        forward = forward.rotateX(0.01f);
                        up =      up.rotateX(0.01f);
                        right =   right.rotateX(0.01f);
                        break;
                    case GLFW_KEY_KP_2:
                        forward = forward.rotateX(-0.01f);
                        up =      up.rotateX(-0.01f);
                        right =   right.rotateX(-0.01f);
                        break;
                    case GLFW_KEY_EQUAL:
                        fov += 0.02;
                        break;
                    case GLFW_KEY_MINUS:
                        fov = Math.max(fov - 0.02f, 0.2f);
                        break;
                    case GLFW_KEY_T:
                        showfps = !showfps;
                        toRemove.add(keyPressed);
                        break;
                    case GLFW_KEY_D:
                        System.out.println("-----------Debug-----------");
                        System.out.println("Camera: \t" + camera + ", Forward:" + forward.toString());
                        for (Triangle triangle : tringles) {
                            System.out.println("Triangles: \t" + triangle.toString());
                        }
                        System.out.println("---------------------------");
                        toRemove.add(keyPressed);
                        break;
                }
            }

            for (Integer key : toRemove) {
                pressedKeys.remove(key);
            }
        }

    }
}
