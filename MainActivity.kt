package ru.vpnscout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File

val Font1 = FontFamily(Font(R.font.montserrat, FontWeight.Normal), Font(R.font.montserrat, FontWeight.SemiBold))
fun color(s: Server) = when { !s.alive -> Color(0xFF9AA0A6); s.score >= 70 -> Color(0xFF2E9E5B); s.score >= 45 -> Color(0xFFE0A526); else -> Color(0xFFD14B4B) }

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize(), color = Color(0xFFF6F7F9)) {
            ProvideTextStyle(TextStyle(fontFamily = Font1, letterSpacing = 0.3.sp)) { Screen() } } } }
    }

    // Передаём конфиг приложению «OpenVPN for Android» / OpenVPN Connect (импорт в одно касание)
    fun connect(s: Server) {
        val f = File(cacheDir, "${s.host}.ovpn").apply { writeText(s.config) }
        val uri = FileProvider.getUriForFile(this, "ru.vpnscout.files", f)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/x-openvpn-profile")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Открыть в OpenVPN"))
    }

    @Composable fun Screen() {
        var servers by remember { mutableStateOf<List<Server>>(emptyList()) }
        var status by remember { mutableStateOf("Ищу серверы…") }
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(tick) { while (true) {
            status = "Проверяю серверы…"
            status = runCatching { servers = measure(loadServers()); "" }.getOrElse { "Нет связи с VPN Gate" }
            delay(5 * 60_000)
        } }
        val best = servers.firstOrNull { it.alive }
        Column(Modifier.padding(20.dp).statusBarsPadding()) {
            Text("Лучший сервер", fontSize = 14.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp)) {
                if (best == null) Text(status.ifEmpty { "Подходящих серверов нет" }, fontSize = 16.sp)
                else {
                    Text(best.country, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text("${best.latency} мс · потери ${(best.loss * 100).toInt()}% · оценка ${best.score}", color = color(best))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { connect(best) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Подключиться") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(status.ifEmpty { "Серверов: ${servers.count { it.alive }}" }, fontSize = 14.sp, color = Color.Gray)
                TextButton(onClick = { tick++ }) { Text("Обновить") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(servers.filter { it.alive }) { s ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(color(s)))
                    Text(s.country, Modifier.padding(start = 12.dp).weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("${s.latency} мс", color = Color.Gray, fontSize = 13.sp)
                    Text("  ${s.score}", color = color(s), fontWeight = FontWeight.SemiBold)
                }
            } }
        }
    }
}
