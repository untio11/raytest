#version 430

layout(local_size_x = 32, local_size_y = 32) in;
layout(rgba32f, binding = 0) uniform image2D img_output;
layout(location = 0) uniform vec3 camera;
layout(location = 1) uniform float fov;
layout(location = 2) uniform mat3 transform;

uniform struct {
    vec3 location;
    vec3 color;
    float radius;
    float shininess;
} spheres[10];

uniform struct {
    bool toggle;
    vec3 location;
} lights[3];

vec3 planepoint = vec3(0.0, -6.0, 0.0);
vec3 planenormal = vec3(0.0, 1.0, 0.0);
ivec2 pixel_coords;


vec3 discriminant(vec3 ray, vec3 source, vec3 target, float sphere_radius) {
    vec3 omc = source - target;
    float b = dot(omc, ray);
    float c = dot(omc, omc) - sphere_radius * sphere_radius;
    return vec3(b, c, (b * b - c));
}

vec4 phong(vec3 point, vec3 normal, vec3 light_source, vec4 sphere_color, float shininess) {
    vec4 result = 0.0 * sphere_color; // ambient light initial color

    vec3 L = (light_source - point); // Direction from point to light source
    float angle = max(dot(normal, L) / (length(normal)*length(L)), 0.0); // Angle between normal and light source

    // Diffuse contribution
    result += angle * sphere_color;

    vec3 E = normalize(point - camera); // viewdirection
    vec3 halfway = normalize(L + E);
    float spec = pow(max(dot(normal, halfway), 0.0), shininess);
    result += spec * sphere_color;
    return result;
}

vec4 shadowBounce(vec3 origin, vec3 light_source) {
    vec3 direction = normalize(light_source - origin);

    vec3 sphere_center;
    float sphere_radius;

    for (int i = 0; i < 10; i++) {
        sphere_center = spheres[i].location;
        sphere_radius = spheres[i].radius;
        vec3 disc = discriminant(direction, origin, sphere_center, sphere_radius);
        float distance = min(-disc.x + sqrt(disc.z), -disc.x - sqrt(disc.z));

        if (disc.z < 0 || distance <= 0) continue;

        return vec4(0.0, 0.0, 0.0, 1.0);
    }

    float ndotu = dot(direction, planenormal); // 0 if ray and plane are parallel.
    float d = (dot((planepoint - camera), planenormal) / ndotu ); // find intersection with bottom plane

    if (d >= 0.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }

    return vec4(1.0, 1.0, 1.0, 1.0);
}

vec3 getRay() {
    // Get (x,y) position of this pixel in the texture (index in global work group)
    pixel_coords = ivec2(gl_GlobalInvocationID.xy);
    ivec2 dimensions = imageSize(img_output);

    // Map pixel coordinates to normalized space: [-1,1]^2 (sorta, taking care of aspect ratio)
    float x = (float(pixel_coords.x * 2.0 - dimensions.x) * 16.0 / (dimensions.x * 9.0)) + camera.x;
    float y = (float(pixel_coords.y * 2.0 - dimensions.y) / dimensions.y) + camera.y;
    float z = camera.z + fov;
    return normalize(vec3(x, y, z) - camera) * transform;
}

vec4 trace() {
    vec4 color = vec4(0.0, 0.0, 0.0, 1.0); // Base color
    vec3 direction = getRay();
    float smallest = 1.0 / 0; // infinity (?)
    vec3 sphere_center;
    float sphere_radius;

    for (int i = 0; i < spheres.length(); i++) {
        sphere_center = spheres[i].location;
        sphere_radius = spheres[i].radius;

        vec3 disc = discriminant(direction, camera, sphere_center, sphere_radius);
        float distance = min(-disc.x + sqrt(disc.z), -disc.x - sqrt(disc.z));

        if (disc.z < 0 || distance <= 0) continue; // no intersection

        smallest = distance < smallest ? distance : smallest;

        if (distance >= 0 && smallest == distance) { // If the current sphere is the closest and it hits, color it
            vec3 intersection = distance * direction + camera;
            vec3 sphere_normal = (intersection - sphere_center) / sphere_radius;
            color = vec4(0.0, 0.0, 0.0, 1.0);
            for (int j = 0; j < lights.length(); j ++) { // Treat the last two spheres as light sources
                if (lights[j].toggle)
                color += phong(intersection, sphere_normal, lights[j].location, vec4(spheres[i].color, 1.0), spheres[i].shininess) * shadowBounce(intersection + 0.0001 * sphere_normal, lights[j].location);
            }
        }
    }

    float ndotu = dot(direction, planenormal); // 0 if ray and plane are parallel.

    float d = (dot((planepoint - camera), planenormal) / ndotu ); // find intersection with bottom plane
    vec3 intersection = d * direction + camera;

    if (d >= 0.0  && d <= smallest) {
        for (int j = 0; j < lights.length(); j ++) { // Treat the last two spheres as light sources
            if (lights[j].toggle)
            color += phong(intersection, planenormal, lights[j].location, vec4(0.4, 0.4, 0.4, 1.0), 556.0) * shadowBounce(intersection + 0.0001 * planenormal, lights[j].location);
        }
    }

    return color;
}


void main() {
    // Output the computed color to the pixel in the image
    imageStore(img_output, pixel_coords, trace());
}