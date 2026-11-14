package app.parivaar.panchang.core.geo

import app.parivaar.panchang.core.model.geo.ManualOffset
import kotlin.math.abs

/** Parsing and formatting for the manual UTC-offset field. UI text lives in strings.xml (geo_picker_*). */
object OffsetPickerModel {
    enum class ErrorCode { EMPTY, FORMAT, OUT_OF_RANGE }

    sealed interface Result {
        data class Ok(val offset: ManualOffset) : Result

        data class Error(val code: ErrorCode) : Result
    }

    private const val SECONDS_PER_HOUR = 3600
    private const val SECONDS_PER_MINUTE = 60
    private const val MAX_MINUTE_OR_SECOND = 59
    private const val G_SIGN = 1
    private const val G_HOUR = 2
    private const val G_MIN = 3
    private const val G_SEC = 4
    private val pattern = Regex("^([+-])?(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$")

    /** Accepts `+04:51`, `-3:30`, `+05:53:20`, `5:30` (no sign means plus). Input is never echoed. */
    fun parse(text: String): Result {
        val t = text.trim()
        val m = pattern.matchEntire(t)
        val groups = m?.groupValues
        val minutes = groups?.get(G_MIN)?.toInt() ?: 0
        val seconds = groups?.get(G_SEC)?.ifEmpty { "0" }?.toInt() ?: 0
        return when {
            t.isEmpty() -> Result.Error(ErrorCode.EMPTY)
            groups == null || minutes > MAX_MINUTE_OR_SECOND || seconds > MAX_MINUTE_OR_SECOND ->
                Result.Error(ErrorCode.FORMAT)
            else -> {
                val magnitude = groups[G_HOUR].toInt() * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds
                val total = if (groups[G_SIGN] == "-") -magnitude else magnitude
                if (total in ManualOffset.MIN_SECONDS..ManualOffset.MAX_SECONDS) {
                    Result.Ok(ManualOffset(total))
                } else {
                    Result.Error(ErrorCode.OUT_OF_RANGE)
                }
            }
        }
    }

    /** `+HH:MM`, or `+HH:MM:SS` when the offset has a seconds part. */
    fun format(offsetSeconds: Int): String {
        val a = abs(offsetSeconds)
        val h = a / SECONDS_PER_HOUR
        val m = a % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val s = a % SECONDS_PER_MINUTE
        val sign = if (offsetSeconds < 0) "-" else "+"
        val base = "$sign${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
        return if (s == 0) base else base + ":" + s.toString().padStart(2, '0')
    }
}
