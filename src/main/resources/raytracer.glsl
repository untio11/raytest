#version 430

layout(local_size_x = 1, local_size_y = 1) in;
layout(rgba32f, binding = 0) uniform image2D img_output;

void main() {
    // Base pixel color
    vec4 pixel = vec4(1.0, 0.0, 0.0, 1.0);
    // Get (x,y) position of this pixel in the texture (index in global work group)
    ivec2 pixel_coords = ivec2(gl_GlobalInvocationID.xy);



    // Output the computed color to the pixel in the image
    imageStore(img_output, pixel_coords, pixel);
}