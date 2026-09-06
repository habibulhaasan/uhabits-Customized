package org.isoron.uhabits.core.models

import org.isoron.uhabits.core.database.CategoryData
import org.isoron.uhabits.core.database.CategoryRepository
import org.isoron.uhabits.core.database.HabitRepository

/**
 * An ordered, position-sorted collection of [Category] objects, backed by
 * SQLite via [CategoryRepository]. Mirrors the load/cache/persist pattern
 * used by SQLiteHabitList, but kept intentionally simpler since categories
 * are typically few in number (tens, not thousands).
 */
class CategoryList(
    private val categoryRepository: CategoryRepository,
    private val habitRepository: HabitRepository
) {
    val observable = ModelObservable()

    private val list = mutableListOf<Category>()
    private var loaded = false

    private fun loadRecords() {
        if (loaded) return
        loaded = true
        list.clear()
        categoryRepository.findAll()
            .sortedBy { it.position }
            .forEach { list.add(copyTo(it)) }
    }

    /** Returns all categories, ordered by position. */
    fun getAll(): List<Category> {
        loadRecords()
        return list.toList()
    }

    fun getById(id: Long): Category? {
        loadRecords()
        return list.firstOrNull { it.id == id }
    }

    /** Number of (non-archived or archived, doesn't matter here) habits currently in this category. */
    fun countHabits(categoryId: Long): Int {
        return habitRepository.findByCategory(categoryId).size
    }

    /** Number of habits with no category assigned. */
    fun countUncategorizedHabits(): Int {
        return habitRepository.findByCategory(null).size
    }

    fun add(category: Category) {
        loadRecords()
        require(category.id == null) { "category already has an id" }
        category.position = list.size
        val id = categoryRepository.insert(copyFrom(category))
        category.id = id
        list.add(category)
        observable.notifyListeners()
    }

    fun update(category: Category) {
        loadRecords()
        val idx = list.indexOfFirst { it.id == category.id }
        require(idx >= 0) { "category not found" }
        categoryRepository.update(copyFrom(category))
        list[idx] = category
        observable.notifyListeners()
    }

    /**
     * Removes the category. Habits that belonged to it become uncategorized
     * (see [CategoryRepository.delete], which clears their category_id
     * before deleting the row) -- habits themselves are never deleted here.
     */
    fun remove(category: Category) {
        loadRecords()
        categoryRepository.delete(category.id!!)
        list.removeAll { it.id == category.id }
        rebuildOrder()
        observable.notifyListeners()
    }

    /** Moves [from] to the position currently occupied by [to]. */
    fun reorder(from: Category, to: Category) {
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
                categoryRepository.update(copyFrom(c))
            }
        }
    }

    companion object {
        fun copyFrom(category: Category) = CategoryData(
            id = category.id,
            name = category.name,
            color = category.color.paletteIndex,
            position = category.position
        )

        fun copyTo(data: CategoryData) = Category(
            id = data.id,
            name = data.name,
            color = PaletteColor(data.color),
            position = data.position
        )
    }
}