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

import org.isoron.uhabits.core.database.TaskData
import org.isoron.uhabits.core.database.TaskRepository

/**
 * An ordered, observable collection of [Task] objects, backed by
 * SQLite via [TaskRepository].
 */
class TaskList(private val taskRepository: TaskRepository) {
    val observable = ModelObservable()

    private val list = mutableListOf<Task>()
    private var loaded = false

    private fun loadRecords() {
        if (loaded) return
        loaded = true
        list.clear()
        taskRepository.findAll()
            .sortedBy { it.position }
            .forEach { list.add(copyTo(it)) }
    }

    /** Returns all tasks, ordered by position. */
    fun getAll(): List<Task> {
        loadRecords()
        return list.toList()
    }

    fun getById(id: Long): Task? {
        loadRecords()
        return list.firstOrNull { it.id == id }
    }

    fun getByCategory(categoryId: Long?): List<Task> {
        loadRecords()
        return list.filter { it.categoryId == categoryId }
    }

    fun getUpcoming(days: Int = 7, nowMillis: Long): List<Task> {
        loadRecords()
        val futureLimit = nowMillis + days * 24L * 60 * 60 * 1000
        return list.filter { task ->
            task.dueDate != null &&
            !task.isCompleted &&
            task.dueDate!! in nowMillis..futureLimit
        }.sortedBy { it.dueDate }
    }

    fun getOverdue(nowMillis: Long): List<Task> {
        loadRecords()
        return list.filter { task ->
            task.dueDate != null &&
            !task.isCompleted &&
            task.dueDate!! < nowMillis
        }.sortedBy { it.dueDate }
    }

    fun add(task: Task) {
        loadRecords()
        require(task.id == null) { "task already has an id" }
        task.position = list.size
        val id = taskRepository.insert(copyFrom(task))
        task.id = id
        list.add(task)
        observable.notifyListeners()
    }

    fun update(task: Task) {
        loadRecords()
        val idx = list.indexOfFirst { it.id == task.id }
        require(idx >= 0) { "task not found" }
        taskRepository.update(copyFrom(task))
        list[idx] = task
        observable.notifyListeners()
    }

    fun remove(task: Task) {
        loadRecords()
        taskRepository.delete(task.id!!)
        list.removeAll { it.id == task.id }
        rebuildOrder()
        observable.notifyListeners()
    }

    fun reorder(from: Task, to: Task) {
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
        for ((index, t) in list.withIndex()) {
            if (t.position != index) {
                t.position = index
                taskRepository.update(copyFrom(t))
            }
        }
    }

    companion object {
        fun copyFrom(task: Task) = TaskData(
            id = task.id,
            title = task.title,
            description = task.description,
            categoryId = task.categoryId,
            dueDate = task.dueDate,
            reminderTime = task.reminderTime,
            completed = if (task.isCompleted) 1 else 0,
            position = task.position,
            recurrenceDays = task.recurrenceDays
        )

        fun copyTo(data: TaskData) = Task(
            id = data.id,
            title = data.title,
            description = data.description,
            categoryId = data.categoryId,
            dueDate = data.dueDate,
            reminderTime = data.reminderTime,
            isCompleted = data.completed != 0,
            position = data.position,
            recurrenceDays = data.recurrenceDays
        )
    }
}
