import com.sun.javafx.collections.FloatArraySyncer;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Main {
    private long window; // The window handle
    private int width = 300;
    private int height = 300;

    private int vaoId, VertexVBO, IndexVBO; // VAO and VBO's
    private int quadProgram, rayProgram; // Shader programs
    private int rayTexture; // Image for the raytracing

    float[] vertices = {
            -1f,  1f, 0f, 1f, // 1/6 -> ID:0
            -0.51f, -0.51f, 0f, 1f, // 2   -> ID:1
             1f, -1f, 0f, 1f, // 3/4 -> ID:2
             1f,  1f, 0f, 1f, // 5   -> ID:3
    };

    byte[] indices = {
            0, 1, 2,
            2, 3, 0
    };

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

        // Remember key state until it has been handled (AKA doesn't miss a key press)
        glfwSetInputMode(window, GLFW_STICKY_KEYS, GLFW_TRUE);
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
        glfwSwapInterval(1);
    }

    private void setupQuad() {
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        FloatBuffer verticesBuffer = createBuffer(vertices);
        ByteBuffer indexBuffer = createBuffer(indices);

        VertexVBO = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, VertexVBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);

        GL30.glVertexAttribPointer(0, 4, GL30.GL_FLOAT, false, 0, 0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        IndexVBO = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, IndexVBO);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
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
        int ray_shader = GL43C.glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(ray_shader, loadFile("src/main/resources/raytracer.glsl"));
        glCompileShader(ray_shader);

        rayProgram = glCreateProgram();
        glAttachShader(rayProgram, ray_shader);
        glLinkProgram(rayProgram);
        glValidateProgram(rayProgram);
    }

    private void createQuadProgram() {
        int vertexshader = GL43C.glCreateShader(GL_VERTEX_SHADER);
        int fragmentshader = GL43C.glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(vertexshader, loadFile("src/main/resources/vertexshader.glsl"));
        glShaderSource(fragmentshader, loadFile("src/main/resources/fragmentshader.glsl"));

        quadProgram = glCreateProgram();
        glAttachShader(quadProgram, vertexshader);
        glAttachShader(quadProgram, fragmentshader);

        GL20.glBindAttribLocation(quadProgram, 0, "vertex"); // Position in (x,y)
        glLinkProgram(quadProgram);
        glValidateProgram(quadProgram);
    }

    private void loop() {
        GL.createCapabilities();
        GL11.glClearColor(0.4f, 0.3f, 0.9f, 0f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        setupQuad();
        createQuadProgram();
        setupTexture();
        createRayProgram();

        while (!glfwWindowShouldClose(window)) {
            render();
            glUseProgram(0);
            glfwPollEvents();
        }
    }

    private void renderQuad() {
        GL30.glBindVertexArray(vaoId);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, IndexVBO);

        // Draw the vertices
        GL11.glDrawElements(GL11.GL_TRIANGLES, indices.length, GL11.GL_UNSIGNED_BYTE, 0);

        // Put everything back to default (deselect)
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL20.glDisableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
    }

    private void render() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        glUseProgram(rayProgram);
        glDispatchCompute(width, height, 1);

        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

        glUseProgram(quadProgram);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, rayTexture);
        renderQuad();

        glfwSwapBuffers(window); // swap the color buffers
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

        handleKeys(pressedKeys);
    }

    private void windowSizeCallback(long window, int width, int height) {
        GL11.glViewport(0, 0, width, height);
        this.width = width;
        this.height = height;
    }

    private void handleKeys(Set<Integer> pressedKeys) {
        for (int keyPressed : pressedKeys) {
            switch (keyPressed) {
                case GLFW_KEY_ESCAPE:
                    glfwSetWindowShouldClose(window, true);
            }
        }
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

    private String loadFile(String filename) {
        StringBuilder shader = new StringBuilder();
        String line = null ;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filename));
            while( (line = reader.readLine()) != null ) {
                shader.append(line);
                shader.append('\n');
            }
        } catch(IOException e) {
            throw new IllegalArgumentException("unable to load shader from file ["+filename+"]");
        }

        return shader.toString();
    }
}
