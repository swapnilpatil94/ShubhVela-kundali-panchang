package app.parivaar.panchang.platform.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.parivaar.panchang.feature.aaj.R as AajR
import app.parivaar.panchang.feature.aaj.share.ShareCardModel
import app.parivaar.panchang.feature.aaj.share.ShareCardRenderer
import app.parivaar.panchang.feature.aaj.share.ShareFormat
import java.io.File
import java.security.SecureRandom

/**
 * Renders the plain share image into the app cache under a random, non-personal name and hands it to the system
 * share sheet through the non-exported FileProvider. Old images are deleted at the next app start (and before each
 * new one is written). The image carries no metadata.
 */
class ShareController(private val context: Context) {
    private val dir get() = File(context.cacheDir, DIR)

    /** Delete every earlier share image. Call at app start, off the main thread. */
    fun purge() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Renders and writes the image; returns its content URI, or null if it could not be written. */
    fun prepare(format: ShareFormat, model: ShareCardModel): Uri? = runCatching {
        purge()
        dir.mkdirs()
        val file = File(dir, "panchang-" + randomName() + ".png")
        val bitmap = ShareCardRenderer.render(format, model)
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        bitmap.recycle()
        FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
    }.getOrNull()

    /** The chooser intent for [uri]: a plain image, read grant only. */
    fun chooser(uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(AajR.string.aaj_share_chooser))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun randomName(): String = ByteArray(RANDOM_BYTES).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIR = "share"
        const val AUTHORITY_SUFFIX = ".share"
        const val PNG_QUALITY = 100
        const val RANDOM_BYTES = 8
    }
}
