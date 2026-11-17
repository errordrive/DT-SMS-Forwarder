package com.digitaltrainer.payments.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.digitaltrainer.payments.data.SmsLog
import com.digitaltrainer.payments.databinding.ItemSmsLogBinding
import com.digitaltrainer.payments.util.ProviderFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsLogAdapter(
    private val onItemClick: (SmsLog) -> Unit
) : ListAdapter<SmsLog, SmsLogAdapter.LogViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemSmsLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class LogViewHolder(
        private val binding: ItemSmsLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SmsLog) {
            binding.root.setOnClickListener {
                onItemClick(item)
            }

            // Logo (§9A: 24dp at the left of each entry)
            val logoRes = ProviderFilter.getLogoDrawableRes(item.from)
            binding.ivProviderLogo.setImageResource(logoRes)

            binding.tvSender.text = ProviderFilter.getDisplayName(item.from)
            binding.tvSim.text = "• ${item.sim}"
            binding.tvTimestamp.text = dateFormat.format(Date(item.createdAt))

            when (item.status) {
                SmsLog.STATUS_SENT -> {
                    binding.tvStatus.text = "Sent"
                    binding.cardStatus.setCardBackgroundColor(Color.parseColor("#16A34A")) // success
                }
                SmsLog.STATUS_QUEUED -> {
                    binding.tvStatus.text = "Queued"
                    binding.cardStatus.setCardBackgroundColor(Color.parseColor("#F59E0B")) // warning
                }
                else -> {
                    binding.tvStatus.text = "Failed"
                    binding.cardStatus.setCardBackgroundColor(Color.parseColor("#DC2626")) // error
                }
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<SmsLog>() {
            override fun areItemsTheSame(oldItem: SmsLog, newItem: SmsLog): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: SmsLog, newItem: SmsLog): Boolean =
                oldItem == newItem
        }
    }
}
