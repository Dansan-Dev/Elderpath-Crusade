#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform float u_blurSize;
uniform vec2 u_direction; // (1.0, 0.0) for horizontal, (0.0, 1.0) for vertical

varying vec2 v_texCoord;

void main() {
    vec4 sum = vec4(0.0);
    sum += texture2D(u_texture, v_texCoord - 4.0 * u_direction * u_blurSize) * 0.05;
    sum += texture2D(u_texture, v_texCoord - 3.0 * u_direction * u_blurSize) * 0.09;
    sum += texture2D(u_texture, v_texCoord - 2.0 * u_direction * u_blurSize) * 0.12;
    sum += texture2D(u_texture, v_texCoord - 1.0 * u_direction * u_blurSize) * 0.15;
    sum += texture2D(u_texture, v_texCoord) * 0.16;
    sum += texture2D(u_texture, v_texCoord + 1.0 * u_direction * u_blurSize) * 0.15;
    sum += texture2D(u_texture, v_texCoord + 2.0 * u_direction * u_blurSize) * 0.12;
    sum += texture2D(u_texture, v_texCoord + 3.0 * u_direction * u_blurSize) * 0.09;
    sum += texture2D(u_texture, v_texCoord + 4.0 * u_direction * u_blurSize) * 0.05;

    gl_FragColor = sum;
}
