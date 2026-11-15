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
 * AGSL sphere renderer (API 33+), the GPU twin of [PlanetShading] (design/shubhvela-screens.html `setupPlanet` /
 * `renderBody` with the engine's lighting). Change both together.
 *
 * Per pixel xy (px): p = (xy - center) / R; view (y up) x = p.x, y = -p.y, r2 = x^2 + y^2.
 * - Disc (r2 < 1): z = sqrt(1 - r2). Body coordinates: roll g about z, then tilt b about x:
 *   bx = cos g x + sin g y, by0 = -sin g x + cos g y, by = cos b by0 + sin b z, bz = -sin b by0 + cos b z
 *   (spin axis N = (-sin g cos b, cos g cos b, sin b); Saturn: b = ring tilt B, g = 0, so N = ring normal).
 *   lon = atan(bx, bz), lat = asin(by); texel u = fract(lon / 2pi + 1) x 512 + off (spin), v = (0.5 - lat / pi) 256
 *   into `tex` (the [PlanetTextures] map as a BitmapShader, linear filtering, repeat in u).
 * - d = n . sun, mu = z. model 0 (Lambert, design): clamp((d + soft) / (1 + soft))^0.85 (1 - limb (1 - mu^0.35));
 *   model 1 (lunar, design): 2 d / (d + mu + 1e-4) (d > 0) x smoothstep(-soft, soft, d) x (1 - limb (1 - mu)) x
 *   exposure; model 2 (emissive, spec): 1 - 0.6 (1 - mu).
 * - Ring shadow on the disc (d > 0): t = -(n . N) / (sun . N) > 0, x (1 - 0.75 alpha(|n + t sun|)).
 * - Colour (0..255): texel x I + earthshine (5, 7, 11)
 *   + rim (255 .9, 235 .85, 200 .7) x (1 - mu)^3 max(0, d + .25) rim.
 * - Rings (design `ringCol`): C 1.24-1.53 (140,125,100) .16, B -1.95 (228,212,172) .8, Cassini -2.02 (120,110,95) .06,
 *   A -2.27 (205,188,148) .55, Encke 2.205-2.215 .05, ripple 0.82 + 0.18 sin(230 r) sin(61 r). Plane hit
 *   z_r = -y cos B / sin B, r = |(x, y, z_r)|. Over the disc only where z_r > z (near half), outside the disc always;
 *   colour x 0.86, x 0.3 on the unlit face (sign(sun . N) != sign(sin B)), x 0.12 in the planet's shadow (ray from the
 *   ring point toward the Sun hits the sphere: b = h . sun < 0, b^2 - (|h|^2 - 1) > 0). |sin B| < 0.005: a line.
 * - Edge alpha = min(1, (1 - r) R 1.2). Output premultiplied.
 */
const val PLANET_AGSL: String = """
uniform shader tex;
uniform float2 center;
uniform float R;
uniform float3 sun;
uniform float off;
uniform float model;
uniform float soft;
uniform float limb;
uniform float exposure;
uniform float earthshine;
uniform float rim;
uniform float ringOn;
uniform float sinB;
uniform float cosB;
uniform float sinG;
uniform float cosG;

const float PI = 3.14159265;

float4 ringCol(float r) {
    float4 c = float4(0.0);
    if (r > 1.24 && r < 1.53) {
        c = float4(140.0, 125.0, 100.0, 0.16);
    } else if (r >= 1.53 && r < 1.95) {
        c = float4(228.0, 212.0, 172.0, 0.8);
    } else if (r >= 1.95 && r < 2.02) {
        c = float4(120.0, 110.0, 95.0, 0.06);
    } else if (r >= 2.02 && r < 2.27) {
        c = float4(205.0, 188.0, 148.0, 0.55);
    }
    if (r > 2.205 && r < 2.215) {
        c.a = 0.05;
    }
    c.a *= 0.82 + 0.18 * sin(r * 230.0) * sin(r * 61.0);
    return c;
}

float intensity(float mu, float d) {
    if (model > 1.5) {
        return 1.0 - 0.6 * (1.0 - mu);
    }
    if (model > 0.5) {
        float ls = 0.0;
        if (d > 0.0) {
            ls = 2.0 * d / (d + mu + 0.0001);
        }
        return ls * smoothstep(-soft, soft, d) * (1.0 - limb * (1.0 - mu)) * exposure;
    }
    float w = clamp((d + soft) / (1.0 + soft), 0.0, 1.0);
    return pow(w, 0.85) * (1.0 - limb * (1.0 - pow(max(mu, 0.0), 0.35)));
}

float ringFace() {
    float sn = sun.y * cosB + sun.z * sinB;
    return (sn * sinB > 0.0) ? 1.0 : 0.3;
}

half4 main(float2 xy) {
    float2 p = (xy - center) / R;
    float x = p.x;
    float y = -p.y;
    float r2 = x * x + y * y;
    float3 nR = float3(0.0, cosB, sinB);
    bool edgeOn = abs(sinB) < 0.005;
    float3 pm = float3(0.0);
    float pa = 0.0;
    if (r2 < 1.0) {
        float z = sqrt(1.0 - r2);
        float bx = cosG * x + sinG * y;
        float by0 = -sinG * x + cosG * y;
        float by = cosB * by0 + sinB * z;
        float bz = -sinB * by0 + cosB * z;
        float lon = atan(bx, bz);
        float lat = asin(clamp(by, -1.0, 1.0));
        float u = fract(lon / (2.0 * PI) + 1.0) * 512.0 + off;
        float v = (0.5 - lat / PI) * 256.0;
        float3 texel = float3(tex.eval(float2(u, v)).rgb) * 255.0;
        float3 n = float3(x, y, z);
        float d = dot(n, sun);
        float sh = intensity(z, d);
        float sn = dot(sun, nR);
        if (ringOn > 0.5 && d > 0.0 && !edgeOn && abs(sn) >= 0.0001) {
            float t = -dot(n, nR) / sn;
            if (t > 0.0) {
                sh *= 1.0 - 0.75 * ringCol(length(n + t * sun)).a;
            }
        }
        float rl = 0.0;
        if (rim > 0.0) {
            rl = pow(1.0 - z, 3.0) * max(0.0, d + 0.25) * rim;
        }
        float3 col = texel * sh + earthshine * float3(5.0, 7.0, 11.0) + float3(229.5, 199.75, 140.0) * rl;
        if (ringOn > 0.5 && !edgeOn) {
            float zr = -y * cosB / sinB;
            if (zr > z) {
                float4 rc = ringCol(length(float3(x, y, zr)));
                col = col * (1.0 - rc.a) + rc.rgb * 0.86 * ringFace() * rc.a;
            }
        }
        float a = min(1.0, (1.0 - sqrt(r2)) * R * 1.2);
        pm = clamp(col, 0.0, 255.0) / 255.0 * a;
        pa = a;
    }
    if (ringOn > 0.5) {
        float4 rc = float4(0.0);
        float lit = ringFace();
        if (edgeOn) {
            rc = ringCol(abs(x));
            rc.a *= 1.0 - smoothstep(0.0, 1.5 / R, abs(y));
            lit = 0.5;
        } else if (r2 >= 1.0) {
            float zr = -y * cosB / sinB;
            float3 h = float3(x, y, zr);
            rc = ringCol(length(h));
            float b = dot(h, sun);
            float cc = dot(h, h) - 1.0;
            if (b < 0.0 && b * b - cc > 0.0) {
                lit *= 0.12;
            }
        }
        pm = rc.rgb * 0.86 * lit / 255.0 * rc.a + pm * (1.0 - rc.a);
        pa = rc.a + pa * (1.0 - rc.a);
    }
    return half4(half3(pm), half(pa));
}
"""

