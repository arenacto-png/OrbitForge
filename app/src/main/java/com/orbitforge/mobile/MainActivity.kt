package com.orbitforge.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbitforge.mobile.agent.AgentEngine
import com.orbitforge.mobile.agent.WorkspaceTools
import com.orbitforge.mobile.agent.WorkspaceTransfer
import com.orbitforge.mobile.agent.WriteApproval
import com.orbitforge.mobile.agent.WorkspaceTerminal
import com.orbitforge.mobile.ai.ApiMessage
import com.orbitforge.mobile.ai.OpenAiCompatibleClient
import com.orbitforge.mobile.ai.ProviderConfig
import com.orbitforge.mobile.diagnostics.DiagnosticLog
import com.orbitforge.mobile.github.GitHubRepositoryClient
import com.orbitforge.mobile.github.GitHubRepository
import com.orbitforge.mobile.mcp.McpClient
import com.orbitforge.mobile.security.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val ForgeBackground = Color(0xFF0B0D12)
private val ForgeSurface = Color(0xFF151923)
private val ForgeAccent = Color(0xFF8B7CFF)
private val ForgeMuted = Color(0xFF9AA4B7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticLog.add(applicationContext, "INFO", "App", "Application launched; version=${BuildConfig.VERSION_NAME}")
        setContent { OrbitForgeTheme { OrbitForgeApp(AppStorage(applicationContext), SecretStore(applicationContext)) } }
    }
}

@Composable
private fun OrbitForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = ForgeAccent, onPrimary = Color.White, background = ForgeBackground,
        surface = ForgeSurface, onSurface = Color(0xFFE8EBF2), secondary = Color(0xFF56D6C9)), content = content)
}

private enum class Destination { Chat, Projects, Editor, Terminal, Settings, Logs }
private data class ChatMessage(val role: String, val text: String)

