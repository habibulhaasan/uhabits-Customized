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
package org.isoron.uhabits.core.models.memory

import org.isoron.uhabits.core.models.CategoryList
import org.isoron.uhabits.core.models.EntryList
import org.isoron.uhabits.core.models.ModelFactory
import org.isoron.uhabits.core.models.ScoreList
import org.isoron.uhabits.core.models.StreakList
import org.isoron.uhabits.core.models.TaskCategoryList
import org.isoron.uhabits.core.models.TaskList

class MemoryModelFactory : ModelFactory {
    override fun buildComputedEntries() = EntryList()
    override fun buildOriginalEntries() = EntryList()
    override fun buildHabitList() = MemoryHabitList()
    override fun buildScoreList() = ScoreList()
    override fun buildStreakList() = StreakList()

    override fun buildCategoryList(): CategoryList =
        throw UnsupportedOperationException("MemoryModelFactory does not support categories")

    override fun buildTaskList(): TaskList =
        throw UnsupportedOperationException("MemoryModelFactory does not support tasks")

    override fun buildTaskCategoryList(): TaskCategoryList =
        throw UnsupportedOperationException("MemoryModelFactory does not support task categories")
}
