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
import androidx.recyclerview.widget.RecyclerView
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

    private var hideCompleted: Boolean = false
    private var showAllTasks: Boolean = false
    private var showUpcomingTasks: Boolean = true
    
    private var currentDayOffset: Int = 0
    private lateinit var gestureDetector: android.view.GestureDetector

    private var actionMode: androidx.appcompat.view.ActionMode? = null
    private val selectedTaskIds = mutableSetOf<Long>()

    private val actionModeCallback = object : androidx.appcompat.view.ActionMode.Callback {
        override fun onCreateActionMode(mode: androidx.appcompat.view.ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.task_list_cab, menu)
            return true
        }

        override fun onPrepareActionMode(mode: androidx.appcompat.view.ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: androidx.appcompat.view.ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.actionDelete -> {
                    selectedTaskIds.forEach { id ->
                        taskList.getAll().find { it.id == id }?.let { taskList.remove(it) }
                    }
                    mode.finish()
                    refreshTasks()
                    true
                }
                R.id.actionComplete -> {
                    selectedTaskIds.forEach { id ->
                        taskList.getAll().find { it.id == id }?.let { task -> 
                            task.isCompleted = true
                            taskList.update(task)
                            // Generate next occurrence for recurring tasks
                            if (task.recurrenceType > 0 && task.dueDate != null) {
                                val cal = java.util.Calendar.getInstance().apply { timeInMillis = task.dueDate!! }
                                when (task.recurrenceType) {
                                    1 -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1) // Daily
                                    2 -> {
                                        val bitmask = task.recurrenceValue
                                        if (bitmask > 0) {
                                            var daysAdded = 0
                                            do {
                                                cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                                daysAdded++
                                                val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
                                            } while ((bitmask and (1 shl dow)) == 0 && daysAdded < 7)
                                        } else {
                                            cal.add(java.util.Calendar.DAY_OF_YEAR, 7) // Fallback
                                        }
                                    }
                                    3 -> cal.add(java.util.Calendar.MONTH, 1) // Monthly
                                    4 -> cal.add(java.util.Calendar.YEAR, 1) // Yearly
                                }
                                
                                val nextDueDate = cal.timeInMillis
                                val nextTask = Task(
                                    title = task.title,
                                    description = task.description,
                                    categoryId = task.categoryId,
                                    dueDate = nextDueDate,
                                    reminderTime = if (task.reminderTime != null) {
                                        val rCal = java.util.Calendar.getInstance().apply { timeInMillis = task.reminderTime!! }
                                        val daysDiff = ((nextDueDate - task.dueDate!!) / (24 * 60 * 60 * 1000)).toInt()
                                        rCal.add(java.util.Calendar.DAY_OF_YEAR, daysDiff)
                                        rCal.timeInMillis
                                    } else null,
                                    recurrenceType = task.recurrenceType,
                                    recurrenceValue = task.recurrenceValue
                                )
                                taskList.add(nextTask)
                            }
                        }
                    }
                    mode.finish()
                    refreshTasks()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: androidx.appcompat.view.ActionMode) {
            actionMode = null
            selectedTaskIds.clear()
            adapter.selectedTaskIds = selectedTaskIds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val component = (application as HabitsApplication).component
        taskList = component.taskList
        taskCategoryList = component.taskCategoryList
        themeSwitcher = AndroidThemeSwitcher(this, component.preferences)
        themeSwitcher.apply()

        val prefs = getPreferences(android.content.Context.MODE_PRIVATE)
        hideCompleted = prefs.getBoolean("hideCompleted", false)
        showAllTasks = prefs.getBoolean("showAllTasks", false)
        showUpcomingTasks = prefs.getBoolean("showUpcomingTasks", true)

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

        gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val isSingleDayView = !showAllTasks && !showUpcomingTasks
                if (!isSingleDayView) return false

                if (e1 != null) {
                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) {
                                // Swipe right - previous day
                                currentDayOffset--
                                refreshTasks()
                            } else {
                                // Swipe left - next day
                                currentDayOffset++
                                refreshTasks()
                            }
                            return true
                        }
                    }
                }
                return false
            }
        })

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TaskAdapter(
            tasks = emptyList(),
            categories = emptyList(),
            theme = themeSwitcher.currentTheme,
            onTaskClick = { task ->
                if (actionMode != null) {
                    task.id?.let { toggleSelection(it) }
                } else {
                    showEditTaskDialog(task)
                }
            },
            onTaskComplete = { task, isChecked ->
                task.isCompleted = isChecked
                taskList.update(task)
                refreshTasks()
            },
            onTaskLongClick = { task ->
                if (actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback)
                }
                task.id?.let { toggleSelection(it) }
            }
        )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                return false
            }
        })
        binding.recyclerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTasks()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.task_list, menu)
        menu?.findItem(R.id.actionTaskHideCompleted)?.isChecked = hideCompleted
        menu?.findItem(R.id.actionTaskShowAll)?.isChecked = showAllTasks
        menu?.findItem(R.id.actionTaskShowUpcoming)?.isChecked = showUpcomingTasks
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val prefs = getPreferences(android.content.Context.MODE_PRIVATE)
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.actionTaskHideCompleted -> {
                hideCompleted = !hideCompleted
                prefs.edit().putBoolean("hideCompleted", hideCompleted).apply()
                invalidateOptionsMenu()
                refreshTasks()
                true
            }
            R.id.actionTaskShowAll -> {
                showAllTasks = !showAllTasks
                prefs.edit().putBoolean("showAllTasks", showAllTasks).apply()
                invalidateOptionsMenu()
                refreshTasks()
                true
            }
            R.id.actionTaskShowUpcoming -> {
                showUpcomingTasks = !showUpcomingTasks
                prefs.edit().putBoolean("showUpcomingTasks", showUpcomingTasks).apply()
                invalidateOptionsMenu()
                refreshTasks()
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

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_YEAR, currentDayOffset)
        val currentViewDayStart = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val currentViewDayEnd = calendar.timeInMillis
        
        calendar.timeInMillis = todayStart
        calendar.add(Calendar.DAY_OF_YEAR, 7)
        val sevenDaysFromNow = calendar.timeInMillis

        val isSingleDayView = !showAllTasks && !showUpcomingTasks

        val displayTasks = if (!limitTo7Days) {
            // Calendar jump - show everything
            sortedTasks
        } else {
            sortedTasks.filter { task ->
                if (hideCompleted && task.isCompleted) return@filter false
                if (task.dueDate == null) return@filter true // Keep tasks without due date
                
                if (showAllTasks) return@filter true
                
                if (isSingleDayView) {
                    if (task.dueDate!! < todayStart && currentDayOffset == 0) {
                        // Show overdue tasks ONLY on the 'today' view
                        return@filter true
                    }
                    task.dueDate!! >= currentViewDayStart && task.dueDate!! < currentViewDayEnd
                } else {
                    if (task.dueDate!! < todayStart) return@filter true // Overdue tasks always shown
                    task.dueDate!! < sevenDaysFromNow
                }
            }
        }

        if (isSingleDayView) {
            val dateFormat = java.text.SimpleDateFormat("EEEE, MMM dd", java.util.Locale.getDefault())
            val dateString = when (currentDayOffset) {
                0 -> getString(R.string.today)
                1 -> getString(R.string.tomorrow)
                -1 -> getString(R.string.yesterday)
                else -> dateFormat.format(java.util.Date(currentViewDayStart))
            }
            supportActionBar?.title = dateString
        } else {
            supportActionBar?.title = getString(R.string.tasks)
        }

        adapter = TaskAdapter(
            tasks = displayTasks,
            categories = categories,
            theme = themeSwitcher.currentTheme,
            onTaskClick = { task ->
                if (actionMode != null) {
                    task.id?.let { toggleSelection(it) }
                } else {
                    showEditTaskDialog(task)
                }
            },
            onTaskComplete = { task, isChecked ->
                task.isCompleted = isChecked
                taskList.update(task)
                // If completing a recurring task, create the next occurrence
                if (isChecked && task.recurrenceType > 0 && task.dueDate != null) {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = task.dueDate!! }
                    when (task.recurrenceType) {
                        1 -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1) // Daily
                        2 -> {
                            // Weekly: find next day in bitmask
                            val bitmask = task.recurrenceValue
                            if (bitmask > 0) {
                                var daysAdded = 0
                                do {
                                    cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                    daysAdded++
                                    val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
                                } while ((bitmask and (1 shl dow)) == 0 && daysAdded < 7)
                            } else {
                                cal.add(java.util.Calendar.DAY_OF_YEAR, 7) // Fallback
                            }
                        }
                        3 -> cal.add(java.util.Calendar.MONTH, 1) // Monthly
                        4 -> cal.add(java.util.Calendar.YEAR, 1) // Yearly
                    }
                    
                    val nextDueDate = cal.timeInMillis
                    val nextTask = Task(
                        title = task.title,
                        description = task.description,
                        categoryId = task.categoryId,
                        dueDate = nextDueDate,
                        reminderTime = if (task.reminderTime != null) {
                            val rCal = java.util.Calendar.getInstance().apply { timeInMillis = task.reminderTime!! }
                            val daysDiff = ((nextDueDate - task.dueDate!!) / (24 * 60 * 60 * 1000)).toInt()
                            rCal.add(java.util.Calendar.DAY_OF_YEAR, daysDiff)
                            rCal.timeInMillis
                        } else null,
                        recurrenceType = task.recurrenceType,
                        recurrenceValue = task.recurrenceValue
                    )
                    taskList.add(nextTask)
                }
                refreshTasks()
            },
            onTaskLongClick = { task ->
                if (actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback)
                }
                task.id?.let { toggleSelection(it) }
            }
        )
        adapter.selectedTaskIds = selectedTaskIds
        binding.recyclerView.adapter = adapter

        val isEmpty = sortedTasks.isEmpty()
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun toggleSelection(taskId: Long) {
        if (selectedTaskIds.contains(taskId)) {
            selectedTaskIds.remove(taskId)
        } else {
            selectedTaskIds.add(taskId)
        }
        
        if (selectedTaskIds.isEmpty()) {
            actionMode?.finish()
        } else {
            actionMode?.title = "${selectedTaskIds.size} selected"
            adapter.selectedTaskIds = selectedTaskIds
        }
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

        val recurrenceOptions = listOf("None" to 0, "Daily" to 1, "Weekly" to 2, "Monthly" to 3, "Yearly" to 4)
        var selectedRecurrenceType = existingTask?.recurrenceType ?: 0
        var selectedRecurrenceValue = existingTask?.recurrenceValue ?: 0

        fun updateRecurrenceLabel() {
            if (selectedRecurrenceType == 2 && selectedRecurrenceValue > 0) {
                val days = mutableListOf<String>()
                val weekDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                for (i in 0..6) {
                    if ((selectedRecurrenceValue and (1 shl (i + 1))) != 0) {
                        days.add(weekDays[i])
                    }
                }
                dialogBinding.taskRecurrenceInput.text = "Weekly (${days.joinToString(", ")})"
            } else {
                val recurrenceLabel = recurrenceOptions.find { it.second == selectedRecurrenceType }?.first 
                    ?: "None"
                dialogBinding.taskRecurrenceInput.text = recurrenceLabel
            }
        }
        updateRecurrenceLabel()

        dialogBinding.taskRecurrenceInput.setOnClickListener {
            val names = recurrenceOptions.map { it.first }.toTypedArray()
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.repeat_every_days))
                .setItems(names) { dialog, which ->
                    selectedRecurrenceType = recurrenceOptions[which].second
                    if (selectedRecurrenceType == 2) {
                        // Show days of week picker
                        val weekDays = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
                        val checkedItems = BooleanArray(7) { i -> (selectedRecurrenceValue and (1 shl (i + 1))) != 0 }
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Select Days")
                            .setMultiChoiceItems(weekDays, checkedItems) { _, whichDay, isChecked ->
                                checkedItems[whichDay] = isChecked
                            }
                            .setPositiveButton(android.R.string.ok) { d, _ ->
                                var newValue = 0
                                for (i in 0..6) {
                                    if (checkedItems[i]) newValue = newValue or (1 shl (i + 1))
                                }
                                selectedRecurrenceValue = newValue
                                updateRecurrenceLabel()
                                d.dismiss()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    } else {
                        selectedRecurrenceValue = 0
                        updateRecurrenceLabel()
                    }
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

                if (existingTask == null) {
                    val titles = title.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    titles.forEach { t ->
                        val task = Task(
                            title = t,
                            description = dialogBinding.taskDescriptionInput.text.toString().trim(),
                            categoryId = categoryId,
                            dueDate = selectedDueDate,
                            reminderTime = selectedReminderTime,
                            recurrenceType = selectedRecurrenceType,
                            recurrenceValue = selectedRecurrenceValue
                        )
                        taskList.add(task)
                    }
                } else {
                    existingTask.title = title
                    existingTask.description = dialogBinding.taskDescriptionInput.text.toString().trim()
                    existingTask.categoryId = categoryId
                    existingTask.dueDate = selectedDueDate
                    existingTask.reminderTime = selectedReminderTime
                    existingTask.recurrenceType = selectedRecurrenceType
                    existingTask.recurrenceValue = selectedRecurrenceValue
                    taskList.update(existingTask)
                }
                dialog.dismiss()
                refreshTasks()
            }
        }
        dialog.show()
    }
}