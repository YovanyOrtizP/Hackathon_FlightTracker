/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.platform.ui.live_updates

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

object SnackbarNotificationManager {
    private lateinit var notificationManager: NotificationManager
    private lateinit var appContext: Context
    private const val CHANNEL_ID = "live_updates_channel_id"
    private const val CHANNEL_NAME = "live_updates_channel_name"
    private const val NOTIFICATION_ID = 1234

    private const val SIMULATED_FLIGHT_DURATION_MS = 60_000L
    private const val DISPLAYED_FLIGHT_DURATION_MINUTES = 60

    private var flightStartTime: Long = 0L

    private enum class OrderState(val delay: Long) {
        INITIALIZING(2000),
        FLIGHT_ROUTE(4000),
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun initialize(context: Context, notifManager: NotificationManager) {
        appContext = context
        notificationManager = notifManager
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    fun start() {
        Handler(Looper.getMainLooper()).postDelayed({
            val notification = buildFlightNotification(null).build()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }, OrderState.INITIALIZING.delay)

        Handler(Looper.getMainLooper()).postDelayed({
            flightStartTime = System.currentTimeMillis()
            logPromotableStatus()
            animateFlightProgress()
        }, OrderState.INITIALIZING.delay + OrderState.FLIGHT_ROUTE.delay)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun animateFlightProgress() {
        val handler = Handler(Looper.getMainLooper())
        val totalDuration = SIMULATED_FLIGHT_DURATION_MS
        val steps = 60
        val interval = totalDuration / steps

        for (i in 0..steps) {
            handler.postDelayed({
                val progress = if (i < 100) (i * 100 / steps) else 100
                val builder = buildFlightNotification(progress)
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            }, i * interval)
        }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildFlightNotification(progress: Int?): NotificationCompat.Builder {
        val origin = "MEX"
        val destination = "SFO"
        val isIndeterminate = progress == null
        val isComplete = progress == 100

        val title = when {
            isIndeterminate -> "Yovany's flight is about to start"
            isComplete -> "Yovany's flight has arrived to SFO"
            else -> "Flying - ${formatRemainingTime(progress ?: 0)}"
        }

        val timeDetails = if (!isIndeterminate && flightStartTime > 0) {
            val takeoff = formatTime(flightStartTime)
            val landing = formatTime(flightStartTime + DISPLAYED_FLIGHT_DURATION_MINUTES * 60_000L)
            "\nTakeoff: $takeoff  •  Landing ETA: $landing"
        } else ""

        val text = when {
            isIndeterminate -> "Get ready..."
            isComplete -> "Yovany landed at his destination"
            else -> "Yovany is flying from $origin to $destination"
        } + timeDetails

        val style = buildBaseProgressStyle()
        if (isIndeterminate) {
            style.setProgressIndeterminate(true)
        } else {
            style.setProgress(progress ?: 0)
        }

        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.app)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .addAction(getShareAction())
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildBaseProgressStyle(): NotificationCompat.ProgressStyle {
        val pointColor = Color.valueOf(236f, 183f, 255f, 1f).toArgb()
        val segmentColor = Color.valueOf(253f, 255f, 255f, 1f).toArgb()

        return NotificationCompat.ProgressStyle()
            .setProgressPoints(listOf(NotificationCompat.ProgressStyle.Point(100).setColor(pointColor)))
            .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(100).setColor(segmentColor)))
            .setStyledByProgress(true)
            .setProgressTrackerIcon(
                IconCompat.createWithResource(appContext, R.drawable.flight)
            )
    }

    private fun getShareAction(): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            null,
            "Share Flight",
            createShareIntent(appContext)
        ).build()
    }

    private fun createShareIntent(context: Context): PendingIntent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out Yovany's flight! https://www.expedia.com/mobile.deeplink/flightTracker")
        }

        return PendingIntent.getActivity(
            context,
            0,
            Intent.createChooser(shareIntent, "Share flight via"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun formatRemainingTime(progress: Int): String {
        val totalMinutes = DISPLAYED_FLIGHT_DURATION_MINUTES
        val remainingMinutes = ((100 - progress) * totalMinutes) / 100

        val hours = remainingMinutes / 60
        val minutes = remainingMinutes % 60

        return when {
            hours > 0 -> String.format("%dh %02dm remaining", hours, minutes)
            else -> String.format("%d min remaining", minutes)
        }
    }

    private fun formatTime(timestamp: Long): String {
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun logPromotableStatus() {
        val tempNotification = buildFlightNotification(0).build()
        Logger.getLogger("canPostPromotedNotifications")
            .log(Level.INFO, notificationManager.canPostPromotedNotifications().toString())
        Logger.getLogger("hasPromotableCharacteristics")
            .log(Level.INFO, tempNotification.hasPromotableCharacteristics().toString())
    }
}