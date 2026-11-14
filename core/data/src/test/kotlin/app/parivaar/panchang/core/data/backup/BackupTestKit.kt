package app.parivaar.panchang.core.data.backup

import app.parivaar.panchang.core.data.crypto.Bytes
import java.io.File

/** In-memory stand-in for an end-to-end encrypted Block Store (D12). Copies in and out like the contract says. */
internal class FakeBackupKeyStore : BackupKeyStore {
    var entry: Pair<Int, ByteArray>? = null
        private set
    var storeCalls = 0
        private set

    override fun isAvailable(): Boolean = true

    override fun store(gen: Int, kb: ByteArray) {
        entry?.second?.let { Bytes.wipe(it) }
        entry = gen to kb.copyOf()
        storeCalls++
    }

    override fun retrieve(): Pair<Int, ByteArray>? = entry?.let { it.first to it.second.copyOf() }

    override fun clear() {
        entry?.second?.let { Bytes.wipe(it) }
        entry = null
    }

    /** Device B gets a copy of what device A stored (the platform syncs it). */
    fun copy(): FakeBackupKeyStore = FakeBackupKeyStore().also { b -> entry?.let { b.store(it.first, it.second) } }
}

/**
 * Frozen regression fixtures (spec section 6 "Formats"): `vectors/snapshot_v1.bin` and `vectors/export_v1.ppx`
 * are `vectors/family_v1.json` (synthetic data) sealed with the FIXED test-only parameters below. Production code
 * always draws keys, salts and nonces from SecureRandom. Regenerate only on a deliberate format change by running
 * the tests with PP_WRITE_FIXTURES=1, then record the new SHA-256 values in vectors/README.md.
 */
internal object FrozenFixtures {
    val SNAPSHOT_KB = ByteArray(32) { it.toByte() } // 00..1f
    const val SNAPSHOT_GEN = 1
    val SNAPSHOT_NONCE = ByteArray(12) { (0x20 + it).toByte() } // 20..2b
    const val EXPORT_PASSPHRASE = "pp-fixture-passphrase"
    val EXPORT_SALT = ByteArray(16) { it.toByte() } // 00..0f
    val EXPORT_NONCE = ByteArray(12) { (0x10 + it).toByte() } // 10..1b
    const val EXPORT_ITERATIONS = 600_000

    private val vectors = File(System.getProperty("pp.root.dir"), "core/data/src/test/resources/vectors")
    val familyJson: ByteArray get() = File(vectors, "family_v1.json").readBytes()
    val snapshotFile = File(vectors, "snapshot_v1.bin")
    val exportFile = File(vectors, "export_v1.ppx")

    fun snapshot(): ByteArray = SnapshotFormat.sealWithNonce(SNAPSHOT_KB, SNAPSHOT_GEN, SNAPSHOT_NONCE, familyJson)

    fun export(): ByteArray = ExportFormat.sealWithFixedParams(
        EXPORT_PASSPHRASE.toCharArray(), familyJson, EXPORT_SALT, EXPORT_NONCE, EXPORT_ITERATIONS,
    )

    /** Writes the fixtures only when explicitly asked (PP_WRITE_FIXTURES=1); otherwise a no-op. */
    fun writeIfRequested() {
        if (System.getenv("PP_WRITE_FIXTURES") != "1") return
        snapshotFile.writeBytes(snapshot())
        exportFile.writeBytes(export())
    }
}

/** One simulated phone: its own KEK, store, key dir and backup dir (spec section 6 "New device"). */
internal class Device(
    root: java.io.File,
    val backupKeys: BackupKeyStore,
    var now: kotlin.time.Instant = kotlin.time.Instant.parse("2025-01-01T00:00:00Z"),
) {
    val keysDir = java.io.File(root, "keys")
    val backupDir = java.io.File(root, "backup")
    val rng = java.security.SecureRandom()
    val wrapper = app.parivaar.panchang.core.data.keys.FakeKeyWrapper(rng)
    val store = app.parivaar.panchang.core.data.store.InMemoryRecordStore()
    val dek = app.parivaar.panchang.core.data.keys.DekManager(keysDir, wrapper, rng) { }
    val snapshots = SnapshotManager(backupDir, keysDir, store, dek, wrapper, backupKeys, rng, { now }, debounceMs = 50)
    val vault = app.parivaar.panchang.core.data.DataVaultImpl(
        store, dek, { throw AssertionError("no wipe") }, snapshots,
    )

    suspend fun readDoc() = store.tx { FamilyRows.read(this, dek.ring) }

    suspend fun seed(doc: app.parivaar.panchang.core.data.codec.FamilyDocument) =
        store.tx { FamilyRows.replaceAll(this, dek.ring, rng, doc) }

    fun files(dir: java.io.File): Set<String> = dir.list()?.toSet() ?: emptySet()
}
