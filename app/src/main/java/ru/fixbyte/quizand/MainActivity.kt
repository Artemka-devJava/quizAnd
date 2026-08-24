package ru.fixbyte.quizand

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ru.fixbyte.quizand.models.AppPhase
import ru.fixbyte.quizand.ui.screens.*
import ru.fixbyte.quizand.ui.theme.QuizAndTheme
import ru.fixbyte.quizand.viewmodels.AppViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuizAndTheme {
                val phase by viewModel.phase.collectAsState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    when (phase) {
                        AppPhase.SPLASH -> SplashScreen()
                        AppPhase.CONNECTION_MODE_SELECTION -> ConnectionModeSelectionScreen(viewModel)
                        AppPhase.ROLE_SELECTION -> RoleSelectionScreen(viewModel)
                        AppPhase.HOST_LOBBY -> HostLobbyScreen(viewModel)
                        AppPhase.HOST_CONTROL -> HostControlScreen(viewModel)
                        AppPhase.PLAYER_JOIN -> PlayerJoinScreen(viewModel)
                        AppPhase.PLAYER_WAITING -> PlayerWaitingScreen(viewModel)
                        AppPhase.PLAYER_QUESTION -> PlayerQuestionScreen(viewModel)
                    }
                }
            }
        }
    }
}

