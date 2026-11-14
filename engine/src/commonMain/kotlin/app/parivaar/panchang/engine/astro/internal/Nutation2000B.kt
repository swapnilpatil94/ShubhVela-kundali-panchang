/*
 * IAU 2000B nutation. Kotlin port of the ERFA function eraNut00b (file src/nut00b.c of
 * https://github.com/liberfa/erfa, "This revision: 2021 May 11"). The 77-row series table below was
 * machine-converted from that source file (not retyped); the arithmetic follows the C code line by line.
 * This port is a modified, derived work: it is neither ERFA nor SOFA itself, and SOFA is not responsible for it.
 *
 * ----------------------------------------------------------------------
 *  Copyright (C) 2013-2023, NumFOCUS Foundation.
 *  All rights reserved.
 *
 *  This library is derived, with permission, from the International
 *  Astronomical Union's "Standards of Fundamental Astronomy" library,
 *  available from http://www.iausofa.org.
 *
 *  The ERFA version is intended to retain identical functionality to
 *  the SOFA library, but made distinct through different function and
 *  file names, as set out in the SOFA license conditions.  The SOFA
 *  original has a role as a reference standard for the IAU and IERS,
 *  and consequently redistribution is permitted only in its unaltered
 *  state.  The ERFA version is not subject to this restriction and
 *  therefore can be included in distributions which do not support the
 *  concept of "read only" software.
 *
 *  Although the intent is to replicate the SOFA API (other than
 *  replacement of prefix names) and results (with the exception of
 *  bugs;  any that are discovered will be fixed), SOFA is not
 *  responsible for any errors found in this version of the library.
 *
 *  If you wish to acknowledge the SOFA heritage, please acknowledge
 *  that you are using a library derived from SOFA, rather than SOFA
 *  itself.
 *
 *
 *  TERMS AND CONDITIONS
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1 Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *  2 Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 *  3 Neither the name of the Standards Of Fundamental Astronomy Board,
 *    the International Astronomical Union nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *  FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE
 *  COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 * ----------------------------------------------------------------------
 */
@file:Suppress("MagicNumber") // series coefficients and unit constants from ERFA nut00b.c / erfam.h

package app.parivaar.panchang.engine.astro.internal

import kotlin.math.cos
import kotlin.math.sin

/**
 * Nutation, IAU 2000B model (McCarthy and Luzum 2003): 77 luni-solar terms plus the fixed offsets that stand
 * in for the planetary terms (-0.135 mas in longitude, +0.388 mas in obliquity; Luzum 2001, as ERFA).
 * Results are in radians, with respect to the equinox and ecliptic of date.
 */
internal object Nutation2000B {
    /** Arcseconds to radians (ERFA_DAS2R). */
    const val ARCSEC_TO_RAD = 4.848136811095359935899141e-6

    /** 2 pi (ERFA_D2PI). */
    private const val TWO_PI = 6.283185307179586476925287

    /** Arcseconds in a full circle (ERFA_TURNAS). */
    private const val TURN_ARCSEC = 1296000.0

    /** Units of 0.1 microarcsecond to radians. */
    private const val U2R = ARCSEC_TO_RAD / 1e7

    /** Fixed offsets in lieu of planetary terms (milliarcseconds to radians). */
    private const val DPPLAN = -0.135 * (ARCSEC_TO_RAD / 1e3)
    private const val DEPLAN = 0.388 * (ARCSEC_TO_RAD / 1e3)

    private const val ARGS_PER_TERM = 5
    private const val COEFS_PER_TERM = 6
    private const val TERMS = 77

