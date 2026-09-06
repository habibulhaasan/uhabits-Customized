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
package org.isoron.uhabits.activities.categories

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.isoron.platform.gui.toInt
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.Category
import org.isoron.uhabits.core.ui.views.Theme
import org.isoron.uhabits.databinding.ItemCategoryBinding

/**
 * Adapter for the category management screen. Reordering is persisted
 * immediately on every drag step, mirroring how HabitCardListView handles
 * habit reordering elsewhere in the app (see HabitCardListView.TouchHelperCallback).
 */
class CategoryAdapter(
    private val categories: MutableList<Category>,
    private val theme: Theme,
    private val habitCounter: (Long) -> Int,
    private val onClick: (Category) -> Unit,
    private val onDelete: (Category) -> Unit,
    private val onReorder: (Category, Category) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    fun replaceAll(newCategories: List<Category>) {
        categories.clear()
        categories.addAll(newCategories)
        notifyDataSetChanged()
    }

    /**
     * Called by [CategoryDragCallback] for every step of an in-progress
     * drag gesture: updates the in-memory order, the visible list, and
     * persists the new position right away.
     */
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
        val binding = ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount() = categories.size

    inner class ViewHolder(private val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: Category) {
            binding.categoryName.text = category.name
            val count = category.id?.let { habitCounter(it) } ?: 0
            binding.habitCount.text = binding.root.resources.getQuantityString(
                R.plurals.category_habit_count,
                count,
                count
            )

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