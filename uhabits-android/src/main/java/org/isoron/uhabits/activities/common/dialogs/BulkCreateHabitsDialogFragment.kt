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

package org.isoron.uhabits.activities.common.dialogs

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.isoron.platform.gui.toInt
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.activities.common.dialogs.ColorPickerDialogFactory
import org.isoron.uhabits.core.commands.CreateHabitCommand
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.databinding.BulkCreateHabitsDialogBinding
import org.isoron.uhabits.utils.dismissCurrentAndShow

/**
 * Lets the user create several habits at once, typing one name per line,
 * instead of going through EditHabitActivity repeatedly.
 */
class BulkCreateHabitsDialogFragment : DialogFragment() {

    private var color = PaletteColor(8)
    private lateinit var binding: BulkCreateHabitsDialogBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val app = requireActivity().application as HabitsApplication
        val component = app.component
        val themeSwitcher = AndroidThemeSwitcher(requireActivity(), component.preferences)

        binding = BulkCreateHabitsDialogBinding.inflate(layoutInflater)
        updateColorSwatch(themeSwitcher)

        binding.colorButton.setOnClickListener {
            val colorPickerFactory = ColorPickerDialogFactory(requireActivity())
            val picker = colorPickerFactory.create(color, themeSwitcher.currentTheme)
            picker.setListener { paletteColor ->
                color = paletteColor
                updateColorSwatch(themeSwitcher)
            }
            picker.dismissCurrentAndShow(parentFragmentManager, "bulkColorPicker")
        }

        fun updatePreview() {
            val count = parseNames(binding.habitNamesInput.text.toString()).size
            binding.previewCount.text = resources.getQuantityString(
                R.plurals.habits_will_be_created,
                count,
                count
            )
        }

        binding.habitNamesInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = updatePreview()
        })
        updatePreview()

        return AlertDialog.Builder(requireActivity())
            .setTitle(R.string.add_multiple_habits)
            .setView(binding.root)
            .setPositiveButton(R.string.create) { _, _ ->
                val names = parseNames(binding.habitNamesInput.text.toString())
                val isNumerical = binding.habitTypeGroup.checkedRadioButtonId == R.id.rbMeasurable
                createHabits(component, names, isNumerical)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    /** One habit name per non-blank line, trimmed, de-duplicated. */
    private fun parseNames(raw: String): List<String> =
        raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun createHabits(
        component: org.isoron.uhabits.inject.HabitsApplicationComponent,
        names: List<String>,
        isNumerical: Boolean
    ) {
        for (name in names) {
            val habit = component.modelFactory.buildHabit()
            habit.name = name
            habit.color = color
            habit.type = if (isNumerical) HabitType.NUMERICAL else HabitType.YES_NO
            habit.frequency = Frequency.DAILY
            if (isNumerical) {
                habit.targetValue = 1.0
                habit.unit = ""
            }
            val command = CreateHabitCommand(
                component.modelFactory,
                component.habitList,
                habit
            )
            component.commandRunner.run(command)
        }
        dismiss()
    }

    private fun updateColorSwatch(themeSwitcher: AndroidThemeSwitcher) {
        val androidColor = themeSwitcher.currentTheme.color(color).toInt()
        binding.colorButton.backgroundTintList = ColorStateList.valueOf(androidColor)
    }

    companion object {
        const val TAG = "bulkCreateHabits"
    }
}