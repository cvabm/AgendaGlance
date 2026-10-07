package com.billremind.app

import android.content.Context

class CalendarFilters(context: Context) {
    private val prefs = context.getSharedPreferences("calendar_filters", Context.MODE_PRIVATE)

    /** null selects all visible calendars; an empty set intentionally selects none. */
    fun selectedIds(): Set<Long>? = prefs.getStringSet("selected_calendar_ids", null)
        ?.mapNotNull { it.toLongOrNull() }?.toSet()

    fun select(ids: Set<Long>?) {
        val editor = prefs.edit()
        if (ids == null) editor.remove("selected_calendar_ids")
        else editor.putStringSet("selected_calendar_ids", ids.map { it.toString() }.toSet())
        editor.apply()
    }
}
