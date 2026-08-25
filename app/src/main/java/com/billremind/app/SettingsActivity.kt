package com.billremind.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.billremind.app.calendar.VendorGuard
import com.billremind.app.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val app get() = application as BillRemindApp

    private val calendarPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rowPermission.setOnClickListener {
            if (!app.deviceCalendar.hasPermission()) {
                calendarPermission.launch(Manifest.permission.READ_CALENDAR)
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

    private fun refresh() {
        val granted = app.deviceCalendar.hasPermission()
        binding.valuePermission.text = if (granted) "已授权读取" else "未授权，点这里申请"
        val list = if (granted) app.deviceCalendar.listCalendars().filter { it.visible } else emptyList()
        binding.valueCalendars.text = when {
            !granted -> "授权后可查看"
            list.isEmpty() -> "未发现日历本，请先打开一次系统日历"
            else -> list.joinToString("、") { it.name.ifBlank { it.displayName() } }
        }
        binding.valueCalendarCount.text = if (granted) "共 ${list.size} 本可见日历" else "需要 READ_CALENDAR"
    }

    private fun showCalendars() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            return
        }
        val list = app.deviceCalendar.listCalendars()
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
