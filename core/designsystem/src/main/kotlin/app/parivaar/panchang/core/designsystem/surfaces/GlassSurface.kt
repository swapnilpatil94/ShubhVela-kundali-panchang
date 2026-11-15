@file:Suppress("MagicNumber", "TooManyFunctions", "MaxLineLength")
package app.parivaar.panchang.core.designsystem.surfaces

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.LocalReducedMotion

/** Reduce Transparency (decision D4): provided by `PanchangTheme` from the stored setting. */
val LocalReduceTransparency = compositionLocalOf { false }

/** The shell's backdrop, provided once above content so bars and sheets can sample it. Null means solid fallback. */
val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/**
 * Own backdrop capture (no Haze). The root content is wrapped in [glassSource]; each [GlassSurface] redraws that
 * recording, offset by its own position, through a blur chain. [tilt] is the low-passed roll in degrees (0 when inactive).
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var sourceOrigin by mutableStateOf(Offset.Zero)
    private val tiltState = mutableFloatStateOf(0f)
    val tilt: State<Float> get() = tiltState
    internal fun setTilt(v: Float) { tiltState.floatValue = v }
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { GlassBackdrop(layer) }
    val reduceT = LocalReduceTransparency.current
    val reduceM = LocalReducedMotion.current
    val saver = rememberBatterySaver()
    val active = SurfacePolicy.tiltActive(Build.VERSION.SDK_INT, reduceT, saver, reduceM)
    TiltEffect(backdrop, active)
    return backdrop
}

/** Records the wrapped content into the backdrop layer while still drawing it normally. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.sourceOrigin = it.boundsInWindow().topLeft }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * Glass for chrome only (dock, bars, sheets); never on content, never glass on glass.
 * Falls back to solid ink3 plus a gold hairline per [SurfacePolicy.glassKind].
 */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop?,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val r = PpTheme.ratna
    val spec = remember(r.mood) { app.parivaar.panchang.core.designsystem.tokens.PpPalette.of(r.mood) }
    val kind = SurfacePolicy.glassKind(
        Build.VERSION.SDK_INT, LocalReduceTransparency.current, rememberBatterySaver(), LocalReducedMotion.current, backdrop != null,
    )
    if (kind == GlassKind.SOLID || backdrop == null) {
        Box(
            modifier.drawWithContent {
                drawPath(Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }, Color(SurfaceMath.solidFill(spec)))
                drawContent()
            }.border(1.dp, Color(SurfaceMath.solidHairline(spec)), shape),
            content = content,
        )
        return
    }
    val glassLayer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { origin = it.boundsInWindow().topLeft }
            .drawWithContent { drawGlass(backdrop, glassLayer, shape, origin, r, kind) }
            .border(1.dp, r.glassBorder, shape),
        content = content,
    )
}

/** The glass pass drawn behind the content: blurred backdrop, tint, top highlight, specular. */
private fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.drawGlass(
    backdrop: GlassBackdrop,
    glassLayer: GraphicsLayer,
    shape: Shape,
    origin: Offset,
    r: app.parivaar.panchang.core.designsystem.tokens.RatnaColors,
    kind: GlassKind,
) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    if (Build.VERSION.SDK_INT >= SurfacePolicy.API_BLUR) {
        glassLayer.renderEffect = blurChain(24.dp.toPx())
        glassLayer.record(size = IntSize(size.width.toInt(), size.height.toInt())) {
            translate(backdrop.sourceOrigin.x - origin.x, backdrop.sourceOrigin.y - origin.y) { drawLayer(backdrop.layer) }
        }
        clipPath(path) { drawLayer(glassLayer) }
    }
    clipPath(path) {
        drawRect(Brush.verticalGradient(listOf(r.glassTintTop, r.glassTintBottom)))
        // Inner top highlight, 22 % white (ink on light moods would vanish; white on ivory tint still reads as a sheen).
        drawLine(
            Color.White.copy(alpha = SurfaceMath.HIGHLIGHT_ALPHA), Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx(),
        )
        val cx = if (kind == GlassKind.BLUR_TILT) SurfaceMath.specularX(backdrop.tilt.value) else 0.3f
        drawRect(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * cx, 0f), radius = size.width * 0.5f,
            ),
        )
    }
    drawContent()
}

/** Blur 24 dp then saturation 1.7 (DESIGN_V2 glass). API 31+. */
@RequiresApi(Build.VERSION_CODES.S)
private fun blurChain(radiusPx: Float) = RenderEffect.createColorFilterEffect(
    ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.7f) }),
    RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP),
).asComposeRenderEffect()

@Composable
fun rememberBatterySaver(): Boolean {
    val context = LocalContext.current
    val pm = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var saver by remember { mutableStateOf(pm.isPowerSaveMode) }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { saver = pm.isPowerSaveMode }
        }
        // Not exported: system broadcast, registered for this composition only.
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return saver
}

/** Gyro-free tilt: TYPE_GAME_ROTATION_VECTOR roll, low-pass 0.1, clamped; unregistered on ON_PAUSE and when inactive. */
@Composable
private fun TiltEffect(backdrop: GlassBackdrop, active: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(backdrop, active, owner) {
        if (!active) {
            backdrop.setTilt(0f)
            return@DisposableEffect onDispose { }
        }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val rot = FloatArray(9)
            private val ori = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val rollDeg = Math.toDegrees(ori[2].toDouble()).toFloat()
                backdrop.setTilt(SurfaceMath.lowPassTilt(backdrop.tilt.value, rollDeg))
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        fun register() { if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) }
        fun unregister() { sm.unregisterListener(listener) }
        val observer = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) register()
            if (ev == Lifecycle.Event.ON_PAUSE) unregister()
        }
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) register()
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); unregister() }
    }
}
