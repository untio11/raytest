#version 430

layout(local_size_x = 32, local_size_y = 32) in;
layout(rgba32f, binding = 0) uniform image2D img_output;

layout(location = 0) uniform vec3 camera;
layout(location = 1) uniform float fov;
layout(location = 2) uniform mat3 transform;
layout(location = 3) uniform int tringle_amount;

layout(std430, binding = 1) buffer VertexPositions {
    vec4 parsed_positions[];
};

layout(std430, binding = 2) buffer VertexNormals {
    vec4 parsed_normals[];
};

struct Triangle {
    vec3 vertices[3];
    vec3 normals[3];
};

Triangle tringles[10];

ivec2 pixel_coords;
vec4 end_color;

vec3 getRay() {
    ivec2 dimensions = imageSize(img_output);
    // Map pixel coordinates to normalized space: [-1,1]^2 (sorta, taking care of aspect ratio)
    float x = (float(pixel_coords.x * 2.0 - dimensions.x) * 16.0 / (dimensions.x * 9.0)) + camera.x;
    float y = (float(pixel_coords.y * 2.0 - dimensions.y) / dimensions.y) + camera.y;
    float z = camera.z + fov;
    return normalize(vec3(x, y, z) - camera) * transform;
}

vec4 trace() {
    vec4 color = vec4(0.0, 0.0, 0.0, 1.0);
    vec3 ray = getRay();
    ray.z = -ray.z; // Really hacky and should not be the case, but i don't really know how to fix it otherwise for now.

    float tringle_area, ray_tringle_angle, d, t, u, v; // u and v can be used for interpolating
    vec3 C, P; // P is the intersection point of the ray and the tringle, C is used to check if the intersection is inside
    vec3 edge0, edge1, edge2; // edge0 (v0->v1) egde1 (v1->v2) edge2(v2->v0)
    vec3 tringle_normal;

    float closest = 1.0 / 0.0;

    //color = vec4(ray, 1.0);

    for (int i = 0; i < tringle_amount; i++) {
        tringle_normal = cross(parsed_positions[(i * 3) + 1].xyz - parsed_positions[(i * 3) + 0].xyz,
                               parsed_positions[(i * 3) + 2].xyz - parsed_positions[(i * 3) + 0].xyz);

        ray_tringle_angle = dot(tringle_normal, ray);

        if (abs(ray_tringle_angle) <= 0.00001) { // Close enough to zero to count as parallel, so move on to the next one
            continue;
        }

        //tringle_area = length(tringle_normal) / 2.0; // Doesn't per say have to be divided by 2
        d = dot(tringle_normal, parsed_positions[(i * 3) + 0].xyz);
        t = (dot(tringle_normal, camera) + d) / ray_tringle_angle;
        if (t < 0.0 || t >= closest) { // Tringle is behind the camera or not the closest tringle, so skip it
            continue;
        }

        P = camera + t * ray;

        // Check with the first edge (v0->v1)
        edge0 = parsed_positions[(i * 3) + 1].xyz - parsed_positions[(i * 3) + 0].xyz;
        C = cross(edge0, (P - parsed_positions[(i * 3) + 0].xyz));
        if (dot(tringle_normal, C) < 0.0) { // P is outside the tringle
            continue;
        }

        // Check with the second edge (v1->v2)
        edge1 = parsed_positions[(i * 3) + 2].xyz - parsed_positions[(i * 3) + 1].xyz;
        C = cross(edge1, (P - parsed_positions[(i * 3) + 1].xyz));
        if (dot(tringle_normal, C) < 0.0) { // P is outside the tringle
            continue;
        }

        // Check with the last edge (v2->v0)
        edge2 = parsed_positions[(i * 3) + 0].xyz - parsed_positions[(i * 3) + 2].xyz;
        C = cross(edge2, (P - parsed_positions[(i * 3) + 2].xyz));
        if (dot(tringle_normal, C) < 0.0) { // P is outside the tringle
            continue;
        }

        color = vec4(1.0, 1.0, 1.0, 1.0);
    }

    return color;
}

void main() {
    // Get (x,y) position of this pixel in the texture (index in global work group)
    pixel_coords = ivec2(gl_GlobalInvocationID.xy);

    //generateTringles();

    end_color = trace();

    imageStore(img_output, pixel_coords, end_color);
}
