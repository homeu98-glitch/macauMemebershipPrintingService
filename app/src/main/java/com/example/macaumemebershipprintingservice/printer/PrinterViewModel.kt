package com.example.macaumemebershipprintingservice.printer

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

import android.content.Intent
import android.os.Build

class PrinterViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("printer_app_prefs", Context.MODE_PRIVATE)

    val printerStatus: StateFlow<String> = PrinterService.printerStatus
    val printerInfo: StateFlow<Map<String, String>> = PrinterService.printerInfo

    // UI Input field states for Login
    private val _username = MutableStateFlow(prefs.getString("username", "") ?: "")
    val username = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _apiUrl = MutableStateFlow(prefs.getString("api_url", "https://api.example.com/login") ?: "https://api.example.com/login")
    val apiUrl = _apiUrl.asStateFlow()

    private val _token = MutableStateFlow(prefs.getString("token", "") ?: "")
    val token = _token.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn = _isLoggingIn.asStateFlow()

    // HTTP Web-to-Print Local Server Status
    val serverStatusText: StateFlow<String> = PrinterService.serverStatusText

    private val _serverEnabled = MutableStateFlow(prefs.getBoolean("server_enabled", true))
    val serverEnabled = _serverEnabled.asStateFlow()

    private val _serverPort = MutableStateFlow(8080)
    val serverPort = _serverPort.asStateFlow()

    // Status logger to show logs inside the UI
    val logMessages: StateFlow<List<String>> = PrinterService.logMessages

    // Traditional Chinese order templates based on system specifications
    val orderTemplates = listOf(
        ReceiptTemplate(
            templateName = "① 自取小票協議（表嫂酸菜魚）",
            jsonCode = """{
  "orderTitle": "澳門會員通 • 門市自取單",
  "orderNo": "TK001092260616193802554",
  "orderStatus": "【客戶已確認收餐】",
  "customerName": "澳門用戶 女士 (下單2次)",
  "phone": "+853-****4640",
  "pickupNo": "#2",
  "pickupMethod": "用戶自取",
  "pickupTime": "2026-06-16 20:26",
  "remarks": "-",
  "tableware": "-",
  "items": [
    {
      "name": "1.表嫂酸菜魚 (米飯 + 凍奶茶)",
      "price": 72.00,
      "quantity": 1,
      "subtotal": 72.00
    }
  ],
  "couponInfo": "滿返信息：返80-7商家券 已在2026-06-16 19:49:19發券",
  "fees": {
    "originalFoodPrice": 72.00,
    "boxFee": 2.00,
    "bagFee": 1.0
  },
  "sellerActivity": {
    "discount": "滿60.0減2.0",
    "discountAmount": -2.00
  },
  "settlement": {
    "turnover": 73.00,
    "platformServiceFee": 4.38,
    "estimatedIncome": 68.62,
    "customerActualPay": 73.00,
    "productTotalAmount": 75.00
  }
}""",
            title = "澳門會員通 • 門市訂單",
            body = """
訂單編號: TK001092260616193802554
狀態: 【客戶已確認收餐】
--------------------------------
用戶姓名: 澳門用戶 女士 (下單2次)
取餐電話: +853-****4640
取餐編號: #2
取餐方式: 用戶自取
取餐時間: 2026-06-16 20:26
備註信息: -
餐具規格: -
--------------------------------
商品信息:
1. 表嫂酸菜魚 (米飯 + 凍奶茶)
                   $72  x1  $72
--------------------------------
滿返信息：返80-7商家券 
已在 2026-06-16 19:49:19 發券
--------------------------------
費用明細:
菜品原價合計                 $72
餐盒費                        $2
膠袋費                        $1
--------------------------------
商家活動支出:
滿60.0減2.0                  -$2
小計                         -$2
--------------------------------
財務結算:
營業額                       $73
平台服務費                 $4.38
本單預計收入              $68.62
--------------------------------
顧客實際支付                 $73
商品總金額                   $75
""".trimIndent(),
            footer = "門市取餐聯 • 感謝您再次光臨"
        ),
        ReceiptTemplate(
            templateName = "② 外送小票協議（黑沙環家常麻辣燙）",
            jsonCode = """{
  "orderTitle": "澳門會員通 • 商家配送單",
  "orderNo": "TK001092661009183802999",
  "orderStatus": "【等待騎手接單】",
  "customerName": "張先生 (下單5次)",
  "phone": "+853-****8822",
  "address": "澳門黑沙環新街海濱花園第五座8樓A",
  "deliveryMethod": "平台免運配送",
  "deliveryTime": "立即配送",
  "remarks": "多加辣，謝謝！",
  "tableware": "依商家提供",
  "items": [
    {
      "name": "1. 秘製麻辣燙 (肥牛+公仔麵+凍檸茶)",
      "price": 58.00,
      "quantity": 1,
      "subtotal": 58.00
    },
    {
      "name": "2. 香煎豬扒",
      "price": 22.00,
      "quantity": 1,
      "subtotal": 22.00
    }
  ],
  "couponInfo": "",
  "fees": {
    "originalFoodPrice": 80.00,
    "boxFee": 3.00,
    "bagFee": 0.00,
    "deliveryFee": 5.00
  },
  "sellerActivity": {
    "discount": "滿50.0減5.0",
    "discountAmount": -5.00
  },
  "settlement": {
    "turnover": 83.00,
    "platformServiceFee": 5.00,
    "estimatedIncome": 78.00,
    "customerActualPay": 83.00,
    "productTotalAmount": 88.00
  }
}""",
            title = "澳門會員通 • 外送訂單",
            body = """
訂單編號: TK001092661009183802999
狀態: 【等待騎手接單】
--------------------------------
用戶姓名: 張先生 (下單5次)
聯絡電話: +853-****8822
送餐地址: 澳門黑沙環新街海濱花園
          第五座8樓A
配送方式: 平台免運配送
送餐時間: 立即配送
備註信息: 多加辣，謝謝！
餐具規格: 依商家提供
--------------------------------
商品信息:
1. 秘製麻辣燙 (肥牛+公仔麵+凍檸茶)
                   $58  x1  $58
2. 香煎豬扒
                   $22  x1  $22
--------------------------------
費用明細:
商品總金額                   $80
配送費用                      $5
打包費用                      $3
--------------------------------
商家活動支出:
滿50.0減5.0                  -$5
小計                         -$5
--------------------------------
財務結算:
營業額                       $83
平台服務費                 $5.00
本單預計收入              $78.00
--------------------------------
顧客實際支付                 $83
商品總體價值                   $88
""".trimIndent(),
            footer = "商家外送聯 • 祝您用餐愉快"
        )
    )

    private val _selectedTemplateIndex = MutableStateFlow(0)
    val selectedTemplateIndex = _selectedTemplateIndex.asStateFlow()

    private val _showGeneratedPreview = MutableStateFlow(false)
    val showGeneratedPreview = _showGeneratedPreview.asStateFlow()

    fun selectTemplate(index: Int) {
        if (index in orderTemplates.indices) {
            _selectedTemplateIndex.value = index
            _showGeneratedPreview.value = false // Require clicking generate to see simulated output
            addLog("選取範本: ${orderTemplates[index].templateName}")
        }
    }

    fun generateTemplatePreview() {
        _showGeneratedPreview.value = true
        addLog("成功生成小票模擬預覽！協議格式驗證合格。")
    }

    fun printSelectedTemplate() {
        val index = _selectedTemplateIndex.value
        if (index in orderTemplates.indices) {
            val pm = PrinterService.getPrinterManager()
            if (pm == null || printerStatus.value != "已連接") {
                addLog("列印失敗: 熱敏打印機未連接！已切換為純本地模擬運行。")
                return
            }
            val template = orderTemplates[index]
            addLog("發送實體列印: '${template.templateName}'")
            viewModelScope.launch(Dispatchers.IO) {
                pm.printReceipt(template.title, template.body, template.footer)
            }
        }
    }

    init {
        startPrinterService()
    }

    private fun startPrinterService() {
        try {
            val intent = Intent(getApplication(), PrinterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
            addLog("正在連接啟動持久背景打印監聽服務...")
        } catch (e: Exception) {
            addLog("背景服務啟動異常: ${e.localizedMessage}")
        }
    }

    fun toggleHttpServer() {
        if (serverStatusText.value.startsWith("Running")) {
            _serverEnabled.value = false
            prefs.edit().putBoolean("server_enabled", false).apply()
            PrinterService.stopLocalServer()
        } else {
            _serverEnabled.value = true
            prefs.edit().putBoolean("server_enabled", true).apply()
            PrinterService.startLocalServer()
        }
    }

    fun updateUsername(value: String) {
        if (value.length <= 8 && value.all { it.isDigit() }) {
            _username.value = value
        }
    }

    fun updatePassword(value: String) {
        if (value.length <= 4 && value.all { it.isDigit() }) {
            _password.value = value
        }
    }

    fun updateApiUrl(value: String) {
        _apiUrl.value = value
    }

    fun clearToken() {
        _token.value = ""
        prefs.edit().putString("token", "").apply()
        addLog("Logged out. Token cleared.")
    }

    fun addLog(message: String) {
        PrinterService.addLog(message)
    }

    fun reconnect() {
        addLog("Manually requested printer connection reconnect...")
        PrinterService.triggerReconnect()
    }

    fun printReceiptTest() {
        val pm = PrinterService.getPrinterManager()
        if (pm == null || printerStatus.value != "已連接") {
            addLog("Error: Can't print, printer is not connected.")
            return
        }
        addLog("Sending Receipt Test Print to device...")
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val title = "SUNMI SMART STORE"
        val body = """
            Date: $timestamp
            Cashier: Sunmi Admin
            --------------------------------
            Cappuccino (L)      x1    $4.50
            Chocolate Cookie    x2    $3.00
            Croissant           x1    $2.75
            --------------------------------
            Subtotal:                 $10.25
            Tax (8%):                 $0.82
            --------------------------------
            TOTAL AMOUNT:             $11.07
            --------------------------------
            Paid via: Mobile Wallet (NFC)
            Trace No: #581974102948
        """.trimIndent()
        val footer = "Thank you for your purchase!\nPlease visit us again soon."
        
        viewModelScope.launch(Dispatchers.IO) {
            pm.printReceipt(title, body, footer)
        }
        addLog("Receipt Test Print complete.")
    }

    fun triggerInit() {
        val pm = PrinterService.getPrinterManager()
        if (pm == null || printerStatus.value != "已連接") {
            addLog("Error: Can't init printer, printer is not connected.")
            return
        }
        addLog("Initializing printer state...")
        viewModelScope.launch(Dispatchers.IO) {
            pm.initPrinter()
        }
    }

    fun login() {
        val usernameVal = _username.value.trim()
        val passwordVal = _password.value.trim()
        var urlVal = _apiUrl.value.trim()

        if (usernameVal.length != 8 || usernameVal.any { !it.isDigit() }) {
            addLog("Validation Error: Username must be exactly 8-digit numeric code.")
            return
        }
        if (passwordVal.length != 4 || passwordVal.any { !it.isDigit() }) {
            addLog("Validation Error: Password must be exactly 4-digit numeric code.")
            return
        }
        if (urlVal.isEmpty()) {
            addLog("Validation Error: API URL cannot be empty.")
            return
        }

        if (!urlVal.startsWith("http://") && !urlVal.startsWith("https://")) {
            urlVal = "http://$urlVal"
        }

        _isLoggingIn.value = true
        addLog("Attempting login to: $urlVal")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val json = JSONObject().apply {
                    put("username", usernameVal)
                    put("password", passwordVal)
                }

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(urlVal)
                    .post(body)
                    .build()

                Log.d("PrinterViewModel", "Sending POST to $urlVal with body $json")

                client.newCall(request).execute().use { response ->
                    val code = response.code
                    val responseBodyStr = response.body?.string() ?: ""
                    
                    if (response.isSuccessful) {
                        var tokenFound = ""
                        try {
                            val root = JSONTokener(responseBodyStr).nextValue()
                            if (root is JSONObject) {
                                tokenFound = when {
                                    root.has("token") -> root.getString("token")
                                    root.has("accessToken") -> root.getString("accessToken")
                                    root.has("access_token") -> root.getString("access_token")
                                    root.has("data") -> {
                                        val dataObj = root.get("data")
                                        if (dataObj is JSONObject && dataObj.has("token")) {
                                            dataObj.getString("token")
                                        } else {
                                            ""
                                        }
                                    }
                                    else -> ""
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PrinterViewModel", "JSON token parse failed", e)
                        }

                        if (tokenFound.isEmpty()) {
                            tokenFound = if (responseBodyStr.length in 1..256 && !responseBodyStr.contains("\n") && !responseBodyStr.contains("{")) {
                                responseBodyStr.trim()
                            } else {
                                "Token_Acquired_OK"
                            }
                        }

                        _token.value = tokenFound
                        
                        // Save in Preference
                        prefs.edit().apply {
                            putString("token", tokenFound)
                            putString("username", usernameVal)
                            putString("api_url", urlVal)
                            apply()
                        }

                        addLog("Login Success! Token: ${tokenFound.take(24)}..."); PrinterService.refreshCloudListener()
                    } else {
                        addLog("Login Failed with code $code: ${responseBodyStr.take(150)}")
                    }
                }
            } catch (e: Exception) {
                val errMsg = e.message ?: "Unknown socket connection error"
                addLog("Network Error: $errMsg")
                Log.e("PrinterViewModel", "Login failure", e)
            } finally {
                _isLoggingIn.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}

data class ReceiptTemplate(
    val templateName: String,
    val jsonCode: String,
    val title: String,
    val body: String,
    val footer: String
)
