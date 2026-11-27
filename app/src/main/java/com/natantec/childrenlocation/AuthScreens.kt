package com.natantec.childrenlocation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Login de adulto: Google ou e-mail e senha. */
@Composable
fun AdultAuthScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun runEmail(create: Boolean) {
        busy = true
        error = null
        scope.launch {
            try {
                if (create) Repo.signUpEmail(email, pass) else Repo.signInEmail(email, pass)
                onDone()
            } catch (e: Exception) {
                error = friendlyError(e)
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Entrar como responsável", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Só adultos têm conta. A criança não precisa de cadastro.",
            Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val act = ctx.findActivity() ?: error("Tela indisponível")
                        Repo.signInGoogle(googleIdToken(act))
                        onDone()
                    } catch (e: Exception) {
                        error = friendlyError(e)
                    }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Entrar com Google") }

        Text("ou com e-mail e senha", Modifier.padding(vertical = 12.dp))

        OutlinedTextField(
            email, { email = it }, label = { Text("E-mail") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            pass, { pass = it }, label = { Text("Senha (mín. 6)") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ runEmail(false) }, enabled = !busy) { Text("Entrar") }
            OutlinedButton({ runEmail(true) }, enabled = !busy) { Text("Criar conta") }
        }
        TextButton(onBack, Modifier.padding(top = 8.dp)) { Text("Voltar") }
    }
}

/** Contas de e-mail e senha precisam confirmar o e-mail (conta Google já vem confirmada). */
@Composable
fun VerifyEmailScreen(onVerified: () -> Unit, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Confirme seu e-mail", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Enviamos um link para ${Repo.myEmail}. Abra o e-mail, toque no link e volte aqui.",
            Modifier.padding(vertical = 12.dp),
        )

        Button(
            onClick = {
                busy = true
                msg = null
                scope.launch {
                    try {
                        if (Repo.refreshVerified()) onVerified()
                        else msg = "Ainda não confirmado. Toque no link do e-mail e tente de novo."
                    } catch (e: Exception) {
                        msg = friendlyError(e)
                    }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Já confirmei") }

        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        Repo.sendVerificationEmail()
                        msg = "E-mail reenviado. Veja também a caixa de spam."
                    } catch (e: Exception) {
                        msg = friendlyError(e)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Reenviar e-mail") }

        msg?.let { Text(it, Modifier.padding(top = 12.dp)) }
        TextButton(onLogout, Modifier.padding(top = 8.dp)) { Text("Sair da conta") }
    }
}
