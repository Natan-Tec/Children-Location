package com.natantec.childrenlocation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Tela do aparelho da CRIANÇA. Não tem configurações, nem botão de sair ou de parar:
 * quem administra tudo são os pais.
 */
@Composable
fun ChildRoot(onUnpaired: () -> Unit) {
    val ctx = LocalContext.current
    val pairing = remember { Prefs.loadPairing(ctx) }
    if (pairing != null) {
        ChildGuard(pairing, onUnpaired)
    } else {
        LaunchedEffect(Unit) { onUnpaired() }
    }
}

@Composable
private fun ChildGuard(p: Pairing, onUnpaired: () -> Unit) {
    val ctx = LocalContext.current
    val stillPaired by remember { Repo.observePaired(p) }.collectAsState(initial = true)

    LaunchedEffect(stillPaired) {
        if (!stillPaired) {
            // Os pais desvincularam este aparelho: limpa tudo e volta para a tela inicial.
            LocationService.stop(ctx)
            Prefs.clearPairing(ctx)
            Repo.signOut()
            onUnpaired()
        }
    }
    if (stillPaired) ChildHome(p)
}

@Composable
private fun ChildHome(p: Pairing) {
    val ctx = LocalContext.current
    val fine = Manifest.permission.ACCESS_FINE_LOCATION
    val background = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    var hasPerm by remember { mutableStateOf(granted(ctx, fine)) }
    val loc by remember { Repo.observeLocation(p.familyId, p.childId) }.collectOrNull()

    fun startAll() {
        hasPerm = granted(ctx, fine)
        if (hasPerm) {
            LocationService.start(ctx)
            askBatteryExemption(ctx)
        }
    }

    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startAll()
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[fine] == true) {
            if (Build.VERSION.SDK_INT >= 29 && !granted(ctx, background)) bgLauncher.launch(background)
            else startAll()
        } else {
            hasPerm = false
        }
    }

    fun requestPerms() {
        val perms = mutableListOf(fine, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        permLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(Unit) {
        if (granted(ctx, fine)) startAll() else requestPerms()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Olá, ${p.childName}! 👋", style = MaterialTheme.typography.headlineSmall)
        Text("Família ${p.familyName}", Modifier.padding(bottom = 8.dp))

        if (!hasPerm) {
            Text(
                "Preciso da permissão de localização para avisar seus responsáveis onde você está.",
                color = MaterialTheme.colorScheme.error,
            )
            Button({ requestPerms() }, Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Permitir localização")
            }
        } else {
            Text("✅ Compartilhando sua localização com seus responsáveis.")
        }

        OsmMap(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), loc?.lat, loc?.lng, "Você")

        Text(
            "Só seus responsáveis podem mudar as configurações deste app.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Pede uma vez para o Android não encerrar o app em segundo plano (Samsung, Xiaomi etc.). */
private fun askBatteryExemption(ctx: Context) {
    if (Prefs.batteryPromptShown(ctx)) return
    Prefs.setBatteryPromptShown(ctx)
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        )
    }
}
