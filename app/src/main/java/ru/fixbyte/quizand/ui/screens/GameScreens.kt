package ru.fixbyte.quizand.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ru.fixbyte.quizand.models.ConnectionMode
import ru.fixbyte.quizand.models.UserRole
import ru.fixbyte.quizand.ui.theme.*
import ru.fixbyte.quizand.util.generateQrBitmap
import ru.fixbyte.quizand.viewmodels.AppViewModel

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(colors = listOf(SplashIndigo, PrimaryBlue))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(8.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text("Я", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Я Знаю",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
fun ConnectionModeSelectionScreen(viewModel: AppViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Text(
                "Как соединены телефоны?",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "От этого зависит, как игроки будут находить ведущего",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Button(
                onClick = { viewModel.chooseConnectionMode(ConnectionMode.ROUTER) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Через Wi-Fi роутер", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Оба телефона в одной домашней/офисной сети — автопоиск хоста",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { viewModel.chooseConnectionMode(ConnectionMode.HOTSPOT) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Через точку доступа телефона", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                    Text(
                        "Один телефон раздаёт хотспот — подключение по IP-адресу",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RoleSelectionScreen(viewModel: AppViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Button(
            onClick = { viewModel.backToConnectionModeSelection() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Назад", fontSize = 12.sp, color = TextPrimary)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Text(
                "Выберите роль",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Button(
                onClick = { viewModel.chooseRole(UserRole.HOST) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Я ведущий", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { viewModel.chooseRole(UserRole.PLAYER) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Я игрок", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Локальная игра по Wi-Fi без интернета",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun HostLobbyScreen(viewModel: AppViewModel) {
    val hostNickname by viewModel.hostNickname.collectAsState()
    val players by viewModel.players.collectAsState()
    val connectionHint by viewModel.connectionHint.collectAsState()
    val connectionMode by viewModel.connectionMode.collectAsState()
    val hostLocalIp by viewModel.hostLocalIp.collectAsState()
    val hostPortText by viewModel.hostPortText.collectAsState()

    var showRulesDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(top = 8.dp)
        ) {
            Text(
                "Запуск игры",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showRulesDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Правила", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { showSettingsDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Настройки", fontSize = 12.sp)
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = hostNickname,
                onValueChange = { viewModel.onHostNicknameChanged(it) },
                label = { Text("Ваш ник") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = { viewModel.startHosting() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 1.dp
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Запустить сервер",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (connectionHint.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = SecondaryTeal.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        connectionHint,
                        fontSize = 14.sp,
                        color = SecondaryTealDark,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (connectionMode == ConnectionMode.HOTSPOT && hostLocalIp != null) {
                var showQrDialog by remember { mutableStateOf(false) }
                val hostAddress = "$hostLocalIp:$hostPortText"

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable { showQrDialog = true },
                    colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PrimaryBlue)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Скажите игрокам подключиться по IP (нажмите для QR-кода):",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Text(
                            hostAddress,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (showQrDialog) {
                    Dialog(onDismissRequest = { showQrDialog = false }) {
                        val qrBitmap = remember(hostAddress) { generateQrBitmap(hostAddress) }
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Отсканируйте камерой на телефоне игрока",
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR-код для подключения",
                                    modifier = Modifier.size(240.dp)
                                )
                                Text(
                                    hostAddress,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { showQrDialog = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Закрыть", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            Text(
                "Подключенные игроки (${players.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            players.forEach { player ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎮", fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
                        Text(player.nickname, fontSize = 14.sp, color = TextPrimary)
                    }
                }
            }

            if (players.isNotEmpty()) {
                Button(
                    onClick = { viewModel.startGameAsHost() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 16.dp)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Начать игру", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }

            OutlinedButton(
                onClick = { viewModel.resetToRoleSelection() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Назад", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
        }
    }

    if (showRulesDialog) {
        HostRulesDialog(onDismiss = { showRulesDialog = false })
    }

    if (showSettingsDialog) {
        HostSettingsDialog(viewModel = viewModel, onDismiss = { showSettingsDialog = false })
    }
}

@Composable
private fun HostRulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        title = { Text("Как проходит раунд", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RuleRow(number = "1", text = "Ведущий задаёт вопрос устно или читает его из внешнего источника.")
                RuleRow(number = "2", text = "Ведущий нажимает «Открыть раунд».")
                RuleRow(number = "3", text = "Игроки нажимают кнопку «Ответить». Засчитывается только первое нажатие.")
                RuleRow(number = "4", text = "Ведущий видит, кто ответил первым, и принимает решение.")
                RuleRow(number = "5", text = "Если ответ неверный — раунд продолжается, но этот игрок повторно нажать уже не может.")
                RuleRow(number = "6", text = "Если ответ верный — игрок получает 1 очко, а раунд закрывается.")
            }
        }
    )
}

@Composable
private fun RuleRow(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(PrimaryBlue, shape = androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Text(text, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HostSettingsDialog(viewModel: AppViewModel, onDismiss: () -> Unit) {
    val hostNickname by viewModel.hostNickname.collectAsState()
    val hostPortText by viewModel.hostPortText.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки ведущего", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = hostNickname,
                    onValueChange = { viewModel.onHostNicknameChanged(it) },
                    label = { Text("Имя ведущего") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = hostPortText,
                    onValueChange = { viewModel.onHostPortTextChanged(it) },
                    label = { Text("Порт") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Text(
                    "Текущий: $hostPortText",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.startHosting()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Применить и перезапустить сервер", fontSize = 13.sp, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun HostControlScreen(viewModel: AppViewModel) {
    val players by viewModel.players.collectAsState()
    val roundIsOpen by viewModel.roundIsOpen.collectAsState()
    val activeResponder by viewModel.activeResponder.collectAsState()
    val scores by viewModel.scores.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Управление", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Button(
                onClick = { viewModel.resetToRoleSelection() },
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Выход", fontSize = 12.sp, color = TextPrimary)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Таблица очков",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                players.sortedByDescending { scores[it.id] ?: 0 }.forEach { player ->
                    val isResponder = player.id == activeResponder?.id
                    val backgroundColor by animateColorAsState(
                        targetValue = if (isResponder) AccentYellow.copy(alpha = 0.2f) else Color.White,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                        label = "bg"
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = backgroundColor),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isResponder) BorderStroke(2.dp, AccentYellow) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isResponder) "🎤" else "🎮", fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
                                Text(player.nickname, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                            Text("${scores[player.id] ?: 0}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                    }
                }
            }

            if (activeResponder != null && roundIsOpen) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentYellow.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, AccentYellow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Отвечает:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            activeResponder!!.nickname,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.judgeCurrentResponder(true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("✓ Верно", fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                            Button(
                                onClick = { viewModel.judgeCurrentResponder(false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("✗ Неверно", fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (roundIsOpen) viewModel.closeRoundAsHost()
                    else viewModel.openRoundAsHost()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (roundIsOpen) AccentRed else AccentGreen
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (roundIsOpen) "Закрыть раунд" else "Открыть раунд",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun PlayerJoinScreen(viewModel: AppViewModel) {
    val playerNickname by viewModel.playerNickname.collectAsState()
    val selectedServerID by viewModel.selectedServerID.collectAsState()
    val discoveredServers by viewModel.discoveredServers.collectAsState()
    val connectionHint by viewModel.connectionHint.collectAsState()
    val connectionMode by viewModel.connectionMode.collectAsState()
    val isRouterMode = connectionMode != ConnectionMode.HOTSPOT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Присоединение", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Button(
                onClick = { viewModel.resetToRoleSelection() },
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Назад", fontSize = 12.sp, color = TextPrimary)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = playerNickname,
                onValueChange = { viewModel.onPlayerNicknameChanged(it) },
                label = { Text("Ваш ник") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(12.dp)
            )

            if (isRouterMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Доступные серверы",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    TextButton(onClick = { viewModel.refreshServerDiscovery() }) {
                        Text("Обновить", fontSize = 14.sp, color = PrimaryBlue)
                    }
                }

                if (discoveredServers.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = SecondaryTeal.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(8.dp),
                                color = SecondaryTeal,
                                strokeWidth = 3.dp
                            )
                            Text(
                                connectionHint,
                                fontSize = 14.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        discoveredServers.forEach { server ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.onServerSelected(server.id) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedServerID == server.id) SecondaryTeal.copy(alpha = 0.15f) else Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = if (selectedServerID == server.id) BorderStroke(2.dp, SecondaryTeal) else null
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(server.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text(
                                        "${server.ipAddress}:${server.port}",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                val disabledReason = when {
                    playerNickname.isEmpty() -> "Введите ник, чтобы подключиться"
                    selectedServerID == null -> "Выберите сервер из списка, чтобы подключиться"
                    else -> null
                }
                if (disabledReason != null) {
                    Text(
                        disabledReason,
                        fontSize = 12.sp,
                        color = AccentRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                Button(
                    onClick = { viewModel.connectAsPlayer() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(bottom = 16.dp),
                    enabled = playerNickname.isNotEmpty() && selectedServerID != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SecondaryTeal,
                        contentColor = Color.White,
                        // Color.Gray (#808080) давал контраст с белым текстом ~4:1 — на грани читаемости.
                        // Более тёмный серый даёт ~6:1, текст остаётся чётким и в неактивном состоянии.
                        disabledContainerColor = Color(0xFF616161),
                        disabledContentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Подключиться", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                var manualHostText by remember { mutableStateOf("") }
                val context = LocalContext.current

                val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
                    if (result.contents != null) {
                        manualHostText = result.contents
                    }
                }
                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        qrLauncher.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("Наведите камеру на QR-код ведущего")
                                .setBeepEnabled(false)
                        )
                    }
                }
                fun launchQrScan() {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        qrLauncher.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("Наведите камеру на QR-код ведущего")
                                .setBeepEnabled(false)
                        )
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                Text(
                    "IP-адрес ведущего",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "В сети хотспота автопоиск ненадёжен — отсканируйте QR-код с экрана ведущего или введите IP-адрес вручную",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedButton(
                    onClick = { launchQrScan() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📷 Сканировать QR-код", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                }
                OutlinedTextField(
                    value = manualHostText,
                    onValueChange = { manualHostText = it },
                    label = { Text("IP:порт (например 192.168.1.5:5000)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = { viewModel.connectAsPlayerManual(manualHostText) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(bottom = 16.dp),
                    enabled = playerNickname.isNotEmpty() && manualHostText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF616161),
                        disabledContentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Подключиться по IP", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun PlayerWaitingScreen(viewModel: AppViewModel) {
    val connectionHint by viewModel.connectionHint.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(60.dp)
                    .padding(bottom = 32.dp),
                color = SecondaryTeal,
                strokeWidth = 4.dp
            )
            Text(
                connectionHint,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 32.dp),
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick = { viewModel.resetToRoleSelection() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, SecondaryTeal)
            ) {
                Text("Отменить", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SecondaryTeal)
            }
        }
    }
}

@Composable
fun PlayerQuestionScreen(viewModel: AppViewModel) {
    val players by viewModel.players.collectAsState()
    val roundIsOpen by viewModel.roundIsOpen.collectAsState()
    val activeResponder by viewModel.activeResponder.collectAsState()
    val localIsCurrentResponder by viewModel.localIsCurrentResponder.collectAsState()
    val localHasAttemptedInRound by viewModel.localHasAttemptedInRound.collectAsState()
    val scores by viewModel.scores.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Игра", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Button(
                onClick = { viewModel.resetToRoleSelection() },
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Выход", fontSize = 12.sp, color = TextPrimary)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Таблица очков",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                players.sortedByDescending { scores[it.id] ?: 0 }.take(3).forEach { player ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🎮", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                                Text(player.nickname, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            }
                            Text("${scores[player.id] ?: 0}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (roundIsOpen) AccentGreen.copy(alpha = 0.15f) else TextTertiary.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        when {
                            activeResponder != null -> "🎤 Идет ответ"
                            roundIsOpen -> "❓ Раунд открыт!"
                            else -> "⏳ Ожидание..."
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (roundIsOpen) AccentGreen else TextSecondary
                    )
                    if (activeResponder != null) {
                        Text(
                            "Отвечает: ${activeResponder!!.nickname}",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                roundIsOpen && !localIsCurrentResponder && !localHasAttemptedInRound -> {
                    Button(
                        onClick = { viewModel.playerPressedAnswerButton() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .shadow(12.dp, RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("BUZZ!", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text("Ответить", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
                localIsCurrentResponder -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AccentYellow.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, AccentYellow)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🎤", fontSize = 36.sp, modifier = Modifier.padding(bottom = 8.dp))
                            Text("Вы отвечаете!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
                localHasAttemptedInRound -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Divider.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("⏳", fontSize = 36.sp, modifier = Modifier.padding(bottom = 8.dp))
                            Text("Вы уже попытались", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}