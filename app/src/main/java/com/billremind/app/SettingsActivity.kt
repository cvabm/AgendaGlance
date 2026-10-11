package com.billremind.app

import android.Manifest
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.billremind.app.calendar.VendorGuard
import com.billremind.app.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val app get() = application as BillRemindApp
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
        if (granted) permission.onGranted()
        binding.valuePermission.text = when {
            granted -> "已授权读取"
            permission.permanentlyDenied() -> "未授权，点这里打开权限设置"
            else -> "未授权，点这里申请"
        }
    }
}
