package com.kdh.carculator.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.kdh.carculator.R
import com.kdh.carculator.databinding.ActivityCarDetailBinding
import com.kdh.carculator.repo.ExpenseRepository
import com.kdh.carculator.repo.OdometerRepository
import kotlinx.coroutines.launch
import com.kdh.carculator.data.entity.Expense
import com.kdh.carculator.repo.SettingsRepository
import com.kdh.carculator.util.Formatters
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kdh.carculator.repo.ExpenseCategoryRepository
import kotlinx.coroutines.flow.first
import android.graphics.Paint
import android.graphics.Color

class CarDetailActivity : BaseDrawerActivity() {
    private lateinit var binding: ActivityCarDetailBinding
    private lateinit var odoRepo: OdometerRepository
    private lateinit var expRepo: ExpenseRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var categoryRepo: ExpenseCategoryRepository

    private val expensesAdapter = CarExpensesAdapter()

    private lateinit var carId: String
    private var isLoading = false
    private var reachedEnd = false
    private val pickCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleImportCsv(uri)
    }
    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) handleExportCsv(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCarDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        carId = intent.getStringExtra("carId") ?: run { finish(); return }
        odoRepo = OdometerRepository(this)
        expRepo = ExpenseRepository(this)
        settingsRepo = SettingsRepository(this)
        categoryRepo = ExpenseCategoryRepository(this)

        val lm = LinearLayoutManager(this)
        binding.recyclerExpenses.layoutManager = lm
        binding.recyclerExpenses.adapter = expensesAdapter
        binding.recyclerExpenses.addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(this, lm.orientation))
        binding.recyclerExpenses.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val total = lm.itemCount
                val lastVisible = lm.findLastVisibleItemPosition()
                if (!isLoading && !reachedEnd && lastVisible >= total - 3) {
                    loadMoreExpenses()
                }
            }
        })

        binding.ibEditMileage.setOnClickListener {
            val i = Intent(this, AddOdometerActivity::class.java)
            i.putExtra("carId", carId)
            startActivity(i)
        }
        binding.fabAddExpense.setOnClickListener {
            val i = Intent(this, AddExpenseActivity::class.java)
            i.putExtra("carId", carId)
            startActivity(i)
        }

        lifecycleScope.launch {
            val s = settingsRepo.get()
            val unit = s?.distanceUnit ?: com.kdh.carculator.data.DistanceUnit.KM
            val currency = s?.currencyCode ?: "INR"
            val latest = odoRepo.latest(carId)
            binding.tvLatestOdo.text = latest?.let {
                val dist = Formatters.formatDistance(it.readingMeters, unit)
                val ts = Formatters.formatDateTime(it.readingAtEpochMs)
                "Latest: $dist at $ts"
            } ?: "No odometer"

            val totalsFlow = expRepo.observeTotalsByCar(carId)
            val cpuFlow = expRepo.observeCostPerUnitByCar(carId)
            val distanceMeters = odoRepo.totalDistanceMeters(carId)
            val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
            nf.currency = java.util.Currency.getInstance(currency)

            launch {
                totalsFlow.collect { rows ->
                    val totalForCurrency = rows.find { it.currencyCode == currency }?.totalAmountMinor ?: 0L
                    binding.tvSummaryTotalCost.text = nf.format(totalForCurrency / 100.0)
                }
            }
            binding.tvSummaryMileage.text = Formatters.formatDistance(distanceMeters, unit)
            launch {
                cpuFlow.collect { rows ->
                    val row = rows.find { it.currencyCode == currency }
                    binding.tvSummaryCostPerUnit.text = row?.let { nf.format(it.costPerUnitMinor / 100.0) + " / ${it.unit}" } ?: "—"
                }
            }
        }

        // Make Total Cost look interactive and show breakdown on tap
        binding.tvSummaryTotalCost.paintFlags = binding.tvSummaryTotalCost.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvSummaryTotalCost.setOnClickListener {
            lifecycleScope.launch {
                val settings = settingsRepo.get()
                val currency = settings?.currencyCode ?: "INR"
                val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
                nf.currency = java.util.Currency.getInstance(currency)

                val byCategory = expRepo.observeTotalsByCarCategory(carId).first()
                val categories = categoryRepo.observeActive().first()
                val idToName = categories.associate { it.id to it.name }

                val inflater = LayoutInflater.from(this@CarDetailActivity)
                val dialogView = inflater.inflate(R.layout.dialog_cpu_breakdown, null)
                // Adjust header label for totals
                dialogView.findViewById<TextView>(R.id.tvHeaderCost)?.text = "Total"
                val rowsContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.rowsContainer)

                byCategory
                    .filter { it.currencyCode == currency }
                    .sortedByDescending { it.totalAmountMinor }
                    .forEach { row ->
                        val itemRow = android.widget.LinearLayout(this@CarDetailActivity).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            setPadding(8, 8, 8, 8)
                        }
                        val tvCat = TextView(this@CarDetailActivity).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                            val name = idToName[row.categoryId] ?: row.categoryId.substringAfter(":", row.categoryId)
                            text = name
                            setTextColor(Color.WHITE)
                            setBackgroundResource(R.drawable.bg_badge)
                            backgroundTintList = ColorStateList.valueOf(resolveCategoryColor(row.categoryId, name))
                        }
                        val tvTotal = TextView(this@CarDetailActivity).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            text = nf.format(row.totalAmountMinor / 100.0)
                            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                        }
                        itemRow.addView(tvCat)
                        itemRow.addView(tvTotal)
                        rowsContainer.addView(itemRow)
                    }

                AlertDialog.Builder(this@CarDetailActivity)
                    .setTitle("Total cost by head")
                    .setView(dialogView)
                    .setPositiveButton("Close", null)
                    .show()
            }
        }

        binding.tvSummaryCostPerUnit.paintFlags = binding.tvSummaryCostPerUnit.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvSummaryCostPerUnit.setOnClickListener {
            lifecycleScope.launch {
                val settings = settingsRepo.get()
                val unit = settings?.distanceUnit ?: com.kdh.carculator.data.DistanceUnit.KM
                val currency = settings?.currencyCode ?: "INR"
                val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
                nf.currency = java.util.Currency.getInstance(currency)

                val byCategory = expRepo.observeCostPerUnitByCarCategory(carId).first()
                val categories = categoryRepo.observeActive().first()
                val idToName = categories.associate { it.id to it.name }

                val inflater = LayoutInflater.from(this@CarDetailActivity)
                val dialogView = inflater.inflate(R.layout.dialog_cpu_breakdown, null)
                val rowsContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.rowsContainer)

                byCategory
                    .filter { it.currencyCode == currency }
                    .sortedByDescending { it.costPerUnitMinor }
                    .forEach { row ->
                        val itemRow = android.widget.LinearLayout(this@CarDetailActivity).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            setPadding(8, 8, 8, 8)
                        }
                        val tvCat = TextView(this@CarDetailActivity).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                            val name = idToName[row.categoryId] ?: row.categoryId.substringAfter(":", row.categoryId)
                            text = name
                            setTextColor(Color.WHITE)
                            setBackgroundResource(R.drawable.bg_badge)
                            backgroundTintList = ColorStateList.valueOf(resolveCategoryColor(row.categoryId, name))
                        }
                        val tvCost = TextView(this@CarDetailActivity).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            text = nf.format(row.costPerUnitMinor / 100.0) + " / ${row.unit}"
                            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                        }
                        itemRow.addView(tvCat)
                        itemRow.addView(tvCost)
                        rowsContainer.addView(itemRow)
                    }

                AlertDialog.Builder(this@CarDetailActivity)
                    .setTitle("Cost per ${if (unit == com.kdh.carculator.data.DistanceUnit.KM) "KM" else "Mile"} by head")
                    .setView(dialogView)
                    .setPositiveButton("Close", null)
                    .show()
            }
        }

        binding.tvHeader.text = "Car"

        loadInitialExpenses()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_car_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import_csv -> {
                pickCsv.launch(arrayOf("text/*", "text/comma-separated-values", "text/csv", "application/csv"))
                true
            }
            R.id.action_export_csv -> {
                val defaultName = "expenses-$carId.csv"
                createCsv.launch(defaultName)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh expenses after possible edits
        reachedEnd = false
        isLoading = false
        expensesAdapter.submitList(emptyList())
        loadInitialExpenses()
    }

    fun resolveCategoryColor(categoryId: String, categoryName: String?): Int {
        val key = categoryId.substringAfter(":", categoryId).uppercase(Locale.getDefault())
        return when (key) {
            "FUEL" -> Color.parseColor("#2E7D32")
            "INSURANCE" -> Color.parseColor("#1565C0")
            "SERVICE" -> Color.parseColor("#00695C")
            "REPAIR" -> Color.parseColor("#B71C1C")
            "BODY_PAINT", "BODY&PAINT", "BODY & PAINT" -> Color.parseColor("#E65100")
            "TAX" -> Color.parseColor("#EF6C00")
            "TOLLS" -> Color.parseColor("#283593")
            "DOWN_PAYMENT", "DOWNPAYMENT" -> Color.parseColor("#6A1B9A")
            "EMI" -> Color.parseColor("#4527A0")
            "CLEANING" -> Color.parseColor("#00897B")
            else -> {
                val palette = listOf("#455A64", "#5D4037", "#7B1FA2", "#00796B", "#1976D2", "#C2185B")
                val idx = (categoryName?.hashCode() ?: key.hashCode()).let { Math.abs(it) % palette.size }
                Color.parseColor(palette[idx])
            }
        }
    }

    private fun buildGrouped(items: List<Expense>): List<ExpenseRow> {
        if (items.isEmpty()) return emptyList()
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val result = mutableListOf<ExpenseRow>()
        var lastHeader: String? = null
        for (e in items) {
            val header = sdf.format(Date(e.occurredAtEpochMs))
            if (header != lastHeader) {
                result.add(ExpenseRow.Header(header))
                lastHeader = header
            }
            result.add(ExpenseRow.Item(e))
        }
        return result
    }

    private fun loadInitialExpenses() {
        isLoading = true
        lifecycleScope.launch {
            val page = expRepo.pageInitialByCar(carId, 10)
            expensesAdapter.submitList(buildGrouped(page))
            if (page.size < 10) reachedEnd = true
            isLoading = false
        }
    }

    private fun loadMoreExpenses() {
        val current = expensesAdapter.currentList
        val lastExpense = current.asReversed().firstOrNull { it is ExpenseRow.Item } as? ExpenseRow.Item
        if (lastExpense == null) { loadInitialExpenses(); return }
        isLoading = true
        lifecycleScope.launch {
            val next = expRepo.pageAfterByCar(carId, lastExpense.expense.occurredAtEpochMs, lastExpense.expense.id, 10)
            if (next.isEmpty()) {
                reachedEnd = true
            } else {
                val merged = current.toMutableList()
                val groupedNext = buildGrouped(next)
                if (merged.isNotEmpty() && groupedNext.isNotEmpty() && merged.last() is ExpenseRow.Header && groupedNext.first() is ExpenseRow.Header && (merged.last() as ExpenseRow.Header).title == (groupedNext.first() as ExpenseRow.Header).title) {
                    merged.addAll(groupedNext.drop(1))
                } else {
                    merged.addAll(groupedNext)
                }
                expensesAdapter.submitList(merged)
                if (next.size < 10) reachedEnd = true
            }
            isLoading = false
        }
    }

    private fun handleImportCsv(uri: Uri) {
        lifecycleScope.launch {
            try {
                val settings = settingsRepo.get()
                val currency = settings?.currencyCode ?: "INR"
                val allCategories = categoryRepo.observeActive().first()
                val nameToCategory = allCategories.associateBy { it.name.trim().lowercase(Locale.getDefault()) }
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                contentResolver.openInputStream(uri)?.use { input ->
                    val rows = com.kdh.carculator.util.CsvUtils.readExpensesCsv(input)
                    var imported = 0
                    for (r in rows) {
                        val head = r.head?.trim()
                        val dateStr = r.dateDdMMyyyy?.trim()
                        val amountStr = r.amount?.trim()
                        if (head.isNullOrEmpty() || dateStr.isNullOrEmpty() || amountStr.isNullOrEmpty()) continue
                        val category = nameToCategory[head.lowercase(Locale.getDefault())]
                        val categoryId = category?.id ?: continue
                        val occurred = com.kdh.carculator.util.CsvUtils.parseDdMMyyyyToEpochMs(dateStr) ?: continue
                        val amountMajor = amountStr.replace(",", "").toBigDecimalOrNull() ?: continue
                        val amountMinor = Formatters.majorToMinorCurrency(amountMajor)
                        val odoMeters = r.odometer?.replace(",", "")?.toDoubleOrNull()?.let {
                            val unit = settings?.distanceUnit ?: com.kdh.carculator.data.DistanceUnit.KM
                            Formatters.toMetersFromUnit(it, unit)
                        }
                        val expense = Expense(
                            id = java.util.UUID.randomUUID().toString(),
                            carId = carId,
                            categoryId = categoryId,
                            amountMinor = amountMinor,
                            currencyCode = currency,
                            occurredAtEpochMs = occurred,
                            odometerAtMeters = odoMeters,
                            vendor = r.vendor?.takeIf { it.isNotBlank() },
                            notes = r.notes?.takeIf { it.isNotBlank() },
                            attachmentUri = null,
                            createdAtEpochMs = System.currentTimeMillis(),
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                        expRepo.add(expense)
                        imported++
                    }
                    android.widget.Toast.makeText(this@CarDetailActivity, "Imported $imported rows", android.widget.Toast.LENGTH_LONG).show()
                    // refresh
                    reachedEnd = false
                    isLoading = false
                    expensesAdapter.submitList(emptyList())
                    loadInitialExpenses()
                }
            } catch (t: Throwable) {
                android.widget.Toast.makeText(this@CarDetailActivity, t.message ?: "Import failed", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleExportCsv(uri: Uri) {
        lifecycleScope.launch {
            try {
                val categories = categoryRepo.observeActive().first().associateBy { it.id }
                val expenses = expRepo.listAllForCar(carId)
                val settings = settingsRepo.get()
                val unit = settings?.distanceUnit ?: com.kdh.carculator.data.DistanceUnit.KM
                val rows = expenses.map { e ->
                    val head = categories[e.categoryId]?.name ?: e.categoryId.substringAfter(":", e.categoryId)
                    com.kdh.carculator.util.CsvExpenseRow(
                        head = head,
                        dateDdMMyyyy = com.kdh.carculator.util.CsvUtils.formatEpochMsToDdMMyyyy(e.occurredAtEpochMs),
                        amount = (e.amountMinor / 100.0).toString(),
                        vendor = e.vendor,
                        odometer = e.odometerAtMeters?.let { meters ->
                            val km = if (unit == com.kdh.carculator.data.DistanceUnit.KM) meters / 1000.0 else meters / 1609.344
                            String.format(Locale.getDefault(), "%.2f", km)
                        },
                        notes = e.notes
                    )
                }
                contentResolver.openOutputStream(uri)?.use { out ->
                    com.kdh.carculator.util.CsvUtils.writeExpensesCsv(rows, out)
                }
                android.widget.Toast.makeText(this@CarDetailActivity, "Exported ${rows.size} rows", android.widget.Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                android.widget.Toast.makeText(this@CarDetailActivity, t.message ?: "Export failed", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

private sealed class ExpenseRow {
    data class Header(val title: String) : ExpenseRow()
    data class Item(val expense: Expense) : ExpenseRow()
}

private object CarExpenseDiff : DiffUtil.ItemCallback<ExpenseRow>() {
    override fun areItemsTheSame(a: ExpenseRow, b: ExpenseRow): Boolean = when {
        a is ExpenseRow.Header && b is ExpenseRow.Header -> a.title == b.title
        a is ExpenseRow.Item && b is ExpenseRow.Item -> a.expense.id == b.expense.id
        else -> false
    }
    override fun areContentsTheSame(a: ExpenseRow, b: ExpenseRow): Boolean = a == b
}

private class CarExpensesAdapter : ListAdapter<ExpenseRow, RecyclerView.ViewHolder>(CarExpenseDiff) {
    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ExpenseRow.Header -> 0
        is ExpenseRow.Item -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_expense_header, parent, false)
            HeaderVH(v)
        } else {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_expense_row, parent, false)
            ItemVH(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is ExpenseRow.Header -> (holder as HeaderVH).bind(row)
            is ExpenseRow.Item -> (holder as ItemVH).bind(row.expense)
        }
    }
}

private class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val t1: TextView = itemView.findViewById(R.id.tvHeader)
    fun bind(h: ExpenseRow.Header) { t1.text = h.title }
}

private class ItemVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
    private val tvVendor: TextView = itemView.findViewById(R.id.tvVendor)
    private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
    private val tvCategoryBadge: TextView = itemView.findViewById(R.id.tvCategoryBadge)
    private val tvViewNotes: TextView = itemView.findViewById(R.id.tvViewNotes)
    fun bind(e: Expense) {
        tvAmount.text = "${e.currencyCode} ${(e.amountMinor / 100.0)}"
        tvVendor.text = e.vendor ?: ""
        tvDate.text = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(e.occurredAtEpochMs))
        val name = e.categoryId.substringAfter(":", e.categoryId)
        tvCategoryBadge.text = name
        tvCategoryBadge.setTextColor(Color.WHITE)
        tvCategoryBadge.setBackgroundResource(R.drawable.bg_badge)
        val color = (itemView.context as? CarDetailActivity)?.resolveCategoryColor(e.categoryId, name) ?: Color.parseColor("#607D8B")
        tvCategoryBadge.backgroundTintList = ColorStateList.valueOf(color)
        if (!e.notes.isNullOrBlank()) {
            tvViewNotes.visibility = View.VISIBLE
            tvViewNotes.setOnClickListener {
                AlertDialog.Builder(itemView.context)
                    .setTitle("Notes")
                    .setMessage(e.notes)
                    .setPositiveButton("Close", null)
                    .show()
            }
        } else {
            tvViewNotes.visibility = View.GONE
            tvViewNotes.setOnClickListener(null)
        }

        // Tap to edit
        itemView.setOnClickListener {
            val ctx = itemView.context
            if (ctx is CarDetailActivity) {
                val i = Intent(ctx, AddExpenseActivity::class.java)
                i.putExtra("carId", e.carId)
                i.putExtra("expenseId", e.id)
                ctx.startActivity(i)
            }
        }
    }
}
