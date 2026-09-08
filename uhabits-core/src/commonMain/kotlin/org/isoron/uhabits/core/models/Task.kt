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
package org.isoron.uhabits.core.models

/**
 * A task is a one-time actionable item with an optional due date and reminder.
 * Tasks are separate from habits and have their own category system.
 */
data class Task(
    var id: Long? = null,
    var title: String = "",
    var description: String = "",
    var categoryId: Long? = null,
    var dueDate: Long? = null,
    var reminderTime: Long? = null,
    var isCompleted: Boolean = false,
    var position: Int = 0,
    val observable: ModelObservable = ModelObservable()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Task) return false

        if (id != other.id) return false
        if (title != other.title) return false
        if (description != other.description) return false
        if (categoryId != other.categoryId) return false
        if (dueDate != other.dueDate) return false
        if (reminderTime != other.reminderTime) return false
        if (isCompleted != other.isCompleted) return false
        if (position != other.position) return false

        return true
    }

    override fun hashCode(): Int {
        var result = (id?.hashCode() ?: 0)
        result = 31 * result + title.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + (categoryId?.hashCode() ?: 0)
        result = 31 * result + (dueDate?.hashCode() ?: 0)
        result = 31 * result + (reminderTime?.hashCode() ?: 0)
        result = 31 * result + isCompleted.hashCode()
        result = 31 * result + position
        return result
    }

    fun hasReminder(): Boolean = reminderTime != null
}
