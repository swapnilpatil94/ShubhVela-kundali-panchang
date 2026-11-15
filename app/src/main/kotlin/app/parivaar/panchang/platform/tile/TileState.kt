package app.parivaar.panchang.platform.tile

import kotlin.coroutines.cancellation.CancellationException

/** Runs [compute]; any failure except cancellation becomes ([fallback], no subtitle): never crashes the process. */
internal fun safeTile(fallback: String, compute: () -> Pair<String, String?>): Pair<String, String?> =
    try {
        compute()
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") _: Exception,
    ) {
        fallback to null
    }
