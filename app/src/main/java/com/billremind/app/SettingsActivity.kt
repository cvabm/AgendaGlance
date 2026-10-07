package com.billremind.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.billremind.app.calendar.CalendarInfo
import com.billremind.app.calendar.VendorGuard
import com.billremind.app.calendar.cancellableCalendarQuery
import com.billremind.app.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val app get() = application as BillRemindApp
    private var calendarJob: Job? = null
    private val permission: CalendarPermission by lazy {
        CalendarPermission(this) { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }
    }

    private val calendarPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permission.onResult(granted); refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rowPermission.setOnClickListener {
            if (!app.deviceCalendar.hasPermission()) {
                permission.request()
            }
        }
        binding.rowCalendars.setOnClickListener { showCalendars() }
        binding.rowOpenCalendar.setOnClickListener {
            if (!VendorGuard.openSystemCalendar(this)) {
                Toast.makeText(this, "没有找到系统日历", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rowAbout.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("系统日历")
                .setMessage(
                    "只读取手机系统日历（小米日历、谷歌日历等）里已有的日程并展示。\n\n" +
                        "应用不保存账单、不写入日历。添加或修改请用系统日历。\n\n" +
                        "当前设备：${VendorGuard.manufacturer()}"
                )
                .setPositiveButton("好", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onStop() {
        calendarJob?.cancel()
        super.onStop()
    }

    private fun refresh() {
        val granted = app.deviceCalendar.hasPermission()
        if (granted) permission.onGranted()
        binding.valuePermission.text = when {
            granted -> "已授权读取"
            permission.permanentlyDenied() -> "未授权，点这里打开权限设置"
            else -> "未授权，点这里申请"
        }
        if (!granted) {
            calendarJob?.cancel()
            binding.valueCalendars.text = "授权后可查看"
            binding.valueCalendarCount.text = "需要日历读取权限"
        } else readCalendars(showDialog = false)
    }

    private fun showCalendars() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permission.request()
            return
        }
        readCalendars(showDialog = true)
    }

    private fun readCalendars(showDialog: Boolean) {
        calendarJob?.cancel()
        binding.valueCalendars.text = "正在读取日历本…"
        binding.valueCalendarCount.text = ""
        calendarJob = lifecycleScope.launch {
            try {
                val list = cancellableCalendarQuery { app.deviceCalendar.listCalendars(it) }
                val visible = list.filter { it.visible }
                binding.valueCalendars.text = if (visible.isEmpty()) "未发现可见日历本，请检查系统日历" else
                    visible.joinToString("、") { it.titleName() }
                binding.valueCalendarCount.text = "共 ${visible.size} 本可见日历"
                if (showDialog) showCalendarsDialog(list)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                binding.valueCalendars.text = "读取失败，点这里重试"
                binding.valueCalendarCount.text = "请检查日历权限及系统日历是否可用"
            }
        }
    }

    private fun showCalendarsDialog(list: List<CalendarInfo>) {
        if (list.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("没有日历")
                .setMessage("小米日历第一次使用前可能是空的。先打开一次系统日历，再回来刷新。")
                .setPositiveButton("打开日历") { _, _ -> VendorGuard.openSystemCalendar(this) }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        val labels = list.map {
            val vis = if (it.visible) "可见" else "已隐藏"
            "${it.displayName()} · $vis"
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("系统里的日历")
            .setItems(labels, null)
            .setPositiveButton("好", null)
            .show()
    }
}
