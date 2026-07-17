package ru.fixbyte.quizand.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fixbyte.quizand.models.UserRole
import ru.fixbyte.quizand.ui.theme.*
import ru.fixbyte.quizand.viewmodels.AppViewModel

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryBlue),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "Q",
                fontSize = 80.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                "QUIZ",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Text(
                "Многопользовательское квиз приложение",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RoleSelectionScreen(viewModel: AppViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Text(
                "Выберите роль",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { viewModel.chooseRole(UserRole.HOST) }
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("👨‍🏫", fontSize = 36.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Text(
                            "Ведущий",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { viewModel.chooseRole(UserRole.PLAYER) }
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SecondaryTeal),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🎮", fontSize = 36.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Text(
                            "Игрок",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HostLobbyScreen(viewModel: AppViewModel) {
    val hostNickname by viewModel.hostNickname.collectAsState()
    val hostPortText by viewModel.hostPortText.collectAsState()
    val players by viewModel.players.collectAsState()
    val connectionHint by viewModel.connectionHint.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Ведущий",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Button(
                onClick = { viewModel.resetToRoleSelection() },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Выход", fontSize = 12.sp, color = TextPrimary)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Настройки сервера",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }

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

            OutlinedTextField(
                value = hostPortText,
                onValueChange = { viewModel.onHostPortTextChanged(it) },
                label = { Text("Порт") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
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
                        .height(50.dp)
                        .padding(top = 16.dp, bottom = 24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Начать игру", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(players.sortedByDescending { scores[it.id] ?: 0 }) { player ->
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

            Text(
                "Доступные серверы",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discoveredServers) { server ->
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
        }

        Button(
            onClick = { viewModel.connectAsPlayer() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(16.dp),
            enabled = playerNickname.isNotEmpty() && selectedServerID != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = SecondaryTeal,
                disabledContainerColor = Color.Gray
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Подключиться", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(players.sortedByDescending { scores[it.id] ?: 0 }.take(3)) { player ->
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

