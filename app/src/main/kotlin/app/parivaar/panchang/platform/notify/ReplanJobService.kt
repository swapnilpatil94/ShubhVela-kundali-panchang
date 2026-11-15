package app.parivaar.panchang.platform.notify

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** Daily re-plan (MASTER section 10; WorkManager is not available, decision D1). */
class ReplanJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            Replan.run(applicationContext)
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        const val JOB_ID = 8001
        private const val PERIOD_MS = 24L * 60 * 60 * 1000

        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(JOB_ID) != null) return
            js.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, ReplanJobService::class.java))
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}

/** Entry used by every re-plan trigger. No-op until the app shell sets [ReminderRuntime.source]. */
object Replan {
    fun run(context: Context) {
        val source = ReminderRuntime.source ?: return
        // The sink needs the zone only to cancel by local date and to render times; take it from the input.
        val zoneHolder = arrayOfNulls<TimeZone>(1)
        val capturing = ReminderPlanSource { now -> source.load(now)?.also { zoneHolder[0] = it.zone } }
        val lazySink = DeferredSink(context) { zoneHolder[0] ?: TimeZone.currentSystemDefault() }
        Replanner({ Clock.System.now() }, capturing, lazySink).replan()
    }
}

private class DeferredSink(private val context: Context, private val zone: () -> TimeZone) :
    app.parivaar.panchang.feature.aaj.reminders.AlarmSink {
    private fun real() = AlarmManagerSink(context, zone())

    override fun setWindow(requestCode: Int, triggerAt: kotlin.time.Instant, window: kotlin.time.Duration) =
        real().setWindow(requestCode, triggerAt, window)

    override fun setReminder(
        reminder: app.parivaar.panchang.feature.aaj.reminders.PlannedReminder,
        window: kotlin.time.Duration,
    ) = real().setReminder(reminder, window)

    override fun cancelAll() = real().cancelAll()
}
