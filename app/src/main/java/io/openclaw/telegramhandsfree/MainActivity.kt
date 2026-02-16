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
import io.openclaw.telegramhandsfree.config.ClawsfreeConfig
import io.openclaw.telegramhandsfree.voice.ClawsfreeForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var inputApiId: TextInputEditText
    private lateinit var inputApiHash: TextInputEditText
    private lateinit var inputPhone: TextInputEditText
    private lateinit var inputGroupId: TextInputEditText
    private lateinit var inputTopicId: TextInputEditText
    private lateinit var connectButton: Button
    private lateinit var connectChatButton: Button
    private lateinit var switchBluetoothMic: SwitchMaterial

    private lateinit var authGroupHeader: LinearLayout
    private lateinit var authGroupContent: LinearLayout
    private lateinit var authGroupChevron: TextView
    private lateinit var chatGroupHeader: LinearLayout
    private lateinit var chatGroupContent: LinearLayout
    private lateinit var chatGroupChevron: TextView
    private lateinit var otherSettingsHeader: LinearLayout
    private lateinit var otherSettingsContent: LinearLayout
    private lateinit var otherSettingsChevron: TextView

    private lateinit var authSection: LinearLayout
    private lateinit var authHintText: TextView
    private lateinit var inputAuthCode: TextInputEditText
    private lateinit var input2faPassword: TextInputEditText
    private lateinit var submitCodeButton: Button

    private lateinit var statusText: TextView
    private lateinit var activityText: TextView
    private lateinit var btnSetAssistant: Button
    private lateinit var btnFinishSetup: Button
    private lateinit var btnRecordToggle: Button

    private var isAuthConnected: Boolean = false
    private var isChatConnected: Boolean = false
    private var authPhase: String = "idle"

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                statusText.text = getString(R.string.status_connecting)
                beginAuthHandshake()
            } else {
                statusText.text = getString(R.string.status_error, "Permissions denied")
            }
        }

    private val requestAssistantRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            val roleGranted = isDefaultAssistant()
            updateAssistantButton()
            if (roleGranted) {
                Toast.makeText(this, "Clawsfree is now the default assistant", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Assistant role was not granted", Toast.LENGTH_LONG).show()
            }
        }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ClawsfreeForegroundService.ACTION_STATUS_UPDATE -> {
                    val status = intent.getStringExtra(ClawsfreeForegroundService.EXTRA_STATUS) ?: return
                    runOnUiThread { handleServiceStatus(status) }
                }
                ClawsfreeForegroundService.ACTION_ACTIVITY_UPDATE -> {
                    val activity = intent.getStringExtra(ClawsfreeForegroundService.EXTRA_ACTIVITY) ?: return
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
        connectChatButton = findViewById(R.id.connect_chat_button)
        switchBluetoothMic = findViewById(R.id.switch_bluetooth_mic)

        authGroupHeader = findViewById(R.id.auth_group_header)
        authGroupContent = findViewById(R.id.auth_group_content)
        authGroupChevron = findViewById(R.id.auth_group_chevron)
        chatGroupHeader = findViewById(R.id.chat_group_header)
        chatGroupContent = findViewById(R.id.chat_group_content)
        chatGroupChevron = findViewById(R.id.chat_group_chevron)
        otherSettingsHeader = findViewById(R.id.other_settings_header)
        otherSettingsContent = findViewById(R.id.other_settings_content)
        otherSettingsChevron = findViewById(R.id.other_settings_chevron)

        authSection = findViewById(R.id.auth_section)
        authHintText = findViewById(R.id.auth_hint_text)
        inputAuthCode = findViewById(R.id.input_auth_code)
        input2faPassword = findViewById(R.id.input_2fa_password)
        submitCodeButton = findViewById(R.id.submit_code_button)

        statusText = findViewById(R.id.status_text)
        activityText = findViewById(R.id.activity_text)
        btnSetAssistant = findViewById(R.id.btn_set_assistant)
        btnFinishSetup = findViewById(R.id.btn_finish_setup)
        btnRecordToggle = findViewById(R.id.btn_record_toggle)
        btnRecordToggle.visibility = View.GONE
        btnRecordToggle.setOnClickListener {
            val intent = Intent(this, ClawsfreeForegroundService::class.java).apply {
                action = ClawsfreeForegroundService.ACTION_TOGGLE_RECORDING
            }
            startService(intent)
        }
        handleActivityState("idle")

        loadSettingsIntoFields()
        isChatConnected = isChatConfigured()
        setAuthGroupCollapsed(collapsed = false)
        setChatGroupCollapsed(collapsed = true)
        setOtherSettingsVisible(isChatConnected)
        setOtherSettingsCollapsed(collapsed = true)
        updateRecordButtonVisibility()

        switchBluetoothMic.isChecked = ClawsfreeConfig.USE_BLUETOOTH_MIC
        switchBluetoothMic.setOnCheckedChangeListener { _, isChecked ->
            ClawsfreeConfig.setBluetoothMic(isChecked)
        }

        authGroupHeader.setOnClickListener {
            setAuthGroupCollapsed(collapsed = authGroupContent.visibility == View.VISIBLE)
        }
        chatGroupHeader.setOnClickListener {
            setChatGroupCollapsed(collapsed = chatGroupContent.visibility == View.VISIBLE)
        }
        otherSettingsHeader.setOnClickListener {
            setOtherSettingsCollapsed(collapsed = otherSettingsContent.visibility == View.VISIBLE)
        }

        connectButton.setOnClickListener { onAuthConnectTapped() }
        connectChatButton.setOnClickListener { onChatConnectTapped() }
        submitCodeButton.setOnClickListener { onSubmitCodeTapped() }
        btnSetAssistant.setOnClickListener { openAssistantSettings() }
        btnFinishSetup.setOnClickListener { onFinishSetupTapped() }
    }

    override fun onResume() {
        super.onResume()
        val statusFilter = IntentFilter().apply {
            addAction(ClawsfreeForegroundService.ACTION_STATUS_UPDATE)
            addAction(ClawsfreeForegroundService.ACTION_ACTIVITY_UPDATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, statusFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, statusFilter)
        }
        // Read last persisted status in case broadcasts were missed (e.g. during permission dialog)
        val prefs = getSharedPreferences(ClawsfreeForegroundService.PREFS_NAME, MODE_PRIVATE)
        prefs.getString(ClawsfreeForegroundService.KEY_LAST_STATUS, null)?.let {
            handleServiceStatus(it)
        }
        prefs.getString(ClawsfreeForegroundService.KEY_LAST_ACTIVITY, null)?.let {
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
        val apiId = ClawsfreeConfig.TELEGRAM_API_ID
        if (apiId > 0) inputApiId.setText(apiId.toString())
        inputApiHash.setText(ClawsfreeConfig.TELEGRAM_API_HASH)
        inputPhone.setText(ClawsfreeConfig.TELEGRAM_PHONE_NUMBER)
        val groupId = ClawsfreeConfig.TELEGRAM_GROUP_ID
        if (groupId != 0L) inputGroupId.setText(groupId.toString())
        val topicId = ClawsfreeConfig.TELEGRAM_TOPIC_ID
        if (topicId != 0L) inputTopicId.setText(topicId.toString())
        inputAuthCode.setText(ClawsfreeConfig.TELEGRAM_AUTH_CODE)
        input2faPassword.setText(ClawsfreeConfig.TELEGRAM_2FA_PASSWORD)
    }

    private fun handleServiceStatus(status: String) {
        when {
            status == "connected" -> {
                authPhase = "connected"
                isAuthConnected = true
                isChatConnected = isChatConfigured()
                statusText.text = getString(R.string.status_connected)
                authSection.visibility = View.GONE
                setAuthGroupCollapsed(collapsed = true)
                setChatGroupCollapsed(collapsed = isChatConnected)
                setOtherSettingsVisible(isChatConnected)
                updateRecordButtonVisibility()
                updateAssistantButton()
                // Show idle activity when first connected
                if (activityText.text.isNullOrBlank()) {
                    handleActivityState("idle")
                }
            }
            status == "connecting" -> {
                authPhase = "connecting"
                statusText.text = getString(R.string.status_connecting)
            }
            status == "waiting_code" -> {
                authPhase = "waiting_code"
                isAuthConnected = false
                statusText.text = getString(R.string.status_waiting_code)
                showAuthSection(getString(R.string.step2_subtitle))
            }
            status == "waiting_password" -> {
                authPhase = "waiting_password"
                isAuthConnected = false
                statusText.text = getString(R.string.status_waiting_password)
                showAuthSection(getString(R.string.step2_subtitle_2fa))
            }
            status.startsWith("error:") -> {
                authPhase = "error"
                val reason = status.removePrefix("error:")
                statusText.text = getString(R.string.status_error, reason)
            }
            status.startsWith("needs_config:") -> {
                authPhase = "needs_config"
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
                    Toast.makeText(this, "Clawsfree is already the default assistant", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Open Digital assistant app and select Clawsfree", Toast.LENGTH_LONG).show()
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
    private fun onAuthConnectTapped() {
        saveAuthSettingsForNewCycle()
        authPhase = "connecting"
        isAuthConnected = false
        setAuthGroupCollapsed(collapsed = false)
        setChatGroupCollapsed(collapsed = true)
        setOtherSettingsVisible(false)
        authSection.visibility = View.GONE
        updateRecordButtonVisibility()

        // Clear stale persisted status so old errors don't flash on resume
        getSharedPreferences(ClawsfreeForegroundService.PREFS_NAME, MODE_PRIVATE)
            .edit().remove(ClawsfreeForegroundService.KEY_LAST_STATUS).apply()

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
            beginAuthHandshake()
        }
    }

    private fun onChatConnectTapped() {
        saveChatConnectionSettings()
        isChatConnected = isChatConfigured()
        if (isChatConnected) {
            statusText.text = getString(R.string.status_chat_connected)
            setChatGroupCollapsed(collapsed = true)
            setOtherSettingsVisible(true)
            setOtherSettingsCollapsed(collapsed = false)
        } else {
            setChatGroupCollapsed(collapsed = false)
            setOtherSettingsVisible(false)
        }
        updateRecordButtonVisibility()
    }

    private fun onFinishSetupTapped() {
        setOtherSettingsCollapsed(collapsed = true)
        updateRecordButtonVisibility()
    }

    /**
     * Step 2: User received the code, entered it, taps "Submit Code".
     * Saves the code (and optional 2FA), restarts service so TDLib picks it up.
     */
    private fun onSubmitCodeTapped() {
        saveAuthCodeSettings()
        statusText.text = getString(R.string.status_connecting)
        val action = if (authPhase == "waiting_password") {
            ClawsfreeForegroundService.ACTION_SUBMIT_PASSWORD
        } else {
            ClawsfreeForegroundService.ACTION_SUBMIT_AUTH
        }
        startService(
            Intent(this, ClawsfreeForegroundService::class.java).apply {
                this.action = action
            }
        )
    }

    private fun showAuthSection(hint: String) {
        setAuthGroupCollapsed(collapsed = false)
        authSection.visibility = View.VISIBLE
        authHintText.text = hint
    }

    private fun saveAuthSettingsForNewCycle() {
        val apiId = inputApiId.text.toString().toIntOrNull() ?: 0
        val apiHash = inputApiHash.text.toString().trim()
        val phone = inputPhone.text.toString().trim()
        val groupId = inputGroupId.text.toString().toLongOrNull() ?: 0L
        val topicId = inputTopicId.text.toString().toLongOrNull() ?: 0L

        ClawsfreeConfig.save(
            apiId = apiId,
            apiHash = apiHash,
            phoneNumber = phone,
            authCode = "",
            twoFaPassword = "",
            groupId = groupId,
            topicId = topicId
        )
    }

    private fun saveAuthCodeSettings() {
        val apiId = inputApiId.text.toString().toIntOrNull() ?: 0
        val apiHash = inputApiHash.text.toString().trim()
        val phone = inputPhone.text.toString().trim()
        val groupId = ClawsfreeConfig.TELEGRAM_GROUP_ID
        val topicId = ClawsfreeConfig.TELEGRAM_TOPIC_ID
        val authCode = inputAuthCode.text.toString().trim()
        val twoFa = input2faPassword.text.toString()

        ClawsfreeConfig.save(
            apiId = apiId,
            apiHash = apiHash,
            phoneNumber = phone,
            authCode = authCode,
            twoFaPassword = twoFa,
            groupId = groupId,
            topicId = topicId
        )
    }

    private fun saveChatConnectionSettings() {
        val apiId = ClawsfreeConfig.TELEGRAM_API_ID
        val apiHash = ClawsfreeConfig.TELEGRAM_API_HASH
        val phone = ClawsfreeConfig.TELEGRAM_PHONE_NUMBER
        val authCode = ClawsfreeConfig.TELEGRAM_AUTH_CODE
        val twoFa = ClawsfreeConfig.TELEGRAM_2FA_PASSWORD
        val groupId = inputGroupId.text.toString().toLongOrNull() ?: 0L
        val topicId = inputTopicId.text.toString().toLongOrNull() ?: 0L

        ClawsfreeConfig.save(
            apiId = apiId,
            apiHash = apiHash,
            phoneNumber = phone,
            authCode = authCode,
            twoFaPassword = twoFa,
            groupId = groupId,
            topicId = topicId
        )
    }

    private fun isChatConfigured(): Boolean {
        return (inputGroupId.text?.toString()?.toLongOrNull() ?: 0L) != 0L
    }

    private fun setAuthGroupCollapsed(collapsed: Boolean) {
        authGroupContent.visibility = if (collapsed) View.GONE else View.VISIBLE
        authGroupChevron.text = if (collapsed) "▸" else "▾"
    }

    private fun setChatGroupCollapsed(collapsed: Boolean) {
        chatGroupContent.visibility = if (collapsed) View.GONE else View.VISIBLE
        chatGroupChevron.text = if (collapsed) "▸" else "▾"
        updateRecordButtonVisibility()
    }

    private fun setOtherSettingsVisible(visible: Boolean) {
        otherSettingsHeader.visibility = if (visible) View.VISIBLE else View.GONE
        otherSettingsContent.visibility = if (visible) otherSettingsContent.visibility else View.GONE
    }

    private fun setOtherSettingsCollapsed(collapsed: Boolean) {
        otherSettingsContent.visibility = if (collapsed) View.GONE else View.VISIBLE
        otherSettingsChevron.text = if (collapsed) "▸" else "▾"
        updateRecordButtonVisibility()
    }

    private fun updateRecordButtonVisibility() {
        btnRecordToggle.visibility = if (isChatConnected) View.VISIBLE else View.GONE
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
        val intent = Intent(this, ClawsfreeForegroundService::class.java).apply {
            action = ClawsfreeForegroundService.ACTION_ENSURE_RUNNING
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun beginAuthHandshake() {
        ensureForegroundServiceRunning()
        startService(
            Intent(this, ClawsfreeForegroundService::class.java).apply {
                action = ClawsfreeForegroundService.ACTION_BEGIN_AUTH
            }
        )
    }
}
