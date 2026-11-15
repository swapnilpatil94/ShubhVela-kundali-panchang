package app.parivaar.panchang.platform.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when an alarm window opens. Not exported; only our own PendingIntents reach it. */
class ReminderReceiver : BroadcastReceiver() {
    @Suppress("ReturnCount")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val extras = intent.extras ?: return
        val map = extras.keySet().associateWith { extras.get(it) }
        val payload = ReminderPayload.fromMap(map) ?: return
        Poster.post(context, payload, id = payload.kind.ordinal * ID_STRIDE + (intent.filterHashCode() and ID_MASK))
    }

    private fun Intent.filterHashCode(): Int = extras?.getString(ReminderPayload.ID)?.hashCode() ?: 0

    companion object {
        const val ACTION = "app.parivaar.panchang.action.REMINDER"
        private const val ID_STRIDE = 1000
        private const val ID_MASK = 0x3FF
    }
}
