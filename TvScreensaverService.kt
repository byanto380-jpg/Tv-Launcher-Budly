package com.custom.tvlauncher

import android.service.dreams.DreamService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class TvScreensaverService : DreamService() {

    private val quotes = listOf(
        "Waktu adalah modal utama; gunakan dengan penuh makna.",
        "Tenang di rumah, hangat bersama keluarga.",
        "Langkah kecil yang konsisten membawa perubahan besar."
    )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        val composeView = ComposeView(this).apply {
            setContent {
                ScreensaverContent(quotes = quotes)
            }
        }
        setContentView(composeView)
    }
}

@Composable
private fun ScreensaverContent(quotes: List<String>) {
    var timeText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var quoteIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
            dateText = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(now)
            delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            quoteIndex = (quoteIndex + 1) % quotes.size
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF060B16), Color(0xFF111C33))
                )
            )
            .padding(48.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = timeText,
                fontSize = 84.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = dateText,
                fontSize = 22.sp,
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "“${quotes[quoteIndex]}”",
                fontSize = 18.sp,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 720.dp)
            )
        }
    }
}
