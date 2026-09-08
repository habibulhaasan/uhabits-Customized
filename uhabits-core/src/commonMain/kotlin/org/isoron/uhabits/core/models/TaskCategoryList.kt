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

import org.isoron.uhabits.core.database.TaskCategoryData
import org.isoron.uhabits.core.database.TaskCategoryRepository

/**
 * An ordered, observable collection of [TaskCategory] objects, backed by
 * SQLite via [TaskCategoryRepository].
 */
class TaskCategoryList(private val taskCategoryRepository: TaskCategoryRepository) {
    val observable = ModelObservable()

    private val list = mutableListOf<TaskCategory>()
    private var loaded = false

    private fun loadRecords() {
        if (loaded) return
        loaded = true
        list.clear()
        taskCategoryRepository.findAll()
            .sortedBy { it.position }
            .forEach { list.add(copyTo(it)) }
    }

    /** Returns all task categories, ordered by position. */
    fun getAll(): List<TaskCategory> {
        loadRecords()
        return list.toList()
    }

    fun getById(id: Long): TaskCategory? {
        loadRecords()
        return list.firstOrNull { it.id == id }
    }

    fun add(category: TaskCategory) {
        loadRecords()
        require(category.id == null) { "category already has an id" }
        category.position = list.size
        val id = taskCategoryRepository.insert(copyFrom(category))
        category.id = id
        list.add(category)
        observable.notifyListeners()
    }

    fun update(category: TaskCategory) {
        loadRecords()
        val idx = list.indexOfFirst { it.id == category.id }
        require(idx >= 0) { "category not found" }
        taskCategoryRepository.update(copyFrom(category))
        list[idx] = category
        observable.notifyListeners()
    }

    fun remove(category: TaskCategory) {
        loadRecords()
        taskCategoryRepository.delete(category.id!!)
        list.removeAll { it.id == category.id }
        rebuildOrder()
        observable.notifyListeners()
    }

    fun reorder(from: TaskCategory, to: TaskCategory) {
        loadRecords()
        val fromIdx = list.indexOfFirst { it.id == from.id }
        val toIdx = list.indexOfFirst { it.id == to.id }
        if (fromIdx < 0 || toIdx < 0 || fromIdx == toIdx) return
        val moved = list.removeAt(fromIdx)
        list.add(toIdx, moved)
        rebuildOrder()
        observable.notifyListeners()
    }

    fun reload() {
        loaded = false
    }

    private fun rebuildOrder() {
        for ((index, c) in list.withIndex()) {
            if (c.position != index) {
                c.position = index
                taskCategoryRepository.update(copyFrom(c))
            }
        }
    }

    companion object {
        fun copyFrom(category: TaskCategory) = TaskCategoryData(
            id = category.id,
            name = category.name,
            color = category.color.paletteIndex,
            position = category.position
        )

        fun copyTo(data: TaskCategoryData) = TaskCategory(
            id = data.id,
            name = data.name,
            color = PaletteColor(data.color),
            position = data.position
        )
    }
}