@Composable
private fun OrbitForgeApp(storage: AppStorage, secrets: SecretStore) {
    // Keep network/agent work alive while the user switches between top-level tabs.
    val appScope = rememberCoroutineScope()
    var destination by remember { mutableStateOf(Destination.Chat) }
    var selectedFile by remember { mutableStateOf("") }
    Scaffold(containerColor = ForgeBackground, bottomBar = {
        NavigationBar(containerColor = ForgeSurface, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            NavigationBarItem(selected = destination == Destination.Chat, onClick = { destination = Destination.Chat }, icon = { Icon(Icons.Default.SmartToy, null) }, label = { Text("Агент") })
            NavigationBarItem(selected = destination == Destination.Projects || destination == Destination.Editor || destination == Destination.Terminal, onClick = { destination = Destination.Projects }, icon = { Icon(Icons.Default.FolderOpen, null) }, label = { Text("Проекты") })
            NavigationBarItem(selected = destination == Destination.Settings || destination == Destination.Logs, onClick = { destination = Destination.Settings }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Настройки") })
        }
    }) { padding ->
        Box(Modifier.fillMaxSize()) {
            // Keep ChatScreen composed while hidden so its state and active request survive tab changes.
            ChatScreen(
                storage,
                secrets,
                appScope,
                modifier = if (destination == Destination.Chat) {
                    Modifier.padding(padding).fillMaxSize()
                } else {
                    Modifier.size(0.dp)
                }
            )
            when (destination) {
                Destination.Chat -> Unit
                Destination.Projects -> ProjectsScreen(storage, onOpenFile = { selectedFile = it; destination = Destination.Editor }, onOpenTerminal = { destination = Destination.Terminal }, modifier = Modifier.padding(padding))
                Destination.Editor -> EditorScreen(storage, selectedFile, onBack = { destination = Destination.Projects }, modifier = Modifier.padding(padding))
                Destination.Terminal -> TerminalScreen(onBack = { destination = Destination.Projects }, modifier = Modifier.padding(padding))
                Destination.Settings -> SettingsScreen(storage, secrets, onOpenLogs = { destination = Destination.Logs }, modifier = Modifier.padding(padding))
                Destination.Logs -> LogsScreen(onBack = { destination = Destination.Settings }, modifier = Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun AppHeader(subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).background(ForgeAccent.copy(alpha = 0.18f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Code, contentDescription = null, tint = ForgeAccent)
        }
        Column { Text("OrbitForge", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 12.sp, color = ForgeMuted) }
        Spacer(Modifier.weight(1f)); Box(Modifier.size(9.dp).background(Color(0xFF56D6C9), CircleShape))
    }
}

@Composable
private fun ChatScreen(storage: AppStorage, secrets: SecretStore, requestScope: CoroutineScope, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val history = remember {
        mutableStateListOf<ChatMessage>().apply {
            val saved = storage.loadMessages()
            for (i in 0 until saved.length()) {
                val obj = saved.optJSONObject(i) ?: continue
                val role = obj.optString("role", "assistant")
                val content = obj.optString("content", "")
                if (content.isNotBlank() && role in listOf("user", "assistant")) add(ChatMessage(role, content))
            }
            if (isEmpty()) add(ChatMessage("system", "OrbitForge готов. Настрой API, чтобы подключить модель."))
        }
    }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var agentMode by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (secrets.hasApiKey()) "Ключ сохранён · готов к запросу" else "Модель не подключена") }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var approvalRequest by remember { mutableStateOf<WriteApproval?>(null) }
    var approvalSignal by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val client = remember { OpenAiCompatibleClient(context.applicationContext) }
    val agent = remember { AgentEngine(context.applicationContext) }

    Column(modifier.fillMaxSize().background(ForgeBackground)) {
        AppHeader("ИИ-среда разработки")
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = ForgeSurface),
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (agentMode) "РЕЖИМ АГЕНТА" else "РЕЖИМ ЧАТА",
                    color = ForgeMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(if (busy) "Выполняется задача…" else "Готов к работе",
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(status, color = ForgeMuted, fontSize = 12.sp)
            }
        }
        if (history.none { it.role == "user" }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("С чего начнём?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Выбери пример или напиши свою задачу. Для ответов модели сначала настрой подключение.",
                    fontSize = 12.sp,
                    color = ForgeMuted
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { draft = "Объясни, как устроен мой проект, и с чего лучше начать." },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) { Text("Изучить проект", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { draft = "Помоги найти и исправить ошибку в моём коде." },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) { Text("Исправить ошибку", fontSize = 11.sp) }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { draft = "Создай понятный README.md для моего проекта." },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) { Text("Создать README", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { draft = "Предложи план улучшения архитектуры и интерфейса проекта." },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) { Text("План улучшений", fontSize = 11.sp) }
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(history) { MessageBubble(it) }
            if (busy) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(status.ifBlank { "Работаю…" }, color = ForgeMuted)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Использовать инструменты агента", color = ForgeMuted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Switch(checked = agentMode, onCheckedChange = { agentMode = it }, enabled = !busy)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (agentMode) "Задача для агента…" else "Напиши сообщение…") },
                shape = RoundedCornerShape(18.dp), maxLines = 4, enabled = !busy
            )
            Button(
                enabled = !busy && draft.isNotBlank(),
                onClick = {
                    val text = draft.trim()
                    if (text.isEmpty()) return@Button
                    val key = secrets.readApiKey()
                    if (key.isBlank()) {
                        status = "Сначала добавь API-ключ в настройках"
                        DiagnosticLog.add(context, "WARN", "Chat", "Request blocked because API key is missing")
                        return@Button
                    }
                    if (storage.model.isBlank()) {
                        status = "Укажи ID модели в настройках"
                        return@Button
                    }

                    draft = ""
                    history.add(ChatMessage("user", text))
                    val requestHistory = history.filter { it.role == "user" || it.role == "assistant" }
                        .map { ApiMessage(it.role, it.text) }
                    history.add(ChatMessage("assistant", ""))
                    val assistantIndex = history.lastIndex
                    persistHistory(history, storage)
                    busy = true
                    status = if (agentMode) "Запускаю агентный цикл…" else "Связываюсь с провайдером…"
                    DiagnosticLog.add(context, "INFO", "Chat", "Request started; agentMode=$agentMode; messages=${history.size}")

                    activeJob = requestScope.launch {
                        try {
                            if (agentMode) {
                                val answer = agent.runTask(
                                    config = ProviderConfig(storage.endpoint, storage.model),
                                    apiKey = key,
                                    userTask = text,
                                    approveWrite = { approval ->
                                        val deferred = CompletableDeferred<Boolean>()
                                        approvalRequest = approval
                                        approvalSignal = deferred
                                        deferred.await()
                                    },
                                    onStatus = { newStatus ->
                                        status = newStatus
                                        if (assistantIndex in history.indices) {
                                            history[assistantIndex] = ChatMessage("assistant", "⏳ $newStatus")
                                        }
                                    }
                                )
                                history[assistantIndex] = ChatMessage("assistant", answer)
                                status = "Задача завершена"
                                DiagnosticLog.add(context, "INFO", "Agent", "Task completed; responseChars=${answer.length}")
                            } else {
                                val answer = StringBuilder()
                                client.streamChat(
                                    ProviderConfig(storage.endpoint, storage.model),
                                    key,
                                    requestHistory
                                ).collect { chunk ->
                                    answer.append(chunk)
                                    history[assistantIndex] = ChatMessage("assistant", answer.toString())
                                }
                                if (answer.isEmpty()) {
                                    history[assistantIndex] = ChatMessage("assistant", "Модель завершила запрос без текстового ответа.")
                                }
                                status = "Ответ получен · ${storage.model}"
                                DiagnosticLog.add(context, "INFO", "Chat", "Request succeeded; responseChars=${answer.length}")
                            }
                            persistHistory(history, storage)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            val safe = e.message ?: e.javaClass.simpleName
                            if (assistantIndex in history.indices) {
                                history[assistantIndex] = ChatMessage(
                                    "assistant",
                                    "Ошибка: $safe\n\nОткрой Настройки → Логи, скопируй диагностический отчёт и отправь его для разбора."
                                )
                            }
                            persistHistory(history, storage)
                            status = "Ошибка · см. Логи"
                            DiagnosticLog.add(context, "ERROR", if (agentMode) "Agent" else "Chat",
                                "Request failed: ${e.javaClass.simpleName}: ${e.message ?: "no details"}")
                        } finally {
                            busy = false
                            activeJob = null
                            approvalRequest = null
                            approvalSignal = null
                        }
                    }
                },
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) { Icon(Icons.Default.Send, contentDescription = "Отправить") }
        }
        if (busy) {
            TextButton(
                onClick = { activeJob?.cancel(); approvalSignal?.cancel(); busy = false; status = "Запрос отменён" },
                modifier = Modifier.align(Alignment.End).padding(end = 12.dp)
            ) { Text("Остановить запрос") }
        }
    }

    val approval = approvalRequest
    if (approval != null) {
        AlertDialog(
            onDismissRequest = {
                approvalSignal?.complete(false)
                approvalRequest = null
                approvalSignal = null
            },
            title = { Text(when {
                approval.path.startsWith("MCP:") -> "Разрешить внешний MCP-вызов?"
                approval.path.startsWith("terminal:") -> "Разрешить команду терминала?"
                else -> "Разрешить запись файла?"
            }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        approval.path.startsWith("MCP:") -> {
                            Text("Внешний инструмент: ${approval.path.removePrefix("MCP: ")}", fontWeight = FontWeight.SemiBold)
                            Text("Аргументы вызова:", color = ForgeMuted)
                            Column(Modifier.heightIn(max = 240.dp).fillMaxWidth().background(ForgeSurface, RoundedCornerShape(8.dp)).padding(10.dp).verticalScroll(rememberScrollState())) {
                                Text(approval.content, fontSize = 11.sp, color = Color(0xFFD5D9E3))
                            }
                            Text("Вызов передаст аргументы настроенному внешнему MCP-серверу. Продолжай только если доверяешь этому серверу.")
                        }
                        approval.path.startsWith("terminal:") -> {
                            Text(approval.path.removePrefix("terminal: "), fontWeight = FontWeight.SemiBold)
                            Text(approval.content, color = ForgeMuted)
                            Text("Будет выполнена только команда из встроенного списка OrbitForge, не системный shell.")
                        }
                        else -> {
                            Text("Путь: ${approval.path}", fontWeight = FontWeight.SemiBold)
                            Text(if (approval.diffPreview.isNotBlank()) "Предпросмотр изменений:" else "Предпросмотр содержимого:", color = ForgeMuted)
                            Column(Modifier.heightIn(max = 240.dp).fillMaxWidth().background(ForgeSurface, RoundedCornerShape(8.dp)).padding(10.dp).verticalScroll(rememberScrollState())) {
                                Text(
                                    (approval.diffPreview.ifBlank { approval.content }).ifBlank { "(пустой файл)" },
                                    fontSize = 11.sp, color = Color(0xFFD5D9E3)
                                )
                            }
                            Text("Лимит записи агента — 10 КБ. Запись выполняется только внутри рабочей папки OrbitForge.")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    approvalSignal?.complete(true)
                    approvalRequest = null
                    approvalSignal = null
                }) { Text("Разрешить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    approvalSignal?.complete(false)
                    approvalRequest = null
                    approvalSignal = null
                }) { Text("Отклонить") }
            }
        )
    }
}

