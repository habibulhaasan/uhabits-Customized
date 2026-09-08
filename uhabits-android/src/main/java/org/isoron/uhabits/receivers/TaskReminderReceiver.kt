/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.Task

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action == null) return
        lastReceivedIntent = intent
        val app = context.applicationContext as HabitsApplication
        val appComponent = app.component
        val tasks = appComponent.taskList
        Log.i(TAG, String.format("Received task intent: %s", intent.toString()))
        var task: Task? = null
        val todayMillis = System.currentTimeMillis()
        val data = intent.data
        if (data != null) task = tasks.getById(ContentUris.parseId(data))
        val timestamp = intent.getLongExtra("timestamp", todayMillis)
        val reminderTime = intent.getLongExtra("reminderTime", todayMillis)
        try {
            when (intent.action) {
                ACTION_SHOW_TASK_REMINDER -> {
                    if (task == null) return
                    Log.d(
                        TAG,
                        String.format(
                            "onShowReminder task=%d timestamp=%d reminderTime=%d",
                            task.id,
                            timestamp,
                            reminderTime
                        )
                    )
                    showTaskNotification(context, task, reminderTime)
                }
                Intent.ACTION_BOOT_COMPLETED -> {
                    Log.d(TAG, "onBootCompleted")
                }
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "could not process intent", e)
        }
    }

    private fun showTaskNotification(context: Context, task: Task, reminderTime: Long) {
        val notificationManager = NotificationManagerCompat.from(context)
        val notification = NotificationCompat.Builder(context, TASK_REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(
                task.description.ifBlank { context.getString(R.string.default_reminder_question) }
            )
            .setWhen(reminderTime)
            .setShowWhen(true)
            .build()
        createTaskNotificationChannel(context)
        try {
            notificationManager.notify(task.id!!.toInt(), notification)
        } catch (_: SecurityException) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS permission for task notification")
        } catch (e: RuntimeException) {
            Log.w(TAG, "Failed to show task notification", e)
        }
    }

    companion object {
        const val ACTION_SHOW_TASK_REMINDER = "org.isoron.uhabits.ACTION_SHOW_TASK_REMINDER"
        private const val TAG = "TaskReminderReceiver"
        private const val TASK_REMINDERS_CHANNEL_ID = "TASK_REMINDERS"

        var lastReceivedIntent: Intent? = null
            private set

        fun createTaskNotificationChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                TASK_REMINDERS_CHANNEL_ID,
                context.getString(R.string.task_reminders),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
    }
}
