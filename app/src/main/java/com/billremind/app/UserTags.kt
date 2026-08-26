package com.billremind.app

import android.content.Context

class UserTags(context: Context) {
    private val prefs = context.getSharedPreferences("user_tags", Context.MODE_PRIVATE)

    fun subscriptionIds(): Set<Long> =
        prefs.getStringSet(KEY_SUBSCRIPTION, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun setSubscribed(eventId: Long, subscribed: Boolean) {
        val next = prefs.getStringSet(KEY_SUBSCRIPTION, emptySet()).orEmpty().toMutableSet()
        val key = eventId.toString()
        if (subscribed) next.add(key) else next.remove(key)
        prefs.edit().putStringSet(KEY_SUBSCRIPTION, next).apply()
    }

    companion object {
        private const val KEY_SUBSCRIPTION = "subscription_event_ids"
    }
}
