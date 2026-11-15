@file:Suppress("MagicNumber")

package app.parivaar.panchang.core.designsystem.planets

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * AGSL sphere renderer (API 33+), docs/specs/M07R.md section 6 "Planets". [PlanetShading] is its CPU twin:
 * change both together.
 *
 * Per pixel xy (px): p = (xy - center) / R, r2 = p.p (y down).
 * - Disc (r2 < 1): view normal n = (p.x, -p.y, sqrt(1 - r2)) (+z to the viewer). Body frame m = n rotated about x
 *   by the ring tilt B (Saturn's spin axis = ring normal; B = 0 otherwise), then spun by `rot` about the body axis:
 *   q = (m.x cos rot + m.z sin rot, m.y, m.z cos rot - m.x sin rot). Texture uv = (fract(lon / 2pi + 0.5),
 *   0.5 - lat / pi) with lon = atan(q.x, q.z), lat = asin(q.y); without a texture (texSize.x == 0) a procedural
 *   fbm surface (three colours, latitude bands for the giants).
 * - Lighting: mu = n.z, mu0 = n . sun. model 0 Lambert I = clamp((mu0 + soft) / (1 + soft), 0, 1); model 1
 *   Lommel-Seeliger I = min(1, 2 mu0 / (mu0 + mu)) (mu0 > 0) x smoothstep(-soft, soft, mu0); model 2 emissive
 *   I = 1 - 0.6 (1 - mu). Lit models: limb darkening I (1 - limb (1 - mu)). Moon: earthshine
 *   0.03 (1 - smoothstep(-0.02, 0.02, mu0)) tinted #9AA0B8 (divided by 0.6 to show through the dark surface).
 * - Edge: alpha = 1 - smoothstep(1 - 1.5 / R, 1, r) (1.5 px antialiasing).
 * - Rings (ringOn = 1): normal nR = (0, cos B, sin B). The view ray at (p.x, -p.y) meets the plane at
 *   z = p.y cos B / sin B (in up-y terms z = -y cos B / sin B); radius rho = |h|; bands C 1.239-1.527 0.15,
 *   B -1.951 0.85, Cassini -2.027 0.10, A -2.269 0.60. Hidden where the disc is in front (z < disc z). Seen
 *   face lit when sign(sun . nR) == sign(sin B), else x0.3; planet shadow on the ring when the ray from h toward
 *   the Sun hits the unit sphere (b = h . sun < 0 and b^2 - (|h|^2 - 1) > 0) x0.12; ring shadow on the disc via
 *   t = -(n . nR) / (sun . nR) > 0, I x (1 - alpha(|n + t sun|)). |sin B| < 0.005: an edge-on line.
 * Output is premultiplied.
 */
const val PLANET_AGSL: String = """
uniform shader tex;
uniform float2 texSize;
uniform float2 center;
uniform float R;
uniform float3 sun;
uniform float rot;
uniform float model;
uniform float soft;
uniform float limb;
uniform float earthshine;
uniform float ringOn;
uniform float sinB;
uniform float cosB;
uniform float3 colA;
uniform float3 colB;
uniform float3 colC;
uniform float bands;
uniform float spots;

const float PI = 3.14159265;

float hash(float3 p) {
    p = fract(p * 0.3183099 + 0.1) * 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise(float3 x) {
    float3 i = floor(x);
    float3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float z0 = mix(mix(hash(i), hash(i + float3(1.0, 0.0, 0.0)), f.x),
                   mix(hash(i + float3(0.0, 1.0, 0.0)), hash(i + float3(1.0, 1.0, 0.0)), f.x), f.y);
    float z1 = mix(mix(hash(i + float3(0.0, 0.0, 1.0)), hash(i + float3(1.0, 0.0, 1.0)), f.x),
                   mix(hash(i + float3(0.0, 1.0, 1.0)), hash(i + float3(1.0, 1.0, 1.0)), f.x), f.y);
    return mix(z0, z1, f.z);
}

float fbm(float3 x) {
    float a = 0.5;
    float s = 0.0;
    for (int k = 0; k < 4; k++) {
        s += a * vnoise(x);
        x = x * 2.03 + 1.7;
        a *= 0.5;
    }
    return s;
}

float band(float r, float r0, float r1) {
    return step(r0, r) * (1.0 - step(r1, r));
}

float ringAlpha(float rho) {
    float a = 0.15 * band(rho, 1.239, 1.527) + 0.85 * band(rho, 1.527, 1.951)
            + 0.10 * band(rho, 1.951, 2.027) + 0.60 * band(rho, 2.027, 2.269);
    return a * (0.88 + 0.12 * sin(rho * 140.0));
}

float3 surface(float3 q) {
    if (texSize.x > 0.0) {
        float lon = atan(q.x, q.z);
        float lat = asin(clamp(q.y, -1.0, 1.0));
        float2 uv = float2(fract(lon / (2.0 * PI) + 0.5), 0.5 - lat / PI);
        return tex.eval(uv * texSize).rgb;
    }
    float nz = fbm(q * 3.0 + 7.31);
    float t = clamp(nz * spots + 0.5 * (1.0 - spots), 0.0, 1.0);
    if (bands > 0.0) {
        float lat = asin(clamp(q.y, -1.0, 1.0));
        float b = sin(lat * bands + nz * 2.5 * spots);
        t = clamp(0.5 + 0.4 * b + (nz - 0.47) * 0.6, 0.0, 1.0);
    }
    float3 c = mix(colA, colB, smoothstep(0.0, 0.6, t));
    return mix(c, colC, smoothstep(0.6, 1.0, t));
}

float intensity(float mu, float mu0) {
    if (model > 1.5) {
        return 1.0 - 0.6 * (1.0 - mu);
    }
    float i = 0.0;
    if (model > 0.5) {
        float ls = 0.0;
        if (mu0 > 0.0) {
            ls = min(1.0, 2.0 * mu0 / (mu0 + mu));
        }
        i = ls * smoothstep(-soft, soft, mu0);
    } else {
        i = clamp((mu0 + soft) / (1.0 + soft), 0.0, 1.0);
    }
    return i * (1.0 - limb * (1.0 - mu));
}

half4 main(float2 xy) {
    float2 p = (xy - center) / R;
    float r2 = dot(p, p);
    float3 pm = float3(0.0);
    float pa = 0.0;
    float3 nR = float3(0.0, cosB, sinB);
    float sn = dot(sun, nR);
    if (r2 < 1.0) {
        float3 n = float3(p.x, -p.y, sqrt(1.0 - r2));
        float my = n.y * cosB + n.z * sinB;
        float mz = -n.y * sinB + n.z * cosB;
        float3 q = float3(n.x * cos(rot) + mz * sin(rot), my, mz * cos(rot) - n.x * sin(rot));
        float3 c = surface(q);
        float mu = n.z;
        float mu0 = dot(n, sun);
        float i = intensity(mu, mu0);
        if (ringOn > 0.5 && abs(sinB) >= 0.005 && abs(sn) >= 0.0001) {
            float t = -dot(n, nR) / sn;
            if (t > 0.0) {
                i *= 1.0 - ringAlpha(length(n + t * sun));
            }
        }
        float es = earthshine * 0.03 * (1.0 - smoothstep(-0.02, 0.02, mu0)) / 0.6;
        float3 tint = float3(0.6039, 0.6275, 0.7216);
        float a = 1.0 - smoothstep(1.0 - 1.5 / R, 1.0, sqrt(r2));
        pm = min(c * (float3(i) + es * tint), float3(1.0)) * a;
        pa = a;
    }
    if (ringOn > 0.5) {
        float ps = -p.y;
        float ra = 0.0;
        float lit = 0.0;
        if (abs(sinB) < 0.005) {
            ra = ringAlpha(abs(p.x)) * (1.0 - smoothstep(0.0, 1.5 / R, abs(ps)));
            lit = 0.5;
        } else {
            float z = -ps * cosB / sinB;
            float3 h = float3(p.x, ps, z);
            ra = ringAlpha(length(h));
            if (r2 < 1.0 && z < sqrt(1.0 - r2)) {
                ra = 0.0;
            }
            float face = (sn * sinB > 0.0) ? 1.0 : 0.3;
            float b = dot(h, sun);
            float cc = dot(h, h) - 1.0;
            float shadow = (b < 0.0 && b * b - cc > 0.0) ? 0.12 : 1.0;
            lit = face * shadow * (0.45 + 0.55 * sqrt(abs(sn)));
        }
        float3 rc = float3(0.8314, 0.7608, 0.6196) * lit;
        pm = rc * ra + pm * (1.0 - ra);
        pa = ra + pa * (1.0 - ra);
    }
    return half4(half3(pm), half(pa));
}
"""

/** Compiles and feeds [PLANET_AGSL]. One instance per drawn planet (uniforms are per instance). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class PlanetShaderProgram private constructor(val shader: RuntimeShader) {
    /** Texture slot (`tex`): a 1 px transparent placeholder until the owner's equirectangular maps arrive. */
    private var texture: Shader =
        BitmapShader(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)

    /** Sets every uniform for one frame of [body] in a [sizePx] square at spin [rot] radians. */
    fun update(body: PlanetBody, light: PlanetLight, sizePx: Float, rot: Float) {
        val style = planetStyle(body)
        val sun = light.unitSun()
        val b = Math.toRadians((light.ringTiltDeg ?: 0f).toDouble())
        shader.setInputShader("tex", texture)
        shader.setFloatUniform("texSize", 0f, 0f)
        shader.setFloatUniform("center", sizePx / 2f, sizePx / 2f)
        shader.setFloatUniform("R", planetRadiusPx(body, sizePx))
        shader.setFloatUniform("sun", sun[0], sun[1], sun[2])
        shader.setFloatUniform("rot", rot)
        shader.setFloatUniform("model", style.model.code)
        shader.setFloatUniform("soft", style.soft)
        shader.setFloatUniform("limb", style.limb)
        shader.setFloatUniform("earthshine", style.earthshine)
        shader.setFloatUniform("ringOn", if (light.ringTiltDeg != null) 1f else 0f)
        shader.setFloatUniform("sinB", kotlin.math.sin(b).toFloat())
        shader.setFloatUniform("cosB", kotlin.math.cos(b).toFloat())
        setColour("colA", style.colA)
        setColour("colB", style.colB)
        setColour("colC", style.colC)
        shader.setFloatUniform("bands", style.bands)
        shader.setFloatUniform("spots", style.spots)
    }

    private fun setColour(name: String, argb: Long) {
        shader.setFloatUniform(
            name,
            ((argb shr 16) and 0xFF) / 255f,
            ((argb shr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
        )
    }

    companion object {
        private const val TAG = "PlanetShader"

        /** The compiled program, or null when AGSL rejects it (the caller draws the Canvas fallback); static log. */
        @Suppress("SwallowedException")
        fun createOrNull(): PlanetShaderProgram? = try {
            PlanetShaderProgram(RuntimeShader(PLANET_AGSL))
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "planet shader unavailable; using the canvas fallback")
            null
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "planet shader unavailable; using the canvas fallback")
            null
        }
    }
}
