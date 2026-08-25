package com.billremind.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.billremind.app.calendar.CalendarInfo
import com.billremind.app.calendar.CalendarRow
import com.billremind.app.calendar.DeviceCalendar
import com.billremind.app.calendar.EventSeries
import com.billremind.app.calendar.RecurrenceKind
import com.billremind.app.calendar.RecurrenceParser
import com.billremind.app.calendar.VendorGuard
import com.billremind.app.databinding.ActivityMainBinding
import com.billremind.app.ui.CalendarAdapter
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val app get() = application as BillRemindApp

    private var calendars: List<CalendarInfo> = emptyList()
    private var series: List<EventSeries> = emptyList()
    private var filterKind: String = FILTER_ALL
    private var chipsReady = false

    private val adapter = CalendarAdapter { openSeries(it) }

    private val calendarPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) load()
        else render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.refresh.setOnRefreshListener { load() }
        binding.fab.setOnClickListener { onFab() }

        binding.filters.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) }
            filterKind = (chip?.tag as? String) ?: FILTER_ALL
            renderList()
        }

        if (!app.deviceCalendar.hasPermission()) {
            calendarPermission.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    override fun onResume() {
        super.onResume()
        if (app.deviceCalendar.hasPermission()) load()
        else render()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                load()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun load() {
        if (!app.deviceCalendar.hasPermission()) {
            binding.refresh.isRefreshing = false
            render()
            return
        }
        binding.refresh.isRefreshing = true
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val cals = app.deviceCalendar.listCalendars().filter { it.visible }
                val items = app.deviceCalendar.listEvents(
                    DeviceCalendar.rangeStartMs(),
                    DeviceCalendar.rangeEndMs()
                )
                cals to RecurrenceParser.group(items, DeviceCalendar.ONCE_DAYS)
            }
            calendars = loaded.first
            series = loaded.second
            ensureChips()
            binding.refresh.isRefreshing = false
            render()
        }
    }

    private fun ensureChips() {
        val kinds = RecurrenceKind.entries.filter { kind -> series.any { it.kind == kind } }
        val tags = listOf(FILTER_ALL) + kinds.map { it.storage }
        val group = binding.filters
        val existing = (0 until group.childCount).mapNotNull { group.getChildAt(it).tag as? String }
        if (chipsReady && existing == tags) return
        group.removeAllViews()
        group.addView(chip("全部", FILTER_ALL, filterKind == FILTER_ALL))
        kinds.forEach { kind ->
            val count = series.count { it.kind == kind }
            group.addView(chip("${kind.label} $count", kind.storage, filterKind == kind.storage))
        }
        if (filterKind != FILTER_ALL && kinds.none { it.storage == filterKind }) {
            filterKind = FILTER_ALL
            (group.getChildAt(0) as? Chip)?.isChecked = true
        }
        chipsReady = true
    }

    private fun chip(label: String, tag: String, checked: Boolean): Chip {
        return Chip(this).apply {
            text = label
            this.tag = tag
            isCheckable = true
            isChecked = checked
            isClickable = true
        }
    }

    private fun render() {
        val granted = app.deviceCalendar.hasPermission()
        if (!granted) {
            adapter.submitList(emptyList())
            binding.list.visibility = View.GONE
            binding.empty.visibility = View.VISIBLE
            binding.emptyTitle.text = "需要日历权限"
            binding.emptyHint.text = "只读取小米日历 / 系统日历里已有的日程，应用自己不记账单。"
            binding.fab.text = "授予权限"
            binding.summaryCard.visibility = View.GONE
            return
        }

        binding.fab.text = "打开系统日历"
        renderList()
    }

    private fun renderList() {
        val filtered = if (filterKind == FILTER_ALL) {
            series
        } else {
            series.filter { it.kind.storage == filterKind }
        }
        val todayItems = filtered.filter { it.daysUntil() == 0 }
            .sortedBy { it.next.beginMs }
        if (todayItems.isEmpty()) {
            binding.summaryCard.visibility = View.GONE
        } else {
            val first = todayItems.first()
            binding.summaryCard.visibility = View.VISIBLE
            binding.summaryTitle.text = "今天 ${todayItems.size} 件"
            binding.summaryAmount.text = first.title
            binding.summaryHint.text = buildString {
                append(first.kind.label)
                append(" · ")
                append(first.ruleLabel)
                if (todayItems.size > 1) {
                    append(" · 另外 ${todayItems.size - 1} 件")
                }
            }
        }

        val rows = buildRows(filtered, grouped = filterKind != FILTER_ALL)
        adapter.submitList(rows)
        val empty = rows.isEmpty()
        binding.list.visibility = if (empty) View.GONE else View.VISIBLE
        binding.empty.visibility = if (empty) View.VISIBLE else View.GONE
        if (empty) {
            binding.emptyTitle.text = if (calendars.isEmpty()) "没有系统日历" else "没有这类日程"
            binding.emptyHint.text = if (calendars.isEmpty()) {
                "请先打开一次小米日历或系统日历，再回到这里下拉刷新。"
            } else {
                "列出未来一年的日程（含明年）。可按每月 / 每年等筛选。"
            }
        }
    }

    private fun buildRows(items: List<EventSeries>, grouped: Boolean): List<CalendarRow> {
        val ordered = items.sortedWith(
            compareBy<EventSeries> { it.next.beginMs }.thenBy { it.title }
        )
        if (!grouped) {
            return ordered.map { CalendarRow.Series(it) }
        }
        val rows = mutableListOf<CalendarRow>()
        ordered.groupBy { it.kind }
            .toSortedMap(compareBy { it.order })
            .forEach { (kind, group) ->
                rows += CalendarRow.Section(kind, "${kind.label} · ${group.size} 项", group.size)
                group.forEach { rows += CalendarRow.Series(it) }
            }
        return rows
    }

    private fun onFab() {
        if (!app.deviceCalendar.hasPermission()) {
            calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            return
        }
        if (!VendorGuard.openSystemCalendar(this)) {
            Toast.makeText(this, "没有找到系统日历", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSeries(series: EventSeries) {
        try {
            startActivity(app.deviceCalendar.viewEventIntent(series.eventId))
        } catch (_: Exception) {
            try {
                startActivity(app.deviceCalendar.viewDayIntent(series.next.beginMs))
            } catch (_: Exception) {
                if (!VendorGuard.openSystemCalendar(this)) {
                    Toast.makeText(this, "打不开这条日程", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val FILTER_ALL = "all"
    }
}
