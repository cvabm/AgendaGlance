package com.billremind.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.billremind.app.R
import com.billremind.app.calendar.CalendarRow
import com.billremind.app.calendar.DaysBadge
import com.billremind.app.calendar.EventSeries
import com.billremind.app.databinding.ItemDayGroupBinding
import com.billremind.app.databinding.ItemDayHeaderBinding
import com.billremind.app.databinding.ItemDayMemberBinding
import com.billremind.app.databinding.ItemEventBinding

class CalendarAdapter(
    private val onClick: (EventSeries) -> Unit,
    private val onLongClick: (EventSeries) -> Unit
) : ListAdapter<CalendarRow, RecyclerView.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<CalendarRow>() {
        override fun areItemsTheSame(oldItem: CalendarRow, newItem: CalendarRow): Boolean = when {
            oldItem is CalendarRow.Section && newItem is CalendarRow.Section ->
                oldItem.kind == newItem.kind
            oldItem is CalendarRow.DayGroup && newItem is CalendarRow.DayGroup ->
                oldItem.date == newItem.date
            oldItem is CalendarRow.Series && newItem is CalendarRow.Series ->
                oldItem.item.eventId == newItem.item.eventId
            else -> false
        }

        override fun areContentsTheSame(oldItem: CalendarRow, newItem: CalendarRow) = oldItem == newItem
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CalendarRow.Section -> TYPE_HEADER
        is CalendarRow.DayGroup -> TYPE_GROUP
        is CalendarRow.Series -> TYPE_EVENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemDayHeaderBinding.inflate(inflater, parent, false))
            TYPE_GROUP -> GroupHolder(ItemDayGroupBinding.inflate(inflater, parent, false))
            else -> EventHolder(ItemEventBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is CalendarRow.Section -> (holder as HeaderHolder).bind(row.title)
            is CalendarRow.DayGroup -> (holder as GroupHolder).bind(row, onClick, onLongClick)
            is CalendarRow.Series -> (holder as EventHolder).bind(row, onClick, onLongClick)
        }
    }

    class HeaderHolder(private val binding: ItemDayHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.label.text = title
        }
    }

    class GroupHolder(private val binding: ItemDayGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            row: CalendarRow.DayGroup,
            onClick: (EventSeries) -> Unit,
            onLongClick: (EventSeries) -> Unit
        ) {
            val badge = DaysBadge.of(row.date, row.renderedOn)
            val titles = mutableListOf<TextView>()
            binding.entries.removeAllViews()
            val inflater = LayoutInflater.from(binding.root.context)
            row.items.forEach { series ->
                val member = ItemDayMemberBinding.inflate(inflater, binding.entries, true)
                member.title.text = series.title
                titles += member.title
                member.root.setOnClickListener { onClick(series) }
                member.root.setOnLongClickListener {
                    onLongClick(series)
                    true
                }
            }
            paintToday(binding.root as MaterialCardView, titles, binding.badge, badge)
            binding.badge.text = row.items.first().daysLabel(row.renderedOn)
        }
    }

    class EventHolder(private val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            row: CalendarRow.Series,
            onClick: (EventSeries) -> Unit,
            onLongClick: (EventSeries) -> Unit
        ) {
            val series = row.item
            binding.title.text = series.title
            val badge = series.daysBadge(row.renderedOn)
            paintToday(binding.root as MaterialCardView, listOf(binding.title), binding.days, badge)
            binding.days.text = row.dayLabel
            binding.root.setOnClickListener { onClick(series) }
            binding.root.setOnLongClickListener {
                onLongClick(series)
                true
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 1
        private const val TYPE_EVENT = 2
        private const val TYPE_GROUP = 3

        private fun paintToday(card: MaterialCardView, titles: List<TextView>, badgeView: TextView, badge: DaysBadge) {
            val today = badge == DaysBadge.TODAY
            val rowHeight = card.resources.displayMetrics.density * if (today) {
                if (titles.size > 1) 56 else 72
            } else {
                if (titles.size > 1) 40 else 48
            }
            titles.forEach { (it.parent as? View)?.minimumHeight = rowHeight.toInt() }
            if (today) {
                card.setCardBackgroundColor(ContextCompat.getColor(card.context, R.color.badge_today_bg))
                card.strokeWidth = 0
                titles.forEach { it.setTextColor(ContextCompat.getColor(card.context, R.color.badge_today_fg)) }
                badgeView.setBackgroundResource(R.drawable.bg_badge_today)
                badgeView.setTextColor(ContextCompat.getColor(card.context, R.color.summary_fg))
            } else {
                card.setCardBackgroundColor(ContextCompat.getColor(card.context, R.color.md_surface_container_low))
                card.strokeWidth = card.resources.displayMetrics.density.toInt().coerceAtLeast(1)
                card.setStrokeColor(ContextCompat.getColor(card.context, R.color.md_surface_variant))
                titles.forEach { it.setTextColor(ContextCompat.getColor(card.context, R.color.md_on_surface)) }
                badgeView.setBackgroundResource(background(badge))
                badgeView.setTextColor(ContextCompat.getColor(card.context, foreground(badge)))
            }
        }

        private fun foreground(badge: DaysBadge) = when (badge) {
            DaysBadge.OVERDUE -> R.color.badge_overdue_fg
            DaysBadge.TODAY -> R.color.badge_today_fg
            DaysBadge.SOON -> R.color.badge_soon_fg
            DaysBadge.THIS_YEAR -> R.color.badge_ok_fg
            DaysBadge.NEXT_YEAR -> R.color.badge_next_year_fg
            DaysBadge.YEAR_AFTER -> R.color.badge_year_after_fg
            DaysBadge.LATER -> R.color.badge_later_fg
        }

        private fun background(badge: DaysBadge) = when (badge) {
            DaysBadge.OVERDUE -> R.drawable.bg_badge_overdue
            DaysBadge.TODAY -> R.drawable.bg_badge_today
            DaysBadge.SOON -> R.drawable.bg_badge_soon
            DaysBadge.THIS_YEAR -> R.drawable.bg_badge_ok
            DaysBadge.NEXT_YEAR -> R.drawable.bg_badge_next_year
            DaysBadge.YEAR_AFTER -> R.drawable.bg_badge_year_after
            DaysBadge.LATER -> R.drawable.bg_badge_later
        }
    }
}
