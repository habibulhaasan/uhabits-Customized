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
package org.isoron.uhabits.activities.tasks.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.isoron.uhabits.core.models.Task
import org.isoron.uhabits.core.models.TaskCategory
import org.isoron.uhabits.core.ui.views.Theme

class TaskAdapter(
    private var tasks: List<Task>,
    private val categories: List<TaskCategory>,
    private val theme: Theme,
    private val onTaskClick: (Task) -> Unit,
    private val onTaskComplete: (Task, Boolean) -> Unit,
    private val onTaskDelete: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskCardViewHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return tasks[position].id ?: position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskCardViewHolder {
        val view = TaskCardView(parent.context, theme)
        return TaskCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskCardViewHolder, position: Int) {
        val task = tasks[position]
        val category = categories.find { it.id == task.categoryId }
        holder.bind(task, category)
    }

    override fun getItemCount(): Int = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    inner class TaskCardViewHolder(
        private val cardView: TaskCardView
    ) : RecyclerView.ViewHolder(cardView) {

        fun bind(task: Task, category: TaskCategory?) {
            cardView.onToggle = { t ->
                val newState = !t.isCompleted
                onTaskComplete(t, newState)
            }
            cardView.onClick = { t ->
                onTaskClick(t)
            }
            cardView.onLongClick = { t ->
                onTaskDelete(t)
            }
            cardView.bind(task, category)
        }
    }
}
