package com.example.macaumemebershipprintingservice.printer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONTokener

class PrinterViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("printer_app_prefs", Context.MODE_PRIVATE)

    val printerStatus: StateFlow<String> = PrinterService.printerStatus
    val printerInfo: StateFlow<Map<String, String>> = PrinterService.printerInfo

    // --- HARDCODED PRESETS FOR ONE-CLICK TEST ---
    private val _username = MutableStateFlow("63936541")
    val username = _username.asStateFlow()

    private val _password = MutableStateFlow("1234")
    val password = _password.asStateFlow()

    private val _apiUrl = MutableStateFlow("https://macauprintpublic-2.vercel.app/api/login")
    val apiUrl = _apiUrl.asStateFlow()
    // --------------------------------------------

    private val _token = MutableStateFlow(prefs.getString("token", "") ?: "")
    val token = _token.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn = _isLoggingIn.asStateFlow()

    val serverStatusText: StateFlow<String> = PrinterService.serverStatusText

    private val _serverEnabled = MutableStateFlow(prefs.getBoolean("server_enabled", true))
    val serverEnabled = _serverEnabled.asStateFlow()

    private val _serverPort = MutableStateFlow(8080)
    val serverPort = _serverPort.asStateFlow()

    val logMessages: StateFlow<List<String>> = PrinterService.logMessages

    val orderTemplates = listOf(
        ReceiptTemplate(
            templateName = "外賣訂單協議 (Takeaway)",
            jsonCode = "{}", // UI will handle the text
            title = "澳門會員通 • 外賣訂單",
            body = "單號: TK001092260616\n...",
            footer = "請憑單號於櫃檯取貨"
        ),
        ReceiptTemplate(
            templateName = "送餐協議 (Delivery)",
            jsonCode = "{}",
            title = "澳門會員通 • 送餐任務",
            body = "單號: DL99283711\n...",
            footer = "地址：澳門黑沙環"
        )
    )

    private val _selectedTemplateIndex = MutableStateFlow(0)
    val selectedTemplateIndex = _selectedTemplateIndex.asStateFlow()

    private val _showGeneratedPreview = MutableStateFlow(false)
    val showGeneratedPreview = _showGeneratedPreview.asStateFlow()

    fun selectTemplate(index: Int) {
        _selectedTemplateIndex.value = index
        _showGeneratedPreview.value = false
    }

    fun generateTemplatePreview() {
        _showGeneratedPreview.value = true
    }

    fun printSelectedTemplate() {
        val currentTemplate = orderTemplates[_selectedTemplateIndex.value]
        viewModelScope.launch(Dispatchers.IO) {
            PrinterService.getPrinterManager()?.printReceipt(currentTemplate.title, currentTemplate.body, currentTemplate.footer)
        }
    }

    init {
        startPrinterService()
    }

    fun startPrinterService() {
        try {
            val context = getApplication<Application>()
            val intent = Intent(context, PrinterService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            addLog("Service Start Failed: ${e.message}")
        }
    }

    fun toggleHttpServer() {
        val nextState = !serverStatusText.value.startsWith("Running")
        if (nextState) PrinterService.startLocalServer() else PrinterService.stopLocalServer()
        prefs.edit().putBoolean("server_enabled", nextState).apply()
    }

    fun updateUsername(newUsername: String) { _username.value = newUsername }
    fun updatePassword(newPassword: String) { _password.value = newPassword }
    fun updateApiUrl(newUrl: String) { _apiUrl.value = newUrl }

    fun clearToken() {
        _token.value = ""
        prefs.edit().remove("token").apply()
        addLog("Session token cleared.")
    }

    fun addLog(message: String) { PrinterService.addLog(message) }
    fun reconnect() { PrinterService.triggerReconnect() }

    fun printReceiptTest() {
        viewModelScope.launch(Dispatchers.IO) {
            PrinterService.getPrinterManager()?.printReceipt("零售測試", "Body...", "Footer")
        }
    }

    fun triggerInit() {
        viewModelScope.launch(Dispatchers.IO) {
            PrinterService.getPrinterManager()?.initPrinter()
            addLog("打印機初始化成功")
        }
    }

    fun login() {
        val u = _username.value.trim()
        val p = _password.value.trim()
        val url = _apiUrl.value.trim()

        _isLoggingIn.value = true
        addLog("正在登入: $url")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder().build()
                val json = JSONObject().apply { put("username", u); put("password", p) }
                val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder().url(url).post(body).build()

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val tokenFound = try {
                            JSONObject(bodyStr).getString("token")
                        } catch (e: Exception) { "TOKEN_OK" }

                        _token.value = tokenFound
                        prefs.edit().putString("token", tokenFound).apply()
                        addLog("登入成功！已獲取 Token")
                        PrinterService.refreshCloudListener()
                    } else {
                        addLog("登入失敗: Code ${response.code}")
                    }
                }
            } catch (e: Exception) {
                addLog("連線錯誤: ${e.message}")
            } finally {
                _isLoggingIn.value = false
            }
        }
    }
}

data class ReceiptTemplate(val templateName: String, val jsonCode: String, val title: String, val body: String, val footer: String)
