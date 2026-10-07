package com.billremind.app

import android.Manifest
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
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
import com.billremind.app.calendar.RecurrenceParser
import com.billremind.app.calendar.VendorGuard
import com.billremind.app.databinding.ActivityMainBinding
import com.billremind.app.ui.CalendarAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val app get() = application as BillRemindApp
    private var calendars: List<CalendarInfo> = emptyList()
    private var series: List<EventSeries> = emptyList()
    private var filterKind = FILTER_LIFE
    private var updatingChips = false
    private var loadJob: Job? = null
    private var dateJob: Job? = null
    private var loadVersion = 0
    private var loadFailed = false
    private var hasLoaded = false
    private val handler = Handler(Looper.getMainLooper())
    private val refreshFromProvider = Runnable { load() }
    private var observing = false
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(refreshFromProvider)
            handler.postDelayed(refreshFromProvider, 300)
        }
    }
    private val adapter = CalendarAdapter(::openSeries, ::toggleSubscription)
    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) load() else render()
    }
    private val permission by lazy {
        CalendarPermission(this) { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        filterKind = savedInstanceState?.getString(KEY_FILTER) ?: FILTER_LIFE
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.refresh.setOnRefreshListener { load() }
        binding.fab.setOnClickListener { onFab() }
        binding.loadError.setOnClickListener { load() }
        binding.filters.setOnCheckedStateChangeListener { _, checkedIds ->
            if (updatingChips) return@setOnCheckedStateChangeListener
            filterKind = if (checkedIds.firstOrNull() == binding.subscriptionFilter.id) FILTER_SUBSCRIPTION else FILTER_LIFE
            render()
        }
        syncCheckedFilters()
        if (!app.deviceCalendar.hasPermission() && !permission.wasRequested() && savedInstanceState == null) permission.request()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        updatingChips = true
        super.onRestoreInstanceState(savedInstanceState)
        updatingChips = false
        filterKind = savedInstanceState.getString(KEY_FILTER) ?: FILTER_LIFE
        syncCheckedFilters()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_FILTER, filterKind)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        load()
        dateJob?.cancel()
        dateJob = lifecycleScope.launch {
            var today = LocalDate.now()
            var zone = ZoneId.systemDefault()
            while (isActive) {
                delay(30_000)
                val newToday = LocalDate.now()
                val newZone = ZoneId.systemDefault()
                if (newToday != today || newZone != zone) {
                    today = newToday
                    zone = newZone
                    load()
                } else render()
            }
        }
    }

    override fun onPause() {
        dateJob?.cancel()
        super.onPause()
    }

    override fun onStop() {
        if (observing) {
            contentResolver.unregisterContentObserver(observer)
            observing = false
        }
        handler.removeCallbacks(refreshFromProvider)
        loadJob?.cancel()
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> { load(); true }
        R.id.action_calendars -> { showCalendarFilter(); true }
        R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun load() {
        val version = ++loadVersion
        loadJob?.cancel()
        if (!app.deviceCalendar.hasPermission()) {
            calendars = emptyList()
            series = emptyList()
            hasLoaded = false
            loadFailed = false
            binding.refresh.isRefreshing = false
            render()
            return
        }
        if (!observing) {
            try {
                contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
                observing = true
            } catch (_: SecurityException) {
                // Queries still check permission and report their own failure state.
            }
        }
        binding.refresh.isRefreshing = true
        render()
        loadJob = lifecycleScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val cals = app.deviceCalendar.listCalendars()
                    val today = LocalDate.now()
                    val zone = ZoneId.systemDefault()
                    val items = app.deviceCalendar.listEvents(
                        today.atStartOfDay(zone).toInstant().toEpochMilli(),
                        today.plusDays(DeviceCalendar.RANGE_DAYS).atStartOfDay(zone).toInstant().toEpochMilli(), cals
                    )
                    cals.filter { it.visible } to RecurrenceParser.group(items, DeviceCalendar.ONCE_DAYS, today)
                }
                if (version != loadVersion) return@launch
                calendars = loaded.first
                // Apply current tags on the UI thread, after any long-press changes during loading.
                val subscribed = app.userTags.subscriptionIds()
                series = loaded.second.map { it.copy(subscribed = it.eventId in subscribed) }
                hasLoaded = true
                loadFailed = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (version == loadVersion) loadFailed = true
            } finally {
                if (version == loadVersion) {
                    binding.refresh.isRefreshing = false
                    render()
                }
            }
        }
    }

    private fun syncCheckedFilters() {
        updatingChips = true
        binding.filters.check(if (filterKind == FILTER_SUBSCRIPTION) binding.subscriptionFilter.id else binding.lifeFilter.id)
        updatingChips = false
    }

    private fun render() {
        val granted = app.deviceCalendar.hasPermission()
        binding.filters.visibility = if (granted) View.VISIBLE else View.GONE
        binding.loadError.visibility = if (granted && loadFailed) View.VISIBLE else View.GONE
        binding.loadError.text = if (hasLoaded) "读取失败，暂时显示上次结果 · 点此重试" else "日历读取失败 · 点此重试"
        if (!granted) {
            adapter.submitList(emptyList())
            binding.list.visibility = View.GONE
            binding.empty.visibility = View.VISIBLE
            binding.emptyTitle.text = "需要日历权限"
            binding.emptyHint.text = "只读取系统日历里已有的日程，不修改日历。"
            binding.fab.text = if (permission.permanentlyDenied()) "打开权限设置" else "授予权限"
            binding.summaryCard.visibility = View.GONE
            return
        }
        binding.fab.text = "打开系统日历"
        val ids = app.calendarFilters.selectedIds()
        val selected = series.filter { ids == null || it.calendarId in ids }
        binding.lifeFilter.text = "生活 ${selected.count { !it.subscribed }}"
        binding.subscriptionFilter.text = "订阅 ${selected.count { it.subscribed }}"
        syncCheckedFilters()
        val filtered = selected.filter { if (filterKind == FILTER_SUBSCRIPTION) it.subscribed else !it.subscribed }
        val today = LocalDate.now()
        val todayItems = filtered.filter { it.next.occursOn(today) }.sortedBy { it.next.beginMs }
        binding.summaryCard.visibility = if (todayItems.isEmpty()) View.GONE else View.VISIBLE
        todayItems.firstOrNull()?.let { first ->
            binding.summaryTitle.text = "今天 ${todayItems.size} 件"
            binding.summaryAmount.text = first.title
            binding.summaryHint.text = "${first.next.dateTimeLabel(today)} · ${first.ruleLabel}" +
                if (todayItems.size > 1) " · 另外 ${todayItems.size - 1} 件" else ""
        }
        val rows = filtered.sortedWith(compareBy<EventSeries> { it.next.beginMs }.thenBy { it.title })
            .map { CalendarRow.Series(it) }
        adapter.submitList(rows)
        binding.list.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        binding.empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        if (rows.isEmpty()) {
            binding.emptyTitle.text = when {
                loadFailed -> "暂时无法读取日历"
                !hasLoaded -> "正在读取日历"
                calendars.isEmpty() -> "没有可见的系统日历"
                ids != null && ids.none { id -> calendars.any { it.id == id } } -> "未选择可见日历本"
                filterKind == FILTER_SUBSCRIPTION -> "还没有订阅"
                else -> "没有生活日程"
            }
            binding.emptyHint.text = when {
                loadFailed -> "请点击上方提示或刷新按钮重试。"
                !hasLoaded -> "正在加载未来一年的日程。"
                calendars.isEmpty() -> "请先打开系统日历，确认日历本可见后再刷新。"
                ids != null -> "可以在右上角菜单中调整日历本筛选。"
                filterKind == FILTER_SUBSCRIPTION -> "长按生活日程，把它加入订阅。"
                else -> "未来一年内未加入订阅的日程会放在这里。"
            }
        }
    }

    private fun showCalendarFilter() {
        if (!app.deviceCalendar.hasPermission()) { permission.request(); return }
        if (calendars.isEmpty()) {
            Toast.makeText(this, if (loadFailed) "请先重试读取日历" else "尚无可见的日历本", Toast.LENGTH_SHORT).show()
            return
        }
        val options = calendars.toList()
        val current = app.calendarFilters.selectedIds()
        val checked = BooleanArray(options.size) { current == null || options[it].id in current }
        MaterialAlertDialogBuilder(this)
            .setTitle("筛选日历本")
            .setMultiChoiceItems(options.map { it.displayName() }.toTypedArray(), checked) { _, which, enabled -> checked[which] = enabled }
            .setPositiveButton("确定") { _, _ ->
                val ids = options.filterIndexed { index, _ -> checked[index] }.map { it.id }.toSet()
                app.calendarFilters.select(if (checked.all { it }) null else ids)
                render()
            }
            .setNeutralButton("全部日历") { _, _ -> app.calendarFilters.select(null); render() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun onFab() {
        if (!app.deviceCalendar.hasPermission()) { permission.request(); return }
        if (!VendorGuard.openSystemCalendar(this)) Toast.makeText(this, "没有找到系统日历", Toast.LENGTH_SHORT).show()
    }

    private fun toggleSubscription(item: EventSeries) {
        val subscribed = !item.subscribed
        app.userTags.setSubscribed(item.eventId, subscribed)
        series = series.map { if (it.eventId == item.eventId) it.copy(subscribed = subscribed) else it }
        render()
        Toast.makeText(this, if (subscribed) "已加入订阅" else "已移出订阅", Toast.LENGTH_SHORT).show()
    }

    private fun openSeries(series: EventSeries) {
        if (!app.deviceCalendar.openEvent(this, series.next)) Toast.makeText(this, "打不开这条日程", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val FILTER_LIFE = "life"
        private const val FILTER_SUBSCRIPTION = "subscription"
        private const val KEY_FILTER = "filter_kind"
    }
}
