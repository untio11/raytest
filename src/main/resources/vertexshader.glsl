#version 330

in vec4 position_in;
in vec4 color_in;

out vec2 tex_coord;
out vec4 color_pass;

void main() {
    gl_Position = vec4(position_in);
    /*
     * Compute texture coordinate by simply
     * interval-mapping from [-1..+1] to [0..1]
     */
    tex_coord = position_in.xy * 0.5 + vec2(0.5, 0.5);
    color_pass = color_in;
}