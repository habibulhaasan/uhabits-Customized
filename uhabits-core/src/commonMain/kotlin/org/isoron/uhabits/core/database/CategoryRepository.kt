package org.isoron.uhabits.core.database

import org.isoron.platform.io.Database
import org.isoron.platform.io.PreparedStatement
import org.isoron.platform.io.StepResult
import org.isoron.platform.io.queryLong

data class CategoryData(
    var id: Long? = null,
    var name: String = "",
    var color: Int = 0,
    var position: Int = 0
)

class CategoryRepository(private val db: Database) {
    private val findAllStmt by lazy {
        db.prepareStatement(
            """SELECT id, name, color, position
               FROM Categories ORDER BY position"""
        )
    }

    private val insertStmt by lazy {
        db.prepareStatement(
            """INSERT INTO Categories(name, color, position)
               VALUES (?, ?, ?)"""
        )
    }

    private val insertWithIdStmt by lazy {
        db.prepareStatement(
            """INSERT INTO Categories(id, name, color, position)
               VALUES (?, ?, ?, ?)"""
        )
    }

    private val updateStmt by lazy {
        db.prepareStatement(
            """UPDATE Categories SET name=?, color=?, position=? WHERE id=?"""
        )
    }

    private val deleteStmt by lazy {
        db.prepareStatement("DELETE FROM Categories WHERE id = ?")
    }

    /**
     * Deleting a category should not delete the habits in it: this clears
     * category_id back to null (uncategorized) for every habit that
     * referenced it. Call this before [delete] when removing a category.
     */
    private val clearHabitsCategoryStmt by lazy {
        db.prepareStatement("UPDATE Habits SET category_id = NULL WHERE category_id = ?")
    }

    fun findAll(): List<CategoryData> {
        findAllStmt.reset()
        val results = mutableListOf<CategoryData>()
        while (findAllStmt.step() == StepResult.ROW) {
            results.add(readRow(findAllStmt))
        }
        return results
    }

    fun insert(data: CategoryData): Long {
        if (data.id != null) {
            insertWithIdStmt.reset()
            insertWithIdStmt.bindLong(1, data.id!!)
            bindForInsert(insertWithIdStmt, data, offset = 1)
            insertWithIdStmt.step()
            return data.id!!
        }
        insertStmt.reset()
        bindForInsert(insertStmt, data)
        insertStmt.step()
        return db.queryLong("SELECT last_insert_rowid()")
    }

    fun update(data: CategoryData) {
        updateStmt.reset()
        bindForInsert(updateStmt, data)
        updateStmt.bindLong(4, data.id!!)
        updateStmt.step()
    }

    fun delete(id: Long) {
        clearHabitsCategoryStmt.reset()
        clearHabitsCategoryStmt.bindLong(1, id)
        clearHabitsCategoryStmt.step()

        deleteStmt.reset()
        deleteStmt.bindLong(1, id)
        deleteStmt.step()
    }

    private fun bindForInsert(stmt: PreparedStatement, data: CategoryData, offset: Int = 0) {
        val o = offset
        stmt.bindText(1 + o, data.name)
        stmt.bindInt(2 + o, data.color)
        stmt.bindInt(3 + o, data.position)
    }

    private fun readRow(stmt: PreparedStatement): CategoryData {
        return CategoryData(
            id = stmt.getLong(0),
            name = stmt.getText(1),
            color = stmt.getInt(2),
            position = stmt.getInt(3)
        )
    }
}