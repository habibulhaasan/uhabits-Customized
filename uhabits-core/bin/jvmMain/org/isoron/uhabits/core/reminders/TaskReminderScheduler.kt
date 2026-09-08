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
package org.isoron.uhabits.core.reminders

import org.isoron.uhabits.core.commands.Command
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.models.Task
import org.isoron.uhabits.core.models.TaskList

open class TaskReminderScheduler(
    private val commandRunner: CommandRunner,
    private val taskList: TaskList
) : CommandRunner.Listener {
    override fun onCommandFinished(command: Command) {
        if (command is CreateRepetitionCommand) return
    }

    open fun schedule(task: Task) {
        if (task.id == null) return
        if (!task.hasReminder()) return
        if (task.isCompleted) return
    }

    open fun scheduleAll() {
        taskList.getAll().filter { it.hasReminder() && !it.isCompleted }.forEach { schedule(it) }
    }

    open fun hasTasksWithReminders(): Boolean {
        return taskList.getAll().any { it.hasReminder() && !it.isCompleted }
    }
}