/** Compiles and feeds [PLANET_AGSL]. One instance per drawn planet (uniforms are per instance). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class PlanetShaderProgram private constructor(val shader: RuntimeShader) {
    private var textureOf: Bitmap? = null

    /** Binds the surface map (512 x 256 from [PlanetTextures]); linear filtering, repeat in longitude. */
    fun setTexture(bitmap: Bitmap) {
        if (textureOf === bitmap) return
        val s = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
        s.filterMode = BitmapShader.FILTER_MODE_LINEAR
        shader.setInputShader("tex", s)
        textureOf = bitmap
    }

    /** Sets every uniform for one frame of [body] in a [sizePx] square at spin [rot] radians. */
    fun update(body: PlanetBody, light: PlanetLight, sizePx: Float, rot: Float) {
        val style = planetStyle(body)
        val sun = light.unitSun()
        val ringOn = light.ringTiltDeg != null
        val b = light.ringTiltDeg?.let { Math.toRadians(it.toDouble()) } ?: style.axisTiltRad.toDouble()
        val g = if (ringOn) 0.0 else style.axisRollRad.toDouble()
        shader.setFloatUniform("center", sizePx / 2f, sizePx / 2f)
        shader.setFloatUniform("R", planetRadiusPx(body, sizePx))
        shader.setFloatUniform("sun", sun[0], sun[1], sun[2])
        shader.setFloatUniform("off", (rot / (2.0 * Math.PI) * PlanetTextures.WIDTH).toFloat())
        shader.setFloatUniform("model", style.model.code)
        shader.setFloatUniform("soft", style.soft)
        shader.setFloatUniform("limb", style.limb)
        shader.setFloatUniform("exposure", style.exposure)
        shader.setFloatUniform("earthshine", style.earthshine)
        shader.setFloatUniform("rim", style.rim)
        shader.setFloatUniform("ringOn", if (ringOn) 1f else 0f)
        shader.setFloatUniform("sinB", kotlin.math.sin(b).toFloat())
        shader.setFloatUniform("cosB", kotlin.math.cos(b).toFloat())
        shader.setFloatUniform("sinG", kotlin.math.sin(g).toFloat())
        shader.setFloatUniform("cosG", kotlin.math.cos(g).toFloat())
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

/** One immutable Bitmap per body over [PlanetTextures.argb] (generate off the main thread). */
internal object PlanetBitmaps {
    private val cache = HashMap<PlanetBody, Bitmap>()

    /** The cached bitmap if it already exists (never generates). */
    fun peek(body: PlanetBody): Bitmap? = synchronized(cache) { cache[body] }

    fun get(body: PlanetBody): Bitmap {
        synchronized(cache) { cache[body]?.let { return it } }
        val w = PlanetTextures.WIDTH
        val bmp = Bitmap.createBitmap(PlanetTextures.argb(body), w, PlanetTextures.HEIGHT, Bitmap.Config.ARGB_8888)
        synchronized(cache) { return cache.getOrPut(body) { bmp } }
    }
}
