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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.widget.TextView

class MainActivity : BaseDrawerActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: CarRepository
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
        binding.recyclerCars.adapter = adapter

        binding.fabAddCar.setOnClickListener {
            startActivity(Intent(this, AddCarActivity::class.java))
        }

        lifecycleScope.launch {
            repo.observeCars().collectLatest { cars ->
                adapter.submitList(cars)
            }
        }
    }
}

private object CarDiff : DiffUtil.ItemCallback<Car>() {
    override fun areItemsTheSame(oldItem: Car, newItem: Car) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Car, newItem: Car) = oldItem == newItem
}

private class CarAdapter(val onClick: (Car) -> Unit) : ListAdapter<Car, CarVH>(CarDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarVH {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return CarVH(view, onClick)
    }

    override fun onBindViewHolder(holder: CarVH, position: Int) {
        holder.bind(getItem(position))
    }
}

private class CarVH(itemView: View, val onClick: (Car) -> Unit) : RecyclerView.ViewHolder(itemView) {
    private val title: TextView = itemView.findViewById(android.R.id.text1)
    private val subtitle: TextView = itemView.findViewById(android.R.id.text2)

    fun bind(car: Car) {
        title.text = car.registrationNumber
        subtitle.text = car.name ?: ""
        itemView.setOnClickListener { onClick(car) }
    }
}
