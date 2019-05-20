#version 430

layout(local_size_x = 1, local_size_y = 1) in;
layout(rgba32f, binding = 0) uniform image2D img_output;

vec3 camera = vec3(0.0, 0.0, -0.1); // Later to be passed as uniform from the application
vec3 direction;

vec3 sphere_center = vec3(2.0, 2.0, 5);
float sphere_radius = 2.5;

void main() {
    // Base pixel color
    vec4 pixel_color = vec4(0.0, 0.0, 0.0, 1.0);
    // Get (x,y) position of this pixel in the texture (index in global work group)
    ivec2 pixel_coords = ivec2(gl_GlobalInvocationID.xy);
    ivec2 dimensions = imageSize(img_output);

    // Map pixel coordinates to normalized space: [-1,1]^2
    float x = (float(pixel_coords.x * 2 - dimensions.x) / dimensions.x);
    float y = (float(pixel_coords.y * 2 - dimensions.y) / dimensions.y);

    direction = normalize(vec3(x, y, 0) - camera);

    vec3 omc = camera - sphere_center;
    float b = dot(omc, direction);
    float c = dot(omc, omc) - sphere_radius * sphere_radius;
    float bsqmc = b * b - c;

    if (bsqmc >= 0.0) {
        pixel_color = vec4(1.0, 0.0, 0.0, 1.0);
    }

    // Output the computed color to the pixel in the image
    imageStore(img_output, pixel_coords, pixel_color);
}