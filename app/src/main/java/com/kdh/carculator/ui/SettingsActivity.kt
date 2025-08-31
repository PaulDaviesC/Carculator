package com.kdh.carculator.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.kdh.carculator.data.DistanceUnit
import com.kdh.carculator.data.entity.AppSettings
import com.kdh.carculator.databinding.ActivitySettingsBinding
import com.kdh.carculator.repo.SettingsRepository
import kotlinx.coroutines.launch
import java.util.Currency

class SettingsActivity : BaseDrawerActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = SettingsRepository(this)

        val units = DistanceUnit.values().map { it.name }
        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, units)
        binding.spUnit.adapter = unitAdapter

        val currencies = Currency.getAvailableCurrencies().map { it.currencyCode }.sorted()
        val currencyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, currencies)
        binding.spCurrency.adapter = currencyAdapter

        lifecycleScope.launch {
            val current = repo.get()
            if (current != null) {
                val unitIdx = units.indexOf(current.distanceUnit.name)
                if (unitIdx >= 0) binding.spUnit.setSelection(unitIdx)
                val curIdx = currencies.indexOf(current.currencyCode)
                if (curIdx >= 0) binding.spCurrency.setSelection(curIdx)
            }
        }

        binding.btnSave.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val unit = DistanceUnit.valueOf(binding.spUnit.selectedItem as String)
                    val currency = binding.spCurrency.selectedItem as String
                    val now = System.currentTimeMillis()
                    val existing = repo.get()
                    val newSettings = if (existing == null) {
                        AppSettings(
                            id = 1,
                            distanceUnit = unit,
                            currencyCode = currency,
                            createdAtEpochMs = now,
                            updatedAtEpochMs = now,
                            schemaVersion = 1
                        )
                    } else {
                        existing.copy(
                            distanceUnit = unit,
                            currencyCode = currency,
                            updatedAtEpochMs = now
                        )
                    }
                    repo.upsert(newSettings)
                    finish()
                } catch (t: Throwable) {
                    Toast.makeText(this@SettingsActivity, t.message ?: "Error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
