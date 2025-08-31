package com.kdh.carculator.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.kdh.carculator.databinding.ActivityAddExpenseBinding
import com.kdh.carculator.data.entity.Expense
import com.kdh.carculator.repo.ExpenseRepository
import com.kdh.carculator.repo.ExpenseCategoryRepository
import com.kdh.carculator.util.Formatters
import com.kdh.carculator.repo.SettingsRepository
import kotlinx.coroutines.flow.collectLatest
import java.math.BigDecimal
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class AddExpenseActivity : BaseDrawerActivity() {
    private lateinit var binding: ActivityAddExpenseBinding
    private lateinit var repo: ExpenseRepository
    private lateinit var catRepo: ExpenseCategoryRepository
    private lateinit var settingsRepo: SettingsRepository

    private var categoryIds: List<String> = emptyList()
    private var selectedEpochMs: Long = System.currentTimeMillis()
    private var configuredCurrency: String = "INR"
    private var editingExpenseId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = ExpenseRepository(this)
        catRepo = ExpenseCategoryRepository(this)
        settingsRepo = SettingsRepository(this)

        val carId = intent.getStringExtra("carId")
        if (carId == null) { finish(); return }
        editingExpenseId = intent.getStringExtra("expenseId")

        lifecycleScope.launch {
            val s = settingsRepo.get()
            configuredCurrency = s?.currencyCode ?: "INR"
            binding.tvCurrency.text = configuredCurrency
        }

        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, mutableListOf())
        binding.spCategory.adapter = adapter

        lifecycleScope.launch {
            catRepo.observeActive().collectLatest { list ->
                categoryIds = list.map { it.id }
                adapter.clear()
                adapter.addAll(list.map { it.name })
                adapter.notifyDataSetChanged()

                // If in edit mode, prefill once categories loaded so spinner can select
                val id = editingExpenseId
                if (id != null) {
                    val existing = repo.getById(id)
                    if (existing != null) {
                        configuredCurrency = existing.currencyCode
                        binding.tvCurrency.text = configuredCurrency
                        selectedEpochMs = existing.occurredAtEpochMs
                        updateSelectedDateLabel()
                        binding.etAmountMinor.setText(BigDecimal(existing.amountMinor).divide(BigDecimal(100)).toPlainString())
                        binding.etOdometerMeters.setText(existing.odometerAtMeters?.toString() ?: "")
                        binding.etVendor.setText(existing.vendor ?: "")
                        binding.etNotes.setText(existing.notes ?: "")
                        val selIndex = categoryIds.indexOf(existing.categoryId)
                        if (selIndex >= 0) binding.spCategory.setSelection(selIndex)
                        binding.btnSave.setText(com.kdh.carculator.R.string.update)
                        binding.btnDelete.visibility = android.view.View.VISIBLE
                    }
                }
            }
        }

        updateSelectedDateLabel()
        binding.btnPickDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedEpochMs }
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val c = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    selectedEpochMs = c.timeInMillis
                    updateSelectedDateLabel()
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.btnCancel.setOnClickListener { finish() }
        binding.btnDelete.setOnClickListener {
            val id = editingExpenseId
            if (id == null) return@setOnClickListener
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(com.kdh.carculator.R.string.delete))
                .setMessage(getString(com.kdh.carculator.R.string.confirm_delete))
                .setPositiveButton(com.kdh.carculator.R.string.delete) { _, _ ->
                    lifecycleScope.launch {
                        try {
                            val existing = repo.getById(id)
                            if (existing != null) {
                                repo.delete(existing)
                            }
                            finish()
                        } catch (t: Throwable) {
                            Toast.makeText(this@AddExpenseActivity, t.message ?: "Error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton(com.kdh.carculator.R.string.cancel, null)
                .show()
        }
        binding.btnSave.setOnClickListener {
            val amountMajorStr = binding.etAmountMinor.text?.toString()?.trim()
            val amountMajor = amountMajorStr?.toBigDecimalOrNull()
            val selectedPos = binding.spCategory.selectedItemPosition
            val categoryId = categoryIds.getOrNull(selectedPos)
            val odo = binding.etOdometerMeters.text?.toString()?.toLongOrNull()
            val vendor = binding.etVendor.text?.toString()?.trim().takeIf { it?.isNotEmpty() == true }
            val notes = binding.etNotes.text?.toString()?.trim().takeIf { it?.isNotEmpty() == true }

            if (amountMajor == null || categoryId == null) {
                Toast.makeText(this, "Fill amount and category", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amountMinor = Formatters.majorToMinorCurrency(amountMajor)
            val now = System.currentTimeMillis()
            val editingId = editingExpenseId
            if (editingId == null) {
                val expense = Expense(
                    id = UUID.randomUUID().toString(),
                    carId = carId,
                    categoryId = categoryId,
                    amountMinor = amountMinor,
                    currencyCode = configuredCurrency,
                    occurredAtEpochMs = selectedEpochMs,
                    odometerAtMeters = odo,
                    vendor = vendor,
                    notes = notes,
                    attachmentUri = null,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )

                lifecycleScope.launch {
                    try {
                        repo.add(expense)
                        finish()
                    } catch (t: Throwable) {
                        Toast.makeText(this@AddExpenseActivity, t.message ?: "Error", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                lifecycleScope.launch {
                    try {
                        val existing = repo.getById(editingId)
                        if (existing == null) {
                            Toast.makeText(this@AddExpenseActivity, "Expense not found", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val updated = existing.copy(
                            categoryId = categoryId,
                            amountMinor = amountMinor,
                            currencyCode = configuredCurrency,
                            occurredAtEpochMs = selectedEpochMs,
                            odometerAtMeters = odo,
                            vendor = vendor,
                            notes = notes,
                            updatedAtEpochMs = now
                        )
                        repo.update(updated)
                        finish()
                    } catch (t: Throwable) {
                        Toast.makeText(this@AddExpenseActivity, t.message ?: "Error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updateSelectedDateLabel() {
        binding.tvSelectedDate.text = Formatters.formatDateTime(selectedEpochMs)
    }
}
