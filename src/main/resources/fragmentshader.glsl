#version 330

in tex_coord; // From the vertex shader
uniform sampler2D tex; // The image created by the raytracer (in theory)

out vec4 color;

void main() {
    color = texture(tex, tex_coord);
}