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
package org.isoron.uhabits.activities.habits.list.views

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.tatarka.inject.annotations.Inject
import org.isoron.uhabits.activities.habits.list.MAX_CHECKMARK_COUNT
import org.isoron.uhabits.core.models.Category
import org.isoron.uhabits.core.models.CategoryList
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.HabitMatcher
import org.isoron.uhabits.core.models.ModelObservable
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.ui.screens.habits.list.HabitCardListCache
import org.isoron.uhabits.core.ui.screens.habits.list.ListHabitsMenuBehavior
import org.isoron.uhabits.core.ui.screens.habits.list.ListHabitsSelectionMenuBehavior
import org.isoron.uhabits.core.utils.MidnightTimer
import org.isoron.uhabits.inject.ActivityScope
import java.util.LinkedList

/**
 * A single row displayed by the list: either a category header (a group
 * label, not backed by a habit) or a habit card.
 *
 * Rows are recomputed as a whole (see [HabitCardListAdapter.rebuildRows])
 * every time the underlying data changes, rather than translating the
 * cache's fine-grained insert/move/remove positions into this row space.
 * That trades away some per-item RecyclerView animations for a much
 * simpler, harder-to-get-wrong implementation -- worth revisiting with
 * DiffUtil once this has been exercised on a device.
 */
private sealed class Row {
    data class CategoryHeader(val category: Category?, val habitCount: Int) : Row()
    data class HabitRow(val habit: Habit) : Row()
}

/**
 * Provides data that backs a [HabitCardListView].
 *
 * The data if fetched and cached by a [HabitCardListCache]. This adapter
 * also holds a list of items that have been selected.
 *
 * Habits are grouped by category (in [CategoryList] order, with an
 * "Uncategorized" group trailing) whenever at least one category exists;
 * otherwise the list renders exactly as before (flat, no headers), so
 * users who never touch the category feature see no change at all.
 */
