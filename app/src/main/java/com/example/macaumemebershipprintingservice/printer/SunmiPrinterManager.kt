package com.example.macaumemebershipprintingservice.printer

import android.content.Context
import android.util.Log
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterException
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SunmiPrinterManager(private val context: Context) {
    private var printerService: SunmiPrinterService? = null
    
    private val _printerStatus = MutableStateFlow("未連接")
    val printerStatus: StateFlow<String> = _printerStatus

    private val _printerInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val printerInfo: StateFlow<Map<String, String>> = _printerInfo

    private val innerPrinterCallback = object : InnerPrinterCallback() {
        override fun onConnected(service: SunmiPrinterService?) {
            printerService = service
            _printerStatus.value = "已連接"
            Log.d("PrinterManager", "Sunmi Printer Service Connected")
            fetchPrinterInfo()
        }

        override fun onDisconnected() {
            printerService = null
            _printerStatus.value = "未連接"
            _printerInfo.value = emptyMap()
            Log.d("PrinterManager", "Sunmi Printer Service Disconnected")
        }
    }

    init {
        connect()
    }

    fun connect() {
        _printerStatus.value = "正在連接..."
        try {
            val bindSuccess = InnerPrinterManager.getInstance().bindService(
                context,
                innerPrinterCallback
            )
            if (!bindSuccess) {
                _printerStatus.value = "綁定失敗 (非商米設備?)"
                Log.e("PrinterManager", "bindService returned false")
            }
        } catch (e: InnerPrinterException) {
            _printerStatus.value = "錯誤: ${e.message}"
            Log.e("PrinterManager", "InnerPrinterException: ", e)
        } catch (e: Exception) {
            _printerStatus.value = "不支援的設備 (Aidl 錯誤)"
            Log.e("PrinterManager", "Exception: ", e)
        }
    }

    fun disconnect() {
        try {
            InnerPrinterManager.getInstance().unBindService(context, innerPrinterCallback)
            printerService = null
            _printerStatus.value = "未連接"
            _printerInfo.value = emptyMap()
        } catch (e: Exception) {
            Log.e("PrinterManager", "unBindService error: ", e)
        }
    }

    private fun fetchPrinterInfo() {
        val service = printerService ?: return
        try {
            val serialNo = try { service.printerSerialNo } catch (e: Exception) { "Unknown" } ?: "Unknown"
            val version = try { service.printerVersion } catch (e: Exception) { "Unknown" } ?: "Unknown"
            val modal = try { service.printerModal } catch (e: Exception) { "Unknown" } ?: "Unknown"
            val paperType = try {
                val paper = service.printerPaper
                if (paper == 1) "58mm Thermal" else if (paper == 2) "80mm Thermal" else "Standard 58mm"
            } catch (e: Exception) {
                "58mm Thermal"
            }

            _printerInfo.value = mapOf(
                "Model" to modal,
                "Serial Number" to serialNo,
                "Firmware Version" to version,
                "Paper Type" to paperType
            )
        } catch (e: Exception) {
            Log.e("PrinterManager", "Failed to get printer info: ", e)
        }
    }

    fun initPrinter() {
        val service = printerService ?: return
        try {
            service.printerInit(null)
            Log.d("PrinterManager", "Printer initialized")
        } catch (e: Exception) {
            Log.e("PrinterManager", "Failed to init printer", e)
        }
    }

    fun printText(text: String, fontSize: Float = 24f, align: Int = 0) {
        val service = printerService ?: return
        try {
            service.printerInit(null)
            service.setAlignment(align, null)
            service.printTextWithFont(text, "", fontSize, null)
            service.lineWrap(3, null)
            Log.d("PrinterManager", "Printed custom text successfully")
        } catch (e: Exception) {
            Log.e("PrinterManager", "Failed to print text", e)
        }
    }

    fun printReceipt(title: String, body: String, footer: String) {
        val service = printerService ?: return
        try {
            service.printerInit(null)
            
            // Draw visual header banner
            service.setAlignment(1, null) // Center
            service.printTextWithFont("$title\n", "", 32f, null)
            service.printTextWithFont("--------------------------------\n", "", 24f, null)
            
            // Draw body left aligned
            service.setAlignment(0, null) // Left
            service.printTextWithFont(body, "", 24f, null)
            service.printTextWithFont("\n--------------------------------\n", "", 24f, null)
            
            // Draw footer center aligned
            service.setAlignment(1, null) // Center
            service.printTextWithFont("$footer\n", "", 20f, null)
            
            // Wrap lines so it exits the printer mouth fully
            service.lineWrap(3, null)
            Log.d("PrinterManager", "Receipt print sent successfully")
        } catch (e: Exception) {
            Log.e("PrinterManager", "Receipt printing failed", e)
        }
    }

    fun printBarCode(code: String, symbology: Int = 8, height: Int = 80, width: Int = 2, textPosition: Int = 2) {
        val service = printerService ?: return
        try {
            service.printerInit(null)
            service.setAlignment(1, null) // Center
            service.printBarCode(code, symbology, height, width, textPosition, null)
            service.lineWrap(3, null)
            Log.d("PrinterManager", "Barcode print sent successfully")
        } catch (e: Exception) {
            Log.e("PrinterManager", "Barcode print failed", e)
        }
    }

    fun printQRCode(text: String, moduleSize: Int = 6, errorCorrectionLevel: Int = 3) {
        val service = printerService ?: return
        try {
            service.printerInit(null)
            service.setAlignment(1, null) // Center
            service.printQRCode(text, moduleSize, errorCorrectionLevel, null)
            service.lineWrap(3, null)
            Log.d("PrinterManager", "QR Code print sent successfully")
        } catch (e: Exception) {
            Log.e("PrinterManager", "QR Code print failed", e)
        }
    }

    fun feed(lines: Int = 3) {
        val service = printerService ?: return
        try {
            service.lineWrap(lines, null)
            Log.d("PrinterManager", "Paper fed by $lines lines")
        } catch (e: Exception) {
            Log.e("PrinterManager", "Paper feed failed", e)
        }
    }
}
