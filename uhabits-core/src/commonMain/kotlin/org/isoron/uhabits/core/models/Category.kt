/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
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
 * A Category is a user-defined group used to organize habits
 * (e.g. "Health", "Work", "Study"). A habit belongs to at most
 * one category via [Habit.categoryId]; habits with a null
 * categoryId are considered uncategorized.
 */
data class Category(
    var id: Long? = null,
    var name: String = "",
    var color: PaletteColor = PaletteColor(8),
    var position: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Category) return false

        if (id != other.id) return false
        if (name != other.name) return false
        if (color != other.color) return false
        if (position != other.position) return false

        return true
    }

    override fun hashCode(): Int {
        var result = (id?.hashCode() ?: 0)
        result = 31 * result + name.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + position
        return result
    }
}