package com.example.macaumemebershipprintingservice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.macaumemebershipprintingservice.printer.PrinterViewModel
import com.example.macaumemebershipprintingservice.printer.SunmiHttpServer
import com.example.macaumemebershipprintingservice.ui.theme.MacaumemebershipprintingserviceTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PrinterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = "android.permission.POST_NOTIFICATIONS"
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 200)
            }
        }

        setContent {
            MacaumemebershipprintingserviceTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    PrinterScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun PrinterScreen(
    viewModel: PrinterViewModel,
    modifier: Modifier = Modifier
) {
    val printerStatus by viewModel.printerStatus.collectAsStateWithLifecycle()
    val printerInfo by viewModel.printerInfo.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val apiUrl by viewModel.apiUrl.collectAsStateWithLifecycle()
    val token by viewModel.token.collectAsStateWithLifecycle()
    val isLoggingIn by viewModel.isLoggingIn.collectAsStateWithLifecycle()
    val logMessages by viewModel.logMessages.collectAsStateWithLifecycle()
    
    val serverStatusText by viewModel.serverStatusText.collectAsStateWithLifecycle()
    val serverEnabled by viewModel.serverEnabled.collectAsStateWithLifecycle()
    val serverPort by viewModel.serverPort.collectAsStateWithLifecycle()

    // Interactive order template selections
    val orderTemplates = viewModel.orderTemplates
    val selectedTemplateIndex by viewModel.selectedTemplateIndex.collectAsStateWithLifecycle()
    val showGeneratedPreview by viewModel.showGeneratedPreview.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val isConnected = printerStatus == "已連接"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Geometric Header block 
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Circle Avatar symbol matching the design scheme
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEADDFF))
                            .clickable { viewModel.reconnect() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "澳門會員通打印服務 標誌",
                            tint = Color(0xFF21005D),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "澳門會員通打印服務",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1B1F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isConnected) "商米智能終端 • 在線 (已連接)" else "商米智能終端 • 待命 (無連線)",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 0.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) Color(0xFF2E7D32) else Color(0xFF616161)
                        )
                    }
                }

                // Dynamic Status Badge matching design palette
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isConnected) Color(0xFFDBE1FF) else Color(0xFFF3EDF7),
                            shape = RoundedCornerShape(100.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .widthIn(max = 120.dp)
                ) {
                    Text(
                        text = if (isConnected) "已連線" else printerStatus,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isConnected) Color(0xFF00174B) else Color(0xFF49454F),
                        modifier = Modifier.testTag("printer_status_label"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Image Hero block nicely framed and integrated
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_banner_1781879501358),
                        contentDescription = "澳門會員通商家智能出票終端",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                    startY = 20f
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "澳門會員通 • 安全熱敏打印網關",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "支援網頁端遠端發送、API 調用與本地模版高解析出票",
                            fontSize = 10.sp,
                            color = Color(0xFFEADDFF)
                        )
                    }
                }
            }
        }

        // Printer Status Grid Container (bg: #f3edf7, shape: rounded-3xl)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF3EDF7), RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "熱敏打印機實時參數",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF49454F)
                )

                // 2x2 Grid using clean Rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Paper Type Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "紙張規格",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF49454F),
                                fontSize = 9.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = printerInfo["Paper Type"] ?: "58mm 熱敏紙",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1B1F)
                            )
                        }
                    }

                    // Device Model Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "設備型號",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF49454F),
                                fontSize = 9.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = printerInfo["Model"] ?: "商米 V2 Pro",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1B1F)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Battery/Power status
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "智能端續航電量",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF49454F),
                                fontSize = 9.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "84% (待命正常)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1B1F)
                            )
                        }
                    }

                    // Buffer Status card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "緩衝器容量",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF49454F),
                                fontSize = 9.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "空閒 / 隨時清空",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF49454F)
                            )
                        }
                    }
                }

                // Metadata Details
                if (printerInfo.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        printerInfo.forEach { (key, value) ->
                            if (key != "Paper Type" && key != "Model") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val keyCh = when(key) {
                                        "Serial Number" -> "硬件序號"
                                        "Firmware Version" -> "固件版本"
                                        else -> key
                                    }
                                    Text(
                                        text = keyCh,
                                        fontSize = 11.sp,
                                        color = Color(0xFF49454F)
                                    )
                                    Text(
                                        text = value,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1C1B1F)
                                    )
                                }
                            }
                        }
                    }
                }

                // Reconnect action trigger button
                Button(
                    onClick = { viewModel.reconnect() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("reconnect_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1C1B1F)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重新連接",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "重新偵測並連接打印服務", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- UNIFIED MQTT CLOUD PRINT & TEMPLATE SECTION ---
        item {
            Text(
                text = "MQTT 雲端打印同步與 JSON 協議規範",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF49454F),
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFE0F2F1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "MQTT 雲端打印",
                                tint = Color(0xFF00796B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "MQTT 雲端接件與協議規範",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1B1F)
                            )
                            Text(
                                text = "支持無 GMS 環境下遠端發送 JSON 排版打印",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF49454F)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFCAC4D0))

                    // Step 1: Login
                    Text(
                        text = "1. 如何啟動 MQTT 雲端同步：",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )
                    Text(
                        text = "請先於下方「憑證與會話授權」區域登入。登入成功後，系統將自動連接 MQTT Broker 並開始監聽您的打印任務。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F)
                    )

                    // Step 2: Implementation
                    Text(
                        text = "2. 網站端發送 JSON 數據示例 (MQTT)：",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )

                    val merchantId = if (username.isEmpty()) "您的商戶ID" else username
                    val mqttCode = """
// MQTT.js (網頁端) 示例
const client = mqtt.connect('wss://broker.hivemq.com:8884/mqtt');
const topic = 'macau/printing/$merchantId/jobs';
const payload = {
  orderTitle: '澳門會員通 • MQTT 訂單',
  orderNo: 'TK001092260616',
  orderStatus: '【客戶已付款】',
  customerName: '澳門用戶',
  phone: '+853-66112233',
  items: [
    { name: '表嫂酸菜魚', quantity: 1 },
    { name: '凍奶茶', quantity: 2 }
  ],
  footer: '由網頁端 MQTT 發送'
};
client.publish(topic, JSON.stringify(payload));
                    """.trimIndent()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF27272A), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = mqttCode,
                            color = Color(0xFFF4F4F5),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }

                    // Step 3: Visualization (Keeping the original visualize logic)
                    HorizontalDivider(color = Color(0xFFCAC4D0))
                    Text(
                        text = "3. 預覽不同業務場景的小票效果：",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        orderTemplates.forEachIndexed { index, template ->
                            val isSelected = selectedTemplateIndex == index
                            Button(
                                onClick = { viewModel.selectTemplate(index) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(0xFF2E7D32) else Color(0xFFF3EDF7),
                                    contentColor = if (isSelected) Color.White else Color(0xFF49454F)
                                ),
                                border = if (!isSelected) BorderStroke(1.dp, Color(0xFFCAC4D0)) else null,
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text(
                                    text = template.templateName.substringBefore("協議"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.generateTemplatePreview() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Generate")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "生成小票列印效果預覽", fontWeight = FontWeight.Bold)
                    }

                    AnimatedVisibility(visible = showGeneratedPreview) {
                        val currentTemplate = orderTemplates[selectedTemplateIndex]
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "出票機物理打印預覽 (模擬 58mm 紙寬):",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                            
                            // Receipt styled canvas
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(1.dp, Color(0xFFCAC4D0)), RoundedCornerShape(16.dp))
                                    .background(Color(0xFFFCFAF2))
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "================================", color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = currentTemplate.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = currentTemplate.body, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = Color.DarkGray, lineHeight = 15.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = currentTemplate.footer, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = "================================", color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Text(
                        text = "當前 MQTT 狀態：" + (if (username.isNotEmpty()) "正在監聽「" + username + "」的任務" else "未登入，請先登入以啟動監聽"),
                        fontSize = 11.sp,
                        color = if (username.isNotEmpty()) Color(0xFF2E7D32) else Color(0xFFEF4444)
                    )
                }
            }
        }

        // Section header for services and gateway
        item {
            Text(
                text = "聯網會話授權與安全憑證 (Bearer Auth)",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF49454F),
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
        }

        // Login Gateway and Verification Setup
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(text = "連線安全憑證與會話授權", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                    Text(text = "獲取安全令牌以授權 API 網關遠端發送打印任務", style = MaterialTheme.typography.bodySmall, color = Color(0xFF49454F))
                    
                    HorizontalDivider(color = Color(0xFFCAC4D0))

                    // API Login Documentation
                    Text(
                        text = "API 登入接口開發規格參考：",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )

                    val loginExample = """
// 1. 請求 (POST JSON)
{
  "username": "10482937",
  "password": "9840"
}

// 2. 預期響應 (JSON)
{
  "status": "success",
  "token": "YOUR_SECURE_TOKEN_HERE"
}
                    """.trimIndent()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = loginExample,
                            color = Color(0xFF334155),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }

                    Text(
                        text = "系統支持從 token, accessToken 或 data.token 字段提取憑證。",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        color = Color(0xFF64748B)
                    )

                    HorizontalDivider(color = Color(0xFFCAC4D0))

                    // API URL field setup
                    OutlinedTextField(
                        value = apiUrl,
                        onValueChange = { viewModel.updateApiUrl(it) },
                        label = { Text("會話驗證主機網址 (API URL)") },
                        placeholder = { Text("https://example.com/api/login") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_url_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isLoggingIn,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "網域設定"
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1C1B1F),
                            unfocusedTextColor = Color(0xFF1C1B1F)
                        )
                    )

                    // 8-digit Username Field
                    OutlinedTextField(
                        value = username,
                        onValueChange = { viewModel.updateUsername(it) },
                        label = { Text("商戶帳號 (8位純數字)") },
                        placeholder = { Text("例如：10482937") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isLoggingIn,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "商戶憑證"
                            )
                        },
                        trailingIcon = {
                            if (username.isNotEmpty()) {
                                if (username.length == 8) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "長度合格",
                                        tint = Color(0xFF4CAF50)
                                    )
                                } else {
                                    Text(
                                        text = "${username.length}/8位",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFF44336),
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1C1B1F),
                            unfocusedTextColor = Color(0xFF1C1B1F)
                        )
                    )

                    // 4-digit Password numeric Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { viewModel.updatePassword(it) },
                        label = { Text("安全授權密碼 (4位數字 PIN 碼)") },
                        placeholder = { Text("例如：9840") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isLoggingIn,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Create,
                                contentDescription = "安全PIN碼"
                            )
                        },
                        trailingIcon = {
                            if (password.isNotEmpty()) {
                                if (password.length == 4) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "長度合格",
                                        tint = Color(0xFF4CAF50)
                                    )
                                } else {
                                    Text(
                                        text = "${password.length}/4位",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFF44336),
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1C1B1F),
                            unfocusedTextColor = Color(0xFF1C1B1F)
                        )
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Login execute triggering button
                    Button(
                        onClick = { viewModel.login() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("login_submit_btn"),
                        enabled = !isLoggingIn && username.length == 8 && password.length == 4 && apiUrl.isNotEmpty(),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6750A4),
                            contentColor = Color.White
                        )
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "正在驗證憑證與回話...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "登入憑證"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "建立連線授權並啟動同步", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Display token dynamically
                    AnimatedVisibility(visible = token.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF3EDF7), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "已獲取當前有效會話授權 (Bearer)",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF21005D)
                                )
                                Text(
                                    text = "清除會話 Token",
                                    fontSize = 10.sp,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { viewModel.clearToken() }
                                        .testTag("clear_token_btn")
                                )
                            }
                            Text(
                                text = "安全憑證 Token:\n$token",
                                color = Color(0xFF1C1B1F),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth().testTag("token_display_text")
                            )
                        }
                    }
                }
            }
        }

        // Section header for Web-to-Print API Gateway
        item {
            Text(
                text = "WEB-TO-PRINT 本地熱敏打印網關服務",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF49454F),
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
        }

        // Local background server controller & CURL info section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_gateway_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "本地網關服務",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "本地打印 HTTP 網關",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1C1B1F)
                                )
                                Text(
                                    text = "此手機將作為伺服器接收網頁端發送之請求",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF49454F)
                                )
                            }
                        }

                        // Switch to start/stop server
                        Switch(
                            checked = serverStatusText.startsWith("Running"),
                            onCheckedChange = { viewModel.toggleHttpServer() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF2E7D32),
                                checkedTrackColor = Color(0xFFC8E6C9)
                            )
                        )
                    }

                    // Server IP/Port Badge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (serverStatusText.startsWith("Running")) Color(0xFFE8F5E9) else Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (serverStatusText.startsWith("Running")) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                        shape = CircleShape
                                    )
                            )
                            
                            val statusChinese = if (serverStatusText.startsWith("Running")) {
                                "伺服器正在健康監聽中..."
                            } else {
                                "伺服器已離線停止"
                            }
                            
                            Text(
                                text = "現狀: $statusChinese $serverStatusText",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (serverStatusText.startsWith("Running")) Color(0xFF1B5E20) else Color(0xFF616161)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFCAC4D0))

                    // API Call Template instructions
                    Text(
                        text = "如何從網頁發送打印請求：",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )

                    if (token.isEmpty()) {
                        // Warning state: Must secure token first
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFF3CD), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFFFEBAA), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "警報",
                                    tint = Color(0xFF856404),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "網關發送要求：必須攜帶 Bearer 授權憑證。請先於上方「憑證與會話授權」板塊登入商戶帳號，獲取有效 Token 後，此處即可自動為您配備 CURL 語句範本。",
                                    fontSize = 11.sp,
                                    color = Color(0xFF856404),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    } else {
                        // Display CURL and JSON format instructions
                        val ip = SunmiHttpServer.getLocalIpAddress() ?: "DEVICE_IP"
                        val formattedCurl = """
                            curl -X POST "http://$ip:$serverPort/print" \
                              -H "Authorization: Bearer $token" \
                              -H "Content-Type: application/json" \
                              -d '{
                                "title": "澳門會員通 • 本機即時測試",
                                "body": "1x 拿鐵咖啡        $4.50\n1x 肉桂捲          $3.20\n------------------------\n應付總額:          $7.70",
                                "footer": "網關遠端測試打印成功"
                              }'
                        """.trimIndent()

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "於您的網頁後台或小程式中，發送附帶 Bearer 授權憑證的 POST 印表請求即可：",
                                fontSize = 11.sp,
                                color = Color(0xFF49454F),
                                lineHeight = 16.sp
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF27272A), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = formattedCurl,
                                    color = Color(0xFFF4F4F5),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action panel: Standard Retail Receipt
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEADDFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "零售預覽",
                                tint = Color(0xFF21005D),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "標准零售及收銀模擬聯",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1B1F)
                            )
                            Text(
                                text = "主要用於校準打印頭偏位與物理紙張寬度對齊",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF49454F)
                            )
                        }
                    }

                    Text(
                        text = "本功能支持直接調用系統底層打印緩衝驅動，支持多語言字體縮放與格式化，並支持 NFC 錢包支付後的本地即時熱敏聯收據。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF49454F),
                        lineHeight = 20.sp
                    )

                    Button(
                        onClick = { viewModel.printReceiptTest() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("print_receipt_btn"),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6750A4),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "樣板零售小票"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "點此列印標准零售模擬收票", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Live execution log traces
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "實時系統運行日誌記錄 (LIVE LOGS)",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF49454F),
                    modifier = Modifier.padding(start = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(24.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
                        .padding(16.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(logMessages) { log ->
                            val colorLog = when {
                                log.contains("Error") || log.contains("失敗") || log.contains("Blocked") || log.contains("拒絕") || log.contains("Failed") || log.contains("錯誤") -> Color(0xFFEF4444)
                                log.contains("Success") || log.contains("成功") || log.contains("已連接") -> Color(0xFF4CAF50)
                                else -> Color(0xFF38BDF8)
                            }
                            Text(
                                text = log,
                                color = colorLog,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    LaunchedEffect(logMessages.size) {
                        if (logMessages.isNotEmpty()) {
                            listState.animateScrollToItem(0)
                        }
                    }
                }
            }
        }

        // Geometric Balanced Footer: Primary styled Action Button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.triggerInit() },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("init_printer_btn"),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6750A4),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重設排版",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "清空重置打印機狀態", fontWeight = FontWeight.Bold)
                }

                // Secondary Accent Action Button (Mirror of Theme's custom round footer btn)
                OutlinedButton(
                    onClick = { viewModel.reconnect() },
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("footer_quick_reconnect_btn"),
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF6750A4)
                    ),
                    border = BorderStroke(2.dp, Color(0xFF6750A4)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "快速偵測重連",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PrinterPreview() {
    MacaumemebershipprintingserviceTheme {
        PrinterScreen(viewModel = PrinterViewModel(androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application))
    }
}
