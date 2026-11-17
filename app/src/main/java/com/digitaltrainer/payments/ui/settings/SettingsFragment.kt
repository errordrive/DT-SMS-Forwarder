package com.digitaltrainer.payments.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.digitaltrainer.payments.R
import com.digitaltrainer.payments.databinding.FragmentSettingsBinding
import com.digitaltrainer.payments.ui.MainViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private val requestSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updatePermissionStatus()
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updatePermissionStatus()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initFields()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun initFields() {
        binding.etWebhookUrl.setText(viewModel.getWebhookUrl())
        binding.etGatewayKey.setText(viewModel.getGatewayKey())
        binding.etDeviceId.setText(viewModel.getDeviceId())
    }

    private fun setupListeners() {
        binding.btnSaveSettings.setOnClickListener {
            val url = binding.etWebhookUrl.text?.toString()?.trim() ?: ""
            val key = binding.etGatewayKey.text?.toString()?.trim() ?: ""
            val deviceId = binding.etDeviceId.text?.toString()?.trim() ?: ""

            val saveResult = viewModel.saveSettings(url, key, deviceId)
            if (saveResult.isSuccess) {
                binding.tilDeviceId.error = null
                // Show normalized lowercase text in input
                binding.etDeviceId.setText(viewModel.getDeviceId())
                Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
            } else {
                val errMessage = saveResult.exceptionOrNull()?.message ?: getString(R.string.device_id_required)
                binding.tilDeviceId.error = errMessage
            }
        }

        binding.btnTestConnection.setOnClickListener {
            val url = binding.etWebhookUrl.text?.toString()?.trim() ?: ""
            val key = binding.etGatewayKey.text?.toString()?.trim() ?: ""
            val deviceId = binding.etDeviceId.text?.toString()?.trim() ?: ""

            // Per §7: If Device ID is empty, show "Enter a Device ID first"
            if (deviceId.isEmpty()) {
                binding.tilDeviceId.error = getString(R.string.enter_device_id_first)
                Toast.makeText(requireContext(), R.string.enter_device_id_first, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            binding.tilDeviceId.error = null

            binding.pbTestProgress.visibility = View.VISIBLE
            binding.btnTestConnection.isEnabled = false

            viewModel.testConnection(url, key, deviceId) { gatewayMsg, heartbeatMsg, isSuccess ->
                binding.pbTestProgress.visibility = View.GONE
                binding.btnTestConnection.isEnabled = true

                val title = if (isSuccess) "Connection Test: Passed" else "Connection Test: Failed"
                val message = "$gatewayMsg\n$heartbeatMsg"

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        binding.btnPermSms.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.RECEIVE_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestSmsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            } else {
                openAppDetailsSettings()
            }
        }

        binding.btnPermNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnClickListener
                }
            }
            openAppDetailsSettings()
        }

        binding.btnPermBattery.setOnClickListener {
            requestIgnoreBatteryOptimizations()
        }
    }

    private fun updatePermissionStatus() {
        val context = context ?: return

        // SMS Permission
        val hasSms = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasSms) {
            binding.btnPermSms.text = getString(R.string.perm_granted)
            binding.btnPermSms.isEnabled = false
        } else {
            binding.btnPermSms.text = getString(R.string.grant)
            binding.btnPermSms.isEnabled = true
        }

        // Notification Permission
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasNotification) {
            binding.btnPermNotification.text = getString(R.string.perm_granted)
            binding.btnPermNotification.isEnabled = false
        } else {
            binding.btnPermNotification.text = getString(R.string.grant)
            binding.btnPermNotification.isEnabled = true
        }

        // Battery Optimization
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

        if (isIgnoringBattery) {
            binding.btnPermBattery.text = getString(R.string.perm_unrestricted)
            binding.btnPermBattery.isEnabled = false
        } else {
            binding.btnPermBattery.text = getString(R.string.optimize_disable)
            binding.btnPermBattery.isEnabled = true
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val context = context ?: return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                openAppDetailsSettings()
            }
        }
    }

    private fun openAppDetailsSettings() {
        val context = context ?: return
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Ignored
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
