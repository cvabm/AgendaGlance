package com.billremind.app

import android.app.Application
import com.billremind.app.calendar.DeviceCalendar

class BillRemindApp : Application() {
    val deviceCalendar by lazy { DeviceCalendar(this) }
    val userTags by lazy { UserTags(this) }
}
