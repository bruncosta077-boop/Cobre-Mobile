package app.cobre.mobile

import java.net.URI

data class DesktopPairingQr(val hostId: String, val origin: String, val expiresAt: Long)

/** QR is discovery data only: it never carries a password, shared key, or access token. */
fun parseDesktopPairingQr(raw: String, now: Long = System.currentTimeMillis()): DesktopPairingQr {
    val uri = runCatching { URI(raw.trim()) }.getOrElse { throw CobreIdException("QR de pareamento inválido.") }
    if (uri.scheme != "cobre" || uri.host != "pair" || uri.path.orEmpty().isNotEmpty()) {
        throw CobreIdException("Este QR não é um pareamento Cobre.")
    }
    val values = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
        val split = part.split('=', limit = 2)
        if (split.size == 2) split[0] to runCatching { java.net.URLDecoder.decode(split[1], "UTF-8") }.getOrNull() else null
    }.toMap()
    if (values["v"] != "1") throw CobreIdException("A versão deste QR não é compatível.")
    val hostId = values["host"].orEmpty()
    if (!hostId.matches(Regex("[0-9a-fA-F-]{36}"))) throw CobreIdException("Identidade do Desktop inválida no QR.")
    val originRaw = values["origin"].orEmpty()
    val originUri = runCatching { URI(originRaw) }.getOrElse { throw CobreIdException("Endereço inválido no QR.") }
    val host = originUri.host?.lowercase().orEmpty()
    val privateHost = host == "localhost" || host == "::1" || host.split('.').let { p ->
        if (p.size != 4) false else {
            val n = p.map { it.toIntOrNull() ?: return@let false }
            n.all { it in 0..255 } && (n[0] == 10 || n[0] == 127 || (n[0] == 192 && n[1] == 168) || (n[0] == 172 && n[1] in 16..31) || (n[0] == 169 && n[1] == 254))
        }
    }
    if (originUri.scheme != "http" || originUri.userInfo != null || originUri.query != null || originUri.fragment != null ||
        originUri.path.orEmpty().isNotEmpty() || originUri.port != 8787 || !privateHost) {
        throw CobreIdException("O QR precisa apontar para o Desktop na rede local.")
    }
    val expires = values["expires"]?.toLongOrNull() ?: throw CobreIdException("Validade ausente no QR.")
    if (expires <= now || expires > now + 6 * 60_000) throw CobreIdException("Este QR expirou. Gere outro no Cobre Desktop.")
    return DesktopPairingQr(hostId, "http://${if (host.contains(':')) "[$host]" else host}:${originUri.port}", expires)
}
