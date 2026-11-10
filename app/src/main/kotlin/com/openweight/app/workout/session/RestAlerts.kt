package com.openweight.app.workout.session

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.openweight.app.MainActivity
import com.openweight.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The "rest is over" alert, for when the phone is locked or another app is open. An alarm fires at
 * the end of the rest and [RestAlarmReceiver] posts a notification; both stay on the phone (no
 * network). The alarm is exact when Android allows it, otherwise as close as the system lets it be.
 */
@Singleton
class RestAlerts @Inject constructor(@param:ApplicationContext private val context: Context) {
  /** The end (epoch ms) of the rest the user skipped: no alert for it, from any screen. */
  val skippedRest = MutableStateFlow<Long?>(null)

  private val alarms: AlarmManager
    get() = context.getSystemService(AlarmManager::class.java)

  /** The app may post notifications (always true before Android 13, unless switched off). */
  fun canNotify(): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
      (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

  /** Alerts at [endsAt] that the rest is over, naming the next set; replaces an earlier alert. */
  fun schedule(endsAt: Instant, next: NextSet?) {
    val intent = alarmIntent(next)
    val at = endsAt.toEpochMilli()
    if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
      alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    } else {
      alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }
  }

  /** No alert: the rest was skipped, the set undone, or the session closed. */
  fun cancel() {
    alarms.cancel(alarmIntent(null))
    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
  }

  private fun alarmIntent(next: NextSet?): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      0,
      Intent(context, RestAlarmReceiver::class.java).putExtra(EXTRA_EXERCISE, next?.exercise).putExtra(EXTRA_SET, next?.setNumber ?: 0),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  internal fun post(next: NextSet?) {
    if (!canNotify()) return
    val nextLabel = next?.let { context.getString(R.string.rest_next_set, it.exercise, it.setNumber) }
    val manager = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 26) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL, context.getString(R.string.rest_channel), NotificationManager.IMPORTANCE_HIGH).apply {
          description = context.getString(R.string.rest_channel_description)
        }
      )
    }
    val open =
      PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val notification =
      NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_tab_workout)
        .setContentTitle(context.getString(R.string.session_rest_over))
        .setContentText(nextLabel?.let { context.getString(R.string.rest_next, it) })
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setContentIntent(open)
        .setAutoCancel(true)
        .setTimeoutAfter(TIMEOUT_MS)
        .build()
    @Suppress("MissingPermission") // checked in canNotify()
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
  }

  companion object {
    const val CHANNEL = "rest"
    const val NOTIFICATION_ID = 1
    const val EXTRA_EXERCISE = "exercise"
    const val EXTRA_SET = "set"
    /** A rest alert older than this is no longer useful. */
    const val TIMEOUT_MS = 10 * 60 * 1000L
  }
}

/** The set that comes after the rest, for the alert's text. */
data class NextSet(val exercise: String, val setNumber: Int)

/** Posts the alert when the rest alarm fires. */
class RestAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val exercise = intent.getStringExtra(RestAlerts.EXTRA_EXERCISE)
    val set = intent.getIntExtra(RestAlerts.EXTRA_SET, 0)
    RestAlerts(context.applicationContext).post(exercise?.let { NextSet(it, set) })
  }
}
