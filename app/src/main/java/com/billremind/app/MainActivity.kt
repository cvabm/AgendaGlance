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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val app get() = application as BillRemindApp

    private var calendars: List<CalendarInfo> = emptyList()
    private var series: List<EventSeries> = emptyList()
    private var filterKind: String = FILTER_LIFE
    private var updatingChips = false

    private val adapter = CalendarAdapter(
        onClick = { openSeries(it) },
        onLongClick = { toggleSubscription(it) }
    )

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

        binding.filters.setOnCheckedStateChangeListener { _, checkedIds ->
            if (updatingChips) return@setOnCheckedStateChangeListener
            filterKind = if (checkedIds.firstOrNull() == binding.subscriptionFilter.id) {
                FILTER_SUBSCRIPTION
            } else {
                FILTER_LIFE
            }
            syncCheckedFilters()
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
                val grouped = RecurrenceParser.group(items, DeviceCalendar.ONCE_DAYS)
                val subscribed = app.userTags.subscriptionIds()
                cals to grouped.map { it.copy(subscribed = it.eventId in subscribed) }
            }
            calendars = loaded.first
            series = loaded.second
            ensureChips()
            binding.refresh.isRefreshing = false
            render()
        }
    }

    private fun ensureChips() {
        binding.lifeFilter.text = "生活 ${series.count { !it.subscribed }}"
        binding.subscriptionFilter.text = "订阅 ${series.count { it.subscribed }}"
        syncCheckedFilters()
    }

    private fun syncCheckedFilters() {
        updatingChips = true
        val checkedId = if (filterKind == FILTER_SUBSCRIPTION) {
            binding.subscriptionFilter.id
        } else {
            binding.lifeFilter.id
        }
        if (binding.filters.checkedChipId != checkedId) {
            binding.filters.check(checkedId)
        }
        updatingChips = false
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
        val filtered = when (filterKind) {
            FILTER_SUBSCRIPTION -> series.filter { it.subscribed }
            else -> series.filter { !it.subscribed }
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
                append(first.categoryLabel())
                append(" · ")
                append(first.ruleLabel)
                if (todayItems.size > 1) {
                    append(" · 另外 ${todayItems.size - 1} 件")
                }
            }
        }

        val rows = buildRows(filtered, grouped = false)
        adapter.submitList(rows)
        val empty = rows.isEmpty()
        binding.list.visibility = if (empty) View.GONE else View.VISIBLE
        binding.empty.visibility = if (empty) View.VISIBLE else View.GONE
        if (empty) {
            binding.emptyTitle.text = when {
                calendars.isEmpty() -> "没有系统日历"
                filterKind == FILTER_SUBSCRIPTION -> "还没有订阅"
                else -> "没有生活日程"
            }
            binding.emptyHint.text = when {
                calendars.isEmpty() -> "请先打开一次小米日历或系统日历，再回到这里下拉刷新。"
                filterKind == FILTER_SUBSCRIPTION -> "长按生活日程，把它加入订阅。"
                else -> "未加入订阅的日程会放在这里。"
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

    private fun toggleSubscription(item: EventSeries) {
        val next = !item.subscribed
        app.userTags.setSubscribed(item.eventId, next)
        series = series.map { if (it.eventId == item.eventId) it.copy(subscribed = next) else it }
        ensureChips()
        renderList()
        Toast.makeText(this, if (next) "已加入订阅" else "已移出订阅", Toast.LENGTH_SHORT).show()
    }

    private fun openSeries(series: EventSeries) {
        if (!app.deviceCalendar.openEvent(this, series.next)) {
            Toast.makeText(this, "打不开这条日程", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val FILTER_LIFE = "life"
        private const val FILTER_SUBSCRIPTION = "subscription"
    }
}
