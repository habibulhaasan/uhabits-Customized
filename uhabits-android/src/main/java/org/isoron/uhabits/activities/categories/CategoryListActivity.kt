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
package org.isoron.uhabits.activities.categories

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import org.isoron.platform.gui.toInt
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.activities.common.dialogs.ColorPickerDialogFactory
import org.isoron.uhabits.core.models.Category
import org.isoron.uhabits.core.models.CategoryList
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.databinding.ActivityCategoryListBinding
import org.isoron.uhabits.databinding.DialogCategoryEditBinding
import org.isoron.uhabits.utils.applyRootViewInsets
import org.isoron.uhabits.utils.setupToolbar

/**
 * Screen that lets the user create, rename, recolor, delete and reorder
 * habit categories. Reachable from the main habit list's overflow menu
 * ("Manage categories").
 */
class CategoryListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoryListBinding
    private lateinit var categoryList: CategoryList
    private lateinit var adapter: CategoryAdapter
    private lateinit var colorPickerFactory: ColorPickerDialogFactory
    private lateinit var themeSwitcher: AndroidThemeSwitcher
    private lateinit var touchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val component = (application as HabitsApplication).component
        categoryList = component.categoryList
        colorPickerFactory = ColorPickerDialogFactory(this)
        themeSwitcher = AndroidThemeSwitcher(this, component.preferences)
        themeSwitcher.apply()

        binding = ActivityCategoryListBinding.inflate(LayoutInflater.from(this))
        binding.root.setupToolbar(
            toolbar = binding.toolbar,
            title = resources.getString(R.string.manage_categories),
            color = PaletteColor(11),
            theme = themeSwitcher.currentTheme
        )
        binding.root.applyRootViewInsets()
        setContentView(binding.root)

        adapter = CategoryAdapter(
            categories = categoryList.getAll().toMutableList(),
            theme = themeSwitcher.currentTheme,
            habitCounter = { categoryList.countHabits(it) },
            onClick = { showEditCategoryDialog(it) },
            onDelete = { confirmDelete(it) },
            onReorder = { from, to -> categoryList.reorder(from, to) },
            onStartDrag = { holder -> touchHelper.startDrag(holder) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        touchHelper = ItemTouchHelper(CategoryDragCallback(adapter))
        touchHelper.attachToRecyclerView(binding.recyclerView)

        updateEmptyState()
    }

    override fun onResume() {
        super.onResume()
        categoryList.reload()
        adapter.replaceAll(categoryList.getAll())
        updateEmptyState()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.category_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.actionAddCategory) {
            showAddCategoryDialog()
            return true
        }
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateEmptyState() {
        val isEmpty = adapter.itemCount == 0
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showAddCategoryDialog() {
        showNameColorDialog(
            title = getString(R.string.new_category),
            initialName = "",
            initialColor = PaletteColor(8)
        ) { name, color ->
            categoryList.add(Category(name = name, color = color))
            adapter.replaceAll(categoryList.getAll())
            updateEmptyState()
        }
    }

    private fun showEditCategoryDialog(category: Category) {
        showNameColorDialog(
            title = getString(R.string.edit_category),
            initialName = category.name,
            initialColor = category.color
        ) { name, color ->
            category.name = name
            category.color = color
            categoryList.update(category)
            adapter.replaceAll(categoryList.getAll())
        }
    }

    private fun confirmDelete(category: Category) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_category))
            .setMessage(getString(R.string.delete_category_confirmation))
            .setPositiveButton(R.string.delete) { _, _ ->
                categoryList.remove(category)
                adapter.replaceAll(categoryList.getAll())
                updateEmptyState()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Single dialog used for both "new category" and "edit category": a name
     * field plus a tappable swatch that opens the app's existing color
     * picker. Positive button is wired manually (via setOnShowListener) so
     * we can reject an empty name without dismissing the dialog.
     */
    private fun showNameColorDialog(
        title: String,
        initialName: String,
        initialColor: PaletteColor,
        onConfirm: (String, PaletteColor) -> Unit
    ) {
        var chosenColor = initialColor
        val dialogBinding = DialogCategoryEditBinding.inflate(LayoutInflater.from(this))
        dialogBinding.categoryNameInput.inputType = InputType.TYPE_CLASS_TEXT
        dialogBinding.categoryNameInput.setText(initialName)
        dialogBinding.categoryNameInput.setSelection(initialName.length)

        fun applySwatch(color: PaletteColor) {
            val swatch = dialogBinding.colorSwatch.background.mutate() as GradientDrawable
            swatch.setColor(themeSwitcher.currentTheme.color(color).toInt())
        }
        applySwatch(chosenColor)

        dialogBinding.colorSwatch.setOnClickListener {
            val picker = colorPickerFactory.create(chosenColor, themeSwitcher.currentTheme)
            picker.setListener { pickedColor ->
                chosenColor = pickedColor
                applySwatch(chosenColor)
            }
            supportFragmentManager.findFragmentByTag("categoryColorPicker")?.let { existing ->
                supportFragmentManager.beginTransaction().remove(existing).commitAllowingStateLoss()
            }
            picker.show(supportFragmentManager, "categoryColorPicker")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogBinding.categoryNameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.category_name_required, Toast.LENGTH_SHORT).show()
                } else {
                    onConfirm(name, chosenColor)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}