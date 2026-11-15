package app.parivaar.panchang.feature.calendar.ics

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import kotlin.time.Clock

/** Pure: one all-day event per festival occurrence, named by [nameOf]; no place, times or family dates. */
object IcsEvents {
    fun of(occurrences: List<FestivalOccurrence>, nameOf: (String) -> String): List<IcsEvent> =
        occurrences.sortedWith(compareBy({ it.date }, { it.id })).map { IcsEvent(it.date, nameOf(it.id)) }
}

/**
 * One-off `.ics` share: written to `cacheDir/share/` under a random name, handed to the share sheet through the
 * non-exported FileProvider. [uriFor] is `FileProvider.getUriForFile(context, "<package>.share", file)` supplied by
 * the app (androidx.core is not a dependency of this module). No permission. Earlier files are deleted before each
 * new one is written and at start ([purge], off the main thread).
 */
class IcsShare(
    cacheDir: File,
    private val uriFor: (File) -> Uri,
    private val chooserTitle: String,
    private val clock: Clock = Clock.System,
) {
    private val dir = File(cacheDir, DIR)

    fun purge() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Writes the calendar and returns its content URI, or null if it could not be written. */
    fun prepare(events: List<IcsEvent>): Uri? = runCatching {
        purge()
        dir.mkdirs()
        val text = IcsWriter.write(events, clock.now()) { UUID.randomUUID().toString() }
        val file = File(dir, randomName() + ".ics")
        file.writeText(text, Charsets.UTF_8)
        uriFor(file)
    }.getOrNull()

    fun chooser(uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun randomName(): String = ByteArray(RANDOM_BYTES).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIR = "share"
        const val RANDOM_BYTES = 8
    }
}
