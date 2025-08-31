package com.kdh.carculator.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.kdh.carculator.databinding.ActivityAddCarBinding
import com.kdh.carculator.data.entity.Car
import com.kdh.carculator.repo.CarRepository
import kotlinx.coroutines.launch
import java.util.UUID
import com.kdh.carculator.repo.SettingsRepository
import com.kdh.carculator.util.Formatters
import com.kdh.carculator.data.entity.OdometerLog
import com.kdh.carculator.repo.OdometerRepository
import java.util.Calendar
import java.math.BigDecimal

class AddCarActivity : BaseDrawerActivity() {
    private lateinit var binding: ActivityAddCarBinding
    private lateinit var repo: CarRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var odoRepo: OdometerRepository

    private var acqDateEpochMs: Long = System.currentTimeMillis()
    private var configuredCurrency: String = "INR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = CarRepository(this)
        settingsRepo = SettingsRepository(this)
        odoRepo = OdometerRepository(this)

        lifecycleScope.launch {
            val s = settingsRepo.get()
            val unit = s?.distanceUnit ?: com.kdh.carculator.data.DistanceUnit.KM
            configuredCurrency = s?.currencyCode ?: "INR"
            binding.etInitialOdo.hint = "Initial odometer (${if (unit == com.kdh.carculator.data.DistanceUnit.KM) "KM" else "Mile"})"
            binding.tvCurrency.text = configuredCurrency
            updateAcqDateLabel()
        }

        binding.btnPickAcqDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = acqDateEpochMs }
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    val c = Calendar.getInstance().apply {
                        set(Calendar.YEAR, y)
                        set(Calendar.MONTH, m)
                        set(Calendar.DAY_OF_MONTH, d)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    acqDateEpochMs = c.timeInMillis
                    updateAcqDateLabel()
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.btnCancel.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener {
            lifecycleScope.launch {
                val reg = binding.etRegistration.text?.toString()?.trim().orEmpty()
                val name = binding.etName.text?.toString()?.trim().takeIf { it?.isNotEmpty() == true }
                if (reg.isEmpty()) {
                    Toast.makeText(this@AddCarActivity, "Registration is required", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val s = settingsRepo.get()
                val unit = s?.distanceUnit ?: com.kdh.carculator.data.DistanceUnit.KM
                val initialOdoValue = binding.etInitialOdo.text?.toString()?.toDoubleOrNull()
                val initialMeters = initialOdoValue?.let { Formatters.toMetersFromUnit(it, unit) }
                val acqCostMajor = binding.etAcquisitionCost.text?.toString()?.toBigDecimalOrNull()
                val acqCostMinor = acqCostMajor?.let { Formatters.majorToMinorCurrency(it) }
                val remYears = binding.etRemainingYears.text?.toString()?.toIntOrNull() ?: 15
                val remMonthsOnly = binding.etRemainingMonths.text?.toString()?.toIntOrNull() ?: 0
                val remainingLifeMonths = remYears * 12 + remMonthsOnly

                val now = System.currentTimeMillis()
                val carId = UUID.randomUUID().toString()
                val car = Car(
                    id = carId,
                    registrationNumber = reg,
                    name = name,
                    createdAtEpochMs = now,
                    archivedAtEpochMs = null,
                    initialOdometerMeters = initialMeters,
                    acquisitionCostMinor = acqCostMinor,
                    acquisitionDateEpochMs = acqDateEpochMs,
                    remainingLifeMonths = remainingLifeMonths
                )
                try {
                    repo.addCar(car)
                    if (initialMeters != null && initialMeters > 0) {
                        val baseLog = OdometerLog(
                            id = UUID.randomUUID().toString(),
                            carId = carId,
                            readingMeters = initialMeters,
                            readingAtEpochMs = now,
                            source = "USER",
                            note = "Initial odometer at creation",
                            createdAtEpochMs = now
                        )
                        odoRepo.add(baseLog)
                    }
                    finish()
                } catch (t: Throwable) {
                    Toast.makeText(this@AddCarActivity, t.message ?: "Error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateAcqDateLabel() {
        binding.tvAcqDate.text = "Acquisition: " + com.kdh.carculator.util.Formatters.formatDateTime(acqDateEpochMs)
    }
}
