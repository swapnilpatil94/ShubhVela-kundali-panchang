@file:Suppress("MagicNumber")

package app.parivaar.panchang.core.designsystem.planets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.parivaar.panchang.core.designsystem.tokens.LocalReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** The budget of the current screen; wrap each screen in [ProvidePlanetBudget]. */
val LocalPlanetBudget = staticCompositionLocalOf { PlanetAnimationBudget() }

/** Gives the screen its own one-animated-planet budget. */
@Composable
fun ProvidePlanetBudget(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPlanetBudget provides remember { PlanetAnimationBudget() }, content = content)
}

/**
 * A real-geometry planet (docs/specs/M07R.md section 6 "Planets"). [light] comes from the feature layer
 * ([planetLightOf] over the engine's `SkyGeometry.light`); no engine call here.
 *
 * API 33+: AGSL ([PLANET_AGSL]); it spins at [PlanetStyle.spinPxPerSec] (<= 30 fps) only when [animated], this
 * view holds the screen's [LocalPlanetBudget], it is on screen, the app is resumed, and neither Reduce Motion
 * nor battery saver is on; otherwise a static frame. Below 33, or if the shader does not compile: a CPU-rendered
 * static frame of the same lighting ([PlanetShading]), computed off the main thread.
 */
@Composable
fun PlanetView(
    body: PlanetBody,
    light: PlanetLight,
    size: Dp,
    animated: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val inPreview = LocalInspectionMode.current
    val program = remember {
        val agsl = !inPreview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (agsl) PlanetShaderProgram.createOrNull() else null
    }
    val a11y = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }
    if (program == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        StaticPlanet(body, light, size, modifier.sunGlow(body).then(a11y))
        return
    }
    val texture = rememberPlanetBitmap(body)
    val rot = remember { mutableFloatStateOf(0f) }
    var onScreen by remember { mutableStateOf(false) }
    val budget = LocalPlanetBudget.current
    val token = remember { Any() }
    var granted by remember { mutableStateOf(false) }
    val wants = animated && spinRadPerSec(body) > 0f
    DisposableEffect(budget, wants) {
        granted = wants && budget.tryAcquire(token)
        onDispose { budget.release(token) }
    }
    val gate = AnimationGate(
        requested = wants,
        budgetGranted = granted,
        reducedMotion = LocalReducedMotion.current,
        batterySaver = rememberBatterySaver().value,
        resumed = rememberResumed().value,
        onScreen = onScreen,
        shaderAvailable = true,
    )
    if (gate.animate) SpinEffect(body) { rot.floatValue = (rot.floatValue + it) % TWO_PI }
    Canvas(
        modifier
            .size(size)
            .then(a11y)
            .onGloballyPositioned { onScreen = it.boundsInWindow().let { b -> b.width > 0f && b.height > 0f } }
            .sunGlow(body)
            .drawBehind {
                val tex = texture ?: return@drawBehind
                program.setTexture(tex)
                program.update(body, light, min(this.size.width, this.size.height), rot.floatValue)
                drawRect(ShaderBrush(program.shader))
            },
    ) {}
}

/** The body's surface map as a Bitmap, generated once per body off the main thread (null until ready). */
@Composable
private fun rememberPlanetBitmap(body: PlanetBody): Bitmap? =
    produceState(PlanetBitmaps.peek(body), body) {
        value = withContext(Dispatchers.Default) { PlanetBitmaps.get(body) }
    }.value

/**
 * The design's sun glow, drop-shadow(0 0 22px rgba(255, 120, 40, .8)): a radial falloff from the disc edge to
 * 22 dp beyond it, drawn behind the Sun only.
 */
private fun Modifier.sunGlow(body: PlanetBody): Modifier = if (body != PlanetBody.SUN) {
    this
} else {
    drawBehind {
        val r = planetRadiusPx(body, min(size.width, size.height))
        val outer = r + SUN_GLOW.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                0f to SUN_GLOW_COLOUR,
                r / outer to SUN_GLOW_COLOUR,
                1f to SUN_GLOW_COLOUR.copy(alpha = 0f),
                center = center,
                radius = outer,
            ),
            radius = outer,
        )
    }
}

/** Advances the spin at the body's rate, at most about 30 times a second, while composed. */
@Composable
private fun SpinEffect(body: PlanetBody, advance: (Float) -> Unit) {
    LaunchedEffect(body) {
        val frames = FrameGate()
        val spin = spinRadPerSec(body)
        var last = Long.MIN_VALUE
        while (true) {
            withFrameNanos { now ->
                if (frames.take(now)) {
                    if (last != Long.MIN_VALUE) advance(spin * (now - last) / NANOS_PER_SECOND)
                    last = now
                }
            }
        }
    }
}

/** CPU-rendered still frame (fallback); at most 256 px, scaled with filtering. */
@Composable
private fun StaticPlanet(body: PlanetBody, light: PlanetLight, size: Dp, modifier: Modifier) {
    val px = with(LocalDensity.current) { size.toPx() }.roundToInt().coerceIn(1, MAX_FALLBACK_PX)
    val image: ImageBitmap? = if (LocalInspectionMode.current) {
        remember(body, light, px) { frame(body, light, px) }
    } else {
        produceState<ImageBitmap?>(null, body, light, px) {
            value = withContext(Dispatchers.Default) { frame(body, light, px) }
        }.value
    }
    Canvas(modifier.size(size)) {
        val img = image ?: return@Canvas
        drawImage(
            img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}

private fun frame(body: PlanetBody, light: PlanetLight, px: Int): ImageBitmap =
    Bitmap.createBitmap(PlanetShading.render(body, light, px), px, px, Bitmap.Config.ARGB_8888).asImageBitmap()

/** Battery saver, updated by the system broadcast while the view is composed. */
@Composable
private fun rememberBatterySaver(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val state = remember { mutableStateOf(isPowerSave(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                state.value = isPowerSave(context)
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        state.value = isPowerSave(context)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

private fun isPowerSave(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true

/** True while the hosting lifecycle is RESUMED. */
@Composable
private fun rememberResumed(): State<Boolean> {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return remember { mutableStateOf(false) }.also { it.value = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) }
}

private const val MAX_FALLBACK_PX = 256
private val SUN_GLOW = 22.dp
private val SUN_GLOW_COLOUR = Color(red = 255, green = 120, blue = 40, alpha = 204)
private const val TWO_PI = (2.0 * Math.PI).toFloat()
private const val NANOS_PER_SECOND = 1e9f
