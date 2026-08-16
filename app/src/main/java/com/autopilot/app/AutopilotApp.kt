package com.autopilot.app

import android.view.ViewGroup
import android.widget.FrameLayout
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private enum class AppScreen { HOME, SETTINGS, ADMIN }

@Composable
fun AutopilotApp(storage: SecureStorage) {
    var user by remember { mutableStateOf(storage.getUser()) }
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var showSplash by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        user = storage.getUser()
        delay(1200)
        showSplash = false
    }

    MaterialTheme(colorScheme = autopilotColors()) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF08111F)) {
            AnimatedContent(
                targetState = if (showSplash) "splash" else screen.name,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen-transition",
            ) { destination ->
                if (destination == "splash") {
                    SplashScreen()
                } else {
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            user = user,
                            onSettings = { screen = AppScreen.SETTINGS },
                        )
                        AppScreen.SETTINGS -> SettingsScreen(
                            user = user,
                            onBack = { screen = AppScreen.HOME },
                            onAdmin = { screen = AppScreen.ADMIN },
                        )
                        AppScreen.ADMIN -> AdminScreen(
                            user = user,
                            onBack = { screen = AppScreen.SETTINGS },
                            onGrantDays = {
                                storage.approveForDays(it)
                                user = storage.getUser()
                            },
                            onGrantLifetime = {
                                storage.approveLifetime()
                                user = storage.getUser()
                            },
                            onExtend = {
                                storage.extendByDays()
                                user = storage.getUser()
                            },
                            onReject = {
                                storage.reject()
                                user = storage.getUser()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PulseAnimation(modifier = Modifier.size(150.dp))
        Spacer(Modifier.height(20.dp))
        Text("AUTOPILOT", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Text("Automation, in control", color = Color(0xFF9CB4C8))
    }
}

@Composable
private fun HomeScreen(user: User, onSettings: () -> Unit) {
    var starting by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(starting) {
        if (starting) {
            delay(1400)
            starting = false
        }
    }
    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("AUTOPILOT", color = Color(0xFF55D6BE), style = MaterialTheme.typography.labelLarge)
                    Text("Control center", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101D2D)) {
                NavigationBarItem(selected = true, onClick = {}, icon = {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = "Home")
                }, label = { Text("Run") })
                NavigationBarItem(selected = false, onClick = onSettings, icon = {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }, label = { Text("Settings") })
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(user)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF132338)),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ready when you are", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Start screen capture to let AUTOPILOT watch for configured targets.",
                        color = Color(0xFF9CB4C8),
                    )
                    Button(
                        onClick = { starting = true },
                        enabled = !starting && user.status == UserStatus.APPROVED,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (starting) {
                            PulseAnimation(modifier = Modifier.size(24.dp))
                        } else {
                            Text(if (user.status == UserStatus.APPROVED) "Start AUTOPILOT" else "Approval required")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(user: User) {
    val statusColor = when (user.status) {
        UserStatus.APPROVED -> Color(0xFF55D6BE)
        UserStatus.PENDING_APPROVAL -> Color(0xFFFFCE6A)
        UserStatus.EXPIRED, UserStatus.REJECTED -> Color(0xFFFF7D8A)
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101D2D)), shape = RoundedCornerShape(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(68.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                PulseAnimation(modifier = Modifier.size(68.dp))
                Box(Modifier.size(12.dp).clip(CircleShape).background(statusColor))
            }
            Column {
                Text("Access status", color = Color(0xFF9CB4C8))
                Text(user.status.name.replace('_', ' '), color = statusColor, style = MaterialTheme.typography.titleMedium)
                if (user.expiryTimestamp > 0L && user.status == UserStatus.APPROVED) {
                    Text(
                        "Expires ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(user.expiryTimestamp))}",
                        color = Color(0xFF9CB4C8),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(user: User, onBack: () -> Unit, onAdmin: () -> Unit) {
    var taps by rememberSaveable { mutableStateOf(0) }
    var lastTap by rememberSaveable { mutableLongStateOf(0L) }
    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        containerColor = Color.Transparent,
        topBar = {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back") }
                Text("Settings", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Account", color = Color(0xFF55D6BE), style = MaterialTheme.typography.labelLarge)
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF132338))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(user.email, color = Color(0xFF9CB4C8))
                    Text("Status: ${user.status}", color = Color(0xFF9CB4C8))
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "AUTOPILOT v2.0.0",
                color = Color(0xFF4F6B83),
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                    val now = System.currentTimeMillis()
                    val nextTaps = if (now - lastTap <= 1500L) taps + 1 else 1
                    taps = nextTaps
                    lastTap = now
                    if (nextTaps >= 10) {
                        taps = 0
                        onAdmin()
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Log out", tint = Color(0xFF9CB4C8))
                Text(" Local session", color = Color(0xFF9CB4C8))
            }
        }
    }
}

@Composable
private fun PulseAnimation(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            LottieAnimationView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setAnimation(R.raw.autopilot_pulse)
                repeatCount = LottieDrawable.INFINITE
                playAnimation()
            }
        },
    )
}

private fun autopilotColors() = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF55D6BE),
    secondary = Color(0xFFFFCE6A),
    background = Color(0xFF08111F),
    surface = Color(0xFF101D2D),
    onPrimary = Color(0xFF07151A),
    onBackground = Color.White,
    onSurface = Color.White,
)