private fun persistHistory(history: List<ChatMessage>, storage: AppStorage) {
    val array = JSONArray(); history.forEach { array.put(JSONObject().put("role", it.role).put("content", it.text)) }; storage.saveMessages(array)
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    if (message.text.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start) {
        Surface(color = if (message.role == "user") Color(0xFF292345) else ForgeSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(0.88f)) {
            Text(message.text, Modifier.padding(14.dp), color = Color(0xFFE8EBF2), fontSize = 14.sp)
        }
    }
}

@Composable
private fun ProjectsScreen(storage: AppStorage, onOpenFile: (String) -> Unit, onOpenTerminal: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transfer = remember { WorkspaceTransfer(context.applicationContext) }
    var treeUriString by remember { mutableStateOf(storage.projectTreeUri) }
    var transferBusy by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var newFilePath by remember { mutableStateOf("README.md") }
    var notice by remember { mutableStateOf("") }
    val workspace = storage.workspace()
    val files = remember(refresh) { workspace.walkTopDown().filter { it.isFile }.take(300).toList() }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                storage.projectTreeUri = uri.toString()
                treeUriString = uri.toString()
                transferBusy = true
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { transfer.importFrom(uri) }
                        notice = "Импортировано файлов: ${result.copied}, пропущено: ${result.skipped}."
                        DiagnosticLog.add(context, "INFO", "WorkspaceTransfer", "Import complete: copied=${result.copied}; skipped=${result.skipped}")
                        refresh++
                    } catch (e: Exception) {
                        notice = "Импорт не выполнен: ${e.message ?: e.javaClass.simpleName}"
                        DiagnosticLog.add(context, "ERROR", "WorkspaceTransfer", "Import failed: ${e.javaClass.simpleName}: ${e.message ?: "no details"}")
                    } finally { transferBusy = false }
                }
            } catch (e: Exception) {
                notice = "Не удалось получить доступ к папке: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    Column(modifier.fillMaxSize().background(ForgeBackground).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeader("Рабочая область", Modifier.padding(horizontal = 0.dp))
        Text("Проекты и файлы", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Твоя локальная рабочая область", color = ForgeMuted, fontSize = 12.sp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ForgeSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(48.dp).background(ForgeAccent.copy(alpha = 0.16f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ForgeAccent) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Рабочая область", fontWeight = FontWeight.SemiBold)
                    Text(if (files.isEmpty()) "Пока нет файлов" else "Файлы готовы к работе", color = ForgeMuted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(files.size.toString(), color = ForgeAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("файлов", color = ForgeMuted, fontSize = 10.sp)
                }
            }
        }
        Text("ИНСТРУМЕНТЫ", color = ForgeMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onOpenTerminal, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Открыть встроенный терминал")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !transferBusy, onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f)) {
                Text(if (transferBusy) "Подожди…" else "Выбрать папку и импортировать")
            }
            OutlinedButton(
                enabled = !transferBusy && treeUriString.isNotBlank(),
                onClick = { confirmExport = true },
                modifier = Modifier.weight(1f)
            ) { Text("Экспортировать") }
        }
        if (treeUriString.isNotBlank()) {
            Text("Связанная папка: ${treeUriString.take(90)}", color = ForgeMuted, fontSize = 10.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = newFilePath, onValueChange = { newFilePath = it }, modifier = Modifier.weight(1f), label = { Text("Путь нового файла") }, singleLine = true)
            Button(onClick = {
                try {
                    val rootPath = workspace.canonicalFile.path
                    require(newFilePath.isNotBlank()) { "Укажи путь файла." }
                    val file = java.io.File(workspace, newFilePath.trim()).canonicalFile
                    require(file.path.startsWith(rootPath + java.io.File.separator)) { "Путь должен быть внутри рабочей папки." }
                    require(!file.exists()) { "Файл уже существует. Открой его из списка." }
                    file.parentFile?.mkdirs()
                    file.writeText("", Charsets.UTF_8)
                    notice = "Файл создан: ${newFilePath.trim()}"
                    DiagnosticLog.add(context, "INFO", "Projects", "File created: ${newFilePath.take(120)}")
                    refresh++
                } catch (e: Exception) {
                    notice = e.message ?: "Не удалось создать файл."
                    DiagnosticLog.add(context, "WARN", "Projects", notice)
                }
            }) { Text("Создать") }
        }
        if (notice.isNotBlank()) Text(notice, color = ForgeMuted, fontSize = 12.sp)
        if (files.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = ForgeSurface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Рабочая папка пуста", fontWeight = FontWeight.SemiBold)
                    Text("Создай README.md или другой файл. Агент сможет читать файлы и предлагать изменения, требующие подтверждения.", color = ForgeMuted, fontSize = 13.sp)
                }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files) { file ->
                val relative = file.relativeTo(workspace).path.replace(java.io.File.separatorChar, '/')
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenFile(relative) }, colors = CardDefaults.cardColors(containerColor = ForgeSurface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, null, tint = ForgeAccent)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(relative, fontWeight = FontWeight.Medium)
                            Text("${file.length()} байт", color = ForgeMuted, fontSize = 12.sp)
                        }
                        Text("Открыть", color = ForgeAccent, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text("Экспортировать рабочую папку?") },
            text = { Text("Файлы из OrbitForge будут скопированы в выбранную внешнюю папку. Совпадающие файлы могут быть перезаписаны. Секретные файлы, сборки и ключи исключаются. Проверь выбранное расположение перед продолжением.") },
            confirmButton = {
                Button(onClick = {
                    confirmExport = false
                    val uri = runCatching { Uri.parse(treeUriString) }.getOrNull()
                    if (uri == null) {
                        notice = "Не удалось прочитать URI папки."
                    } else {
                        transferBusy = true
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) { transfer.exportTo(uri) }
                                notice = "Экспортировано файлов: ${result.copied}, пропущено: ${result.skipped}."
                                DiagnosticLog.add(context, "INFO", "WorkspaceTransfer", "Export complete: copied=${result.copied}; skipped=${result.skipped}")
                            } catch (e: Exception) {
                                notice = "Экспорт не выполнен: ${e.message ?: e.javaClass.simpleName}"
                                DiagnosticLog.add(context, "ERROR", "WorkspaceTransfer", "Export failed: ${e.javaClass.simpleName}: ${e.message ?: "no details"}")
                            } finally { transferBusy = false }
                        }
                    }
                }) { Text("Экспортировать файлы") }
            },
            dismissButton = { TextButton(onClick = { confirmExport = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun TerminalScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val terminal = remember { WorkspaceTerminal(context.applicationContext) }
    val entries = remember { mutableStateListOf<Pair<String, String>>() }
    var command by remember { mutableStateOf("help") }
    var pendingCommand by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }

    fun runCommand(value: String) {
        val trimmed = value.trim()
        if (trimmed.isBlank() || running) return
        running = true
        scope.launch {
            try {
                val output = withContext(Dispatchers.IO) { terminal.execute(trimmed) }
                if (output == "__CLEAR__") entries.clear()
                else entries.add(trimmed to output)
                while (entries.size > 80) entries.removeAt(0)
                DiagnosticLog.add(context, "INFO", "Terminal", "Command executed: ${trimmed.substringBefore(' ').take(32)}; chars=${trimmed.length}")
                notice = ""
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val message = e.message ?: e.javaClass.simpleName
                entries.add(trimmed to "Ошибка: $message")
                DiagnosticLog.add(context, "WARN", "Terminal", "Command failed: ${trimmed.substringBefore(' ').take(32)}; ${e.javaClass.simpleName}: $message")
                notice = message
            } finally {
                running = false
            }
        }
    }

    Column(modifier.fillMaxSize().background(ForgeBackground).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppHeader("Ограниченный терминал", Modifier.padding(horizontal = 0.dp))
        Text("Команды выполняются внутри рабочей папки OrbitForge. Это не системный shell; пайпы, перенаправления и цепочки команд запрещены.", color = ForgeMuted, fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF080A0E), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entries.isEmpty()) item { Text("Введи help, чтобы увидеть команды.", color = ForgeMuted, fontSize = 12.sp) }
            items(entries) { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("> ${entry.first}", color = Color(0xFF56D6C9), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(entry.second, color = Color(0xFFD5D9E3), fontSize = 12.sp, lineHeight = 15.sp)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = command, onValueChange = { command = it }, modifier = Modifier.weight(1f), placeholder = { Text("Команда") }, singleLine = true)
            Button(enabled = !running && command.isNotBlank(), onClick = {
                val value = command.trim()
                if (terminal.isMutating(value)) pendingCommand = value else runCommand(value)
                command = ""
            }) { Text(if (running) "Выполняю…" else "Выполнить") }
        }
        if (notice.isNotBlank()) Text(notice, color = ForgeMuted, fontSize = 12.sp)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Назад к проектам") }
    }
    val approval = pendingCommand
    if (approval != null) {
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text("Подтвердить команду?") },
            text = { Text("Команда изменит рабочую папку: $approval\n\nСистемные команды и произвольный shell не запускаются.") },
            confirmButton = { Button(enabled = !running, onClick = { pendingCommand = null; runCommand(approval) }) { Text("Выполнить") } },
            dismissButton = { TextButton(onClick = { pendingCommand = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun EditorScreen(storage: AppStorage, relativePath: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tools = remember { WorkspaceTools(context.applicationContext) }
    val initialRead = remember(relativePath) { runCatching { tools.readFile(relativePath) } }
    var content by remember(relativePath) { mutableStateOf(initialRead.getOrDefault("")) }
    var notice by remember(relativePath) { mutableStateOf(initialRead.exceptionOrNull()?.message.orEmpty()) }
    Column(modifier.fillMaxSize().background(ForgeBackground).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppHeader("Редактор файлов", Modifier.padding(horizontal = 0.dp))
        Text(relativePath, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(value = content, onValueChange = { content = it }, modifier = Modifier.weight(1f).fillMaxWidth(), placeholder = { Text("Содержимое файла") }, maxLines = 1000, shape = RoundedCornerShape(12.dp))
        if (notice.isNotBlank()) Text(notice, color = ForgeMuted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Назад") }
            Button(onClick = {
                try {
                    val result = tools.writeFileFromUser(relativePath, content)
                    notice = result
                    DiagnosticLog.add(context, "INFO", "Editor", "File saved: $relativePath (${content.length} chars)")
                } catch (e: Exception) {
                    notice = e.message ?: "Не удалось сохранить файл"
                    DiagnosticLog.add(context, "ERROR", "Editor", "Save failed for $relativePath: ${e.message ?: e.javaClass.simpleName}")
                }
            }, modifier = Modifier.weight(1f)) { Text("Сохранить") }
        }
    }
}


@Composable
private fun SettingsScreen(storage: AppStorage, secrets: SecretStore, onOpenLogs: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf(storage.endpoint) }
    var model by remember { mutableStateOf(storage.model) }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var showGitHubToken by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var githubToken by remember { mutableStateOf("") }
    var repoName by remember { mutableStateOf("OrbitForge") }
    var repoTarget by remember { mutableStateOf("") }
    var confirmPush by remember { mutableStateOf(false) }
    var repoDescription by remember { mutableStateOf("Android AI development workspace") }
    var repoPrivate by remember { mutableStateOf(true) }
    var githubBusy by remember { mutableStateOf(false) }
    var repositories by remember { mutableStateOf<List<GitHubRepository>>(emptyList()) }
    var mcpEndpoint by remember { mutableStateOf(storage.mcpEndpoint) }
    var mcpToken by remember { mutableStateOf("") }
    var showMcpToken by remember { mutableStateOf(false) }
    var mcpBusy by remember { mutableStateOf(false) }
    val githubClient = remember { GitHubRepositoryClient() }
    Column(modifier.fillMaxSize().background(ForgeBackground).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeader("Центр управления", Modifier.padding(horizontal = 0.dp))
        Text("Настройки", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ForgeSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(42.dp).background(
                        (if (secrets.hasApiKey()) Color(0xFF56D6C9) else ForgeAccent).copy(alpha = 0.16f),
                        RoundedCornerShape(13.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = if (secrets.hasApiKey()) Color(0xFF56D6C9) else ForgeAccent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (secrets.hasApiKey()) "Ключ API сохранён" else "Подключение не настроено", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (secrets.hasApiKey() && storage.model.isNotBlank()) "Модель: ${storage.model}" else "Укажи провайдера и модель ниже",
                        color = ForgeMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Text("ПРОВАЙДЕР ИИ", color = ForgeMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Совместимый с OpenAI Chat Completions API провайдер", color = ForgeMuted, fontSize = 13.sp)
        OutlinedTextField(endpoint, { endpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Базовый URL API") }, singleLine = true)
        OutlinedTextField(model, { model = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ID модели") }, singleLine = true)
        OutlinedTextField(apiKey, { apiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (secrets.hasApiKey()) "Новый API-ключ (пусто — без изменений)" else "API-ключ") }, singleLine = true, visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation())
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Скрыть ключ" else "Показать ключ") }; Spacer(Modifier.weight(1f))
            Text(if (secrets.hasApiKey()) "Ключ сохранён" else "Ключ не задан", color = ForgeMuted, fontSize = 12.sp)
        }
        Button(onClick = {
            if (!endpoint.trim().startsWith("https://")) { notice = "Адрес должен начинаться с https://"; return@Button }
            if (model.isBlank()) { notice = "Укажи ID модели."; return@Button }
            try { ProviderConfig(endpoint, model).chatCompletionsUrl() } catch (e: Exception) { notice = e.message ?: "Некорректный URL"; return@Button }
            storage.endpoint = endpoint; storage.model = model
            val keyUpdated = apiKey.isNotBlank()
            if (keyUpdated) secrets.saveApiKey(apiKey)
            apiKey = ""; notice = "Настройки сохранены на устройстве."
            DiagnosticLog.add(context, "INFO", "Settings", "Provider settings saved; model=${model.take(80)}, keyUpdated=$keyUpdated")
        }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить настройки") }
        if (secrets.hasApiKey()) {
            TextButton(onClick = { secrets.saveApiKey(""); apiKey = ""; notice = "API-ключ удалён с устройства."; DiagnosticLog.add(context, "INFO", "Settings", "Stored API key deleted") }, modifier = Modifier.align(Alignment.End)) {
                Text("Удалить сохранённый API-ключ")
            }
        }
        Button(
            enabled = !testing,
            onClick = {
                val key = apiKey.takeIf { it.isNotBlank() } ?: secrets.readApiKey()
                if (key.isBlank()) {
                    notice = "Сначала введи и сохрани API-ключ."
                    return@Button
                }
                if (model.isBlank()) {
                    notice = "Укажи ID модели."
                    return@Button
                }
                testing = true
                notice = "Проверяю подключение коротким запросом…"
                scope.launch {
                    val answer = StringBuilder()
                    try {
                        OpenAiCompatibleClient(context.applicationContext)
                            .streamChat(ProviderConfig(endpoint, model), key, listOf(ApiMessage("user", "Reply with OK only.")))
                            .collect { answer.append(it) }
                        notice = if (answer.isNotBlank()) "Подключение успешно. Ответ модели получен." else "Ответ получен, но пустой."
                        DiagnosticLog.add(context, "INFO", "Settings", "Connection test succeeded; responseChars=${answer.length}")
                    } catch (e: Exception) {
                        notice = "Проверка не пройдена: ${e.message ?: e.javaClass.simpleName}"
                        DiagnosticLog.add(context, "ERROR", "Settings", "Connection test failed: ${e.javaClass.simpleName}: ${e.message ?: "no details"}")
                    } finally {
                        testing = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (testing) "Проверяю…" else "Проверить подключение") }
        if (notice.isNotBlank()) Text(notice, color = ForgeMuted)
        Card(colors = CardDefaults.cardColors(containerColor = ForgeSurface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MCP — внешние инструменты", fontWeight = FontWeight.SemiBold)
                Text("Подключается удалённый MCP-сервер по HTTPS. Каждый внешний вызов потребует подтверждения.", color = ForgeMuted, fontSize = 12.sp)
                OutlinedTextField(mcpEndpoint, { mcpEndpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("MCP endpoint (HTTPS)") }, singleLine = true)
                OutlinedTextField(
                    mcpToken, { mcpToken = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(if (secrets.hasMcpToken()) "Новый MCP bearer token (пусто — без изменений)" else "MCP bearer token (необязательно)") },
                    visualTransformation = if (showMcpToken) VisualTransformation.None else PasswordVisualTransformation()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showMcpToken = !showMcpToken }) { Text(if (showMcpToken) "Скрыть token" else "Показать token") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        if (mcpEndpoint.isNotBlank() && !mcpEndpoint.trim().startsWith("https://")) {
                            notice = "MCP endpoint должен использовать HTTPS."
                            return@Button
                        }
                        storage.mcpEndpoint = mcpEndpoint.trim()
                        if (mcpToken.isNotBlank()) secrets.saveMcpToken(mcpToken.trim())
                        mcpToken = ""
                        notice = "MCP настройки сохранены."
                        DiagnosticLog.add(context, "INFO", "MCP", "MCP endpoint settings saved")
                    }) { Text("Сохранить MCP") }
                }
                if (secrets.hasMcpToken()) {
                    TextButton(onClick = {
                        secrets.saveMcpToken("")
                        mcpToken = ""
                        notice = "MCP token удалён с устройства."
                        DiagnosticLog.add(context, "INFO", "MCP", "Stored MCP token deleted")
                    }, modifier = Modifier.align(Alignment.End)) { Text("Удалить MCP token") }
                }
                OutlinedButton(
                    enabled = !mcpBusy,
                    onClick = {
                        if (mcpEndpoint.isBlank()) { notice = "Укажи MCP endpoint."; return@OutlinedButton }
                        mcpBusy = true
                        scope.launch {
                            try {
                                val session = McpClient().connect(mcpEndpoint, mcpToken.takeIf { it.isNotBlank() } ?: secrets.readMcpToken())
                                notice = "MCP подключён. Инструментов: ${session.tools.size}. Все вызовы потребуют подтверждения."
                                DiagnosticLog.add(context, "INFO", "MCP", "Connection test succeeded; tools=${session.tools.size}")
                            } catch (e: Exception) {
                                notice = "MCP: ${e.message ?: e.javaClass.simpleName}"
                                DiagnosticLog.add(context, "ERROR", "MCP", "Connection test failed: ${e.javaClass.simpleName}: ${e.message ?: "no details"}")
                            } finally { mcpBusy = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (mcpBusy) "Проверяю MCP…" else "Проверить MCP и список инструментов") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = ForgeSurface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GitHub", fontWeight = FontWeight.SemiBold)
                Text("Токен хранится отдельно в Android Keystore. Для создания репозитория токену нужны соответствующие права GitHub.", color = ForgeMuted, fontSize = 12.sp)
                OutlinedTextField(
                    value = githubToken, onValueChange = { githubToken = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(if (secrets.hasGitHubToken()) "Новый GitHub token (пусто — без изменений)" else "GitHub token") },
                    visualTransformation = if (showGitHubToken) VisualTransformation.None else PasswordVisualTransformation()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showGitHubToken = !showGitHubToken }) { Text(if (showGitHubToken) "Скрыть токен" else "Показать токен") }
                    Spacer(Modifier.weight(1f))
                    Text(if (secrets.hasGitHubToken()) "GitHub token сохранён" else "GitHub token не задан", color = ForgeMuted, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        if (githubToken.isNotBlank()) secrets.saveGitHubToken(githubToken.trim())
                        githubToken = ""
                        notice = if (secrets.hasGitHubToken()) "GitHub token сохранён." else "Введи токен перед сохранением."
                        DiagnosticLog.add(context, "INFO", "GitHub", "GitHub token storage updated")
                    }) { Text("Сохранить токен") }
                }
                if (secrets.hasGitHubToken()) {
                    TextButton(onClick = {
                        secrets.saveGitHubToken("")
                        githubToken = ""
                        notice = "GitHub token удалён с устройства."
                        DiagnosticLog.add(context, "INFO", "GitHub", "Stored GitHub token deleted")
                    }, modifier = Modifier.align(Alignment.End)) { Text("Удалить GitHub token") }
                }
                OutlinedTextField(repoName, { repoName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Имя нового репозитория") }, singleLine = true)
                OutlinedTextField(repoDescription, { repoDescription = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Описание") }, maxLines = 2)
                OutlinedTextField(repoTarget, { repoTarget = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Репозиторий для синхронизации (owner/name)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = repoPrivate, onCheckedChange = { repoPrivate = it })
                    Text("Приватный репозиторий", color = ForgeMuted, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !githubBusy, onClick = {
                        val token = githubToken.takeIf { it.isNotBlank() } ?: secrets.readGitHubToken()
                        if (token.isBlank()) { notice = "Сначала введи GitHub token."; return@Button }
                        githubBusy = true
                        scope.launch {
                            try {
                                val repo = githubClient.createRepository(token, repoName.trim(), repoDescription.trim(), repoPrivate)
                                repositories = (listOf(repo) + repositories.filterNot { it.fullName == repo.fullName }).take(30)
                                repoTarget = repo.fullName
                                notice = "Репозиторий создан: ${repo.fullName}"
                                DiagnosticLog.add(context, "INFO", "GitHub", "Repository created: ${repo.fullName}")
                            } catch (e: Exception) {
                                notice = e.message ?: "Не удалось создать репозиторий."
                                DiagnosticLog.add(context, "ERROR", "GitHub", "Create repository failed: ${e.javaClass.simpleName}: ${e.message ?: "no details"}")
                            } finally { githubBusy = false }
                        }
                    }, modifier = Modifier.weight(1f)) { Text(if (githubBusy) "Подожди…" else "Создать репозиторий") }
                    OutlinedButton(enabled = !githubBusy, onClick = {
                        val token = githubToken.takeIf { it.isNotBlank() } ?: secrets.readGitHubToken()
                        if (token.isBlank()) { notice = "Сначала введи GitHub token."; return@OutlinedButton }
                        githubBusy = true
                        scope.launch {
                            try {
                                repositories = githubClient.listRepositories(token)
                                if (repoTarget.isBlank() && repositories.isNotEmpty()) repoTarget = repositories.first().fullName
                                notice = "Загружено репозиториев: ${repositories.size}"
                                DiagnosticLog.add(context, "INFO", "GitHub", "Repository list loaded: ${repositories.size}")
                            } catch (e: Exception) {
                                notice = e.message ?: "Не удалось загрузить репозитории."
                                DiagnosticLog.add(context, "ERROR", "GitHub", "List repositories failed: ${e.javaClass.simpleName}: ${e.message ?: "no details"}")
                            } finally { githubBusy = false }
                        }
                    }) { Text("Обновить") }
                }
                OutlinedButton(
                    enabled = !githubBusy,
                    onClick = {
                        if (secrets.readGitHubToken().isBlank() && githubToken.isBlank()) {
                            notice = "Сначала сохрани GitHub token."
                        } else if (!repoTarget.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
                            notice = "Укажи репозиторий в формате owner/name или выбери его из списка."
                        } else {
                            confirmPush = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Отправить рабочую папку в GitHub") }
                repositories.forEach { repo ->
                    Column(
                        Modifier.fillMaxWidth().clickable { repoTarget = repo.fullName }
                            .background(if (repoTarget == repo.fullName) ForgeAccent.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(repo.fullName, fontWeight = FontWeight.Medium)
                        Text("${if (repo.isPrivate) "Приватный" else "Публичный"} · ${repo.htmlUrl}", color = ForgeMuted, fontSize = 11.sp)
                        Text(if (repoTarget == repo.fullName) "Выбран для синхронизации" else "Нажми, чтобы выбрать", color = ForgeAccent, fontSize = 11.sp)
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = ForgeSurface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Логи и диагностика", fontWeight = FontWeight.SemiBold)
                Text("Если что-то сломалось, скопируй диагностический отчёт или отправь его через меню Android. Секреты маскируются; перед отправкой всё равно проверь содержимое.", color = ForgeMuted, fontSize = 13.sp)
                OutlinedButton(onClick = onOpenLogs, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.BugReport, null); Text("Открыть логи и диагностику") }
            }
        }
    }
    if (confirmPush) {
        AlertDialog(
            onDismissRequest = { confirmPush = false },
            title = { Text("Отправить файлы в GitHub?") },
            text = {
                Text("OrbitForge создаст один commit в $repoTarget. Отправляются текстовые файлы из внутренней рабочей папки; известные секретные файлы, бинарники и файлы с похожими на токены строками пропускаются. Перед отправкой проверь содержимое рабочей папки.")
            },
            confirmButton = {
                Button(onClick = {
                    confirmPush = false
                    githubBusy = true
                    val token = githubToken.takeIf { it.isNotBlank() } ?: secrets.readGitHubToken()
                    scope.launch {
                        try {
                            val result = githubClient.pushWorkspace(token, repoTarget, storage.workspace())
                            notice = "Синхронизация завершена: ${result.uploadedFiles} файлов отправлено, ${result.skippedFiles} пропущено, ветка ${result.branch}."
                            DiagnosticLog.add(context, "INFO", "GitHub", "Workspace sync complete: uploaded=${result.uploadedFiles}; skipped=${result.skippedFiles}; branch=${result.branch}")
                        } catch (e: Exception) {
                            notice = e.message ?: "Синхронизация не удалась."
                            DiagnosticLog.add(context, "ERROR", "GitHub", "Workspace sync failed: ${e.javaClass.simpleName}: ${e.message ?: "no details"}")
                        } finally { githubBusy = false }
                    }
                }) { Text("Подтвердить отправку") }
            },
            dismissButton = { TextButton(onClick = { confirmPush = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun LogsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(makeDiagnosticReport(context)) }
    var notice by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().background(ForgeBackground).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeader("Диагностика приложения", Modifier.padding(horizontal = 0.dp)); Text("Логи и отчёт об ошибках", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Отчёт содержит технические сведения. Проверь его перед отправкой и удали личные данные, если они есть.", color = ForgeMuted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                report = makeDiagnosticReport(context)
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OrbitForge diagnostics", report)); notice = "Отчёт скопирован в буфер обмена."
                DiagnosticLog.add(context, "INFO", "Diagnostics", "Report copied to clipboard")
            }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.ContentCopy, null); Text("Копировать") }
            OutlinedButton(onClick = {
                report = makeDiagnosticReport(context)
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "OrbitForge diagnostic report"); putExtra(Intent.EXTRA_TEXT, report) }
                context.startActivity(Intent.createChooser(intent, "Отправить отчёт"))
            }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Share, null); Text("Отправить") }
        }
        OutlinedButton(onClick = { DiagnosticLog.clear(context); report = makeDiagnosticReport(context); notice = "Журнал очищен." }, modifier = Modifier.fillMaxWidth()) { Text("Очистить журнал") }
        if (notice.isNotBlank()) Text(notice, color = Color(0xFF56D6C9), fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(ForgeSurface, RoundedCornerShape(12.dp)).padding(12.dp)) { item { Text(report, color = Color(0xFFD5D9E3), fontSize = 11.sp, lineHeight = 15.sp) } }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.End)) { Text("Назад в настройки") }
    }
}

private fun makeDiagnosticReport(context: android.content.Context): String = buildString {
    appendLine("OrbitForge diagnostic report")
    appendLine("App version: ${BuildConfig.VERSION_NAME}")
    appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
    appendLine("--- LOGS ---")
    append(DiagnosticLog.redact(DiagnosticLog.read(context)))
}
