package com.example.macaumemebershipprintingservice.printer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.NetworkInterface
import java.util.Collections

class SunmiHttpServer(
    private val context: Context,
    private val port: Int,
    private val onPrintRequest: (title: String, body: String, footer: String, token: String) -> Pair<Boolean, String>
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(onStatusChanged: (String) -> Unit) {
        if (serverJob?.isActive == true) {
            val ip = getLocalIpAddress() ?: "127.0.0.1"
            onStatusChanged("Running on http://$ip:$port")
            return
        }
        
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                val ip = getLocalIpAddress() ?: "127.0.0.1"
                withContext(Dispatchers.Main) {
                    onStatusChanged("Running on http://$ip:$port")
                }
                Log.d("SunmiHttpServer", "Server started on port $port")
                
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e("SunmiHttpServer", "Server error: ${e.message}")
                withContext(Dispatchers.Main) {
                    onStatusChanged("Stopped (Error: ${e.localizedMessage})")
                }
            }
        }
    }

    fun stop(onStatusChanged: (String) -> Unit) {
        scope.launch {
            try {
                serverSocket?.close()
                serverSocket = null
                serverJob?.cancel()
                serverJob = null
                withContext(Dispatchers.Main) {
                    onStatusChanged("Stopped")
                }
                Log.d("SunmiHttpServer", "Server stopped manually")
            } catch (e: Exception) {
                Log.e("SunmiHttpServer", "Error stopping server", e)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30000; val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val out = socket.getOutputStream()

            // Read Request Line
            val requestLine = reader.readLine() ?: return
            Log.d("SunmiHttpServer", "Request: $requestLine")

            val tokens = requestLine.split(" ")
            if (tokens.size < 3) {
                sendResponse(out, 400, "Bad Request", "{\"status\":\"error\",\"message\":\"Invalid HTTP request\"}")
                return
            }

            val method = tokens[0]
            val path = tokens[1]

            // Read Headers
            var contentLength = 0
            var authHeader = ""
            var line: String?
            while (true) {
                line = reader.readLine()
                if (line == null || line.trim().isEmpty()) {
                    break
                }
                val index = line.indexOf(":")
                if (index != -1) {
                    val key = line.substring(0, index).trim().lowercase()
                    val value = line.substring(index + 1).trim()
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    } else if (key == "authorization") {
                        authHeader = value
                    }
                }
            }

            // Standard CORS preflight options
            if (method.uppercase() == "OPTIONS") {
                sendResponse(out, 200, "OK", "")
                return
            }

            if (method.uppercase() == "POST" && (path == "/print" || path == "/api/print")) {
                // Read Request Body
                val bodyBuilder = java.lang.StringBuilder()
                if (contentLength > 0) {
                    val buffer = CharArray(1024)
                    var totalRead = 0
                    while (totalRead < contentLength) {
                        val read = reader.read(buffer, 0, Math.min(buffer.size, contentLength - totalRead))
                        if (read == -1) break
                        bodyBuilder.append(buffer, 0, read)
                        totalRead += read
                    }
                }

                val bodyStr = bodyBuilder.toString()
                Log.d("SunmiHttpServer", "Print Body: $bodyStr")

                // Extract token from Bearer prefix if any
                val token = if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
                    authHeader.substring(7).trim()
                } else {
                    authHeader.trim()
                }

                try {
                    val jsonObj = JSONTokener(bodyStr).nextValue() as? JSONObject
                    if (jsonObj == null) {
                        sendResponse(out, 400, "Bad Request", "{\"status\":\"error\",\"message\":\"Invalid JSON body\"}")
                        return
                    }

                    val title = jsonObj.optString("title", "WEB RECEIPT")
                    val bodyText = jsonObj.optString("body", "")
                    val footer = jsonObj.optString("footer", "Printed via Local Web API")

                    if (bodyText.isEmpty()) {
                        sendResponse(out, 400, "Bad Request", "{\"status\":\"error\",\"message\":\"'body' field is required\"}")
                        return
                    }

                    // Callback to viewmodel for credential verification and print
                    val (printSuccess, printMsg) = onPrintRequest(title, bodyText, footer, token)

                    if (printSuccess) {
                        sendResponse(out, 200, "OK", "{\"status\":\"success\",\"message\":\"$printMsg\"}")
                    } else {
                        val code = if (printMsg.contains("Unauthorized", ignoreCase = true)) 401 else 500
                        val statusText = if (code == 401) "Unauthorized" else "Internal Server Error"
                        sendResponse(out, code, statusText, "{\"status\":\"error\",\"message\":\"$printMsg\"}")
                    }

                } catch (e: Exception) {
                    sendResponse(out, 400, "Bad Request", "{\"status\":\"error\",\"message\":\"JSON parsing error: ${e.message}\"}")
                }
            } else if (method.uppercase() == "GET" && path == "/status") {
                sendResponse(out, 200, "OK", "{\"status\":\"running\",\"message\":\"Sunmi V2 Pro Local Server API is online\"}")
            } else {
                sendResponse(out, 404, "Not Found", "{\"status\":\"error\",\"message\":\"API Endpoint not found. Try POST /print\"}")
            }

        } catch (e: Exception) {
            Log.e("SunmiHttpServer", "Handling client socket failed: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun sendResponse(out: OutputStream, code: Int, statusText: String, body: String) {
        try {
            val responseBytes = body.toByteArray(Charsets.UTF_8)
            val header = "HTTP/1.1 $code $statusText\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${responseBytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: Content-Type, Authorization\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
            out.write(header.toByteArray(Charsets.UTF_8))
            if (responseBytes.isNotEmpty()) {
                out.write(responseBytes)
            }
            out.flush()
        } catch (e: Exception) {
            Log.e("SunmiHttpServer", "Error writing HTTP response", e)
        }
    }

    companion object {
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress) {
                            val sAddr = addr.hostAddress
                            val isIPv4 = sAddr.indexOf(':') < 0
                            if (isIPv4) {
                                return sAddr
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e("SunmiHttpServer", "Failed to retrieve local IP: ", ex)
            }
            return null
        }
    }
}
