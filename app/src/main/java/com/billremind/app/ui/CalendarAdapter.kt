package com.billremind.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.billremind.app.R
import com.billremind.app.calendar.CalendarRow
import com.billremind.app.calendar.EventSeries
import com.billremind.app.calendar.RecurrenceKind
import com.billremind.app.databinding.ItemDayHeaderBinding
import com.billremind.app.databinding.ItemEventBinding

class CalendarAdapter(
    private val onClick: (EventSeries) -> Unit
) : ListAdapter<CalendarRow, RecyclerView.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<CalendarRow>() {
        override fun areItemsTheSame(oldItem: CalendarRow, newItem: CalendarRow): Boolean = when {
            oldItem is CalendarRow.Section && newItem is CalendarRow.Section ->
                oldItem.kind == newItem.kind
            oldItem is CalendarRow.Series && newItem is CalendarRow.Series ->
                oldItem.item.eventId == newItem.item.eventId
            else -> false
        }

        override fun areContentsTheSame(oldItem: CalendarRow, newItem: CalendarRow) = oldItem == newItem
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CalendarRow.Section -> TYPE_HEADER
        is CalendarRow.Series -> TYPE_EVENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemDayHeaderBinding.inflate(inflater, parent, false))
        } else {
            EventHolder(ItemEventBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is CalendarRow.Section -> (holder as HeaderHolder).bind(row)
            is CalendarRow.Series -> (holder as EventHolder).bind(row.item, onClick)
        }
    }

    class HeaderHolder(private val binding: ItemDayHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: CalendarRow.Section) {
            binding.label.text = row.title
        }
    }

    class EventHolder(private val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(series: EventSeries, onClick: (EventSeries) -> Unit) {
            val color = if (series.color != 0) series.color else Color.parseColor("#0F6B63")
            binding.colorBar.setBackgroundColor(color or 0xFF000000.toInt())
            val (kindBg, kindFg) = kindColors(series.kind)
            binding.badge.text = series.kind.label
            binding.badge.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(binding.root.context, kindBg)
            )
            binding.badge.setTextColor(ContextCompat.getColor(binding.root.context, kindFg))
            binding.title.text = series.title
            binding.subtitle.text = series.subtitle()
            binding.days.text = series.daysLabel()
            val days = series.daysUntil()
            val badgeBg = when {
                days <= 0 -> R.drawable.bg_badge_overdue
                days <= 3 -> R.drawable.bg_badge_soon
                else -> R.drawable.bg_badge_ok
            }
            val badgeFg = when {
                days <= 0 -> R.color.badge_overdue_fg
                days <= 3 -> R.color.badge_soon_fg
                else -> R.color.badge_ok_fg
            }
            binding.days.setBackgroundResource(badgeBg)
            binding.days.setTextColor(ContextCompat.getColor(binding.root.context, badgeFg))
            binding.root.setOnClickListener { onClick(series) }
        }
    }

    companion object {
        private const val TYPE_HEADER = 1
        private const val TYPE_EVENT = 2

        private fun kindColors(kind: RecurrenceKind): Pair<Int, Int> = when (kind) {
            RecurrenceKind.MONTHLY -> R.color.kind_monthly_bg to R.color.kind_monthly_fg
            RecurrenceKind.YEARLY -> R.color.kind_yearly_bg to R.color.kind_yearly_fg
            RecurrenceKind.ONCE -> R.color.kind_once_bg to R.color.kind_once_fg
            RecurrenceKind.WEEKLY -> R.color.kind_weekly_bg to R.color.kind_weekly_fg
            RecurrenceKind.DAILY -> R.color.kind_daily_bg to R.color.kind_daily_fg
            RecurrenceKind.QUARTERLY -> R.color.kind_quarterly_bg to R.color.kind_quarterly_fg
            RecurrenceKind.OTHER -> R.color.kind_other_bg to R.color.kind_other_fg
        }
    }
}
