package org.isoron.uhabits.activities.tasks.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.Task
import org.isoron.uhabits.core.models.TaskCategory
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.ui.views.Theme
import org.isoron.platform.gui.toInt
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class TaskListItem {
    data class DateHeader(val dateString: String, val isUpcoming: Boolean) : TaskListItem()
    data class CategoryHeader(val category: TaskCategory?) : TaskListItem()
    data class Item(val task: Task, val category: TaskCategory?, val isUpcoming: Boolean) : TaskListItem()
}

class TaskAdapter(
    private var tasks: List<Task>,
    private val categories: List<TaskCategory>,
    private val theme: Theme,
    private val onTaskClick: (Task) -> Unit,
    private val onTaskComplete: (Task, Boolean) -> Unit,
    private val onTaskLongClick: (Task) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var selectedTaskIds: Set<Long> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private var items: List<TaskListItem> = emptyList()

    init {
        setHasStableIds(false)
        updateItems()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TaskListItem.DateHeader -> VIEW_TYPE_DATE_HEADER
            is TaskListItem.CategoryHeader -> VIEW_TYPE_CATEGORY_HEADER
            is TaskListItem.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_task_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            VIEW_TYPE_CATEGORY_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_task_category_header, parent, false)
                CategoryHeaderViewHolder(view)
            }
            else -> {
                val view = TaskCardView(parent.context, theme)
                TaskCardViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is TaskListItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item)
            is TaskListItem.CategoryHeader -> (holder as CategoryHeaderViewHolder).bind(item)
            is TaskListItem.Item -> (holder as TaskCardViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun findDatePosition(targetDateMs: Long): Int {
        val dateFormat = SimpleDateFormat("EEE, MMM dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowStart = calendar.timeInMillis

        val targetString = when (targetDateMs) {
            todayStart -> "Today"
            tomorrowStart -> "Tomorrow"
            else -> dateFormat.format(Date(targetDateMs))
        }

        return items.indexOfFirst { it is TaskListItem.DateHeader && it.dateString == targetString }
    }

    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        updateItems()
        notifyDataSetChanged()
    }

    private fun updateItems() {
        val newItems = mutableListOf<TaskListItem>()
        
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()
        
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowStart = calendar.timeInMillis

        val groupedByDate = tasks.groupBy { task ->
            if (task.dueDate == null) {
                Long.MAX_VALUE 
            } else {
                calendar.timeInMillis = task.dueDate!!
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
        }.toSortedMap()

        val dateFormat = SimpleDateFormat("EEE, MMM dd", Locale.getDefault())

        for ((dateMs, tasksForDate) in groupedByDate) {
            val isUpcoming = dateMs >= tomorrowStart && dateMs != Long.MAX_VALUE

            val dateString = when (dateMs) {
                Long.MAX_VALUE -> "No Due Date"
                todayStart -> "Today"
                tomorrowStart -> "Tomorrow"
                else -> dateFormat.format(Date(dateMs))
            }

            newItems.add(TaskListItem.DateHeader(dateString, isUpcoming))

            val groupedByCategory = tasksForDate.groupBy { it.categoryId }
            
            val sortedCatIds = groupedByCategory.keys.sortedBy { catId ->
                if (catId == null) -1 else categories.indexOfFirst { it.id == catId }.takeIf { it >= 0 } ?: 9999
            }

            for (catId in sortedCatIds) {
                val catTasks = groupedByCategory[catId] ?: continue
                val category = categories.find { it.id == catId }
                
                newItems.add(TaskListItem.CategoryHeader(category))

                for (task in catTasks) {
                    newItems.add(TaskListItem.Item(task, category, isUpcoming))
                }
            }
        }

        items = newItems
    }

    inner class DateHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.dateHeaderTitle)
        fun bind(item: TaskListItem.DateHeader) {
            titleView.text = item.dateString
            titleView.alpha = if (item.isUpcoming) 0.4f else 1.0f
        }
    }

    inner class CategoryHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.categoryHeaderName)
        private val countView: TextView = view.findViewById(R.id.categoryHeaderCount)
        private val colorBar: View = view.findViewById(R.id.categoryHeaderColorBar)
        
        fun bind(item: TaskListItem.CategoryHeader) {
            val cat = item.category
            if (cat != null) {
                titleView.text = cat.name
                colorBar.setBackgroundColor(theme.color(cat.color).toInt())
            } else {
                titleView.text = titleView.context.getString(R.string.uncategorized)
                colorBar.setBackgroundColor(theme.mediumContrastTextColor.toInt())
            }
            countView.visibility = View.GONE
        }
    }

    inner class TaskCardViewHolder(
        private val cardView: TaskCardView
    ) : RecyclerView.ViewHolder(cardView) {

        fun bind(item: TaskListItem.Item) {
            cardView.onToggle = { t ->
                val newState = !t.isCompleted
                onTaskComplete(t, newState)
            }
            cardView.onClick = { t ->
                onTaskClick(t)
            }
            cardView.onLongClick = { t ->
                onTaskLongClick(t)
            }
            cardView.bind(item.task, item.category)
            cardView.alpha = if (item.isUpcoming) 0.4f else 1.0f
            
            val isSelected = selectedTaskIds.contains(item.task.id)
            if (isSelected) {
                cardView.background = android.graphics.drawable.ColorDrawable(theme.color(PaletteColor(3)).toInt())
            } else {
                cardView.background = null
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_DATE_HEADER = 0
        private const val VIEW_TYPE_CATEGORY_HEADER = 1
        private const val VIEW_TYPE_ITEM = 2
    }
}
