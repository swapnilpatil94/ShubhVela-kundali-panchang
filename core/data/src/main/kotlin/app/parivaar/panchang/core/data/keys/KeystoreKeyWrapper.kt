package app.parivaar.panchang.core.data.keys

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore KEK (spec section 4 "Hierarchy"): AES-256, alias `pp.kek.v1`, ENCRYPT|DECRYPT, GCM,
 * no padding, randomized encryption required (Keystore picks the IV), no user-auth binding and no
 * unlocked-device requirement (D7). StrongBox is tried on API >= 28 and dropped on
 * [StrongBoxUnavailableException]. The KEK never leaves Keystore. Device-only: not exercised on the JVM
 * (D12; FakeKeyWrapper stands in). Error mapping: invalidated/unrecoverable/missing key or bad tag ->
 * [KeyLostException]; KeyStoreException, ProviderException, IOException and any other provider failure
 * -> [KeyUnavailableException] (non-destructive; the caller retries).
 */
internal class KeystoreKeyWrapper(private val alias: String = ALIAS) : KeyWrapper {

    override fun exists(): Boolean = guard { keyStore().containsAlias(alias) }

    override fun create() {
        guard {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && generateStrongBox()) return@guard
            generate(strongBox = false)
        }
    }

    override fun delete() {
        guard { keyStore().deleteEntry(alias) }
    }

    override fun wrap(plain: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> = guard {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, kek())
        c.updateAAD(aad)
        val ct = c.doFinal(plain)
        c.iv to ct
    }

    override fun unwrap(iv: ByteArray, ct: ByteArray, aad: ByteArray): ByteArray = guard {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, kek(), GCMParameterSpec(TAG_BITS, iv))
        c.updateAAD(aad)
        c.doFinal(ct)
    }

    override fun toString(): String = "KeystoreKeyWrapper(redacted)"

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun kek(): SecretKey = keyStore().getKey(alias, null) as? SecretKey ?: throw KeyLostException()

    /** True if a StrongBox-backed key was created; false if StrongBox is unavailable on this device. */
    // androidx.annotation is not a dependency of this module (no new libraries in M06); callers check SDK_INT.
    @SuppressLint("UseRequiresApi")
    @TargetApi(Build.VERSION_CODES.P)
    private fun generateStrongBox(): Boolean =
        try {
            generate(strongBox = true)
            true
        } catch (@Suppress("SwallowedException") e: StrongBoxUnavailableException) {
            false
        }

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(KEY_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) spec.setIsStrongBoxBacked(true)
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec.build()) }.generateKey()
    }

    @Suppress("SwallowedException", "ThrowsCount")
    private inline fun <T> guard(block: () -> T): T =
        try {
            block()
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw KeyLostException()
        } catch (e: UnrecoverableKeyException) {
            throw KeyLostException()
        } catch (e: AEADBadTagException) {
            throw KeyLostException()
        } catch (e: KeyStoreException) {
            throw KeyUnavailableException()
        } catch (e: GeneralSecurityException) {
            throw KeyUnavailableException()
        } catch (e: ProviderException) {
            throw KeyUnavailableException()
        } catch (e: IOException) {
            throw KeyUnavailableException()
        }

    private companion object {
        const val ALIAS = "pp.kek.v1"
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
    }
}
