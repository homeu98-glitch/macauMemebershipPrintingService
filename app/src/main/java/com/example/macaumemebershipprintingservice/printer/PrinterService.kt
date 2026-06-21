package com.example.macaumemebershipprintingservice.printer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.macaumemebershipprintingservice.MainActivity
import com.example.macaumemebershipprintingservice.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import org.json.JSONArray

class PrinterService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mqttClient: MqttAsyncClient? = null
    
    // Default Public Broker for testing (User should use a private one for production)
    private val DEFAULT_BROKER = "tcp://broker.hivemq.com:1883"

    override fun onCreate() {
        super.onCreate()
        instance = this
        addLog("打印背景服務已建立 (Service Created)")
        startForegroundServiceWithNotification()

        // Initialize Printer Manager
        _printerManager = SunmiPrinterManager(applicationContext)
        _printerManager?.let { pm ->
            serviceScope.launch {
                pm.printerStatus.collect { status ->
                    _printerStatus.value = status
                    updateStateLogAndNotification()
                }
            }
            serviceScope.launch {
                pm.printerInfo.collect { info ->
                    _printerInfo.value = info
                }
            }
        }

        // Setup the local HTTP Server
        setupHttpServer()
        setupMqttListener()
        
        // Auto-start HTTP Server if enabled in preferences
        val prefs = getSharedPreferences("printer_app_prefs", Context.MODE_PRIVATE)
        val serverEnabled = prefs.getBoolean("server_enabled", true)
        if (serverEnabled) {
            startLocalServer()
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "printer_service_channel"
        val channelName = "澳門會員通 • 打印背景監聽服務"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持網頁遠端發送之本地熱敏打印接口處於在線狀態"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Stop Service Action for user convenience
        val stopIntent = Intent(this, PrinterService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("澳門會員通數字出票終端")
            .setContentText("熱敏打印機監聽中...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "關閉背景服務",
                stopPendingIntent
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun updateStateLogAndNotification() {
        val status = _printerStatus.value
        val serverText = _serverStatusText.value
        val statusLabel = if (status == "已連接") "已連接物理打印機" else "打印機未連接 ($status)"
        val serverLabel = if (serverText.startsWith("Running")) "本地網關已啟動" else "本地網關已關閉"

        val channelId = "printer_service_channel"
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val stopIntent = Intent(this, PrinterService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("澳門會員通 • 背景連線中")
            .setContentText("$statusLabel | $serverLabel")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "關閉背景服務",
                stopPendingIntent
            )
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            if (action == ACTION_STOP_SERVICE) {
                addLog("收到關閉請求，背景服務正在終止。")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun setupHttpServer() {
        val prefs = getSharedPreferences("printer_app_prefs", Context.MODE_PRIVATE)
        val serverPort = prefs.getInt("server_port", 8080)

        _httpServer = SunmiHttpServer(
            context = applicationContext,
            port = serverPort,
            onPrintRequest = { title, bodyText, footer, incomingToken ->
                handleIncomingApiPrint(title, bodyText, footer, incomingToken)
            }
        )
    }

    private fun handleIncomingApiPrint(
        title: String,
        bodyText: String,
        footer: String,
        incomingToken: String
    ): Pair<Boolean, String> {
        val prefs = getSharedPreferences("printer_app_prefs", Context.MODE_PRIVATE)
        val activeToken = prefs.getString("token", "")?.trim() ?: ""

        if (activeToken.isEmpty()) {
            val err = "API Unauthorized: Device has no active session token. Please login first."
            addLog("Blocked Web Print: $err")
            return Pair(false, err)
        }

        if (incomingToken.isEmpty() || incomingToken != activeToken) {
            val err = "API Unauthorized: Token mismatch or missing Authorization header."
            addLog("Blocked Web Print: $err")
            return Pair(false, err)
        }

        if (_printerStatus.value != "已連接") {
            val err = "Device Offline: Server received print block but printer service is unavailable."
            addLog("Error executing Web Print: $err")
            return Pair(false, err)
        }

        addLog("API Authorized! Printing web request: '$title'")
        
        serviceScope.launch(Dispatchers.IO) {
            _printerManager?.printReceipt(title, bodyText, footer)
        }

        return Pair(true, "Receipt processed and sent to thermal spooler.")
    }

    fun setupMqttListener() {
        val prefs = getSharedPreferences("printer_app_prefs", Context.MODE_PRIVATE)
        val merchantId = prefs.getString("username", "") ?: ""
        
        if (merchantId.isEmpty()) {
            addLog("MQTT 監聽器等待中：請先登入商戶帳號。")
            return
        }

        mqttClient?.let {
            if (it.isConnected) {
                try { it.disconnect() } catch (e: Exception) {}
            }
        }

        val clientId = "MacauPrint_${merchantId}_${System.currentTimeMillis()}"
        val topic = "macau/printing/$merchantId/jobs"
        
        addLog("正在連接 MQTT Broker ($DEFAULT_BROKER)...")

        try {
            mqttClient = MqttAsyncClient(DEFAULT_BROKER, clientId, MemoryPersistence())
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                keepAliveInterval = 60
                setAutomaticReconnect(true)
            }

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    addLog("MQTT 連接成功！正在訂閱主題: $topic")
                    mqttClient?.subscribe(topic, 1) { _, message ->
                        val payload = String(message.payload)
                        handleMqttMessage(payload)
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    addLog("MQTT 連接失敗: ${exception?.message}")
                }
            })
        } catch (e: Exception) {
            addLog("MQTT 初始化錯誤: ${e.message}")
        }
    }

    private fun handleMqttMessage(payload: String) {
        try {
            val json = JSONObject(payload)
            val title: String
            val bodyText: String
            val footer: String

            if (json.has("items") || json.has("orderNo")) {
                // Structured JSON format
                title = json.optString("orderTitle", "雲端訂單")
                val orderNo = json.optString("orderNo", "N/A")
                val status = json.optString("orderStatus", "N/A")
                val customer = json.optString("customerName", "訪客")
                val phone = json.optString("phone", "N/A")
                
                val itemsArray = json.optJSONArray("items")
                val itemsList = mutableListOf<String>()
                if (itemsArray != null) {
                    for (i in 0 until itemsArray.length()) {
                        val item = itemsArray.getJSONObject(i)
                        itemsList.add("- ${item.optString("name")} x${item.optInt("quantity")}")
                    }
                }
                val itemsStr = if (itemsList.isEmpty()) "(無商品)" else itemsList.joinToString("\n")
                
                bodyText = "單號: $orderNo\n狀態: $status\n----------------\n客戶: $customer\n電話: $phone\n----------------\n商品:\n$itemsStr"
                footer = json.optString("footer", "MQTT 訂單自動打印")
            } else {
                // Legacy raw text format
                title = json.optString("title", "WEB RECEIPT")
                bodyText = json.optString("body", "")
                footer = json.optString("footer", "Printed via MQTT")
            }

            if (bodyText.isNotEmpty()) {
                addLog("收到 MQTT 打印請求: $title")
                serviceScope.launch(Dispatchers.IO) {
                    if (_printerStatus.value == "已連接") {
                        _printerManager?.printReceipt(title, bodyText, footer)
                    } else {
                        addLog("MQTT 打印失敗：打印機未連接。")
                    }
                }
            }
        } catch (e: Exception) {
            addLog("MQTT 數據解析錯誤: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        addLog("背景打印服務已完全終止。")
        serviceScope.cancel()
        try {
            mqttClient?.disconnect()
            mqttClient = null
        } catch (e: Exception) {}
        _printerManager?.disconnect()
        _printerManager = null
        _httpServer?.stop { }
        _httpServer = null
        _serverStatusText.value = "Stopped"
        _printerStatus.value = "未連接"
        instance = null
    }

    companion object {
        private const val NOTIFICATION_ID = 9918
        const val ACTION_STOP_SERVICE = "com.example.macaumemebershipprintingservice.printer.ACTION_STOP_SERVICE"

        var instance: PrinterService? = null
            private set

        // Shared background status flows
        private val _printerStatus = MutableStateFlow("未連接")
        val printerStatus: StateFlow<String> = _printerStatus.asStateFlow()

        private val _printerInfo = MutableStateFlow<Map<String, String>>(emptyMap())
        val printerInfo: StateFlow<Map<String, String>> = _printerInfo.asStateFlow()

        private val _serverStatusText = MutableStateFlow("Stopped")
        val serverStatusText: StateFlow<String> = _serverStatusText.asStateFlow()

        private val _logMessages = MutableStateFlow<List<String>>(listOf("系統 initialized. 正在調度背景服務..."))
        val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

        private var _printerManager: SunmiPrinterManager? = null
        private var _httpServer: SunmiHttpServer? = null

        fun addLog(message: String) {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            _logMessages.update { currentList ->
                listOf("[$timestamp] $message") + currentList.take(49)
            }
            Log.d("PrinterService", "[$timestamp] $message")
        }

        fun getPrinterManager(): SunmiPrinterManager? = _printerManager

        fun startLocalServer() {
            if (_serverStatusText.value.startsWith("Running")) return
            addLog("啟動 Web Print 網關...")
            _httpServer?.start { status ->
                _serverStatusText.value = status
                if (status.startsWith("Running")) {
                    addLog("Web Print 服務已上線. $status")
                } else if (status.startsWith("Stopped")) {
                    addLog("Web Print 服務也離線。")
                }
                instance?.updateStateLogAndNotification()
            }
            // Also refresh MQTT listener
            instance?.setupMqttListener()
        }

        fun stopLocalServer() {
            addLog("關閉 Web Print 網關...")
            _httpServer?.stop { status ->
                _serverStatusText.value = status
                addLog("Web Print 服務關閉離線。")
                instance?.updateStateLogAndNotification()
            }
        }

        fun triggerReconnect() {
            addLog("手動請求打印機熱插拔重連...")
            _printerManager?.connect()
        }

        fun refreshCloudListener() {
            instance?.setupMqttListener()
        }
    }
}
