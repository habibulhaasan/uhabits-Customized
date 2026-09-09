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
package org.isoron.uhabits.activities.tasks.list

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.Task
import org.isoron.uhabits.databinding.ActivityTaskListBinding
import org.isoron.uhabits.utils.applyRootViewInsets
import org.isoron.uhabits.utils.setupToolbar
import java.util.*

class TaskListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskListBinding
    private lateinit var taskList: org.isoron.uhabits.core.models.TaskList
    private lateinit var taskCategoryList: org.isoron.uhabits.core.models.TaskCategoryList
    private lateinit var adapter: TaskAdapter
    private lateinit var themeSwitcher: AndroidThemeSwitcher

    private var limitTo7Days = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val component = (application as HabitsApplication).component
        taskList = component.taskList
        taskCategoryList = component.taskCategoryList
        themeSwitcher = AndroidThemeSwitcher(this, component.preferences)
        themeSwitcher.apply()

        binding = ActivityTaskListBinding.inflate(layoutInflater)
        binding.root.setupToolbar(
            toolbar = binding.toolbar,
            title = getString(R.string.tasks),
            color = PaletteColor(11),
            theme = themeSwitcher.currentTheme,
            displayHomeAsUpEnabled = false
        )
        binding.root.applyRootViewInsets()
        setContentView(binding.root)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TaskAdapter(
            tasks = emptyList(),
            categories = emptyList(),
            theme = themeSwitcher.currentTheme,
            onTaskClick = { showEditTaskDialog(it) },
            onTaskComplete = { task, isChecked ->
                task.isCompleted = isChecked
                taskList.update(task)
                refreshTasks()
            },
            onTaskDelete = { task -> showDeleteConfirm(task) }
        )
        binding.recyclerView.adapter = adapter

    }

    override fun onResume() {
        super.onResume()
        refreshTasks()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.task_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.actionAddTask -> {
                showAddTaskDialog()
                true
            }
            R.id.actionJumpToDate -> {
                showCalendarJump()
                true
            }
            R.id.actionManageTaskCategories -> {
                startActivity(
                    android.content.Intent(
                        this,
                        org.isoron.uhabits.activities.tasks.categories.TaskCategoryListActivity::class.java
                    )
                )
                true
            }
            R.id.actionSwitchToHabits -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refreshTasks() {
        taskCategoryList.reload()
        taskList.reload()
        val categories = taskCategoryList.getAll()
        val allTasks = taskList.getAll()

        val now = System.currentTimeMillis()
        val sortedTasks = allTasks.sortedWith(
            compareBy<Task> { it.isCompleted }
                .thenBy { task ->
                    if (!task.isCompleted) {
                        task.dueDate ?: Long.MAX_VALUE
                    } else {
                        task.position.toLong()
                    }
                }
        )

        val sevenDaysFromNow = now + 7L * 24 * 60 * 60 * 1000
        val displayTasks = if (limitTo7Days) {
            sortedTasks.filter { task ->
                if (task.isCompleted) true
                else if (task.dueDate == null) true
                else task.dueDate!! <= sevenDaysFromNow
            }
        } else {
            sortedTasks
        }

        adapter = TaskAdapter(
            tasks = displayTasks,
            categories = categories,
            theme = themeSwitcher.currentTheme,
            onTaskClick = { showEditTaskDialog(it) },
            onTaskComplete = { task, isChecked ->
                task.isCompleted = isChecked
                taskList.update(task)
                // If completing a recurring task, create the next occurrence
                if (isChecked && task.recurrenceDays > 0 && task.dueDate != null) {
                    val nextDueDate = task.dueDate!! + task.recurrenceDays.toLong() * 24 * 60 * 60 * 1000
                    val nextTask = Task(
                        title = task.title,
                        description = task.description,
                        categoryId = task.categoryId,
                        dueDate = nextDueDate,
                        reminderTime = if (task.reminderTime != null) {
                            task.reminderTime!! + task.recurrenceDays.toLong() * 24 * 60 * 60 * 1000
                        } else null,
                        recurrenceDays = task.recurrenceDays
                    )
                    taskList.add(nextTask)
                }
                refreshTasks()
            },
            onTaskDelete = { task -> showDeleteConfirm(task) }
        )
        binding.recyclerView.adapter = adapter

        val isEmpty = sortedTasks.isEmpty()
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showDeleteConfirm(task: Task) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_task))
            .setMessage(getString(R.string.delete_task_confirmation))
            .setPositiveButton(R.string.delete) { _, _ ->
                taskList.remove(task)
                refreshTasks()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddTaskDialog() { showTaskDialog(null) }
    private fun showEditTaskDialog(task: Task) { showTaskDialog(task) }

    private fun showCalendarJump() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        android.app.DatePickerDialog(this, { _, y, m, d ->
            val cal = Calendar.getInstance()
            cal.set(y, m, d, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val selectedStart = cal.timeInMillis
            
            limitTo7Days = false
            refreshTasks()
            
            val targetPosition = adapter.findDatePosition(selectedStart)
            if (targetPosition >= 0) {
                binding.recyclerView.smoothScrollToPosition(targetPosition)
            } else {
                android.widget.Toast.makeText(this, getString(R.string.no_tasks_yet), android.widget.Toast.LENGTH_SHORT).show()
            }
        }, year, month, day).show()
    }

    private fun showTaskDialog(existingTask: Task?) {
        val dialogBinding = org.isoron.uhabits.databinding.DialogTaskEditBinding.inflate(layoutInflater)
        val categories = taskCategoryList.getAll()

        var selectedDueDate: Long? = existingTask?.dueDate
        var selectedReminderTime: Long? = existingTask?.reminderTime

        existingTask?.let {
            dialogBinding.taskTitleInput.setText(it.title)
            dialogBinding.taskDescriptionInput.setText(it.description)
            if (it.recurrenceDays > 0) {
                dialogBinding.taskRecurrenceInput.setText(it.recurrenceDays.toString())
            }
            it.dueDate?.let { date ->
                dialogBinding.taskDueDateInput.setText(
                    android.text.format.DateFormat.getDateFormat(this).format(java.util.Date(date))
                )
            }
            it.reminderTime?.let { time ->
                dialogBinding.taskReminderInput.setText(
                    android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(time))
                )
            }
        }

        val categoryNames = mutableListOf<String>()
        categoryNames.add(getString(R.string.uncategorized))
        categories.forEach { categoryNames.add(it.name) }

        var selectedCategoryIndex = 0
        existingTask?.categoryId?.let { catId ->
            categories.indexOfFirst { it.id == catId }.takeIf { it >= 0 }?.let { idx ->
                selectedCategoryIndex = idx + 1
            }
        }

        val categoryAdapter = android.widget.ArrayAdapter(this, android.R.layout.select_dialog_item, categoryNames)

        dialogBinding.taskCategoryInput.setText(categoryNames[selectedCategoryIndex])
        dialogBinding.taskCategoryInput.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.task_category))
                .setAdapter(categoryAdapter) { dialog, which ->
                    selectedCategoryIndex = which
                    dialogBinding.taskCategoryInput.setText(categoryNames[which])
                    dialog.dismiss()
                }
                .show()
        }

        dialogBinding.taskDueDateInput.setOnClickListener {
            val calendar = Calendar.getInstance()
            // Use existing due date if editing, otherwise default to today
            existingTask?.dueDate?.let { calendar.timeInMillis = it }
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            DatePickerDialog(this, { _, y, m, d ->
                val cal = Calendar.getInstance()
                cal.set(y, m, d, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                selectedDueDate = cal.timeInMillis
                dialogBinding.taskDueDateInput.setText(
                    android.text.format.DateFormat.getDateFormat(this).format(cal.time)
                )
            }, year, month, day).show()
        }

        dialogBinding.taskReminderInput.setOnClickListener {
            val calendar = Calendar.getInstance()
            existingTask?.reminderTime?.let { calendar.timeInMillis = it }
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            TimePickerDialog(this, { _, h, m ->
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, m)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                selectedReminderTime = cal.timeInMillis
                dialogBinding.taskReminderInput.setText(
                    android.text.format.DateFormat.getTimeFormat(this).format(cal.time)
                )
            }, hour, minute, true).show()
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (existingTask == null) getString(R.string.new_task) else getString(R.string.edit_task))
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = dialogBinding.taskTitleInput.text.toString().trim()
                if (title.isEmpty()) {
                    dialogBinding.taskTitleInput.error = getString(R.string.validation_cannot_be_blank)
                    return@setOnClickListener
                }

                val categoryId = if (selectedCategoryIndex == 0) null else categories[selectedCategoryIndex - 1].id
                val recurrenceDays = dialogBinding.taskRecurrenceInput.text.toString().trim().toIntOrNull() ?: 0

                if (existingTask == null) {
                    val task = Task(
                        title = title,
                        description = dialogBinding.taskDescriptionInput.text.toString().trim(),
                        categoryId = categoryId,
                        dueDate = selectedDueDate,
                        reminderTime = selectedReminderTime,
                        recurrenceDays = recurrenceDays
                    )
                    taskList.add(task)
                } else {
                    existingTask.title = title
                    existingTask.description = dialogBinding.taskDescriptionInput.text.toString().trim()
                    existingTask.categoryId = categoryId
                    existingTask.dueDate = selectedDueDate
                    existingTask.reminderTime = selectedReminderTime
                    existingTask.recurrenceDays = recurrenceDays
                    taskList.update(existingTask)
                }
                dialog.dismiss()
                refreshTasks()
            }
        }
        dialog.show()
    }
}