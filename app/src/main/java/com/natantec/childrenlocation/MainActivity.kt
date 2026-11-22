package com.natantec.childrenlocation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private enum class Screen { LOADING, AUTH, ROLE, CHILD, PARENT }

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
    var screen by remember { mutableStateOf(Screen.LOADING) }

    LaunchedEffect(Unit) {
        screen = if (Repo.uid == null) {
            Screen.AUTH
        } else {
            when (runCatching { Repo.getRole() }.getOrNull()) {
                "child" -> Screen.CHILD
                "parent" -> Screen.PARENT
                else -> Screen.ROLE
            }
        }
    }

    val logout = { Repo.signOut(); screen = Screen.AUTH }

    when (screen) {
        Screen.LOADING -> Text("Carregando...", Modifier.padding(24.dp))
        Screen.AUTH -> AuthScreen(onDone = { role ->
            screen = when (role) {
                "child" -> Screen.CHILD
                "parent" -> Screen.PARENT
                else -> Screen.ROLE
            }
        })
        Screen.ROLE -> RoleScreen(onChosen = { screen = if (it == "child") Screen.CHILD else Screen.PARENT })
        Screen.CHILD -> ChildScreen(onLogout = logout)
        Screen.PARENT -> ParentScreen(onLogout = logout)
    }
}

// ---------------------------------------------------------------- Login

@Composable
private fun AuthScreen(onDone: (String?) -> Unit) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run(create: Boolean) {
        busy = true; error = null
        scope.launch {
            try {
                if (create) Repo.signUp(email, pass) else Repo.signIn(email, pass)
                onDone(if (create) null else Repo.getRole())
            } catch (e: Exception) {
                error = e.message ?: "Erro ao entrar"
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Children Location", style = MaterialTheme.typography.headlineMedium)
        Text("Entre ou crie sua conta", Modifier.padding(bottom = 16.dp))
        OutlinedTextField(email, { email = it }, label = { Text("E-mail") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("Senha (mín. 6)") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ run(false) }, enabled = !busy) { Text("Entrar") }
            OutlinedButton({ run(true) }, enabled = !busy) { Text("Criar conta") }
        }
    }
}

@Composable
private fun RoleScreen(onChosen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    fun choose(role: String) {
        scope.launch {
            try { Repo.setRole(role); onChosen(role) } catch (e: Exception) { error = e.message }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Quem vai usar este celular?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Atenção: a escolha não pode ser alterada depois.",
            Modifier.padding(vertical = 12.dp)
        )
        Button({ choose("parent") }, Modifier.fillMaxWidth()) { Text("Sou pai / mãe / responsável") }
        OutlinedButton({ choose("child") }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Sou o filho(a) — este celular será localizado")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

// ---------------------------------------------------------------- Criança

@Composable
private fun ChildScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val links by Repo.observeLinks("childUid").collectAsState(initial = emptyList())
    var code by remember { mutableStateOf<String?>(null) }
    var sharing by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    // O código é de uso único: assim que um responsável vincula, ele é apagado.
    LaunchedEffect(links) {
        val current = code
        if (current != null && links.any { it.code == current }) {
            Repo.deletePairingCode(current); code = null
        }
    }

    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        LocationService.start(ctx); sharing = true
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            if (Build.VERSION.SDK_INT >= 29 && !granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                // Android 11+: o sistema leva a pessoa às configurações para escolher "Sempre".
                bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                LocationService.start(ctx); sharing = true
            }
        } else msg = "Sem permissão de localização não dá para compartilhar."
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Modo filho(a)", style = MaterialTheme.typography.headlineSmall)

        if (!sharing) {
            Button({
                val perms = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
                if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                permLauncher.launch(perms.toTypedArray())
            }, Modifier.fillMaxWidth()) { Text("Ativar compartilhamento de localização") }
        } else {
            Text("Compartilhando sua localização.")
            OutlinedButton({ LocationService.stop(ctx); sharing = false }) { Text("Parar compartilhamento") }
        }

        Text("Vincular um responsável", style = MaterialTheme.typography.titleMedium)
        if (code == null) {
            OutlinedButton({
                scope.launch { runCatching { Repo.createPairingCode() }.onSuccess { code = it }.onFailure { msg = it.message } }
            }) { Text("Gerar código (vale 10 min)") }
        } else {
            Text(code!!, style = MaterialTheme.typography.displaySmall)
            Text("Digite este código no app do responsável.")
        }

        Text("Quem vê minha localização", style = MaterialTheme.typography.titleMedium)
        if (links.isEmpty()) Text("Ninguém por enquanto.")
        links.forEach { l ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(l.parentEmail, Modifier.padding(top = 12.dp))
                TextButton({ scope.launch { runCatching { Repo.revokeLink(l) } } }) { Text("Remover") }
            }
        }

        msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onLogout) { Text("Sair da conta") }
    }
}

private fun granted(ctx: Context, perm: String) =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

// ---------------------------------------------------------------- Pai / mãe

@Composable
private fun ParentScreen(onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val links by Repo.observeLinks("parentUid").collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<Link?>(null) }
    var codeInput by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Modo responsável", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(codeInput, { codeInput = it.filter(Char::isDigit).take(8) },
                label = { Text("Código do filho(a)") }, singleLine = true, modifier = Modifier.weight(1f))
            Button({
                scope.launch {
                    msg = null
                    runCatching { Repo.linkWithCode(codeInput) }
                        .onSuccess { codeInput = "" }
                        .onFailure { msg = "Código inválido ou expirado" }
                }
            }, Modifier.padding(top = 8.dp)) { Text("Vincular") }
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            links.forEach { l ->
                OutlinedButton({ selected = l }) { Text(l.childName) }
            }
        }

        val sel = selected ?: links.firstOrNull()
        if (sel != null) ChildMap(sel, Modifier.weight(1f).fillMaxWidth())
        else Text("Nenhum filho vinculado ainda. Peça para ele gerar um código no celular dele.")

        TextButton(onLogout) { Text("Sair da conta") }
    }
}

@Composable
private fun ChildMap(link: Link, modifier: Modifier) {
    val loc by Repo.observeLocation(link.childUid).collectAsState(initial = null)
    val ctx = LocalContext.current

    Column(modifier) {
        val l = loc
        Text(
            if (l == null) "Aguardando localização de ${link.childName}..."
            else "${link.childName} · bateria ${l.battery}% · ±${l.accuracy.toInt()} m · " +
                DateUtils.getRelativeTimeSpanString(l.updatedMs)
        )
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = {
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(16.0)
                }
            },
            update = { map ->
                val point = loc?.let { GeoPoint(it.lat, it.lng) }
                map.overlays.clear()
                if (point != null) {
                    map.overlays.add(Marker(map).apply {
                        position = point
                        title = link.childName
                    })
                    map.controller.animateTo(point)
                }
                map.invalidate()
            }
        )
    }
}
