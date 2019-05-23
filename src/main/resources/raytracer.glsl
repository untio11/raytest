#version 430

layout(local_size_x = 32, local_size_y = 32) in;
layout(rgba32f, binding = 0) uniform image2D img_output;
layout(location = 0) uniform vec3 camera;
layout(location = 1) uniform struct {
    vec3 location;
    vec3 color;
float radius;
} spheres[11];

vec3 planepoint = vec3(0.0, -1.0, 0.0);
ivec2 pixel_coords;

vec3 light_center;
float light_radius;

vec3 discriminant(vec3 ray, vec3 source, vec3 target, float sphere_radius) {
    vec3 omc = source - target;
    float b = dot(omc, ray);
    float c = dot(omc, omc) - sphere_radius * sphere_radius;
    return vec3(b, c, (b * b - c));
}

vec4 shadowBounce(vec3 origin) {
    vec4 color = vec4(0.0, 0.0, 0.0, 1.0); // Base shadow color

    vec3 direction = normalize(spheres[10].location - origin);

    float smallest = 1.0 / 0; // infinity (?)
    vec3 sphere_center;
    float sphere_radius;

    for (int i = 0; i < 10; i++) {
        sphere_center = spheres[i].location;
        sphere_radius = spheres[i].radius;

        vec3 disc = discriminant(direction, origin, sphere_center, sphere_radius);
        float distance = min(-disc.x + sqrt(disc.z), -disc.x - sqrt(disc.z));

        if (disc.z < 0 || distance <= 0) continue;

        return vec4(0.0, 0.0, 0.0, 0.0);
    }

    return vec4(1.0, 1.0, 1.0, 1.0);
}

vec4 trace() {
    vec4 color = vec4(0.7, 0.91, 1.0, 1.0); // Base color

    // Get (x,y) position of this pixel in the texture (index in global work group)
    pixel_coords = ivec2(gl_GlobalInvocationID.xy);
    ivec2 dimensions = imageSize(img_output);

    // Map pixel coordinates to normalized space: [-1,1]^2
    float x = (float(pixel_coords.x * 2 - dimensions.x) / dimensions.x) + camera.x;
    float y = (float(pixel_coords.y * 2 - dimensions.y) / dimensions.y) + camera.y;
    float z = camera.z + 1.2;

    vec3 direction = normalize(vec3(x, y, z) - camera);
    float smallest = 1.0 / 0; // infinity (?)
    vec3 intersection;
    vec3 sphere_center;
    float sphere_radius;

    for (int i = 0; i < 11; i++) {
        sphere_center = spheres[i].location;
        sphere_radius = spheres[i].radius;

        vec3 disc = discriminant(direction, camera, sphere_center, sphere_radius);

        if (disc.z < 0) continue; // no intersection

        float distance = min(-disc.x + sqrt(disc.z), -disc.x - sqrt(disc.z));
        smallest = distance < smallest ? distance : smallest;

        if (distance >= 0 && smallest == distance) { // If the current sphere is the closest and it hits, color it
            vec3 intersection = distance * direction;
            color = vec4(spheres[i].color, 1.0);// * shadowBounce(intersection);
        }
    }

    vec3 normal = vec3(0.0, 1.0, 0.0);
    float ndotu = dot(direction, normal); // 0 if ray and plane are parallel.

    float d = (dot((planepoint - camera), normal) / ndotu ); // find intersection with bottom plane
    intersection = d * direction + camera;

    if (d >= 0.0  && d <= smallest) {
        color = vec4(0.4, 0.4, 0.4, 1.0) * shadowBounce(intersection);
    }

    return color;
}


void main() {
    // Output the computed color to the pixel in the image
    imageStore(img_output, pixel_coords, trace());
}