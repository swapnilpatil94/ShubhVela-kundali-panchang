package app.parivaar.panchang.engine.astro

/**
 * Random-access bytes of an ephemeris data file. The engine does no file I/O itself: the platform layer
 * supplies an implementation (asset pack, memory-mapped file, ...).
 */
interface BinSource {
    /** Total length in bytes. */
    val size: Long

    /** Copies exactly [len] bytes starting at byte [pos] into `dst[off until off + len]`. */
    fun read(pos: Long, dst: ByteArray, off: Int, len: Int)
}

/** [BinSource] over an in-memory array (the array is not copied). */
class ByteArrayBinSource(private val bytes: ByteArray) : BinSource {
    override val size: Long get() = bytes.size.toLong()

    override fun read(pos: Long, dst: ByteArray, off: Int, len: Int) {
        require(pos >= 0 && len >= 0 && pos + len <= bytes.size) { "read of $len bytes at $pos is out of range" }
        bytes.copyInto(dst, off, pos.toInt(), pos.toInt() + len)
    }
}
