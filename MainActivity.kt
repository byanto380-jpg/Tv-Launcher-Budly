package com.custom.tvlauncher

import android.Manifest
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TVLauncherScreen()
        }
    }
}

@Composable
fun TVLauncherScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationSettings = remember { LocationSettings(context) }
    val leagueSettings = remember { LeagueSettings(context) }
    val m3uManager = remember { M3uPlaylistManager(context) }

    val activeCity by locationSettings.cityFlow.collectAsState(initial = CityPresets.list.first())
    val activeLeague by leagueSettings.leagueFlow.collectAsState(initial = LeaguePresets.list.first())
    val playlistSource by m3uManager.playlistUrlFlow.collectAsState(initial = "")

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var showCityDialog by remember { mutableStateOf(false) }
    var showLeagueDialog by remember { mutableStateOf(false) }
    var showM3uDialog by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }

    var prayerTimes by remember { mutableStateOf<Timings?>(null) }
    var hijriDate by remember { mutableStateOf("Memuat Hijriah...") }
    var weatherInfo by remember { mutableStateOf("--°C") }
    var matches by remember { mutableStateOf<List<MatchEvent>>(emptyList()) }
    var isLoadingMatches by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<ResolveInfo>>(emptyList()) }
    var iptvChannels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }

    val filteredApps by remember(installedApps, appSearchQuery, context) {
        derivedStateOf {
            val query = appSearchQuery.trim()
            if (query.isEmpty()) {
                installedApps
            } else {
                installedApps.filter { appInfo ->
                    appInfo.loadLabel(context.packageManager)
                        .toString()
                        .contains(query, ignoreCase = true)
                }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        PrayerNotifier.schedulePeriodicCheck(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            currentDate = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(now)
            prayerTimes?.let { timings ->
                PrayerNotifier.checkAndNotify(context, timings, activeCity.city)
            }
            delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        installedApps = pm.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

    LaunchedEffect(activeLeague) {
        isLoadingMatches = true
        matches = emptyList()
        try {
            matches = NetworkClient.sports.getNextMatches(leagueId = activeLeague.id).events?.take(4) ?: emptyList()
        } catch (_: Exception) { } finally {
            isLoadingMatches = false
        }
    }

    LaunchedEffect(activeCity) {
        hijriDate = "Memuat Hijriah..."
        weatherInfo = "--°C"

        try {
            val res = NetworkClient.aladhan.getPrayerTimes(
                city = activeCity.city,
                country = activeCity.country
            )
            prayerTimes = res.data.timings
            val h = res.data.date.hijri
            hijriDate = "${h.date.split("-")[0]} ${h.month.en} ${h.year} H"
        } catch (_: Exception) {
            hijriDate = "Gagal memuat jadwal"
        }

        try {
            val w = NetworkClient.weather.getWeather(
                lat = activeCity.lat,
                lon = activeCity.lon
            ).current_weather
            val status = if (w.weathercode <= 3) "Cerah Berawan" else "Hujan / Mendung"
            weatherInfo = "${w.temperature}°C • $status"
        } catch (_: Exception) {
            weatherInfo = "Cuaca N/A"
        }
    }

    LaunchedEffect(playlistSource) {
        iptvChannels = m3uManager.loadChannels(playlistSource)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A).copy(alpha = 0.85f), Color(0xFF1E293B).copy(alpha = 0.9f))
                )
            )
            .padding(24.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // --- BARIS ATAS ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = currentTime, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = "$currentDate  |  $hijriDate  •  ${activeCity.city}",
                            fontSize = 15.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        InfoChip(title = "CUACA", value = weatherInfo)

                        FocusableButton(text = "Kota") {
                            showCityDialog = true
                        }

                        FocusableButton(text = "Liga Bola") {
                            showLeagueDialog = true
                        }

                        FocusableButton(text = "Playlist M3U") {
                            showM3uDialog = true
                        }

                        FocusableButton(text = "Live Wallpaper") {
                            try {
                                context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                            }
                        }

                        FocusableButton(text = "Screensaver") {
                            try {
                                context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        }

                        FocusableButton(text = "Pengaturan") {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                }
            }

            // --- BARIS TENGAH: JADWAL SHOLAT & PERTANDINGAN SEPAK BOLA ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "JADWAL WAKTU SHOLAT • ${activeCity.city.uppercase()}",
                                color = Color(0xFF4ADE80),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                PrayerItem("Subuh", prayerTimes?.Fajr?.let(PrayerNotifier::cleanTime) ?: "--:--")
                                PrayerItem("Dzuhur", prayerTimes?.Dhuhr?.let(PrayerNotifier::cleanTime) ?: "--:--")
                                PrayerItem("Ashar", prayerTimes?.Asr?.let(PrayerNotifier::cleanTime) ?: "--:--")
                                PrayerItem("Maghrib", prayerTimes?.Maghrib?.let(PrayerNotifier::cleanTime) ?: "--:--")
                                PrayerItem("Isya", prayerTimes?.Isha?.let(PrayerNotifier::cleanTime) ?: "--:--")
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "JADWAL BOLA • ${activeLeague.name.uppercase()}",
                                color = Color(0xFFFACC15),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            when {
                                isLoadingMatches -> {
                                    Text("Memuat jadwal ${activeLeague.name}...", color = Color.LightGray, fontSize = 13.sp)
                                }
                                matches.isEmpty() -> {
                                    Text("Belum ada jadwal pertandingan tersedia.", color = Color.LightGray, fontSize = 13.sp)
                                }
                                else -> {
                                    matches.take(3).forEach { match ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = match.strEvent,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                                            )
                                            Text("${match.dateEvent} (${match.strTime.take(5)})", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- WIDGET PLAYLIST IPTV M3U ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (iptvChannels.isEmpty()) {
                                "SALURAN IPTV (M3U) • BELUM DIATUR"
                            } else {
                                "SALURAN IPTV (M3U) • ${iptvChannels.size} KANAL"
                            },
                            color = Color(0xFFF472B6),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Klik 'Playlist M3U' untuk menambah URL atau path file .m3u",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    if (iptvChannels.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(iptvChannels) { channel ->
                                ChannelCard(channel = channel) {
                                    val opened = launchIptvStream(context, channel)
                                    if (!opened) {
                                        Toast.makeText(
                                            context,
                                            "Tidak ditemukan aplikasi pemutar video untuk stream ini",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- DAFTAR APLIKASI DENGAN PENCARIAN CEPAT & IKON ASLI ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "APLIKASI TERINSTAL (${filteredApps.size}/${installedApps.size})",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = appSearchQuery,
                                onValueChange = { appSearchQuery = it },
                                placeholder = { Text("Cari aplikasi...", fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier.width(260.dp)
                            )
                            if (appSearchQuery.isNotEmpty()) {
                                FocusableButton(text = "Hapus") {
                                    appSearchQuery = ""
                                }
                            }
                        }
                    }

                    if (filteredApps.isEmpty()) {
                        Text(
                            text = "Tidak ada aplikasi yang cocok dengan "$appSearchQuery".",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(filteredApps, key = { it.activityInfo.packageName }) { appInfo ->
                                val appName = appInfo.loadLabel(context.packageManager).toString()
                                val pkgName = appInfo.activityInfo.packageName
                                val iconDrawable = remember(pkgName) {
                                    try {
                                        appInfo.loadIcon(context.packageManager)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                AppCard(name = appName, icon = iconDrawable) {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(pkgName)
                                    if (launchIntent != null) context.startActivity(launchIntent)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCityDialog) {
            CityPickerDialog(
                current = activeCity,
                onDismiss = { showCityDialog = false },
                onSelect = { selected ->
                    scope.launch {
                        locationSettings.saveCity(selected)
                    }
                    showCityDialog = false
                }
            )
        }

        if (showLeagueDialog) {
            LeaguePickerDialog(
                current = activeLeague,
                onDismiss = { showLeagueDialog = false },
                onSelect = { selectedLeague ->
                    scope.launch {
                        leagueSettings.saveLeague(selectedLeague)
                    }
                    showLeagueDialog = false
                }
            )
        }

        if (showM3uDialog) {
            M3uPlaylistDialog(
                currentSource = playlistSource,
                onDismiss = { showM3uDialog = false },
                onSave = { newSource ->
                    scope.launch {
                        m3uManager.savePlaylistSource(newSource)
                    }
                    showM3uDialog = false
                }
            )
        }
    }
}

@Composable
fun LeaguePickerDialog(
    current: LeagueConfig,
    onDismiss: () -> Unit,
    onSelect: (LeagueConfig) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        title = { Text("Pilih Liga Sepak Bola", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pilih kompetisi untuk jadwal pertandingan:", fontSize = 13.sp, color = Color(0xFFFACC15))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LeaguePresets.list) { league ->
                        FilterChip(
                            selected = league.id == current.id,
                            onClick = { onSelect(league) },
                            label = { Text(league.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = Color(0xFF38BDF8))
            }
        }
    )
}

@Composable
fun M3uPlaylistDialog(
    currentSource: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var inputSource by remember(currentSource) { mutableStateOf(currentSource) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        title = { Text("Pengaturan Playlist IPTV (.M3U)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Masukkan URL https://.../playlist.m3u atau path file lokal seperti /sdcard/Download/tv.m3u:",
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = inputSource,
                    onValueChange = { inputSource = it },
                    label = { Text("URL atau Path File .m3u") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(inputSource) }) {
                Text("Simpan & Muat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = Color.LightGray)
            }
        }
    )
}

@Composable
fun ChannelCard(channel: IptvChannel, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(width = 176.dp, height = 74.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .background(if (isFocused) Color(0xFF9D174D) else Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .border(2.dp, if (isFocused) Color.White else Color(0xFF334155), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = channel.group.uppercase(),
            color = if (isFocused) Color(0xFFFBCFE8) else Color(0xFFF472B6),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = channel.name,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CityPickerDialog(
    current: CityConfig,
    onDismiss: () -> Unit,
    onSelect: (CityConfig) -> Unit
) {
    var customCity by remember(current.city) { mutableStateOf(current.city) }
    var customCountry by remember(current.country) { mutableStateOf(current.country) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        title = { Text("Pengaturan Lokasi Kota", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pilih kota cepat:", fontSize = 13.sp, color = Color(0xFF38BDF8))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CityPresets.list) { preset ->
                        FilterChip(
                            selected = preset.city.equals(current.city, ignoreCase = true),
                            onClick = { onSelect(preset) },
                            label = { Text(preset.city) }
                        )
                    }
                }

                OutlinedTextField(
                    value = customCity,
                    onValueChange = { customCity = it },
                    label = { Text("Nama Kota Manual") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customCountry,
                    onValueChange = { customCountry = it },
                    label = { Text("Negara") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (customCity.isNotBlank()) {
                        onSelect(
                            CityConfig(
                                city = customCity.trim(),
                                country = customCountry.trim().ifBlank { "Indonesia" },
                                lat = current.lat,
                                lon = current.lon
                            )
                        )
                    }
                }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = Color.LightGray)
            }
        }
    )
}

@Composable
fun PrayerItem(name: String, time: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, color = Color.LightGray, fontSize = 13.sp)
        Text(time, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
fun InfoChip(title: String, value: String) {
    Column(
        modifier = Modifier
            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(title, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FocusableButton(text: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .background(if (isFocused) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(12.dp))
            .border(2.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text, color = if (isFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun AppCard(name: String, icon: Any?, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(width = 148.dp, height = 102.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .background(if (isFocused) Color(0xFF2563EB) else Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .border(2.dp, if (isFocused) Color.White else Color(0xFF334155), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .focusable()
            .padding(10.dp)
    ) {
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF334155), RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
