package io.openclaw.telegramhandsfree.telegram

import android.os.Build
import android.util.Log
import io.openclaw.telegramhandsfree.config.ClawsfreeConfig
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

class TdLibReflectiveBridge(
    private val onReady: () -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onWaitingCode: () -> Unit,
    private val onWaitingPassword: () -> Unit,
    private val onIncomingVoice: (chatId: Long, file: File, senderUserId: Long?) -> Unit
) {
    // TODO(MVP-next): replace reflective invocation with strongly-typed TDLib bindings when final dependency packaging is locked.
    // TODO(MVP-next): replace temporary auth placeholders with secure provisioning before release.
    private var tdApiClass: Class<*>? = null
    private var clientClass: Class<*>? = null
    private var clientInstance: Any? = null
    private lateinit var databaseDirectory: File
    private lateinit var filesDirectory: File

    private val pendingVoiceDownloads = ConcurrentHashMap<Int, PendingVoiceDownload>()

    fun initialize(appFilesDir: File, appCacheDir: File): Boolean {
        return runCatching {
            databaseDirectory = File(appFilesDir, "tdlib-db").apply { mkdirs() }
            filesDirectory = File(appCacheDir, "tdlib-files").apply { mkdirs() }

            tdApiClass = Class.forName("org.drinkless.tdlib.TdApi")
            clientClass = Class.forName("org.drinkless.tdlib.Client")

            val updateHandler = resultHandler { update ->
                handleUpdate(update)
            }

            val exceptionHandler = exceptionHandler { throwable ->
                onError("TDLib exception: ${throwable.message ?: "unknown"}")
            }

            val createMethod = clientClass!!.methods.first { it.name == "create" && it.parameterCount == 3 }
            clientInstance = createMethod.invoke(null, updateHandler, exceptionHandler, exceptionHandler)

            onStatus("TDLib client created, waiting authorization state updates")
            true
        }.getOrElse { error ->
            onError("TDLib bridge init failed: ${error.message ?: "unknown error"}")
            false
        }
    }

    /** Called by UI when user enters auth code after WaitingCode state */
    fun submitAuthCode() {
        sendAuthCodeOrFail()
    }

    /** Called by UI when user enters 2FA password after WaitingPassword state */
    fun submitPassword() {
        sendPasswordOrFail()
    }

    fun refreshTargetChatBinding() {
        loadChats()
        val targetChatId = ClawsfreeConfig.TELEGRAM_GROUP_ID
        if (targetChatId != 0L) {
            openChat(targetChatId)
        }
    }

    fun sendVoiceMessage(chatId: Long, file: File): Boolean {
        val client = clientInstance ?: return false
        return runCatching {
            val inputFileLocal = newTdApiObject("InputFileLocal", file.absolutePath)
            val inputMessageVoiceNote = newTdApiObject("InputMessageVoiceNote")
            setFieldAny(inputMessageVoiceNote, inputFileLocal, "voiceNote", "voice_note")
            setFieldAny(inputMessageVoiceNote, null, "waveform")
            setFieldAny(inputMessageVoiceNote, 0, "duration")

            val sendMessage = newTdApiObject("SendMessage")
            setFieldAny(sendMessage, chatId, "chatId", "chat_id")
            setFieldAny(sendMessage, ClawsfreeConfig.TELEGRAM_TOPIC_ID, "messageThreadId", "message_thread_id")
            setFieldAny(sendMessage, null, "replyTo", "reply_to")
            setFieldAny(sendMessage, null, "options")
            setFieldAny(sendMessage, null, "replyMarkup", "reply_markup")
            setFieldAny(sendMessage, inputMessageVoiceNote, "inputMessageContent", "input_message_content")

            sendFunction(client, sendMessage) { response ->
                val responseName = response.javaClass.simpleName
                if (responseName == "Error") {
                    val code = getFieldValue(response, "code") ?: "?"
                    val message = getFieldValue(response, "message") ?: "unknown"
                    onError("TDLib send failed code=$code message=$message")
                }
            }
            true
        }.getOrElse { error ->
            onError("TDLib send voice bridge failure: ${error.message ?: "unknown"}")
            false
        }
    }

    private fun handleUpdate(update: Any) {
        when (update.javaClass.simpleName) {
            "UpdateAuthorizationState" -> handleAuthorizationUpdate(update)
            "UpdateNewMessage" -> handleNewMessage(update)
            "UpdateChatLastMessage" -> handleChatLastMessage(update)
            "UpdateFile" -> handleUpdateFile(update)
        }
    }

    private fun handleAuthorizationUpdate(update: Any) {
        val authState = getFieldValueAny(update, "authorizationState", "authorization_state") ?: return
        when (authState.javaClass.simpleName) {
            "AuthorizationStateWaitTdlibParameters" -> sendTdlibParameters()
            "AuthorizationStateWaitPhoneNumber" -> sendPhoneNumberOrFail()
            "AuthorizationStateWaitCode" -> sendAuthCodeOrFail()
            "AuthorizationStateWaitPassword" -> sendPasswordOrFail()
            "AuthorizationStateReady" -> {
                onStatus("TDLib authorization ready")
                loadChats()
                onReady()
            }
            "AuthorizationStateClosed" -> onError("TDLib authorization closed")
            else -> onStatus("TDLib authorization state: ${authState.javaClass.simpleName}")
        }
    }

    private fun sendTdlibParameters() {
        val client = clientInstance ?: return
        runCatching {
            val params = newTdApiObject("SetTdlibParameters")
            setFieldAny(params, false, "useTestDc", "use_test_dc")
            setFieldAny(params, databaseDirectory.absolutePath, "databaseDirectory", "database_directory")
            setFieldAny(params, filesDirectory.absolutePath, "filesDirectory", "files_directory")
            setFieldAny(params, ByteArray(0), "databaseEncryptionKey", "database_encryption_key")
            setFieldAny(params, true, "useFileDatabase", "use_file_database")
            setFieldAny(params, true, "useChatInfoDatabase", "use_chat_info_database")
            setFieldAny(params, true, "useMessageDatabase", "use_message_database")
            setFieldAny(params, false, "useSecretChats", "use_secret_chats")
            setFieldAny(params, ClawsfreeConfig.TELEGRAM_API_ID, "apiId", "api_id")
            setFieldAny(params, ClawsfreeConfig.TELEGRAM_API_HASH, "apiHash", "api_hash")
            setFieldAny(params, "en", "systemLanguageCode", "system_language_code")
            setFieldAny(params, Build.MODEL ?: "Android", "deviceModel", "device_model")
            setFieldAny(params, Build.VERSION.RELEASE ?: "Android", "systemVersion", "system_version")
            setFieldAny(params, "0.1.0", "applicationVersion", "application_version")

            sendFunction(client, params) { response ->
                if (response.javaClass.simpleName == "Error") {
                    onError("SetTdlibParameters failed: ${getFieldValue(response, "message")}")
                }
            }
        }.onFailure {
            onError("Failed to send TDLib parameters: ${it.message}")
        }
    }

    private fun sendPhoneNumberOrFail() {
        val client = clientInstance ?: return
        val phone = ClawsfreeConfig.TELEGRAM_PHONE_NUMBER
        if (phone.isBlank()) {
            onError("Authorization needs phone number. Enter it in Settings.")
            return
        }

        runCatching {
            val phoneSettings = newTdApiObject("PhoneNumberAuthenticationSettings")
            setFieldAny(phoneSettings, false, "allowFlashCall", "allow_flash_call")
            setFieldAny(phoneSettings, false, "allowMissedCall", "allow_missed_call")
            setFieldAny(phoneSettings, false, "isCurrentPhoneNumber", "is_current_phone_number")
            setFieldAny(phoneSettings, false, "allowSmsRetrieverApi", "allow_sms_retriever_api")

            val setPhone = newTdApiObject("SetAuthenticationPhoneNumber", phone, phoneSettings)
            sendFunction(client, setPhone) { response ->
                if (response.javaClass.simpleName == "Error") {
                    onError("SetAuthenticationPhoneNumber failed: ${getFieldValue(response, "message")}")
                }
            }
        }.onFailure {
            onError("Failed to send phone number: ${it.message}")
        }
    }

    private fun sendAuthCodeOrFail() {
        val client = clientInstance ?: return
        val code = ClawsfreeConfig.TELEGRAM_AUTH_CODE
        if (code.isBlank()) {
            onWaitingCode()
            return
        }

        runCatching {
            val checkCode = newTdApiObject("CheckAuthenticationCode", code)
            sendFunction(client, checkCode) { response ->
                if (response.javaClass.simpleName == "Error") {
                    onError("CheckAuthenticationCode failed: ${getFieldValue(response, "message")}")
                }
            }
        }.onFailure {
            onError("Failed to send auth code: ${it.message}")
        }
    }

    private fun sendPasswordOrFail() {
        val client = clientInstance ?: return
        val password = ClawsfreeConfig.TELEGRAM_2FA_PASSWORD
        if (password.isBlank()) {
            onWaitingPassword()
            return
        }

        runCatching {
            val checkPassword = newTdApiObject("CheckAuthenticationPassword", password)
            sendFunction(client, checkPassword) { response ->
                if (response.javaClass.simpleName == "Error") {
                    onError("CheckAuthenticationPassword failed: ${getFieldValue(response, "message")}")
                }
            }
        }.onFailure {
            onError("Failed to send 2FA password: ${it.message}")
        }
    }

    /**
     * TDLib must load the chat list before it knows about any chat.
     * Without this, sendMessage returns "Chat not found" (code 400).
     */
    private fun loadChats() {
        val client = clientInstance ?: return
        runCatching {
            // Load main chat list so TDLib caches our target group
            val chatListMain = newTdApiObject("ChatListMain")
            val loadChats = newTdApiObject("LoadChats", chatListMain, 100)
            sendFunction(client, loadChats) { response ->
                if (response.javaClass.simpleName == "Error") {
                    // 404 = no more chats to load, that's OK
                    val code = (getFieldValue(response, "code") as? Number)?.toInt() ?: 0
                    if (code != 404) {
                        Log.w(TAG, "LoadChats failed: ${getFieldValue(response, "message")}")
                    }
                }
                // Also explicitly open the target chat
                val targetChatId = ClawsfreeConfig.TELEGRAM_GROUP_ID
                if (targetChatId != 0L) {
                    openChat(targetChatId)
                }
            }
        }.onFailure {
            Log.w(TAG, "Failed to load chats: ${it.message}")
        }
    }

    private fun openChat(chatId: Long) {
        val client = clientInstance ?: return
        runCatching {
            val getChat = newTdApiObject("GetChat", chatId)
            sendFunction(client, getChat) { response ->
                if (response.javaClass.simpleName == "Error") {
                    Log.w(TAG, "GetChat($chatId) failed: ${getFieldValue(response, "message")}")
                } else {
                    Log.i(TAG, "Chat $chatId loaded into TDLib cache")
                }
            }
        }.onFailure {
            Log.w(TAG, "Failed to open chat $chatId: ${it.message}")
        }
    }

    private fun handleNewMessage(update: Any) {
        val message = getFieldValueAny(update, "message") ?: return
        processIncomingMessage(message, source = "UpdateNewMessage")
    }

    private fun handleChatLastMessage(update: Any) {
        val message = getFieldValueAny(update, "lastMessage", "last_message") ?: return
        processIncomingMessage(message, source = "UpdateChatLastMessage")
    }

    private fun processIncomingMessage(message: Any, source: String) {
        val chatId = ((getFieldValueAny(message, "chatId", "chat_id") as? Number)?.toLong()) ?: return
        val targetChatId = ClawsfreeConfig.TELEGRAM_GROUP_ID
        if (chatId != targetChatId) {
            Log.i(TAG, "Ignoring message source=$source from chatId=$chatId, targetChatId=$targetChatId")
            return
        }

        // Skip messages sent by this app (our own outgoing voice notes)
        val isOutgoing = (getFieldValueAny(message, "isOutgoing", "is_outgoing") as? Boolean) == true
        if (isOutgoing) {
            Log.i(TAG, "Ignoring outgoing message source=$source in target chatId=$chatId")
            return
        }

        val content = getFieldValueAny(message, "content") ?: return
        val contentType = content.javaClass.simpleName
        val senderId = extractSenderUserId(message)

        val mediaFile = when (content.javaClass.simpleName) {
            "MessageVoiceNote" -> {
                val voiceNote = getFieldValueAny(content, "voiceNote", "voice_note") ?: return
                getFieldValueAny(voiceNote, "voice") ?: return
            }
            "MessageAudio" -> {
                val audio = getFieldValueAny(content, "audio") ?: return
                getFieldValueAny(audio, "audio") ?: return
            }
            "MessageDocument" -> {
                val messageDocument = getFieldValueAny(content, "document") ?: return
                val mime = (getFieldValueAny(messageDocument, "mimeType", "mime_type") as? String).orEmpty()
                if (!mime.startsWith("audio/")) {
                    Log.i(TAG, "Ignoring MessageDocument source=$source with non-audio mimeType=$mime")
                    return
                }
                getFieldValueAny(messageDocument, "document") ?: return
            }
            else -> {
                Log.i(TAG, "Ignoring non-media contentType=$contentType source=$source chatId=$chatId")
                return
            }
        }

        val fileId = (getFieldValueAny(mediaFile, "id") as? Number)?.toInt() ?: run {
            Log.w(TAG, "Unable to resolve TDLib file id for source=$source contentType=$contentType chatId=$chatId")
            return
        }
        val local = getFieldValueAny(mediaFile, "local")
        val path = (local?.let { getFieldValueAny(it, "path") } as? String).orEmpty()
        val downloaded = (local?.let { getFieldValueAny(it, "isDownloadingCompleted", "is_downloading_completed") } as? Boolean) == true

        Log.i(TAG, "Incoming media source=$source contentType=$contentType chatId=$chatId fileId=$fileId downloaded=$downloaded path=${path.ifBlank { "<empty>" }}")

        if (downloaded && path.isNotBlank()) {
            onIncomingVoice(chatId, File(path), senderId)
            return
        }

        pendingVoiceDownloads[fileId] = PendingVoiceDownload(chatId, senderId)
        requestFileDownload(fileId)
    }

    private fun handleUpdateFile(update: Any) {
        val file = getFieldValueAny(update, "file") ?: return
        val fileId = (getFieldValueAny(file, "id") as? Number)?.toInt() ?: return
        val pending = pendingVoiceDownloads[fileId] ?: return

        val local = getFieldValueAny(file, "local") ?: return
        val path = (getFieldValueAny(local, "path") as? String).orEmpty()
        val downloaded = (getFieldValueAny(local, "isDownloadingCompleted", "is_downloading_completed") as? Boolean) == true

        if (downloaded && path.isNotBlank()) {
            pendingVoiceDownloads.remove(fileId)
            Log.i(TAG, "Downloaded pending media fileId=$fileId path=$path")
            onIncomingVoice(pending.chatId, File(path), pending.senderUserId)
        }
    }

    private fun requestFileDownload(fileId: Int) {
        val client = clientInstance ?: return
        runCatching {
            val downloadFile = newTdApiObject("DownloadFile", fileId, 32, 0L, 0L, false)
            sendFunction(client, downloadFile) { response ->
                if (response.javaClass.simpleName == "Error") {
                    onError("DownloadFile failed for fileId=$fileId: ${getFieldValue(response, "message")}")
                }
            }
        }.onFailure {
            onError("Failed to request file download for fileId=$fileId: ${it.message}")
        }
    }

    private fun extractSenderUserId(message: Any): Long? {
        val senderId = getFieldValueAny(message, "senderId", "sender_id") ?: return null
        if (senderId.javaClass.simpleName != "MessageSenderUser") return null
        return (getFieldValueAny(senderId, "userId", "user_id") as? Number)?.toLong()
    }

    private fun sendFunction(client: Any, functionObject: Any, callback: (Any) -> Unit) {
        val resultHandler = resultHandler(callback)
        val sendMethod = client.javaClass.methods.first { it.name == "send" && it.parameterCount == 2 }
        sendMethod.invoke(client, functionObject, resultHandler)
    }

    private fun resultHandler(block: (Any) -> Unit): Any {
        val handlerInterface = Class.forName("org.drinkless.tdlib.Client\$ResultHandler")
        val invocationHandler = InvocationHandler { _, method, args ->
            if (method.name == "onResult") {
                args?.firstOrNull()?.let { block(it) }
            }
            null
        }
        return Proxy.newProxyInstance(handlerInterface.classLoader, arrayOf(handlerInterface), invocationHandler)
    }

    private fun exceptionHandler(block: (Throwable) -> Unit): Any {
        val handlerInterface = Class.forName("org.drinkless.tdlib.Client\$ExceptionHandler")
        val invocationHandler = InvocationHandler { _, method, args ->
            if (method.name == "onException") {
                (args?.firstOrNull() as? Throwable)?.let(block)
            }
            null
        }
        return Proxy.newProxyInstance(handlerInterface.classLoader, arrayOf(handlerInterface), invocationHandler)
    }

    private fun newTdApiObject(simpleName: String, vararg args: Any?): Any {
        val tdApi = tdApiClass ?: error("TDLib TdApi class unavailable")
        val target = Class.forName("${tdApi.name}$$simpleName")
        val constructors = target.declaredConstructors
        val constructor = matchConstructor(constructors, args)
            ?: error("No matching constructor for $simpleName with ${args.size} args")

        constructor.isAccessible = true
        return constructor.newInstance(*args)
    }

    private fun matchConstructor(constructors: Array<Constructor<*>>, args: Array<out Any?>): Constructor<*>? {
        return constructors.firstOrNull { ctor ->
            val types = ctor.parameterTypes
            if (types.size != args.size) return@firstOrNull false
            types.indices.all { index ->
                val arg = args[index]
                val expected = wrapPrimitive(types[index])
                arg == null || expected.isAssignableFrom(arg.javaClass)
            }
        }
    }

    private fun wrapPrimitive(type: Class<*>): Class<*> {
        return when (type) {
            java.lang.Integer.TYPE -> java.lang.Integer::class.java
            java.lang.Long.TYPE -> java.lang.Long::class.java
            java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
            java.lang.Double.TYPE -> java.lang.Double::class.java
            java.lang.Float.TYPE -> java.lang.Float::class.java
            java.lang.Short.TYPE -> java.lang.Short::class.java
            java.lang.Byte.TYPE -> java.lang.Byte::class.java
            java.lang.Character.TYPE -> java.lang.Character::class.java
            else -> type
        }
    }

    private fun getFieldValue(instance: Any, fieldName: String): Any? {
        return runCatching {
            val field = instance.javaClass.getField(fieldName)
            field.get(instance)
        }.getOrNull()
    }

    private fun getFieldValueAny(instance: Any, vararg candidates: String): Any? {
        for (candidate in candidates) {
            val value = getFieldValue(instance, candidate)
            if (value != null) return value
        }
        return null
    }

    private fun setFieldAny(instance: Any, value: Any?, vararg candidates: String) {
        for (candidate in candidates) {
            val success = runCatching {
                val field = instance.javaClass.getField(candidate)
                field.set(instance, value)
                true
            }.getOrDefault(false)
            if (success) return
        }

        if (value != null) {
            Log.v(TAG, "Unable to set fields=${candidates.joinToString()} on ${instance.javaClass.simpleName}")
        }
    }

    private data class PendingVoiceDownload(
        val chatId: Long,
        val senderUserId: Long?
    )

    companion object {
        private const val TAG = "TdLibReflectiveBridge"
    }
}
