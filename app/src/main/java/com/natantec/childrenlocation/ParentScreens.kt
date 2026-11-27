package com.natantec.childrenlocation

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val DOWNLOAD_URL = "https://github.com/Natan-Tec/Children-Location/releases"

// ---------------------------------------------------------------- Raiz do lado dos pais

@Composable
fun ParentRoot(onLogout: () -> Unit) {
    var verified by remember { mutableStateOf(Repo.user?.isEmailVerified == true) }
    if (!verified) {
        VerifyEmailScreen(onVerified = { verified = true }, onLogout = onLogout)
    } else {
        ParentVerified(onLogout)
    }
}

@Composable
private fun ParentVerified(onLogout: () -> Unit) {
    LaunchedEffect(Unit) { runCatching { Repo.ensureUserDoc() } }

    val userBox by remember { Repo.observeUser() }.collectOrNull()
    val familyBox by remember { Repo.observeMyFamily() }.collectOrNull()
    val invites by remember { Repo.observeMyInvites() }.collectAsState(initial = emptyList<Invite>())

    val user = userBox?.value
    val family = familyBox?.value

    when {
        userBox == null || familyBox == null ->
            Text("Carregando...", Modifier.padding(24.dp))
        user?.bloqueado == true -> BlockedScreen(onLogout)
        family != null -> FamilyHome(family, onLogout)
        else -> NoFamilyScreen(user, invites, onLogout)
    }
}

@Composable
private fun BlockedScreen(onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Acesso bloqueado", style = MaterialTheme.typography.headlineSmall)
        Text("Esta conta não tem permissão para usar o app.", Modifier.padding(vertical = 12.dp))
        TextButton(onLogout) { Text("Sair da conta") }
    }
}

// ---------------------------------------------------------------- Sem família ainda

@Composable
private fun NoFamilyScreen(user: UserDoc?, invites: List<Invite>, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var familyName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Bem-vindo(a)", style = MaterialTheme.typography.headlineSmall)

        invites.forEach { inv ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${inv.invitedByName} convidou você para a família \"${inv.familyName}\".")
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({
                            scope.launch {
                                try { Repo.acceptInvite(inv) } catch (e: Exception) { error = friendlyError(e) }
                            }
                        }) { Text("Aceitar") }
                        OutlinedButton({
                            scope.launch { runCatching { Repo.deleteInvite(inv) } }
                        }) { Text("Recusar") }
                    }
                }
            }
        }

        if (user == null) {
            Text("Preparando sua conta...")
        } else if (user.aprovado) {
            Text("Dê um nome para a sua família e comece a cadastrar as crianças.")
            OutlinedTextField(
                familyName, { familyName = it }, label = { Text("Nome da família") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    scope.launch {
                        try { Repo.createFamily(familyName) } catch (e: Exception) { error = friendlyError(e) }
                    }
                },
                enabled = familyName.isNotBlank(),
            ) { Text("Criar família") }
        } else {
            Text(
                "Sua conta foi criada e está aguardando a aprovação do administrador. " +
                    "Assim que for liberada, esta tela muda sozinha. Se alguém já convidou você, " +
                    "o convite aparece acima e não precisa de aprovação."
            )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onLogout) { Text("Sair da conta") }
    }
}

// ---------------------------------------------------------------- Início da família

private enum class Tab { CHILDREN, ADULTS }

@Composable
private fun FamilyHome(family: Family, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.CHILDREN) }
    var mapFor by remember { mutableStateOf<Child?>(null) }

    val open = mapFor
    if (open != null) {
        ChildMapScreen(open, onBack = { mapFor = null })
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Família ${family.name}", style = MaterialTheme.typography.headlineSmall)

        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == Tab.CHILDREN) Button({ }) { Text("Crianças") }
            else OutlinedButton({ tab = Tab.CHILDREN }) { Text("Crianças") }
            if (tab == Tab.ADULTS) Button({ }) { Text("Responsáveis") }
            else OutlinedButton({ tab = Tab.ADULTS }) { Text("Responsáveis") }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (tab) {
                Tab.CHILDREN -> ChildrenTab(family, onOpenMap = { mapFor = it })
                Tab.ADULTS -> AdultsTab(family)
            }
        }

        TextButton(onLogout) { Text("Sair da conta") }
    }
}

