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
package org.isoron.uhabits.activities.tasks.list

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.isoron.platform.gui.toInt
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.common.views.RingView
import org.isoron.uhabits.core.models.Task
import org.isoron.uhabits.core.models.TaskCategory
import org.isoron.uhabits.core.ui.views.Theme
import org.isoron.uhabits.utils.InterfaceUtils
import org.isoron.uhabits.utils.dp
import org.isoron.uhabits.utils.sp
import org.isoron.uhabits.utils.sres
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom view for displaying a single task in the task list,
 * visually matching the HabitCardView style.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class TaskCardView(
    context: Context,
    private val theme: Theme
) : FrameLayout(context) {

    var task: Task? = null
        private set

    var category: TaskCategory? = null
        private set

    var onToggle: (Task) -> Unit = {}
    var onClick: (Task) -> Unit = {}
    var onLongClick: (Task) -> Unit = {}

    private val scoreRing: RingView
    private val label: TextView
    private val infoPanel: LinearLayout
    private val dueDateText: TextView
    private val descriptionText: TextView
    private val checkmarkView: TaskCheckmarkView
    private val innerFrame: LinearLayout

    init {
        scoreRing = RingView(context).apply {
            val thickness = dp(3f)
            val margin = dp(8f).toInt()
            val ringSize = dp(15f).toInt()
            layoutParams = LinearLayout.LayoutParams(ringSize, ringSize).apply {
                setMargins(margin, 0, margin, 0)
                gravity = Gravity.CENTER
            }
            setThickness(thickness)
            setPercentage(1.0f)
            setPrecision(1.0f / 16)
        }

        label = TextView(context).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            textSize = 17f
        }

        dueDateText = TextView(context).apply {
            maxLines = 1
            textSize = 12f
            alpha = 0.7f
        }

        descriptionText = TextView(context).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            textSize = 12f
            alpha = 0.5f
        }

        infoPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(dp(4f).toInt(), 0, dp(4f).toInt(), 0)
            }
            addView(dueDateText)
            addView(descriptionText)
        }

        checkmarkView = TaskCheckmarkView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(40f).toInt(),
                dp(40f).toInt()
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        innerFrame = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            elevation = dp(1f)

            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(2f)
                setColor(theme.cardBackgroundColor.toInt())
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val typedArray = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                foreground = context.getDrawable(typedArray.getResourceId(0, 0))
                typedArray.recycle()
            }

            addView(scoreRing)
            addView(label)
            addView(infoPanel)
            addView(checkmarkView)
        }

        clipToPadding = false
        layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        val margin = dp(4f).toInt()
        setPadding(margin, margin, margin, margin)
        addView(innerFrame)
    }

    fun bind(task: Task, category: TaskCategory?) {
        this.task = task
        this.category = category

        val accentColor = if (category != null) {
            theme.color(category.color).toInt()
        } else {
            sres.getColor(R.attr.contrast60)
        }

        // Title
        label.text = task.title
        if (task.isCompleted) {
            label.paintFlags = label.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            label.setTextColor(sres.getColor(R.attr.contrast60))
        } else {
            label.paintFlags = label.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            label.setTextColor(accentColor)
        }

        // Score ring
        scoreRing.setColor(accentColor)
        scoreRing.setPercentage(if (task.isCompleted) 1.0f else 0.25f)

        // Due date
        if (task.dueDate != null) {
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            dueDateText.text = sdf.format(Date(task.dueDate!!))
            dueDateText.visibility = View.VISIBLE

            // Highlight overdue in a faded state
            val now = System.currentTimeMillis()
            if (!task.isCompleted && task.dueDate!! < now) {
                dueDateText.alpha = 0.5f
            } else {
                dueDateText.alpha = 0.7f
            }
        } else {
            dueDateText.visibility = View.GONE
        }

        // Description
        if (task.description.isNotBlank()) {
            descriptionText.text = task.description
            descriptionText.visibility = View.VISIBLE
        } else {
            descriptionText.visibility = View.GONE
        }

        // Checkmark
        checkmarkView.color = accentColor
        checkmarkView.isChecked = task.isCompleted
        checkmarkView.setOnClickListener {
            checkmarkView.isChecked = !checkmarkView.isChecked
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onToggle(task)
        }

        // Card click
        innerFrame.setOnClickListener {
            onClick(task)
        }

        innerFrame.setOnLongClickListener {
            onLongClick(task)
            true
        }
    }
}

/**
 * Custom view that draws a FontAwesome checkmark matching the habit
 * CheckmarkButtonView style. Shows a filled check (✓) when done,
 * or a subtle circle outline when not done.
 */
class TaskCheckmarkView(context: Context) : View(context) {

    var color: Int = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    var isChecked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val paint = TextPaint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = InterfaceUtils.getFontAwesome(context)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (isChecked) {
            paint.color = color
            paint.textSize = sp(14.0f)
            val icon = context.getString(R.string.fa_check)
            val em = paint.measureText("m")
            canvas.drawText(icon, w / 2f, h / 2f + 0.4f * em, paint)
        } else {
            val lowContrastColor = sres.getColor(R.attr.contrast40)
            paint.color = lowContrastColor
            paint.textSize = sp(14.0f)
            val icon = context.getString(R.string.fa_times)
            val em = paint.measureText("m")
            canvas.drawText(icon, w / 2f, h / 2f + 0.4f * em, paint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = resources.getDimensionPixelSize(R.dimen.checkmarkHeight)
        val width = resources.getDimensionPixelSize(R.dimen.checkmarkWidth)
        setMeasuredDimension(width, height)
    }
}
