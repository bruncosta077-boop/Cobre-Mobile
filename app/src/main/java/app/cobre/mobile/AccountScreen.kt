package app.cobre.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val AccountCard = Color(0xFF191816)
private val AccountText = Color(0xFFECE6DF)
private val AccountMuted = Color(0xFF9B938B)
private val AccountGood = Color(0xFF7EB783)
private val AccountError = Color(0xFFE58B78)

@Composable
fun AccountPage(
    repository: CobreIdRepository,
    state: AccountUi,
    desktopOrigin: String,
    desktopStatus: String,
    desktopTrackCount: Int,
    onState: (AccountUi) -> Unit,
    onDesktopGrantChanged: () -> Unit = {},
    onScanDesktop: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(repository.serverUrl) }
    var showServerEditor by remember { mutableStateOf(repository.serverUrl.isBlank()) }
    var tvCode by remember { mutableStateOf("") }
    var showDevices by remember { mutableStateOf(false) }
    var showTv by remember { mutableStateOf(false) }
    var desktopNowPlaying by remember(desktopOrigin) { mutableStateOf("") }
    var desktopVolume by remember(desktopOrigin) { mutableFloatStateOf(50f) }
    var desktopPlaying by remember(desktopOrigin) { mutableStateOf(false) }
    var email by remember { mutableStateOf(state.email.ifBlank { repository.savedEmail }) }
    var password by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }

    LaunchedEffect(repository.desktopGrantId(), desktopOrigin) {
        if (repository.desktopGrantId() != null && desktopOrigin.isNotBlank()) {
            runCatching { repository.readDesktopNowPlaying(desktopOrigin) }.onSuccess {
                desktopNowPlaying = listOf(it.optString("title"), it.optString("artist"))
                    .filter(String::isNotBlank).joinToString(" · ").ifBlank { "Nenhuma faixa informada" }
                desktopVolume = it.optDouble("volume", 50.0).toFloat()
                desktopPlaying = it.optBoolean("playing")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Conexões", color = AccountText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Uma conta Cobre ID para conectar este celular ao computador.", color = AccountMuted)

        if (state.message.isNotBlank()) {
            Surface(
                color = (if (state.error) AccountError else AccountGood).copy(alpha = .12f),
                shape = RoundedCornerShape(13.dp),
            ) {
                Text(
                    state.message,
                    color = if (state.error) AccountError else AccountGood,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(13.dp),
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = AccountCard), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (state.signedIn && !state.error) Icons.Default.CheckCircle else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (state.signedIn && !state.error) AccountGood else AccountMuted,
                    )
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(if (state.signedIn) "Conta conectada" else "Entrar no Cobre ID", color = AccountText, fontWeight = FontWeight.SemiBold)
                        Text(if (state.signedIn) state.email else "Entre uma vez para vincular seus aparelhos.", color = AccountMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (!state.signedIn) {
                    if (showServerEditor) {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Endereço do Cobre Server") },
                            placeholder = { Text("http://192.168.1.20:8080") },
                            singleLine = true,
                            colors = accountFieldColors(),
                        )
                        Text("Use o endereço do Server na mesma rede do computador.", color = AccountMuted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        TextButton(onClick = { showServerEditor = true }, contentPadding = PaddingValues(0.dp)) {
                            Text("Alterar Cobre Server", color = AccountMuted)
                        }
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("E-mail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = accountFieldColors(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = accountFieldColors(),
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                onState(state.copy(busy = true, message = "Conectando…", error = false))
                                runCatching {
                                    repository.saveServerUrl(serverUrl)
                                    repository.login(email, password, create)
                                    repository.sendPresence()
                                    repository.loadDevices()
                                }.onSuccess { devices ->
                                    password = ""
                                    showServerEditor = false
                                    onState(AccountUi(true, email.trim(), devices, message = "Conta conectada."))
                                }.onFailure { error ->
                                    onState(state.copy(busy = false, message = error.message ?: "Não foi possível entrar.", error = true))
                                }
                            }
                        },
                        enabled = !state.busy && email.isNotBlank() && password.length >= 8 && serverUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(if (create) "Criar conta e continuar" else "Entrar e continuar")
                    }
                    TextButton(onClick = { create = !create }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (create) "Já tenho uma conta" else "Criar uma conta Cobre ID", color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    TextButton(onClick = { showServerEditor = !showServerEditor }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (showServerEditor) "Ocultar endereço do Server" else "Alterar Cobre Server", color = AccountMuted)
                    }
                    if (showServerEditor) {
                        OutlinedTextField(serverUrl, { serverUrl = it }, Modifier.fillMaxWidth(), label = { Text("Endereço do Cobre Server") }, singleLine = true, colors = accountFieldColors())
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { repository.saveServerUrl(serverUrl); repository.restoreSession(); repository.loadDevices() }
                                    .onSuccess { onState(state.copy(devices = it, message = "Cobre Server atualizado.", error = false)); showServerEditor = false }
                                    .onFailure { onState(state.copy(message = it.message ?: "Não foi possível validar o Server.", error = true)) }
                            }
                        }) { Text("Salvar endereço", color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }

        if (state.signedIn) {
            Card(colors = CardDefaults.cardColors(containerColor = AccountCard), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Devices, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("Biblioteca do computador", color = AccountText, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    desktopStatus.startsWith("Computador conectado") -> "$desktopTrackCount faixas disponíveis"
                                    desktopStatus.startsWith("Conectando") -> "Conectando à rede local…"
                                    desktopStatus.isNotBlank() -> desktopStatus
                                    repository.desktopGrantId() != null -> "Autorização salva · aguardando conexão local"
                                    else -> "Conecte uma vez pelo QR do Desktop"
                                },
                                color = if (desktopStatus.startsWith("Computador conectado")) AccountGood else AccountMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Text("Leia o QR exibido no Desktop e aprove o pedido quando ele aparecer. O endereço e a permissão são salvos automaticamente.", color = AccountMuted, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = onScanDesktop,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Default.QrCodeScanner, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (repository.desktopGrantId() == null) "Conectar computador pelo QR" else "Refazer conexão pelo QR")
                    }

                    if (repository.desktopGrantId() != null) {
                        HorizontalDivider(color = Color(0xFF302C28))
                        Text("CONTROLES DO DESKTOP", color = AccountMuted, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        val canControl = desktopOrigin.isNotBlank()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(enabled = canControl, onClick = { scope.launch { runCatching { repository.sendDesktopCommand(desktopOrigin, "previous") }.onFailure { onState(state.copy(message = it.message ?: "Falha no controle remoto.", error = true)) } } }) {
                                Icon(Icons.Default.SkipPrevious, "Faixa anterior", tint = if (canControl) AccountText else AccountMuted)
                            }
                            IconButton(enabled = canControl, onClick = { scope.launch { runCatching { repository.sendDesktopCommand(desktopOrigin, "toggle") }.onSuccess { desktopPlaying = !desktopPlaying }.onFailure { onState(state.copy(message = it.message ?: "Falha no controle remoto.", error = true)) } } }) {
                                Icon(if (desktopPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproduzir ou pausar no Desktop", tint = if (canControl) MaterialTheme.colorScheme.primary else AccountMuted)
                            }
                            IconButton(enabled = canControl, onClick = { scope.launch { runCatching { repository.sendDesktopCommand(desktopOrigin, "next") }.onFailure { onState(state.copy(message = it.message ?: "Falha no controle remoto.", error = true)) } } }) {
                                Icon(Icons.Default.SkipNext, "Próxima faixa", tint = if (canControl) AccountText else AccountMuted)
                            }
                            IconButton(enabled = canControl, onClick = { scope.launch { runCatching { repository.readDesktopNowPlaying(desktopOrigin) }.onSuccess { desktopNowPlaying = listOf(it.optString("title"), it.optString("artist")).filter(String::isNotBlank).joinToString(" · ").ifBlank { "Nenhuma faixa informada" }; desktopVolume = it.optDouble("volume", 50.0).toFloat(); desktopPlaying = it.optBoolean("playing") }.onFailure { onState(state.copy(message = it.message ?: "Não foi possível consultar o Desktop.", error = true)) } } }) {
                                Icon(Icons.Default.Refresh, "Atualizar estado", tint = if (canControl) AccountMuted else AccountMuted)
                            }
                        }
                        if (desktopNowPlaying.isNotBlank()) Text("No computador: $desktopNowPlaying", color = AccountMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, null, tint = AccountMuted)
                            Slider(
                                enabled = canControl,
                                value = desktopVolume,
                                onValueChange = { desktopVolume = it },
                                onValueChangeFinished = { scope.launch { runCatching { repository.sendDesktopCommand(desktopOrigin, "volume", desktopVolume.toDouble()) }.onFailure { onState(state.copy(message = it.message ?: "Falha ao ajustar o volume.", error = true)) } } },
                                valueRange = 0f..100f,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${desktopVolume.toInt()}%", color = AccountMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    onState(state.copy(busy = true, message = "Removendo autorização…", error = false))
                                    runCatching { repository.revokeDesktopGrant() }
                                        .onSuccess { onDesktopGrantChanged(); onState(state.copy(busy = false, message = "Acesso do celular revogado.", error = false)) }
                                        .onFailure { onState(state.copy(busy = false, message = it.message ?: "Não foi possível revogar.", error = true)) }
                                }
                            },
                            enabled = !state.busy,
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("Remover este computador", color = AccountError) }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = AccountCard), shape = RoundedCornerShape(18.dp)) {
                Column {
                    TextButton(onClick = { showDevices = !showDevices }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        Icon(Icons.Default.Devices, null, tint = MaterialTheme.colorScheme.primary)
                        Text("  Aparelhos da conta (${state.devices.size})", color = AccountText, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ExpandMore, null, tint = AccountMuted)
                    }
                    if (showDevices) Column(Modifier.padding(start = 17.dp, end = 17.dp, bottom = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.devices.forEach { device ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(if (device.online) AccountGood else AccountMuted, CircleShape))
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(device.name + if (device.current) " · Este celular" else "", color = AccountText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (device.online) "Online" else "Offline", color = AccountMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = AccountCard), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 4.dp)) {
                    TextButton(onClick = { showTv = !showTv }, modifier = Modifier.fillMaxWidth()) {
                        Text("Vincular Cobre TV", color = AccountText, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ExpandMore, null, tint = AccountMuted)
                    }
                    if (showTv) {
                        Text("Digite o código exibido na TV. Ele expira em poucos minutos.", color = AccountMuted, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = tvCode,
                            onValueChange = { value -> tvCode = value.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(9) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            label = { Text("Código da TV") },
                            placeholder = { Text("ABCD-EFGH") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            colors = accountFieldColors(),
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    onState(state.copy(busy = true, message = "Autorizando TV…", error = false))
                                    runCatching { val device = repository.authorizeTv(tvCode); val devices = repository.loadDevices(); device to devices }
                                        .onSuccess { (device, devices) -> tvCode = ""; onState(state.copy(busy = false, devices = devices, message = "${device.name} autorizada na sua conta.", error = false)) }
                                        .onFailure { onState(state.copy(busy = false, message = it.message ?: "Não foi possível autorizar a TV.", error = true)) }
                                }
                            },
                            enabled = !state.busy && tvCode.matches(Regex("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}")),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                        ) { Text("Autorizar TV") }
                    }
                }
            }

            TextButton(onClick = {
                scope.launch {
                    runCatching { repository.logout() }
                    onDesktopGrantChanged()
                    onState(AccountUi(message = "Sessão encerrada."))
                }
            }, enabled = !state.busy, modifier = Modifier.align(Alignment.End)) { Text("Sair da conta", color = AccountError) }
        }
        Text("O Cobre Server confirma a permissão. Biblioteca e áudio seguem diretamente do computador para o celular na rede local.", color = AccountMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun accountFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color(0xFF302C28),
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = AccountMuted,
    focusedTextColor = AccountText,
    unfocusedTextColor = AccountText,
    cursorColor = MaterialTheme.colorScheme.primary,
)