// ---------------------------------------------------------------- Aba Crianças

private val EMOJIS = listOf("👦", "👧", "🧒", "👶", "🐻", "🦊", "⚽", "🌟")

@Composable
private fun ChildrenTab(family: Family, onOpenMap: (Child) -> Unit) {
    val scope = rememberCoroutineScope()
    val children by remember(family.id) { Repo.observeChildren(family) }
        .collectAsState(initial = emptyList<Child>())
    var showAdd by remember { mutableStateOf(false) }
    var qrFor by remember { mutableStateOf<Child?>(null) }
    var removeFor by remember { mutableStateOf<Child?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Button({ showAdd = true }, Modifier.fillMaxWidth()) { Text("➕ Adicionar criança") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    if (children.isEmpty()) {
        Text(
            "Nenhuma criança ainda. Toque em \"Adicionar criança\", escolha o nome e mostre o QR code " +
                "para o celular dela.",
            Modifier.padding(top = 16.dp),
        )
    }

    children.forEach { c ->
        ChildCard(
            c,
            onMap = { onOpenMap(c) },
            onQr = { qrFor = c },
            onUnpair = {
                scope.launch { try { Repo.unpairChild(c) } catch (e: Exception) { error = friendlyError(e) } }
            },
            onRemove = { removeFor = c },
        )
    }

    if (showAdd) {
        AddChildDialog(
            onDismiss = { showAdd = false },
            onCreate = { name, emoji ->
                scope.launch {
                    try {
                        val c = Repo.addChild(family, name, emoji)
                        showAdd = false
                        qrFor = c
                    } catch (e: Exception) {
                        error = friendlyError(e)
                        showAdd = false
                    }
                }
            },
        )
    }

    qrFor?.let { c -> QrDialog(c, onClose = { qrFor = null }) }

    removeFor?.let { c ->
        AlertDialog(
            onDismissRequest = { removeFor = null },
            title = { Text("Remover ${c.name}?") },
            text = { Text("O perfil e o vínculo com o celular dela serão apagados.") },
            confirmButton = {
                TextButton({
                    scope.launch { runCatching { Repo.deleteChild(c) } }
                    removeFor = null
                }) { Text("Remover") }
            },
            dismissButton = { TextButton({ removeFor = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ChildCard(
    c: Child,
    onMap: () -> Unit,
    onQr: () -> Unit,
    onUnpair: () -> Unit,
    onRemove: () -> Unit,
) {
    val loc by remember(c.id) { Repo.observeLocation(c.familyId, c.id) }.collectOrNull()

    val status = when {
        !c.paired -> "Aguardando pareamento (mostre o QR code)"
        loc == null -> "Pareado. Aguardando a primeira localização..."
        else -> {
            val ago = System.currentTimeMillis() - loc!!.updatedMs
            val rel = DateUtils.getRelativeTimeSpanString(loc!!.updatedMs).toString()
            if (ago > 10 * 60 * 1000L) "⚠️ Sem sinal: última atualização $rel"
            else "Atualizado $rel · bateria ${loc!!.battery}%"
        }
    }

    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(c.emoji, style = MaterialTheme.typography.headlineMedium)
                Column {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (c.paired) {
                    Button(onMap) { Text("Ver mapa") }
                    TextButton(onUnpair) { Text("Desvincular") }
                } else {
                    Button(onQr) { Text("Mostrar QR") }
                }
                TextButton(onRemove) { Text("Remover") }
            }
        }
    }
}

@Composable
private fun AddChildDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(EMOJIS[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova criança") },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it }, label = { Text("Nome") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Escolha uma figura", Modifier.padding(top = 12.dp, bottom = 4.dp))
                EMOJIS.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { e ->
                            if (e == emoji) Button({ emoji = e }, contentPadding = EMOJI_PADDING) { Text(e) }
                            else OutlinedButton({ emoji = e }, contentPadding = EMOJI_PADDING) { Text(e) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({ onCreate(name, emoji) }, enabled = name.isNotBlank()) { Text("Criar e mostrar QR") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}

private val EMOJI_PADDING = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

@Composable
private fun QrDialog(c: Child, onClose: () -> Unit) {
    var qr by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(c.id) {
        try { qr = Repo.createPairQr(c) } catch (e: Exception) { error = friendlyError(e) }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("QR code de ${c.name}") },
        text = {
            Column {
                val code = qr
                if (code != null) {
                    val bmp = remember(code) { qrBitmap(code) }
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "QR code de pareamento",
                        modifier = Modifier.size(260.dp),
                    )
                    Text(
                        "No celular da criança, instale o app e toque em \"Sou criança: escanear QR code\". " +
                            "Vale por 15 minutos e só pode ser usado uma vez.",
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Gerando QR code...")
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Fechar") } },
    )
}

// ---------------------------------------------------------------- Mapa da criança

@Composable
private fun ChildMapScreen(c: Child, onBack: () -> Unit) {
    val loc by remember(c.id) { Repo.observeLocation(c.familyId, c.id) }.collectOrNull()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onBack) { Text("← Voltar") }
            Text(
                "${c.emoji} ${c.name}",
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        val l = loc
        val ago = if (l == null) 0L else System.currentTimeMillis() - l.updatedMs
        Text(
            when {
                l == null -> "Aguardando a localização de ${c.name}..."
                ago > 10 * 60 * 1000L ->
                    "⚠️ Sem sinal desde ${DateUtils.getRelativeTimeSpanString(l.updatedMs)}"
                else ->
                    "Bateria ${l.battery}% · precisão ±${l.accuracy.toInt()} m · " +
                        DateUtils.getRelativeTimeSpanString(l.updatedMs)
            },
            Modifier.padding(vertical = 8.dp),
        )

        OsmMap(Modifier.weight(1f).fillMaxWidth(), l?.lat, l?.lng, c.name)
    }
}

// ---------------------------------------------------------------- Aba Responsáveis

@Composable
private fun AdultsTab(family: Family) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = Repo.uid
    val invites by remember(family.id) { Repo.observeFamilyInvites(family.id) }
        .collectAsState(initial = emptyList<Invite>())
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun share(mail: String) {
        val msg = "Olá! Convido você para acompanhar a família \"${family.name}\" no app Children Location.\n" +
            "1) Baixe o app: $DOWNLOAD_URL\n" +
            "2) Crie sua conta (ou entre com Google) usando exatamente este e-mail: $mail\n" +
            "3) Toque em \"Aceitar\" no convite."
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, msg)
        }
        ctx.startActivity(Intent.createChooser(send, "Enviar convite"))
    }

    Text("Quem acompanha", style = MaterialTheme.typography.titleMedium)
    family.members.forEach { m ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val label = family.memberNames[m] ?: m
            val owner = if (m == family.ownerUid) " · dono" else ""
            Text(label + owner, Modifier.padding(top = 12.dp).weight(1f))
            if (me == family.ownerUid && m != me) {
                TextButton({
                    scope.launch {
                        try { Repo.removeMember(family, m) } catch (e: Exception) { error = friendlyError(e) }
                    }
                }) { Text("Remover") }
            }
        }
    }

    Text("Convidar outro adulto", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleMedium)
    Text(
        "Digite o e-mail da mãe, avó etc. O convite é enviado pelo WhatsApp ou e-mail do seu celular.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        email, { email = it }, label = { Text("E-mail do convidado") },
        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Button(
        onClick = {
            scope.launch {
                try {
                    Repo.createInvite(family, email)
                    share(email.trim().lowercase())
                    email = ""
                    error = null
                } catch (e: Exception) {
                    error = friendlyError(e)
                }
            }
        },
        enabled = email.isNotBlank(),
        modifier = Modifier.padding(top = 8.dp),
    ) { Text("Enviar convite") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    if (invites.isNotEmpty()) {
        Text("Convites pendentes", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleMedium)
        invites.forEach { inv ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(inv.email, Modifier.padding(top = 12.dp).weight(1f))
                TextButton({ share(inv.email) }) { Text("Reenviar") }
                TextButton({ scope.launch { runCatching { Repo.deleteInvite(inv) } } }) { Text("Cancelar") }
            }
        }
    }
}
