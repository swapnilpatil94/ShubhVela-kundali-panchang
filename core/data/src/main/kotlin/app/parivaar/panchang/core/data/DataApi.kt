package app.parivaar.panchang.core.data

import app.parivaar.panchang.core.model.bond.Bond
import app.parivaar.panchang.core.model.place.SavedPlace
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

/** Failure codes. Never carry user data; messages derived from them must not either. */
enum class DataError {
    KEY_LOST,
    KEY_UNAVAILABLE,
    LOCKED,
    VALIDATION,
    LIMIT,
    SELF_EXISTS,
    NOT_FOUND,
    IO,
    FILE_FORMAT,
    FILE_VERSION,
    FILE_AUTH,
    FILE_TOO_LARGE,
    PASSPHRASE_POLICY,
}

sealed interface DataResult<out T> {
    data class Ok<T>(val value: T) : DataResult<T>

    data class Err(val error: DataError) : DataResult<Nothing>
}

/** [unreadable] counts tampered or undecodable rows; such rows are never returned. */
data class Records<T>(val items: List<T>, val unreadable: Int)

sealed interface VaultState {
    data object Opening : VaultState

    data object Ready : VaultState

    data object KeyLost : VaultState

    data object KeyUnavailable : VaultState

    data object Restoring : VaultState
}

interface DataVault {
    val state: StateFlow<VaultState>

    suspend fun open(): VaultState

    /** Deletes the unreadable data (via the wiper), then opens a fresh store. */
    suspend fun startFresh(): DataResult<Unit>
}

interface ProfileRepository {
    fun observeAll(): Flow<Records<Profile>>

    suspend fun get(id: RecordId): DataResult<Profile>

    suspend fun upsert(p: Profile): DataResult<Unit>

    suspend fun delete(id: RecordId): DataResult<Unit>

    suspend fun reorder(ids: List<RecordId>): DataResult<Unit>
}

interface PlaceRepository {
    fun observeAll(): Flow<Records<SavedPlace>>

    suspend fun upsert(p: SavedPlace): DataResult<Unit>

    suspend fun delete(id: RecordId): DataResult<Unit>
}

interface BondRepository {
    fun observeAll(): Flow<Records<Bond>>

    suspend fun upsert(b: Bond): DataResult<Unit>

    suspend fun delete(id: RecordId): DataResult<Unit>
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(f: (AppSettings) -> AppSettings): DataResult<Unit>
}

/**
 * MERGE: same id, the incoming record wins iff strictly later (Profile.updatedAt, SavedPlace.lastUsedAt,
 * Bond.createdAt); local settings are kept. REPLACE_ALL replaces everything including settings.
 */
enum class ImportMode { REPLACE_ALL, MERGE }

data class TransferSummary(val profiles: Int, val places: Int, val bonds: Int)

interface FamilyTransfer {
    /** Zeroes [passphrase] before returning. */
    suspend fun export(out: OutputStream, passphrase: CharArray): DataResult<TransferSummary>

    /** Zeroes [passphrase] before returning. Writes nothing on any error. */
    suspend fun import(input: InputStream, passphrase: CharArray, mode: ImportMode): DataResult<TransferSummary>
}

/** Delete all data. Idempotent; continues past errors and reports the first one. */
interface DataWiper {
    suspend fun deleteAll(): DataResult<Unit>
}

fun newRecordId(rng: SecureRandom): RecordId {
    val bytes = ByteArray(RecordId.LENGTH / 2)
    rng.nextBytes(bytes)
    val hex = StringBuilder(RecordId.LENGTH)
    for (b in bytes) {
        val v = b.toInt() and 0xff
        hex.append(HEX[v ushr NIBBLE]).append(HEX[v and NIBBLE_MASK])
    }
    return RecordId(hex.toString())
}

private const val HEX = "0123456789abcdef"
private const val NIBBLE = 4
private const val NIBBLE_MASK = 0xf