@Inject
@ActivityScope
@SuppressLint("NotifyDataSetChanged")
class HabitCardListAdapter(
    private val cache: HabitCardListCache,
    private val categoryList: CategoryList,
    private val preferences: Preferences,
    private val midnightTimer: MidnightTimer
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
    HabitCardListCache.Listener,
    MidnightTimer.MidnightListener,
    ListHabitsMenuBehavior.Adapter,
    ListHabitsSelectionMenuBehavior.Adapter {
    val observable: ModelObservable = ModelObservable()
    private var listView: HabitCardListView? = null
    val selected: LinkedList<Habit> = LinkedList()

    private var rows: List<Row> = emptyList()

    private val categoryListListener = ModelObservable.Listener {
        rebuildRows()
        notifyDataSetChanged()
    }

    override fun atMidnight() {
        cache.refreshAllHabits()
    }

    fun cancelRefresh() {
        cache.cancelTasks()
    }

    fun hasNoHabit(): Boolean {
        return cache.hasNoHabit()
    }

    /**
     * Sets all items as not selected.
     */
    override fun clearSelection() {
        if (selected.isEmpty()) return

        selected.clear()
        notifyDataSetChanged()
        observable.notifyListeners()
    }

    override fun getSelected(): List<Habit> {
        return ArrayList(selected)
    }

    /**
     * Returns the item that occupies a certain position on the list, or
     * null if that position holds a category header rather than a habit.
     *
     * @param position position of the item
     * @return the item at given position or null if position is invalid
     */
    @Deprecated("")
    fun getItem(position: Int): Habit? {
        return (rows.getOrNull(position) as? Row.HabitRow)?.habit
    }

    override fun getItemCount(): Int {
        return rows.size
    }

    override fun getItemViewType(position: Int): Int {
        return when (rows.getOrNull(position)) {
            is Row.CategoryHeader -> VIEW_TYPE_HEADER
            else -> VIEW_TYPE_HABIT
        }
    }

    override fun getItemId(position: Int): Long {
        return when (val row = rows.getOrNull(position)) {
            is Row.HabitRow -> row.habit.id!!
            is Row.CategoryHeader -> HEADER_ID_BASE - (row.category?.id ?: -1L)
            null -> RecyclerView.NO_ID
        }
    }

    /**
     * Returns whether list of selected items is empty.
     *
     * @return true if selection is empty, false otherwise
     */
    val isSelectionEmpty: Boolean
        get() = selected.isEmpty()
    val isSortable: Boolean
        get() = cache.primaryOrder == HabitList.Order.BY_POSITION

    /**
     * Notify the adapter that it has been attached to a ListView.
     */
    fun onAttached() {
        cache.onAttached()
        midnightTimer.addListener(this)
        categoryList.observable.addListener(categoryListListener)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        if (listView == null) return
        when (val row = rows.getOrNull(position) ?: return) {
            is Row.CategoryHeader -> {
                listView!!.bindCategoryHeaderView(
                    holder as CategoryHeaderViewHolder,
                    row.category,
                    row.habitCount
                )
            }
            is Row.HabitRow -> {
                val habit = row.habit
                val id = habit.id ?: return
                val score = cache.getScore(id)
                val checkmarks = cache.getCheckmarks(id)
                val notes = cache.getNotes(id)
                val isSelected = selected.contains(habit)
                listView!!.bindCardView(holder as HabitCardViewHolder, habit, score, checkmarks, notes, isSelected)
            }
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        if (holder is HabitCardViewHolder) listView!!.attachCardView(holder)
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is HabitCardViewHolder) listView!!.detachCardView(holder)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            CategoryHeaderViewHolder(listView!!.createCategoryHeaderView())
        } else {
            HabitCardViewHolder(listView!!.createHabitCardView())
        }
    }

    /**
     * Notify the adapter that it has been detached from a ListView.
     */
    fun onDetached() {
        cache.onDetached()
        midnightTimer.removeListener(this)
        categoryList.observable.removeListener(categoryListListener)
    }

    override fun onItemChanged(position: Int) {
        rebuildRows()
        notifyDataSetChanged()
        observable.notifyListeners()
    }

    override fun onItemInserted(position: Int) {
        rebuildRows()
        notifyDataSetChanged()
        observable.notifyListeners()
    }

    override fun onItemMoved(oldPosition: Int, newPosition: Int) {
        rebuildRows()
        notifyDataSetChanged()
        observable.notifyListeners()
    }

    override fun onItemRemoved(position: Int) {
        rebuildRows()
        notifyDataSetChanged()
        observable.notifyListeners()
    }

    override fun onRefreshFinished() {
        rebuildRows()
        notifyDataSetChanged()
        observable.notifyListeners()
    }

    /**
     * Recomputes [rows] from the cache's current (flat) habit list and the
     * current category list. Habits keep the relative order the cache
     * already sorted them in (by whatever primary/secondary order is
     * active); grouping only reshuffles them into per-category runs.
     *
     * If no categories exist yet, this produces a flat list of HabitRows
     * with no headers at all -- identical in shape to the pre-grouping
     * behavior, so nothing changes for users who haven't created a category.
     */
    private fun rebuildRows() {
        val allHabits = (0 until cache.habitCount).mapNotNull { cache.getHabitByPosition(it) }
        val categories = categoryList.getAll()

        if (categories.isEmpty()) {
            rows = allHabits.map { Row.HabitRow(it) }
            return
        }

        val knownCategoryIds = categories.mapNotNull { it.id }.toSet()
        val byCategory = allHabits.groupBy { h -> h.categoryId.takeIf { it in knownCategoryIds } }

        val newRows = mutableListOf<Row>()
        for (category in categories) {
            val habitsInCategory = byCategory[category.id] ?: continue
            if (habitsInCategory.isEmpty()) continue
            newRows.add(Row.CategoryHeader(category, habitsInCategory.size))
            habitsInCategory.forEach { newRows.add(Row.HabitRow(it)) }
        }

        val uncategorized = byCategory[null] ?: emptyList()
        if (uncategorized.isNotEmpty()) {
            newRows.add(Row.CategoryHeader(null, uncategorized.size))
            uncategorized.forEach { newRows.add(Row.HabitRow(it)) }
        }

        rows = newRows
    }

    /**
     * Removes a list of habits from the adapter.
     *
     * @param selected list of habits to be removed
     */
    override fun performRemove(selected: List<Habit>) {
        for (habit in selected) cache.remove(habit.id!!)
    }

    /**
     * Changes the order of habits on the adapter.
     *
     * [from] and [to] are row-space (RecyclerView adapter) positions, which
     * this translates into the cache's own flat habit-space positions
     * before delegating.
     *
     * @param from the row position of the habit that should be moved
     * @param to   the row position currently occupied by the target habit
     */
    fun performReorder(from: Int, to: Int) {
        val fromId = (rows.getOrNull(from) as? Row.HabitRow)?.habit?.id ?: return
        val toId = (rows.getOrNull(to) as? Row.HabitRow)?.habit?.id ?: return
        val cacheFrom = findCacheIndexById(fromId)
        val cacheTo = findCacheIndexById(toId)
        if (cacheFrom < 0 || cacheTo < 0 || cacheFrom == cacheTo) return
        cache.reorder(cacheFrom, cacheTo)
    }

    private fun findCacheIndexById(id: Long): Int {
        for (i in 0 until cache.habitCount) {
            if (cache.getHabitByPosition(i)?.id == id) return i
        }
        return -1
    }

    override fun refresh() {
        cache.refreshAllHabits()
    }

    override fun setFilter(matcher: HabitMatcher) {
        cache.setFilter(matcher)
    }

    /**
     * Sets the HabitCardListView that this adapter will provide data for.
     *
     * @param listView the HabitCardListView associated with this adapter
     */
    fun setListView(listView: HabitCardListView?) {
        this.listView = listView
    }

    override var primaryOrder: HabitList.Order
        get() = cache.primaryOrder
        set(value) {
            cache.primaryOrder = value
            preferences.defaultPrimaryOrder = value
        }

    override var secondaryOrder: HabitList.Order
        get() = cache.secondaryOrder
        set(value) {
            cache.secondaryOrder = value
            preferences.defaultSecondaryOrder = value
        }

    /**
     * Selects or deselects the item at a given position. A no-op if the
     * position holds a category header rather than a habit.
     *
     * @param position position of the item to be toggled
     */
    fun toggleSelection(position: Int) {
        val h = (rows.getOrNull(position) as? Row.HabitRow)?.habit ?: return
        val k = selected.indexOf(h)
        if (k < 0) selected.add(h) else selected.remove(h)
        notifyDataSetChanged()
    }

    companion object {
        const val VIEW_TYPE_HABIT = 0
        const val VIEW_TYPE_HEADER = 1

        /**
         * Base for synthetic header item ids (see getItemId). Habit ids are
         * positive longs assigned by SQLite autoincrement, so subtracting a
         * (categoryId or -1) from a value near Long.MIN_VALUE can never
         * collide with a real habit id.
         */
        private const val HEADER_ID_BASE = Long.MIN_VALUE / 2
    }

    init {
        cache.setListener(this)
        cache.setCheckmarkCount(
            MAX_CHECKMARK_COUNT
        )
        cache.secondaryOrder = preferences.defaultSecondaryOrder
        cache.primaryOrder = preferences.defaultPrimaryOrder
        setHasStableIds(true)
    }
}