#version 430

layout(local_size_x = 32, local_size_y = 32) in;
layout(rgba32f, binding = 0) uniform image2D img_output;
layout(location = 0) uniform vec3 camera;
layout(location = 1) uniform struct {
    vec3 location;
    vec3 color;
    float radius;
} spheres[10];

vec3 plane[] = {
    vec3(-2f, -2f, 0f),
    vec3(2f, -2f, 4f)
};

vec3 direction;
ivec2 pixel_coords;

vec3 sphere_center;
float sphere_radius;

vec4 trace() {
    vec4 color = vec4(0.7, 0.91, 1.0, 1.0);

    // Get (x,y) position of this pixel in the texture (index in global work group)
    pixel_coords = ivec2(gl_GlobalInvocationID.xy);
    ivec2 dimensions = imageSize(img_output);

    // Map pixel coordinates to normalized space: [-1,1]^2
    float x = (float(pixel_coords.x * 2 - dimensions.x) / dimensions.x);
    float y = (float(pixel_coords.y * 2 - dimensions.y) / dimensions.y);

    direction = normalize(vec3(x, y, 0) - camera);
    float smallest = 1.0 / 0; // infinity (?)

    for (int i = 0; i < 10; i++) {
        sphere_center = spheres[i].location;
        sphere_radius = spheres[i].radius;

        vec3 omc = camera - sphere_center;
        float b = dot(omc, direction);
        float c = dot(omc, omc) - sphere_radius * sphere_radius;
        float bsqmc = b * b - c;

        float distance = min(-b + sqrt(bsqmc), -b - sqrt(bsqmc));
        smallest = distance < smallest ? distance : smallest;

        if (smallest == distance && bsqmc >= 0.0) { // If the current sphere is the closest and it hits, color it
            color = vec4(spheres[i].color, 1.0);
        }
    }

    vec3 normal = vec3(0.0, 1.0, 0.0);
    float d = ( dot((plane[0] - camera), normal) / dot(direction, normal) ); // find intersection with bottom plane

    if (d >= 0.0 && length(d * direction + camera) <= smallest) {
        color = vec4(0.4, 0.4, 0.4, 1.0);
    }

    return color;
}

void main() {
    // Output the computed color to the pixel in the image
    imageStore(img_output, pixel_coords, trace());
}