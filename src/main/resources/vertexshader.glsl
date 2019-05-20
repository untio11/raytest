#version 330

in vec2 vertex;
out vec2 tex_coord;

void main() {
    gl_Position = vec4(vertex, 0.0, 1.0);

    /*
     * Compute texture coordinate by simply
     * interval-mapping from [-1..+1] to [0..1]
     */
    texcoord = vertex * 0.5 + vec2(0.5, 0.5);
}