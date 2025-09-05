package com.kdh.carculator.ui

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kdh.carculator.databinding.ActivityMainBinding
import com.kdh.carculator.data.entity.Car
import com.kdh.carculator.repo.CarRepository
import com.kdh.carculator.repo.ExpenseRepository
import com.kdh.carculator.repo.SettingsRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.widget.TextView
import com.google.android.material.chip.Chip
import java.text.NumberFormat
import java.util.Locale
import com.kdh.carculator.R

class MainActivity : BaseDrawerActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: CarRepository
    private lateinit var expRepo: ExpenseRepository
    private lateinit var settingsRepo: SettingsRepository
    private var currency: String = "INR"
    private var nf: NumberFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())

    private val adapter = CarAdapter { car ->
        val i = Intent(this, CarDetailActivity::class.java)
        i.putExtra("carId", car.id)
        startActivity(i)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = CarRepository(this)
        expRepo = ExpenseRepository(this)
        settingsRepo = SettingsRepository(this)
        binding.recyclerCars.adapter = adapter

        binding.fabAddCar.setOnClickListener {
            startActivity(Intent(this, AddCarActivity::class.java))
        }

        lifecycleScope.launch {
            val s = settingsRepo.get()
            currency = s?.currencyCode ?: "INR"
            nf.currency = java.util.Currency.getInstance(currency)

            launch {
                repo.observeCars().collectLatest { cars ->
                    adapter.submitList(cars)
                }
            }

            launch {
                expRepo.observeCostPerUnitForAllCars().collect { rows ->
                    val map = rows
                        .filter { it.currencyCode == currency }
                        .associate { row ->
                            val text = nf.format(row.costPerUnitMinor / 100.0) + " / ${row.unit}"
                            row.carId to text
                        }
                    adapter.updateCpuMap(map)
                }
            }
        }
    }
}

private object CarDiff : DiffUtil.ItemCallback<Car>() {
    override fun areItemsTheSame(oldItem: Car, newItem: Car) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Car, newItem: Car) = oldItem == newItem
}

private class CarAdapter(val onClick: (Car) -> Unit) : ListAdapter<Car, CarVH>(CarDiff) {
    private var cpuByCarId: Map<String, String> = emptyMap()

    fun updateCpuMap(newMap: Map<String, String>) {
        cpuByCarId = newMap
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_car_row, parent, false)
        return CarVH(view, onClick)
    }

    override fun onBindViewHolder(holder: CarVH, position: Int) {
        val car = getItem(position)
        holder.bind(car, cpuByCarId[car.id])
    }
}

private class CarVH(itemView: View, val onClick: (Car) -> Unit) : RecyclerView.ViewHolder(itemView) {
    private val tvReg: TextView = itemView.findViewById(R.id.tvReg)
    private val tvName: TextView = itemView.findViewById(R.id.tvName)
    private val chipCpu: Chip = itemView.findViewById(R.id.chipCpu)
    private val tvDistance: TextView? = itemView.findViewById(R.id.tvDistance)

    fun bind(car: Car, cpuText: String?) {
        tvReg.text = car.registrationNumber
        tvName.text = car.name ?: ""
        chipCpu.text = cpuText ?: "—"
        tvDistance?.text = ""
        itemView.setOnClickListener { onClick(car) }
    }
}
