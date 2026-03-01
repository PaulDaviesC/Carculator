package com.kdh.carculator.ui

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.DynamicColors
import com.kdh.carculator.databinding.ActivityAddOdometerBinding
import com.kdh.carculator.data.entity.OdometerLog
import com.kdh.carculator.repo.OdometerRepository
import com.kdh.carculator.repo.SettingsRepository
import com.kdh.carculator.util.Formatters
import kotlinx.coroutines.launch
import java.util.UUID

class AddOdometerActivity : BaseDrawerActivity() {
    private lateinit var binding: ActivityAddOdometerBinding
    private lateinit var repo: OdometerRepository
    private lateinit var settingsRepo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAddOdometerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = OdometerRepository(this)
        settingsRepo = SettingsRepository(this)

        val carId = intent.getStringExtra("carId")
        if (carId == null) { finish(); return }

        lifecycleScope.launch {
            val s = settingsRepo.get()
            val unit = s?.distanceUnit ?: com.kdh.carculator.data.DistanceUnit.KM
            binding.tilReading.hint = "Odometer (${if (unit == com.kdh.carculator.data.DistanceUnit.KM) "KM" else "Mile"})"

            binding.btnCancel.setOnClickListener { finish() }
            binding.btnSave.setOnClickListener {
                val input = binding.etReading.text?.toString()?.toDoubleOrNull()
                if (input == null) {
                    binding.tilReading.error = "Enter a valid reading"
                    return@setOnClickListener
                }
                binding.tilReading.error = null
                val readingMeters = Formatters.toMetersFromUnit(input, unit)
                val now = System.currentTimeMillis()
                val log = OdometerLog(
                    id = UUID.randomUUID().toString(),
                    carId = carId,
                    readingMeters = readingMeters,
                    readingAtEpochMs = now,
                    source = "USER",
                    note = null,
                    createdAtEpochMs = now
                )
                lifecycleScope.launch {
                    try {
                        repo.add(log)
                        finish()
                    } catch (t: Throwable) {
                        Toast.makeText(this@AddOdometerActivity, t.message ?: "Error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
