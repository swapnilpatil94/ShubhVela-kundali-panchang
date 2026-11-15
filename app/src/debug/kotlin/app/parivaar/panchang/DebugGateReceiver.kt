package app.parivaar.panchang

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.parivaar.panchang.shell.DebugGate

/** Debug only: `am broadcast -n app.parivaar.panchang/.DebugGateReceiver --es gate keylost|unavailable|off`. */
class DebugGateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = DebugGate.set(intent.getStringExtra("gate"))
}