    /**
     * @param t TT, Julian centuries since J2000.0
     * @param out receives (dpsi, deps) in radians at indices 0 and 1
     */
    fun compute(t: Double, out: DoubleArray) {
        // Fundamental (Delaunay) arguments from Simon et al. (1994); `%` is C fmod.
        val el = (485868.249036 + 1717915923.2178 * t) % TURN_ARCSEC * ARCSEC_TO_RAD // Moon mean anomaly
        val elp = (1287104.79305 + 129596581.0481 * t) % TURN_ARCSEC * ARCSEC_TO_RAD // Sun mean anomaly
        val f = (335779.526232 + 1739527262.8478 * t) % TURN_ARCSEC * ARCSEC_TO_RAD // Moon argument of latitude
        val d = (1072260.70369 + 1602961601.2090 * t) % TURN_ARCSEC * ARCSEC_TO_RAD // Moon elongation from Sun
        val om = (450160.398036 + -6962890.5431 * t) % TURN_ARCSEC * ARCSEC_TO_RAD // Moon ascending node

        var dp = 0.0
        var de = 0.0
        // Summation of the luni-solar series, smallest terms first.
        for (i in TERMS - 1 downTo 0) {
            val a = i * ARGS_PER_TERM
            val arg = (
                ARG[a].toDouble() * el +
                    ARG[a + 1].toDouble() * elp +
                    ARG[a + 2].toDouble() * f +
                    ARG[a + 3].toDouble() * d +
                    ARG[a + 4].toDouble() * om
                ) % TWO_PI
            val sarg = sin(arg)
            val carg = cos(arg)
            val c = i * COEFS_PER_TERM
            dp += (COEF[c] + COEF[c + 1] * t) * sarg + COEF[c + 2] * carg
            de += (COEF[c + 3] + COEF[c + 4] * t) * carg + COEF[c + 5] * sarg
        }
        // 0.1 microarcsecond units to radians, plus the fixed planetary offsets.
        out[0] = dp * U2R + DPPLAN
        out[1] = de * U2R + DEPLAN
    }

