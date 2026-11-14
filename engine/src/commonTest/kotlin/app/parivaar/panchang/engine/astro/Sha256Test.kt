package app.parivaar.panchang.engine.astro

import app.parivaar.panchang.engine.astro.internal.Sha256
import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {
    @Test
    fun emptyInput() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hashHex(ByteArray(0)),
        )
    }

    @Test
    fun abc() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hashHex("abc".encodeToByteArray()),
        )
    }

    @Test
    fun chunkedUpdateMatchesOneShot() {
        val data = ByteArray(1000) { (it * 31).toByte() }
        val s = Sha256()
        var i = 0
        while (i < data.size) { val n = minOf(37, data.size - i); s.update(data, i, n); i += n }
        assertEquals(Sha256.hashHex(data), Sha256.hex(s.digest()))
    }
}
