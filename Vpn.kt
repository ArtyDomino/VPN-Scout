package ru.vpnscout

import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.*
import kotlin.math.*

// Север и северо-восток Европы: код страны -> примерное расстояние от Санкт-Петербурга, км
val REGIONS = mapOf("FI" to 300, "EE" to 350, "LV" to 600, "SE" to 700, "LT" to 800,
    "NO" to 1100, "DK" to 1300, "PL" to 1400)

data class Server(
    val host: String, val ip: String, val country: String, val code: String,
    val speedMbps: Double, val config: String, val port: Int, val tcp: Boolean,
    val latency: Int = -1, val loss: Double = 1.0, val jitter: Double = 0.0, val score: Int = 0
) { val alive get() = latency >= 0 && loss < 1.0 }

suspend fun loadServers(): List<Server> = withContext(Dispatchers.IO) {
    val text = listOf("https://www.vpngate.net/api/iphone/", "http://www.vpngate.net/api/iphone/")
        .firstNotNullOfOrNull { runCatching { URL(it).readText() }.getOrNull() } ?: error("VPN Gate недоступен")
    text.lineSequence().filter { !it.startsWith("*") && !it.startsWith("#") }.mapNotNull { l ->
        val p = l.split(","); if (p.size < 15 || p[6] !in REGIONS) return@mapNotNull null
        val cfg = runCatching { String(Base64.decode(p[14], Base64.DEFAULT)) }.getOrNull() ?: return@mapNotNull null
        Server(p[0], p[1], p[5], p[6], (p[4].toLongOrNull() ?: 0) / 1e6, cfg,
            Regex("(?m)^remote\\s+\\S+\\s+(\\d+)").find(cfg)?.groupValues?.get(1)?.toInt() ?: 443,
            Regex("(?m)^proto\\s+(\\w+)").find(cfg)?.groupValues?.get(1)?.startsWith("tcp") == true)
    }.toList()
}

private fun probe(s: Server): Int? = runCatching {
    val t = System.nanoTime()
    if (s.tcp) Socket().use { it.connect(InetSocketAddress(s.ip, s.port), 1500) }
    else if (!InetAddress.getByName(s.ip).isReachable(1500)) return null
    ((System.nanoTime() - t) / 1_000_000).toInt()
}.getOrNull()

suspend fun measure(list: List<Server>): List<Server> = coroutineScope {
    val gate = Semaphore(20)
    list.map { s -> async(Dispatchers.IO) { gate.withPermit {
        val ok = (1..5).map { probe(s) }.filterNotNull()
        if (ok.isEmpty()) return@withPermit s
        val avg = ok.average(); val loss = 1 - ok.size / 5.0
        val jit = sqrt(ok.map { (it - avg).pow(2) }.average())
        val km = REGIONS[s.code] ?: 1500
        val sc = 100 - min(avg, 400.0) / 5 - loss * 60 - jit / 2 - km / 100.0 + min(s.speedMbps, 50.0) / 5
        s.copy(latency = avg.roundToInt(), loss = loss, jitter = jit, score = sc.roundToInt().coerceIn(0, 100))
    } } }.awaitAll().sortedWith(compareByDescending<Server> { it.alive }.thenByDescending { it.score })
}
