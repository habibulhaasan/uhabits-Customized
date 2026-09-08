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
package org.isoron.uhabits.activities.tasks.categories

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.isoron.platform.gui.toInt
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.TaskCategory
import org.isoron.uhabits.core.ui.views.Theme
import org.isoron.uhabits.databinding.ItemTaskCategoryBinding

class TaskCategoryAdapter(
    private val categories: MutableList<TaskCategory>,
    private val theme: Theme,
    private val onClick: (TaskCategory) -> Unit,
    private val onDelete: (TaskCategory) -> Unit,
    private val onReorder: (TaskCategory, TaskCategory) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<TaskCategoryAdapter.ViewHolder>() {

    fun replaceAll(newCategories: List<TaskCategory>) {
        categories.clear()
        categories.addAll(newCategories)
        notifyDataSetChanged()
    }

    fun performReorder(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition || fromPosition < 0 || toPosition < 0) return
        val fromCategory = categories[fromPosition]
        val toCategory = categories[toPosition]
        val moved = categories.removeAt(fromPosition)
        categories.add(toPosition, moved)
        notifyItemMoved(fromPosition, toPosition)
        onReorder(fromCategory, toCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTaskCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount() = categories.size

    inner class ViewHolder(private val binding: ItemTaskCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: TaskCategory) {
            binding.categoryName.text = category.name
            val swatch = binding.colorSwatch.background.mutate() as GradientDrawable
            swatch.setColor(theme.color(category.color).toInt())

            binding.root.setOnClickListener { onClick(category) }
            binding.deleteButton.setOnClickListener { onDelete(category) }

            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
            }
        }
    }
}
