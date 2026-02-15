package io.openclaw.telegramhandsfree

import android.Manifest
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import io.openclaw.telegramhandsfree.config.NovaConfig
import io.openclaw.telegramhandsfree.voice.NovaForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var inputApiId: TextInputEditText
    private lateinit var inputApiHash: TextInputEditText
    private lateinit var inputPhone: TextInputEditText
    private lateinit var inputGroupId: TextInputEditText
    private lateinit var inputTopicId: TextInputEditText
    private lateinit var connectButton: Button
    private lateinit var switchBluetoothMic: SwitchMaterial

    private lateinit var authSection: LinearLayout
    private lateinit var authHintText: TextView
    private lateinit var inputAuthCode: TextInputEditText
    private lateinit var input2faPassword: TextInputEditText
    private lateinit var submitCodeButton: Button

    private lateinit var statusText: TextView
    private lateinit var activityText: TextView
    private lateinit var btnSetAssistant: Button
    private lateinit var btnRecordToggle: Button

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                statusText.text = getString(R.string.status_connecting)
                ensureForegroundServiceRunning()
            } else {
                statusText.text = getString(R.string.status_error, "Permissions denied")
            }
        }

    private val requestAssistantRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            val roleGranted = isDefaultAssistant()
            updateAssistantButton()
            if (roleGranted) {
                Toast.makeText(this, "Nova is now the default assistant", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Assistant role was not granted", Toast.LENGTH_LONG).show()
            }
        }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                NovaForegroundService.ACTION_STATUS_UPDATE -> {
                    val status = intent.getStringExtra(NovaForegroundService.EXTRA_STATUS) ?: return
                    runOnUiThread { handleServiceStatus(status) }
                }
                NovaForegroundService.ACTION_ACTIVITY_UPDATE -> {
                    val activity = intent.getStringExtra(NovaForegroundService.EXTRA_ACTIVITY) ?: return
                    runOnUiThread { handleActivityState(activity) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputApiId = findViewById(R.id.input_api_id)
        inputApiHash = findViewById(R.id.input_api_hash)
        inputPhone = findViewById(R.id.input_phone)
        inputGroupId = findViewById(R.id.input_group_id)
        inputTopicId = findViewById(R.id.input_topic_id)
        connectButton = findViewById(R.id.connect_button)
        switchBluetoothMic = findViewById(R.id.switch_bluetooth_mic)

        authSection = findViewById(R.id.auth_section)
        authHintText = findViewById(R.id.auth_hint_text)
        inputAuthCode = findViewById(R.id.input_auth_code)
        input2faPassword = findViewById(R.id.input_2fa_password)
        submitCodeButton = findViewById(R.id.submit_code_button)

        statusText = findViewById(R.id.status_text)
        activityText = findViewById(R.id.activity_text)
        btnSetAssistant = findViewById(R.id.btn_set_assistant)
        btnSetAssistant.visibility = View.VISIBLE
        btnRecordToggle = findViewById(R.id.btn_record_toggle)
        btnRecordToggle.visibility = View.VISIBLE
        btnRecordToggle.setOnClickListener {
            val intent = Intent(this, NovaForegroundService::class.java).apply {
                action = NovaForegroundService.ACTION_TOGGLE_RECORDING
            }
            startService(intent)
        }
        handleActivityState("idle")

        loadSettingsIntoFields()

        switchBluetoothMic.isChecked = NovaConfig.USE_BLUETOOTH_MIC
        switchBluetoothMic.setOnCheckedChangeListener { _, isChecked ->
            NovaConfig.setBluetoothMic(isChecked)
        }

        connectButton.setOnClickListener { onConnectTapped() }
        submitCodeButton.setOnClickListener { onSubmitCodeTapped() }
        btnSetAssistant.setOnClickListener { openAssistantSettings() }
    }

    override fun onResume() {
        super.onResume()
        val statusFilter = IntentFilter().apply {
            addAction(NovaForegroundService.ACTION_STATUS_UPDATE)
            addAction(NovaForegroundService.ACTION_ACTIVITY_UPDATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, statusFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, statusFilter)
        }
        // Read last persisted status in case broadcasts were missed (e.g. during permission dialog)
        val prefs = getSharedPreferences(NovaForegroundService.PREFS_NAME, MODE_PRIVATE)
        prefs.getString(NovaForegroundService.KEY_LAST_STATUS, null)?.let {
            handleServiceStatus(it)
        }
        prefs.getString(NovaForegroundService.KEY_LAST_ACTIVITY, null)?.let {
            handleActivityState(it)
        }
        // Refresh assistant button state (user may have just returned from settings)
        updateAssistantButton()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
    }

    private fun loadSettingsIntoFields() {
        val apiId = NovaConfig.TELEGRAM_API_ID
        if (apiId > 0) inputApiId.setText(apiId.toString())
        inputApiHash.setText(NovaConfig.TELEGRAM_API_HASH)
        inputPhone.setText(NovaConfig.TELEGRAM_PHONE_NUMBER)
        val groupId = NovaConfig.TELEGRAM_GROUP_ID
        if (groupId != 0L) inputGroupId.setText(groupId.toString())
        val topicId = NovaConfig.TELEGRAM_TOPIC_ID
        if (topicId != 0L) inputTopicId.setText(topicId.toString())
        inputAuthCode.setText(NovaConfig.TELEGRAM_AUTH_CODE)
        input2faPassword.setText(NovaConfig.TELEGRAM_2FA_PASSWORD)
    }

    private fun handleServiceStatus(status: String) {
        when {
            status == "connected" -> {
                statusText.text = getString(R.string.status_connected)
                authSection.visibility = View.GONE
                updateAssistantButton()
                btnRecordToggle.visibility = View.VISIBLE
                // Show idle activity when first connected
                if (activityText.text.isNullOrBlank()) {
                    handleActivityState("idle")
                }
            }
            status == "connecting" -> {
                statusText.text = getString(R.string.status_connecting)
            }
            status == "waiting_code" -> {
                statusText.text = getString(R.string.status_waiting_code)
                showAuthSection(getString(R.string.step2_subtitle))
            }
            status == "waiting_password" -> {
                statusText.text = getString(R.string.status_waiting_password)
                showAuthSection(getString(R.string.step2_subtitle_2fa))
            }
            status.startsWith("error:") -> {
                val reason = status.removePrefix("error:")
                statusText.text = getString(R.string.status_error, reason)
            }
            status.startsWith("needs_config:") -> {
                statusText.text = getString(R.string.status_error, status.removePrefix("needs_config:"))
            }
        }
    }

    private fun handleActivityState(state: String) {
        activityText.text = when (state) {
            "recording" -> {
                btnRecordToggle.text = getString(R.string.btn_record_toggle_stop)
                getString(R.string.activity_recording)
            }
            "sending" -> {
                btnRecordToggle.text = getString(R.string.btn_record_toggle_start)
                getString(R.string.activity_sending)
            }
            "sent" -> {
                btnRecordToggle.text = getString(R.string.btn_record_toggle_start)
                getString(R.string.activity_sent)
            }
            else -> {
                btnRecordToggle.text = getString(R.string.btn_record_toggle_start)
                getString(R.string.activity_idle)
            }
        }
    }

    private fun openAssistantSettings() {
        try {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                    Toast.makeText(this, "Nova is already the default assistant", Toast.LENGTH_SHORT).show()
                    updateAssistantButton()
                    return
                }
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                requestAssistantRoleLauncher.launch(intent)
                return
            } else {
                Toast.makeText(this, "Assistant role is not available on this device", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Role request failed: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // Fallback: open default-apps settings directly
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            Toast.makeText(this, "Open Digital assistant app and select Nova", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open settings: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateAssistantButton() {
        btnSetAssistant.visibility = View.VISIBLE
        if (isDefaultAssistant()) {
            btnSetAssistant.text = getString(R.string.btn_assistant_active)
            btnSetAssistant.isEnabled = false
        } else {
            btnSetAssistant.text = getString(R.string.btn_set_assistant)
            btnSetAssistant.isEnabled = true
        }
    }

    private fun isDefaultAssistant(): Boolean {
        val roleManager = getSystemService(RoleManager::class.java)
        return roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
    }

    /**
     * Connect / Save: saves settings and starts (or keeps) the service.
     * If already authenticated, TDLib will go straight to Ready —
     * no SMS code is needed. Auth section only appears when TDLib asks for it.
     * NEVER stops the service —avoids TDLib DB lock.
     */
    private fun onConnectTapped() {
        saveConnectionSettings()
        // Clear stale persisted status so old errors don't flash on resume
        getSharedPreferences(NovaForegroundService.PREFS_NAME, MODE_PRIVATE)
            .edit().remove(NovaForegroundService.KEY_LAST_STATUS).apply()

        val missing = requiredPermissions().filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            // Permissions dialog will appear; service starts in the callback after grant
            statusText.text = "Requesting permissions…"
            requestPermissionsLauncher.launch(missing.toTypedArray())
        } else {
            // All permissions already granted — start service immediately
            statusText.text = getString(R.string.status_connecting)
            ensureForegroundServiceRunning()
        }
    }

    /**
     * Step 2: User received the code, entered it, taps "Submit Code".
     * Saves the code (and optional 2FA), restarts service so TDLib picks it up.
     */
    private fun onSubmitCodeTapped() {
        saveAllSettings()
        statusText.text = getString(R.string.status_connecting)
        // Don't restart service — tell the running TDLib client to submit the code
        val intent = Intent(this, NovaForegroundService::class.java).apply {
            action = NovaForegroundService.ACTION_SUBMIT_AUTH
        }
        startService(intent)
    }

    private fun showAuthSection(hint: String) {
        authSection.visibility = View.VISIBLE
        authHintText.text = hint
    }

    private fun saveConnectionSettings() {
        val apiId = inputApiId.text.toString().toIntOrNull() ?: 0
        val apiHash = inputApiHash.text.toString().trim()
        val phone = inputPhone.text.toString().trim()
        val groupId = inputGroupId.text.toString().toLongOrNull() ?: 0L
        val topicId = inputTopicId.text.toString().toLongOrNull() ?: 0L

        NovaConfig.save(
            apiId = apiId,
            apiHash = apiHash,
            phoneNumber = phone,
            authCode = "",
            twoFaPassword = "",
            groupId = groupId,
            topicId = topicId
        )
    }

    private fun saveAllSettings() {
        val apiId = inputApiId.text.toString().toIntOrNull() ?: 0
        val apiHash = inputApiHash.text.toString().trim()
        val phone = inputPhone.text.toString().trim()
        val groupId = inputGroupId.text.toString().toLongOrNull() ?: 0L
        val topicId = inputTopicId.text.toString().toLongOrNull() ?: 0L
        val authCode = inputAuthCode.text.toString().trim()
        val twoFa = input2faPassword.text.toString()

        NovaConfig.save(
            apiId = apiId,
            apiHash = apiHash,
            phoneNumber = phone,
            authCode = authCode,
            twoFaPassword = twoFa,
            groupId = groupId,
            topicId = topicId
        )
    }

    private fun requiredPermissions(): List<String> {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions
    }

    private fun ensureForegroundServiceRunning() {
        val intent = Intent(this, NovaForegroundService::class.java).apply {
            action = NovaForegroundService.ACTION_ENSURE_RUNNING
        }
        ContextCompat.startForegroundService(this, intent)
    }
}
