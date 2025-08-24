package com.kdh.kitnadetihai.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.kdh.kitnadetihai.databinding.ActivityAddExpenseBinding
import com.kdh.kitnadetihai.data.entity.Expense
import com.kdh.kitnadetihai.repo.ExpenseRepository
import com.kdh.kitnadetihai.repo.ExpenseCategoryRepository
import com.kdh.kitnadetihai.util.Formatters
import com.kdh.kitnadetihai.repo.SettingsRepository
import kotlinx.coroutines.flow.collectLatest
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = ExpenseRepository(this)
        catRepo = ExpenseCategoryRepository(this)
        settingsRepo = SettingsRepository(this)

        val carId = intent.getStringExtra("carId")
        if (carId == null) { finish(); return }

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
        }
    }

    private fun updateSelectedDateLabel() {
        binding.tvSelectedDate.text = Formatters.formatDateTime(selectedEpochMs)
    }
}
