package app.parivaar.panchang.core.data.keys

import android.annotation.SuppressLint
import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Best-effort fsync of a directory after an atomic rename, so the new directory entry survives a power loss.
 * Never fails: platforms that reject directory fsync (or a JVM test host) just skip it.
 */
internal object DirSync {
    // API 26+ and the JVM test host (SDK_INT 0) use java.nio; API 24-25 use android.system.Os.
    @SuppressLint("NewApi")
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun fsync(dir: File?) {
        if (dir == null) return
        try {
            if (Build.VERSION.SDK_INT in 1 until Build.VERSION_CODES.O) {
                val fd = Os.open(dir.path, OsConstants.O_RDONLY, 0)
                try {
                    Os.fsync(fd)
                } finally {
                    Os.close(fd)
                }
            } else {
                FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
            }
        } catch (_: Exception) {
            // Best effort only (some filesystems refuse directory fsync).
        } catch (_: LinkageError) {
            // Best effort only.
        }
    }
}
