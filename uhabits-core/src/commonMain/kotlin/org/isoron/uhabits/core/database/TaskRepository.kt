package org.isoron.uhabits.core.database

import org.isoron.platform.io.Database
import org.isoron.platform.io.PreparedStatement
import org.isoron.platform.io.StepResult
import org.isoron.platform.io.queryLong

data class TaskData(
    var id: Long? = null,
    var title: String = "",
    var description: String = "",
    var categoryId: Long? = null,
    var dueDate: Long? = null,
    var reminderTime: Long? = null,
    var completed: Int = 0,
    var position: Int = 0
)

class TaskRepository(private val db: Database) {
    private val findAllStmt by lazy {
        db.prepareStatement(
            """SELECT id, title, description, category_id, due_date, reminder_time, completed, position
               FROM Tasks ORDER BY position"""
        )
    }

    private val findByCategoryStmt by lazy {
        db.prepareStatement(
            """SELECT id, title, description, category_id, due_date, reminder_time, completed, position
               FROM Tasks WHERE category_id IS ? OR category_id = ? ORDER BY position"""
        )
    }

    private val findUpcomingStmt by lazy {
        db.prepareStatement(
            """SELECT id, title, description, category_id, due_date, reminder_time, completed, position
               FROM Tasks WHERE completed = 0 AND due_date IS NOT NULL AND due_date >= ? AND due_date <= ? ORDER BY due_date"""
        )
    }

    private val findOverdueStmt by lazy {
        db.prepareStatement(
            """SELECT id, title, description, category_id, due_date, reminder_time, completed, position
               FROM Tasks WHERE completed = 0 AND due_date IS NOT NULL AND due_date < ? ORDER BY due_date"""
        )
    }

    private val insertStmt by lazy {
        db.prepareStatement(
            """INSERT INTO Tasks(title, description, category_id, due_date, reminder_time, completed, position)
               VALUES (?, ?, ?, ?, ?, ?, ?)"""
        )
    }

    private val insertWithIdStmt by lazy {
        db.prepareStatement(
            """INSERT INTO Tasks(id, title, description, category_id, due_date, reminder_time, completed, position)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)"""
        )
    }

    private val updateStmt by lazy {
        db.prepareStatement(
            """UPDATE Tasks SET title=?, description=?, category_id=?, due_date=?, reminder_time=?, completed=?, position=? WHERE id=?"""
        )
    }

    private val deleteStmt by lazy {
        db.prepareStatement("DELETE FROM Tasks WHERE id = ?")
    }

    fun findAll(): List<TaskData> {
        findAllStmt.reset()
        val results = mutableListOf<TaskData>()
        while (findAllStmt.step() == StepResult.ROW) {
            results.add(readRow(findAllStmt))
        }
        return results
    }

    fun findByCategory(categoryId: Long?): List<TaskData> {
        findByCategoryStmt.reset()
        if (categoryId == null) {
            findByCategoryStmt.bindNull(1)
            findByCategoryStmt.bindNull(2)
        } else {
            findByCategoryStmt.bindLong(1, categoryId)
            findByCategoryStmt.bindLong(2, categoryId)
        }
        val results = mutableListOf<TaskData>()
        while (findByCategoryStmt.step() == StepResult.ROW) {
            results.add(readRow(findByCategoryStmt))
        }
        return results
    }

    fun findUpcoming(from: Long, to: Long): List<TaskData> {
        findUpcomingStmt.reset()
        findUpcomingStmt.bindLong(1, from)
        findUpcomingStmt.bindLong(2, to)
        val results = mutableListOf<TaskData>()
        while (findUpcomingStmt.step() == StepResult.ROW) {
            results.add(readRow(findUpcomingStmt))
        }
        return results
    }

    fun findOverdue(before: Long): List<TaskData> {
        findOverdueStmt.reset()
        findOverdueStmt.bindLong(1, before)
        val results = mutableListOf<TaskData>()
        while (findOverdueStmt.step() == StepResult.ROW) {
            results.add(readRow(findOverdueStmt))
        }
        return results
    }

    fun insert(data: TaskData): Long {
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

    fun update(data: TaskData) {
        updateStmt.reset()
        bindForInsert(updateStmt, data)
        updateStmt.bindLong(8, data.id!!)
        updateStmt.step()
    }

    fun delete(id: Long) {
        deleteStmt.reset()
        deleteStmt.bindLong(1, id)
        deleteStmt.step()
    }

    private fun bindForInsert(stmt: PreparedStatement, data: TaskData, offset: Int = 0) {
        val o = offset
        stmt.bindText(1 + o, data.title)
        stmt.bindText(2 + o, data.description)
        if (data.categoryId != null) stmt.bindLong(3 + o, data.categoryId!!) else stmt.bindNull(3 + o)
        if (data.dueDate != null) stmt.bindLong(4 + o, data.dueDate!!) else stmt.bindNull(4 + o)
        if (data.reminderTime != null) stmt.bindLong(5 + o, data.reminderTime!!) else stmt.bindNull(5 + o)
        stmt.bindInt(6 + o, data.completed)
        stmt.bindInt(7 + o, data.position)
    }

    private fun readRow(stmt: PreparedStatement): TaskData {
        return TaskData(
            id = stmt.getLong(0),
            title = stmt.getText(1),
            description = stmt.getText(2),
            categoryId = stmt.getLongOrNull(3),
            dueDate = stmt.getLongOrNull(4),
            reminderTime = stmt.getLongOrNull(5),
            completed = stmt.getInt(6),
            position = stmt.getInt(7)
        )
    }
}
