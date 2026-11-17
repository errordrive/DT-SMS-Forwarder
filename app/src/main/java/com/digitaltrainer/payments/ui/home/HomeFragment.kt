package com.digitaltrainer.payments.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.digitaltrainer.payments.R
import com.digitaltrainer.payments.data.SmsLog
import com.digitaltrainer.payments.databinding.DialogLogDetailBinding
import com.digitaltrainer.payments.databinding.FragmentHomeBinding
import com.digitaltrainer.payments.ui.MainViewModel
import com.digitaltrainer.payments.util.ProviderFilter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var logAdapter: SmsLogAdapter

    private val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwitch()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        logAdapter = SmsLogAdapter { log ->
            showLogDetailDialog(log)
        }
        binding.rvLogs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = logAdapter
            itemAnimator = null
        }
    }

    private fun setupSwitch() {
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != (viewModel.isServiceRunning.value == true)) {
                viewModel.setServiceRunning(isChecked)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.isServiceRunning.observe(viewLifecycleOwner) { isRunning ->
            binding.switchService.isChecked = isRunning
            if (isRunning) {
                binding.tvStatusText.text = getString(R.string.status_running)
                binding.tvStatusText.setTextColor(Color.parseColor("#16A34A")) // green
                binding.ivStatusDot.setImageResource(R.drawable.ic_status_running)
            } else {
                binding.tvStatusText.text = getString(R.string.status_stopped)
                binding.tvStatusText.setTextColor(Color.parseColor("#808285")) // secondary
                binding.ivStatusDot.setImageResource(R.drawable.ic_status_stopped)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.bkashTodayCount.collectLatest { count ->
                        binding.tvCountBkash.text = count.toString()
                    }
                }
                launch {
                    viewModel.nagadTodayCount.collectLatest { count ->
                        binding.tvCountNagad.text = count.toString()
                    }
                }
                launch {
                    viewModel.rocketTodayCount.collectLatest { count ->
                        binding.tvCountRocket.text = count.toString()
                    }
                }
                launch {
                    viewModel.lastForwardedStamp.collectLatest { timestamp ->
                        if (timestamp != null && timestamp > 0L) {
                            val formatted = timeFormat.format(Date(timestamp))
                            binding.tvLastForwarded.text = getString(R.string.last_forwarded_format, formatted)
                        } else {
                            binding.tvLastForwarded.text = getString(R.string.last_forwarded_never)
                        }
                    }
                }
                launch {
                    viewModel.recentLogs.collectLatest { logs ->
                        logAdapter.submitList(logs)
                        if (logs.isEmpty()) {
                            binding.tvEmptyLogs.visibility = View.VISIBLE
                            binding.rvLogs.visibility = View.GONE
                        } else {
                            binding.tvEmptyLogs.visibility = View.GONE
                            binding.rvLogs.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun showLogDetailDialog(log: SmsLog) {
        val dialogBinding = DialogLogDetailBinding.inflate(layoutInflater)

        dialogBinding.dialogIvLogo.setImageResource(ProviderFilter.getLogoDrawableRes(log.from))
        dialogBinding.dialogTvSender.text = ProviderFilter.getDisplayName(log.from)

        val metaText = "${timeFormat.format(Date(log.createdAt))} • ${log.sim} • Attempts: ${log.attemptCount}"
        dialogBinding.dialogTvMeta.text = metaText

        dialogBinding.dialogTvStatus.text = log.status
        when (log.status) {
            SmsLog.STATUS_SENT -> dialogBinding.dialogTvStatus.setTextColor(Color.parseColor("#16A34A"))
            SmsLog.STATUS_QUEUED -> dialogBinding.dialogTvStatus.setTextColor(Color.parseColor("#F59E0B"))
            else -> dialogBinding.dialogTvStatus.setTextColor(Color.parseColor("#DC2626"))
        }

        dialogBinding.dialogTvRawText.text = log.text
        dialogBinding.dialogTvServerResponse.text = log.serverResponse ?: "No server response recorded yet."

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