    // Per term: multipliers of l, l', F, D, Om (ARG), then the longitude sin, t*sin, cos and the obliquity
    // cos, t*cos, sin coefficients (COEF), in units of 0.1 microarcsecond (and the same per Julian century).
    // BEGIN GENERATED TABLE (rows 1..77 of x[] in ERFA nut00b.c)
    private val ARG = intArrayOf(
        0, 0, 0, 0, 1,
        0, 0, 2, -2, 2,
        0, 0, 2, 0, 2,
        0, 0, 0, 0, 2,
        0, 1, 0, 0, 0,
        0, 1, 2, -2, 2,
        1, 0, 0, 0, 0,
        0, 0, 2, 0, 1,
        1, 0, 2, 0, 2,
        0, -1, 2, -2, 2,
        0, 0, 2, -2, 1,
        -1, 0, 2, 0, 2,
        -1, 0, 0, 2, 0,
        1, 0, 0, 0, 1,
        -1, 0, 0, 0, 1,
        -1, 0, 2, 2, 2,
        1, 0, 2, 0, 1,
        -2, 0, 2, 0, 1,
        0, 0, 0, 2, 0,
        0, 0, 2, 2, 2,
        0, -2, 2, -2, 2,
        -2, 0, 0, 2, 0,
        2, 0, 2, 0, 2,
        1, 0, 2, -2, 2,
        -1, 0, 2, 0, 1,
        2, 0, 0, 0, 0,
        0, 0, 2, 0, 0,
        0, 1, 0, 0, 1,
        -1, 0, 0, 2, 1,
        0, 2, 2, -2, 2,
        0, 0, -2, 2, 0,
        1, 0, 0, -2, 1,
        0, -1, 0, 0, 1,
        -1, 0, 2, 2, 1,
        0, 2, 0, 0, 0,
        1, 0, 2, 2, 2,
        -2, 0, 2, 0, 0,
        0, 1, 2, 0, 2,
        0, 0, 2, 2, 1,
        0, -1, 2, 0, 2,
        0, 0, 0, 2, 1,
        1, 0, 2, -2, 1,
        2, 0, 2, -2, 2,
        -2, 0, 0, 2, 1,
        2, 0, 2, 0, 1,
        0, -1, 2, -2, 1,
        0, 0, 0, -2, 1,
        -1, -1, 0, 2, 0,
        2, 0, 0, -2, 1,
        1, 0, 0, 2, 0,
        0, 1, 2, -2, 1,
        1, -1, 0, 0, 0,
        -2, 0, 2, 0, 2,
        3, 0, 2, 0, 2,
        0, -1, 0, 2, 0,
        1, -1, 2, 0, 2,
        0, 0, 0, 1, 0,
        -1, -1, 2, 2, 2,
        -1, 0, 2, 0, 0,
        0, -1, 2, 2, 2,
        -2, 0, 0, 0, 1,
        1, 1, 2, 0, 2,
        2, 0, 0, 0, 1,
        -1, 1, 0, 1, 0,
        1, 1, 0, 0, 0,
        1, 0, 2, 0, 0,
        -1, 0, 2, -2, 1,
        1, 0, 0, 0, 2,
        -1, 0, 0, 1, 0,
        0, 0, 2, 1, 2,
        -1, 0, 2, 4, 2,
        -1, 1, 0, 1, 1,
        0, -2, 2, -2, 1,
        1, 0, 2, 2, 1,
        -2, 0, 2, 2, 2,
        -1, 0, 0, 0, 2,
        1, 1, 2, -2, 2,
    )
    private val COEF = doubleArrayOf(
        -172064161.0, -174666.0, 33386.0, 92052331.0, 9086.0, 15377.0,
        -13170906.0, -1675.0, -13696.0, 5730336.0, -3015.0, -4587.0,
        -2276413.0, -234.0, 2796.0, 978459.0, -485.0, 1374.0,
        2074554.0, 207.0, -698.0, -897492.0, 470.0, -291.0,
        1475877.0, -3633.0, 11817.0, 73871.0, -184.0, -1924.0,
        -516821.0, 1226.0, -524.0, 224386.0, -677.0, -174.0,
        711159.0, 73.0, -872.0, -6750.0, 0.0, 358.0,
        -387298.0, -367.0, 380.0, 200728.0, 18.0, 318.0,
        -301461.0, -36.0, 816.0, 129025.0, -63.0, 367.0,
        215829.0, -494.0, 111.0, -95929.0, 299.0, 132.0,
        128227.0, 137.0, 181.0, -68982.0, -9.0, 39.0,
        123457.0, 11.0, 19.0, -53311.0, 32.0, -4.0,
        156994.0, 10.0, -168.0, -1235.0, 0.0, 82.0,
        63110.0, 63.0, 27.0, -33228.0, 0.0, -9.0,
        -57976.0, -63.0, -189.0, 31429.0, 0.0, -75.0,
        -59641.0, -11.0, 149.0, 25543.0, -11.0, 66.0,
        -51613.0, -42.0, 129.0, 26366.0, 0.0, 78.0,
        45893.0, 50.0, 31.0, -24236.0, -10.0, 20.0,
        63384.0, 11.0, -150.0, -1220.0, 0.0, 29.0,
        -38571.0, -1.0, 158.0, 16452.0, -11.0, 68.0,
        32481.0, 0.0, 0.0, -13870.0, 0.0, 0.0,
        -47722.0, 0.0, -18.0, 477.0, 0.0, -25.0,
        -31046.0, -1.0, 131.0, 13238.0, -11.0, 59.0,
        28593.0, 0.0, -1.0, -12338.0, 10.0, -3.0,
        20441.0, 21.0, 10.0, -10758.0, 0.0, -3.0,
        29243.0, 0.0, -74.0, -609.0, 0.0, 13.0,
        25887.0, 0.0, -66.0, -550.0, 0.0, 11.0,
        -14053.0, -25.0, 79.0, 8551.0, -2.0, -45.0,
        15164.0, 10.0, 11.0, -8001.0, 0.0, -1.0,
        -15794.0, 72.0, -16.0, 6850.0, -42.0, -5.0,
        21783.0, 0.0, 13.0, -167.0, 0.0, 13.0,
        -12873.0, -10.0, -37.0, 6953.0, 0.0, -14.0,
        -12654.0, 11.0, 63.0, 6415.0, 0.0, 26.0,
        -10204.0, 0.0, 25.0, 5222.0, 0.0, 15.0,
        16707.0, -85.0, -10.0, 168.0, -1.0, 10.0,
        -7691.0, 0.0, 44.0, 3268.0, 0.0, 19.0,
        -11024.0, 0.0, -14.0, 104.0, 0.0, 2.0,
        7566.0, -21.0, -11.0, -3250.0, 0.0, -5.0,
        -6637.0, -11.0, 25.0, 3353.0, 0.0, 14.0,
        -7141.0, 21.0, 8.0, 3070.0, 0.0, 4.0,
        -6302.0, -11.0, 2.0, 3272.0, 0.0, 4.0,
        5800.0, 10.0, 2.0, -3045.0, 0.0, -1.0,
        6443.0, 0.0, -7.0, -2768.0, 0.0, -4.0,
        -5774.0, -11.0, -15.0, 3041.0, 0.0, -5.0,
        -5350.0, 0.0, 21.0, 2695.0, 0.0, 12.0,
        -4752.0, -11.0, -3.0, 2719.0, 0.0, -3.0,
        -4940.0, -11.0, -21.0, 2720.0, 0.0, -9.0,
        7350.0, 0.0, -8.0, -51.0, 0.0, 4.0,
        4065.0, 0.0, 6.0, -2206.0, 0.0, 1.0,
        6579.0, 0.0, -24.0, -199.0, 0.0, 2.0,
        3579.0, 0.0, 5.0, -1900.0, 0.0, 1.0,
        4725.0, 0.0, -6.0, -41.0, 0.0, 3.0,
        -3075.0, 0.0, -2.0, 1313.0, 0.0, -1.0,
        -2904.0, 0.0, 15.0, 1233.0, 0.0, 7.0,
        4348.0, 0.0, -10.0, -81.0, 0.0, 2.0,
        -2878.0, 0.0, 8.0, 1232.0, 0.0, 4.0,
        -4230.0, 0.0, 5.0, -20.0, 0.0, -2.0,
        -2819.0, 0.0, 7.0, 1207.0, 0.0, 3.0,
        -4056.0, 0.0, 5.0, 40.0, 0.0, -2.0,
        -2647.0, 0.0, 11.0, 1129.0, 0.0, 5.0,
        -2294.0, 0.0, -10.0, 1266.0, 0.0, -4.0,
        2481.0, 0.0, -7.0, -1062.0, 0.0, -3.0,
        2179.0, 0.0, -2.0, -1129.0, 0.0, -2.0,
        3276.0, 0.0, 1.0, -9.0, 0.0, 0.0,
        -3389.0, 0.0, 5.0, 35.0, 0.0, -2.0,
        3339.0, 0.0, -13.0, -107.0, 0.0, 1.0,
        -1987.0, 0.0, -6.0, 1073.0, 0.0, -2.0,
        -1981.0, 0.0, 0.0, 854.0, 0.0, 0.0,
        4026.0, 0.0, -353.0, -553.0, 0.0, -139.0,
        1660.0, 0.0, -5.0, -710.0, 0.0, -2.0,
        -1521.0, 0.0, 9.0, 647.0, 0.0, 4.0,
        1314.0, 0.0, 0.0, -700.0, 0.0, 0.0,
        -1283.0, 0.0, 0.0, 672.0, 0.0, 0.0,
        -1331.0, 0.0, 8.0, 663.0, 0.0, 4.0,
        1383.0, 0.0, -2.0, -594.0, 0.0, -2.0,
        1405.0, 0.0, 4.0, -610.0, 0.0, 2.0,
        1290.0, 0.0, 0.0, -556.0, 0.0, 0.0,
    )
    // END GENERATED TABLE

    init {
        check(ARG.size == TERMS * ARGS_PER_TERM && COEF.size == TERMS * COEFS_PER_TERM)
    }
}
