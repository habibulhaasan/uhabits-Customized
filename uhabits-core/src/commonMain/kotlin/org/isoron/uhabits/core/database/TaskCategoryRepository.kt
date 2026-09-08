package org.isoron.uhabits.core.database

import org.isoron.platform.io.Database
import org.isoron.platform.io.PreparedStatement
import org.isoron.platform.io.StepResult
import org.isoron.platform.io.queryLong

data class TaskCategoryData(
    var id: Long? = null,
    var name: String = "",
    var color: Int = 0,
    var position: Int = 0
)

class TaskCategoryRepository(private val db: Database) {
    private val findAllStmt by lazy {
        db.prepareStatement(
            """SELECT id, name, color, position
               FROM TaskCategories ORDER BY position"""
        )
    }

    private val insertStmt by lazy {
        db.prepareStatement(
            """INSERT INTO TaskCategories(name, color, position)
               VALUES (?, ?, ?)"""
        )
    }

    private val insertWithIdStmt by lazy {
        db.prepareStatement(
            """INSERT INTO TaskCategories(id, name, color, position)
               VALUES (?, ?, ?, ?)"""
        )
    }

    private val updateStmt by lazy {
        db.prepareStatement(
            """UPDATE TaskCategories SET name=?, color=?, position=? WHERE id=?"""
        )
    }

    private val deleteStmt by lazy {
        db.prepareStatement("DELETE FROM TaskCategories WHERE id = ?")
    }

    private val clearTasksCategoryStmt by lazy {
        db.prepareStatement("UPDATE Tasks SET category_id = NULL WHERE category_id = ?")
    }

    fun findAll(): List<TaskCategoryData> {
        findAllStmt.reset()
        val results = mutableListOf<TaskCategoryData>()
        while (findAllStmt.step() == StepResult.ROW) {
            results.add(readRow(findAllStmt))
        }
        return results
    }

    fun insert(data: TaskCategoryData): Long {
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

    fun update(data: TaskCategoryData) {
        updateStmt.reset()
        bindForInsert(updateStmt, data)
        updateStmt.bindLong(4, data.id!!)
        updateStmt.step()
    }

    fun delete(id: Long) {
        clearTasksCategoryStmt.reset()
        clearTasksCategoryStmt.bindLong(1, id)
        clearTasksCategoryStmt.step()

        deleteStmt.reset()
        deleteStmt.bindLong(1, id)
        deleteStmt.step()
    }

    private fun bindForInsert(stmt: PreparedStatement, data: TaskCategoryData, offset: Int = 0) {
        val o = offset
        stmt.bindText(1 + o, data.name)
        stmt.bindInt(2 + o, data.color)
        stmt.bindInt(3 + o, data.position)
    }

    private fun readRow(stmt: PreparedStatement): TaskCategoryData {
        return TaskCategoryData(
            id = stmt.getLong(0),
            name = stmt.getText(1),
            color = stmt.getInt(2),
            position = stmt.getInt(3)
        )
    }
}
