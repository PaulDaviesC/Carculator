package com.kdh.kitnadetihai.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.kdh.kitnadetihai.databinding.ActivityExpenseListBinding
import com.kdh.kitnadetihai.data.entity.Expense
import com.kdh.kitnadetihai.repo.ExpenseRepository
import kotlinx.coroutines.launch

class ExpenseListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExpenseListBinding
    private lateinit var repo: ExpenseRepository
    private val adapter = ExpenseAdapter()

    private lateinit var carId: String
    private var isLoading = false
    private var reachedEnd = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        carId = intent.getStringExtra("carId") ?: run { finish(); return }
        repo = ExpenseRepository(this)

        val lm = LinearLayoutManager(this)
        binding.recyclerExpenses.layoutManager = lm
        binding.recyclerExpenses.adapter = adapter

        binding.recyclerExpenses.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val total = lm.itemCount
                val lastVisible = lm.findLastVisibleItemPosition()
                if (!isLoading && !reachedEnd && lastVisible >= total - 3) {
                    loadMore()
                }
            }
        })

        loadInitial()
    }

    private fun loadInitial() {
        isLoading = true
        lifecycleScope.launch {
            val page = repo.pageInitialByCar(carId, 10)
            adapter.submitList(page)
            if (page.size < 10) reachedEnd = true
            isLoading = false
        }
    }

    private fun loadMore() {
        val current = adapter.currentList
        if (current.isEmpty()) { loadInitial(); return }
        val last = current.last()
        isLoading = true
        lifecycleScope.launch {
            val next = repo.pageAfterByCar(carId, last.occurredAtEpochMs, last.id, 10)
            if (next.isEmpty()) {
                reachedEnd = true
            } else {
                val merged = current.toMutableList().apply { addAll(next) }
                adapter.submitList(merged)
                if (next.size < 10) reachedEnd = true
            }
            isLoading = false
        }
    }
}

private object ExpenseDiff : DiffUtil.ItemCallback<Expense>() {
    override fun areItemsTheSame(a: Expense, b: Expense) = a.id == b.id
    override fun areContentsTheSame(a: Expense, b: Expense) = a == b
}

private class ExpenseAdapter : ListAdapter<Expense, ExpenseVH>(ExpenseDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseVH {
        val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return ExpenseVH(v)
    }
    override fun onBindViewHolder(holder: ExpenseVH, position: Int) = holder.bind(getItem(position))
}

private class ExpenseVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val t1: TextView = itemView.findViewById(android.R.id.text1)
    private val t2: TextView = itemView.findViewById(android.R.id.text2)
    fun bind(e: Expense) {
        t1.text = "${e.currencyCode} ${(e.amountMinor / 100.0)}"
        t2.text = e.notes ?: e.vendor ?: e.categoryId
    }
}
