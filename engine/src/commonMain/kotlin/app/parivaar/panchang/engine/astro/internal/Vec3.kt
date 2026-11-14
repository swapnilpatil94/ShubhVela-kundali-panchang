package app.parivaar.panchang.engine.astro.internal

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Immutable 3-vector (units are the caller's: km, km/day, or unit vectors). */
internal data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)

    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)

    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)

    operator fun div(k: Double) = Vec3(x / k, y / k, z / k)

    fun dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    fun norm(): Double = sqrt(dot(this))

    fun unit(): Vec3 = this / norm()
}

/** Row-major 3x3 matrix. Frame rotations R1, R3 follow docs/specs/M01.md section 3.1 (ERFA sign convention). */
internal class Mat3(private val m: DoubleArray) {
    init {
        require(m.size == SIZE * SIZE)
    }

    operator fun times(v: Vec3) = Vec3(rowDot(0, v), rowDot(1, v), rowDot(2, v))

    private fun rowDot(i: Int, v: Vec3): Double = m[SIZE * i] * v.x + m[SIZE * i + 1] * v.y + m[SIZE * i + 2] * v.z

    operator fun times(o: Mat3): Mat3 {
        val r = DoubleArray(SIZE * SIZE)
        for (i in 0 until SIZE) {
            for (j in 0 until SIZE) {
                var s = 0.0
                for (k in 0 until SIZE) s += m[SIZE * i + k] * o.m[SIZE * k + j]
                r[SIZE * i + j] = s
            }
        }
        return Mat3(r)
    }

    companion object {
        private const val SIZE = 3

        /** R1(a) = [[1, 0, 0], [0, cos a, sin a], [0, -sin a, cos a]]. */
        fun r1(a: Double): Mat3 {
            val c = cos(a)
            val s = sin(a)
            return Mat3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, c, s, 0.0, -s, c))
        }

        /** R3(a) = [[cos a, sin a, 0], [-sin a, cos a, 0], [0, 0, 1]]. */
        fun r3(a: Double): Mat3 {
            val c = cos(a)
            val s = sin(a)
            return Mat3(doubleArrayOf(c, s, 0.0, -s, c, 0.0, 0.0, 0.0, 1.0))
        }
    }
}

internal const val FULL_TURN_DEG = 360.0
internal const val HALF_TURN_DEG = 180.0

/** Degrees reduced to [0, 360). */
internal fun norm360(deg: Double): Double {
    var x = deg % FULL_TURN_DEG
    if (x < 0.0) x += FULL_TURN_DEG
    return if (x >= FULL_TURN_DEG) 0.0 else x // -1e-17 + 360 rounds to 360.0
}

/** Degrees reduced to [-180, 180). */
internal fun wrap180(deg: Double): Double = norm360(deg + HALF_TURN_DEG) - HALF_TURN_DEG
