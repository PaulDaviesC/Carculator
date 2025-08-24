package com.kdh.kitnadetihai.ui

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.kdh.kitnadetihai.databinding.ActivityAddCarBinding
import com.kdh.kitnadetihai.data.entity.Car
import com.kdh.kitnadetihai.repo.CarRepository
import kotlinx.coroutines.launch
import java.util.UUID

class AddCarActivity : BaseDrawerActivity() {
    private lateinit var binding: ActivityAddCarBinding
    private lateinit var repo: CarRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = CarRepository(this)

        binding.btnCancel.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener {
            val reg = binding.etRegistration.text?.toString()?.trim().orEmpty()
            val name = binding.etName.text?.toString()?.trim().takeIf { it?.isNotEmpty() == true }
            if (reg.isEmpty()) {
                Toast.makeText(this, "Registration is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val now = System.currentTimeMillis()
            val car = Car(
                id = UUID.randomUUID().toString(),
                registrationNumber = reg,
                name = name,
                createdAtEpochMs = now,
                archivedAtEpochMs = null
            )
            lifecycleScope.launch {
                try {
                    repo.addCar(car)
                    finish()
                } catch (t: Throwable) {
                    Toast.makeText(this@AddCarActivity, t.message ?: "Error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
