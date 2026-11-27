package com.natantec.childrenlocation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

private enum class Screen { START, ADULT_AUTH, PARENT, CHILD }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    var screen by remember {
        mutableStateOf(
            when {
                Prefs.loadPairing(ctx) != null && Repo.uid != null -> Screen.CHILD
                Repo.user != null && !Repo.isAnonymous -> Screen.PARENT
                else -> Screen.START
            }
        )
    }

    when (screen) {
        Screen.START -> StartScreen(
            onPaired = { screen = Screen.CHILD },
            onAdult = {
                screen = if (Repo.user != null && !Repo.isAnonymous) Screen.PARENT else Screen.ADULT_AUTH
            },
        )
        Screen.ADULT_AUTH -> AdultAuthScreen(
            onDone = { screen = Screen.PARENT },
            onBack = { screen = Screen.START },
        )
        Screen.PARENT -> ParentRoot(onLogout = { Repo.signOut(); screen = Screen.START })
        Screen.CHILD -> ChildRoot(onUnpaired = { screen = Screen.START })
    }
}

// ---------------------------------------------------------------- Tela inicial

@Composable
private fun StartScreen(onPaired: () -> Unit, onAdult: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Children Location", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Quem vai usar este celular?",
            Modifier.padding(top = 8.dp, bottom = 24.dp),
            style = MaterialTheme.typography.titleMedium,
        )

        Button(
            onClick = {
                error = null
                busy = true
                scanQr(
                    ctx,
                    onResult = { qr ->
                        scope.launch {
                            try {
                                val p = Repo.claimPairing(qr)
                                Prefs.savePairing(ctx, p)
                                onPaired()
                            } catch (e: Exception) {
                                error = e.message ?: "Não foi possível conectar"
                            }
                            busy = false
                        }
                    },
                    onError = { msg -> error = msg; busy = false },
                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sou criança: escanear QR code") }

        OutlinedButton(
            onClick = onAdult,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("Sou pai, mãe ou responsável") }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }
        Text(
            "O QR code é gerado no celular dos seus pais, em \"Crianças\".",
            Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
