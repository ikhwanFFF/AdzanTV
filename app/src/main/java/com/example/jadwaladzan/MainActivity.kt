package com.example.jadwaladzan

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val BULAN = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Scheduler.ensureChannel(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var place by remember { mutableStateOf(Store.place(ctx)) }
    var today by remember { mutableStateOf<Day?>(null) }
    var month by remember { mutableStateOf<List<Day>>(emptyList()) }
    var tz by remember { mutableStateOf(7.0) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var alarmOn by remember { mutableStateOf(Store.enabled(ctx)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    LaunchedEffect(place) {
        loading = true; error = null
        try {
            Store.savePlace(ctx, place)
            val d = withContext(Dispatchers.IO) { fetchSchedule(place, LocalDate.now(), "daily") }
            tz = d.tz; today = d.days.firstOrNull()
            val m = withContext(Dispatchers.IO) { fetchSchedule(place, LocalDate.now(), "monthly") }
            month = m.days
            withContext(Dispatchers.IO) { Scheduler.refresh(ctx) }
        } catch (e: Exception) {
            error = e.message ?: e.toString()
        }
        loading = false
    }

    // Adzan berikutnya
    val zone = ZoneOffset.ofTotalSeconds((tz * 3600).toInt())
    val nowT = Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
    var nextKey: String? = null
    var secs = 0L
    val td = today
    if (td != null) {
        for (k in ADZAN) {
            val s = td.times[k] ?: ""
            if (s.isEmpty()) continue
            val t = LocalTime.parse(s)
            if (t.isAfter(nowT)) { nextKey = k; secs = Duration.between(nowT, t).seconds; break }
        }
    }
    val todayStr = LocalDate.now().toString()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Jadwal Adzan", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Hisab KHGT Muhammadiyah", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Cari kota…") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    if (query.isNotBlank()) scope.launch {
                        try {
                            error = null
                            results = withContext(Dispatchers.IO) { searchPlaces(query.trim()) }
                            if (results.isEmpty()) error = "Lokasi tidak ditemukan"
                        } catch (e: Exception) { error = e.message }
                    }
                }) { Text("Cari") }
            }
            results.take(6).forEach { p ->
                TextButton(onClick = { place = p; results = emptyList(); query = "" }) {
                    Text(p.name, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(place.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "UTC${if (tz >= 0) "+" else ""}${tz.toInt()}" + if (loading) " · memuat…" else "",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (nextKey != null && td != null) {
                        Text("Adzan berikutnya: ${LABELS[nextKey]}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(td.times[nextKey] ?: "", fontSize = 44.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Text("%02d:%02d:%02d lagi".format(secs / 3600, secs % 3600 / 60, secs % 60))
                    } else if (td != null) {
                        Text("Semua adzan hari ini sudah lewat")
                    }
                    Spacer(Modifier.height(12.dp))
                    td?.let { d ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ORDER.forEach { k ->
                                val active = k == nextKey
                                Column(
                                    Modifier.weight(1f)
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            MaterialTheme.shapes.small
                                        ).padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(LABELS[k]!!, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(d.times[k]?.ifEmpty { "--" } ?: "--", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notifikasi adzan", fontWeight = FontWeight.SemiBold)
                        Text("Pengingat saat masuk waktu sholat", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = alarmOn, onCheckedChange = { on ->
                        alarmOn = on
                        Store.setEnabled(ctx, on)
                        if (on) {
                            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                            if (Build.VERSION.SDK_INT >= 31 &&
                                !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
                            ) {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(Uri.parse("package:" + ctx.packageName))
                                )
                            }
                        }
                        scope.launch(Dispatchers.IO) { Scheduler.refresh(ctx) }
                    })
                }
            }
        }
        item {
            Text("Jadwal bulan ini", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            TimeRow(listOf("Tgl") + ORDER.map { LABELS[it]!!.take(4) }, bold = true)
        }
        items(month) { d ->
            val label = try {
                val ld = LocalDate.parse(d.date); "${ld.dayOfMonth} ${BULAN[ld.monthValue - 1]}"
            } catch (e: Exception) { d.date }
            TimeRow(
                listOf(label) + ORDER.map { d.times[it]?.ifEmpty { "-" } ?: "-" },
                bg = if (d.date == todayStr) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
        }
    }
}

@Composable
fun TimeRow(cells: List<String>, bold: Boolean = false, bg: Color = Color.Transparent) {
    Row(Modifier.fillMaxWidth().background(bg).padding(vertical = 6.dp, horizontal = 4.dp)) {
        cells.forEachIndexed { i, t ->
            Text(
                t, Modifier.weight(if (i == 0) 1.3f else 1f), fontSize = 11.sp, maxLines = 1,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.Center
            )
        }
    }
}
