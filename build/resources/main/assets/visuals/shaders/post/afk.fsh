#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform AfkConfig {
    float Strength;
};

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// Kino-Look: Unschärfe zum Rand, weiches Glühen heller Stellen, Farbkorrektur, Vignette und feines Filmkorn.
void main() {
    vec2 uv = texCoord;
    vec2 texel = 1.0 / InSize;
    vec2 centered = (uv - 0.5) * vec2(InSize.x / InSize.y, 1.0);
    float dist = length(centered);

    // Unschärfe wächst zum Bildrand (Tilt-Shift-Gefühl)
    float blurAmount = smoothstep(0.25, 0.85, dist) * 6.0 * Strength;
    vec3 base = texture(InSampler, uv).rgb;
    vec3 acc = base;
    float weight = 1.0;
    float jitter = hash(uv * 37.0) * 0.5;
    for (int i = 0; i < 12; i++) {
        float angle = float(i) * 0.5236 + jitter;
        float radius = blurAmount * (0.5 + float(i % 3) * 0.5);
        vec2 offset = vec2(cos(angle), sin(angle)) * texel * radius;
        acc += texture(InSampler, uv + offset).rgb;
        weight += 1.0;
    }
    vec3 color = acc / weight;

    // weiches Glühen: helle Bereiche der Umgebung addieren
    vec3 glowAcc = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float angle = float(i) * 0.7854;
        glowAcc += texture(InSampler, uv + vec2(cos(angle), sin(angle)) * texel * 10.0).rgb;
    }
    color += max(glowAcc / 8.0 - 0.6, 0.0) * 0.8 * Strength;

    // Farbkorrektur: mehr Sättigung und Kontrast, warme Lichter, kühle Schatten
    float lum = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(lum), color, 1.0 + 0.12 * Strength);
    color = (color - 0.5) * (1.0 + 0.08 * Strength) + 0.5;
    color += vec3(0.02, 0.0, -0.02) * lum * Strength;
    color += vec3(-0.01, 0.0, 0.02) * (1.0 - lum) * Strength;

    // Vignette
    float vignette = smoothstep(0.95, 0.35, dist);
    color *= mix(1.0, vignette, 0.55 * Strength);

    // Filmkorn
    float grain = hash(uv * InSize + fract(GameTime * 1000.0)) - 0.5;
    color += grain * 0.035 * Strength;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
