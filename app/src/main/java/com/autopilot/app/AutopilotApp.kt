package com.autopilot.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import android.view.ViewGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class AppScreen { HOME, SETTINGS, ADMIN }

@Composable
fun AutopilotApp(storage: SecureStorage) {
    val context = LocalContext.current
    val networkTime = remember { NetworkTimeProvider(context) }
    var user by remember { mutableStateOf(storage.getUser(networkTime.currentTimeMillis())) }
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var showSplash by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        var lastSyncElapsed = 0L
        while (isActive) {
            val elapsedNow = SystemClock.elapsedRealtime()
            if (lastSyncElapsed == 0L || elapsedNow - lastSyncElapsed >= NETWORK_SYNC_INTERVAL) {
                networkTime.sync()
                lastSyncElapsed = elapsedNow
            }
            user = storage.getUser(networkTime.currentTimeMillis())
            delay(1_000L)
        }
    }
    LaunchedEffect(Unit) {
        delay(900L)
        showSplash = false
    }

    fun refreshUser() {
        user = storage.getUser(networkTime.currentTimeMillis())
    }

    MaterialTheme(colorScheme = autopilotColors()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0B0F19), Color(0xFF101C2C), Color(0xFF0B0F19)),
                        ),
                    ),
            ) {
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
                                storage = storage,
                                networkTime = networkTime,
                                onUserChanged = ::refreshUser,
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
                                    storage.approveForDays(it, networkTime.currentTimeMillis())
                                    refreshUser()
                                },
                                onGrantLifetime = {
                                    storage.approveLifetime()
                                    refreshUser()
                                },
                                onExtend = {
                                    storage.extendByDays(networkTime.currentTimeMillis())
                                    refreshUser()
                                },
                                onReject = {
                                    storage.reject()
                                    refreshUser()
                                },
                                onToggleAdFree = {
                                    storage.setAdFreeOverride(it)
                                    refreshUser()
                                },
                                onSetAdFreeForUid = { uid, enabled ->
                                    if (storage.setAdFreeForUid(uid, enabled)) {
                                        refreshUser()
                                        true
                                    } else {
                                        false
                                    }
                                },
                            )
                        }
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
        Text("AUTOPILOT", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Black)
        Text("Automation, in control", color = Color(0xFF9CB4C8))
    }
}

@Composable
private fun HomeScreen(
    user: User,
    storage: SecureStorage,
    networkTime: NetworkTimeProvider,
    onUserChanged: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val captureStats by CaptureTelemetry.stats.collectAsState()
    var rewardReturned by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && storage.hasPendingRewardSession()) {
                rewardReturned = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startRewardSession() {
        val now = networkTime.currentTimeMillis()
        if (storage.beginRewardSession(now)) {
            rewardReturned = false
            AdService.openUserInitiatedAd(context)
            onUserChanged()
        } else {
            message = "Sync network time and return from the current reward session before starting another."
        }
    }

    fun completeRewardSession() {
        val result = storage.completeRewardSession(networkTime.currentTimeMillis())
        message = when {
            !result.completed -> "Please keep the ad open for at least 25 seconds, then return and confirm."
            result.rewarded -> "Reward complete. One day of access has been added."
            else -> "Reward counted. Ads completed: ${result.count} / 10."
        }
        rewardReturned = false
        onUserChanged()
    }

    fun runUserAction(action: () -> Unit) {
        if (!user.hasActiveAccess) return
        if (user.shouldShowAds) AdService.openUserInitiatedAd(context)
        action()
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_DATA, data)
                },
            )
        }
    }

    fun startCapture() {
        val activity = context as? Activity
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (activity == null || manager == null) {
            message = "Screen capture permission is unavailable on this device."
        } else {
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        }
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        containerColor = Color.Transparent,
        topBar = {
            HeroHeader(
                user = user,
                now = networkTime.currentTimeMillis(),
                scanning = captureStats.scanning,
                onSettings = onSettings,
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xDD101A2A)) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = "Home") },
                    label = { Text("Run") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSettings,
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PerformanceCard(captureStats)
            TargetCard(captureStats)
            ControlCard(
                hasAccess = user.hasActiveAccess,
                scanning = captureStats.scanning,
                onStart = { runUserAction(::startCapture) },
                onPause = {
                    runUserAction {
                        context.startService(
                            Intent(context, ScreenCaptureService::class.java)
                                .setAction(ScreenCaptureService.ACTION_PAUSE),
                        )
                    }
                },
                onStop = {
                    runUserAction {
                        context.startService(
                            Intent(context, ScreenCaptureService::class.java)
                                .setAction(ScreenCaptureService.ACTION_STOP),
                        )
                    }
                },
            )
            AnimatedVisibility(
                visible = user.shouldShowAds,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RewardCard(
                        user = user,
                        rewardReturned = rewardReturned,
                        onStart = ::startRewardSession,
                        onComplete = ::completeRewardSession,
                    )
                    BannerAd(onClick = { AdService.openUserInitiatedAd(context) })
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (user.status == UserStatus.EXPIRED) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Subscription expired") },
            text = { Text("AUTOPILOT is stopped until access is restored. Choose an option below.") },
            confirmButton = {
                TextButton(onClick = if (rewardReturned) ::completeRewardSession else ::startRewardSession) {
                    Text(if (rewardReturned) "Confirm Ad Viewed" else "Watch 10 Ads for +1 Day")
                }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { message = "Enter a valid license key from the admin panel." }) {
                        Text("Enter License Key")
                    }
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_SUBJECT, "AUTOPILOT access request")
                            })
                        }
                    }) {
                        Text("Contact Admin")
                    }
                }
            },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun HeroHeader(user: User, now: Long?, scanning: Boolean, onSettings: () -> Unit) {
    val accent = if (scanning) Color(0xFF63E6BE) else Color(0xFF7890A9)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(50.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = "AUTOPILOT", tint = accent, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("AUTOPILOT", color = Color(0xFF63E6BE), style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
            Text(if (scanning) "Scanning live" else "Control center", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                when {
                    user.status == UserStatus.LIFETIME -> "LIFETIME ACCESS"
                    now != null && user.expiryTimestamp > 0L -> "${user.status.name.replace('_', ' ')} · ${formatRemaining(user.expiryTimestamp, now)}"
                    else -> user.status.name.replace('_', ' ')
                },
                color = Color(0xFF9CB4C8),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color.White)
        }
    }
}

@Composable
private fun PerformanceCard(stats: CaptureStats) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PERFORMANCE", color = Color(0xFF7B91A9), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
                Text("Detection telemetry", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            StatusPill(if (stats.scanning) "ACTIVE" else "STANDBY", if (stats.scanning) Color(0xFF63E6BE) else Color(0xFF7890A9))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("SCAN FPS", "${stats.fps}", Modifier.weight(1f))
            Metric("CONFIDENCE", "${(stats.confidence * 100).toInt()}%", Modifier.weight(1f))
            Metric("SESSION CLICKS", "${stats.clicks}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { stats.confidence.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
            color = Color(0xFF63E6BE),
            trackColor = Color(0xFF22354A),
        )
    }
}

@Composable
private fun TargetCard(stats: CaptureStats) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF182C3D)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected target", tint = Color(0xFF63E6BE), modifier = Modifier.size(32.dp))
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("TARGET TEMPLATE", color = Color(0xFF7B91A9), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.3.sp)
                Text("Autopilot badge", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Scale tolerance · 0.50× — 1.75×", color = Color(0xFF9CB4C8), style = MaterialTheme.typography.bodySmall)
            }
            Text("${stats.matchedScale}×", color = Color(0xFF63E6BE), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ControlCard(
    hasAccess: Boolean,
    scanning: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    GlassCard {
        Text("AUTOMATION CONTROLS", color = Color(0xFF7B91A9), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
        Text(
            if (hasAccess) "Ready for precision capture" else "Access required to start a session",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionIcon("Start", Color(0xFF63E6BE), hasAccess && !scanning, Icons.Outlined.PlayArrow, onStart)
            ActionIcon("Pause", Color(0xFFFFCE6A), hasAccess && scanning, Icons.Outlined.Pause, onPause)
            ActionIcon("Stop", Color(0xFFFF7D8A), hasAccess && scanning, Icons.Outlined.Stop, onStop)
        }
    }
}

@Composable
private fun ActionIcon(
    label: String,
    tint: Color,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(tint.copy(alpha = if (enabled) 0.16f else 0.06f)),
        ) {
            Icon(icon, contentDescription = label, tint = if (enabled) tint else Color(0xFF425A72), modifier = Modifier.size(25.dp))
        }
        Text(label, color = if (enabled) tint else Color(0xFF425A72), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Color(0xFF7B91A9), fontSize = 9.sp, letterSpacing = 0.5.sp)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xCC132237)),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            content()
        }
    }
}

@Composable
private fun StatusPill(text: String, tint: Color) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.13f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(6.dp))
        Text(text, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RewardCard(user: User, rewardReturned: Boolean, onStart: () -> Unit, onComplete: () -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("REWARD ACCESS", color = Color(0xFF7B91A9), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.4.sp)
                Text("+1 day access", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("${user.rewardAdsCompleted} / 10", color = Color(0xFFFFCE6A), fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { user.rewardAdsCompleted / 10f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = Color(0xFFFFCE6A),
            trackColor = Color(0xFF3C3440),
        )
        Text(
            if (rewardReturned) "Return confirmed? Count this session." else "Complete ten voluntary sessions to unlock one day.",
            color = Color(0xFF9CB4C8),
            style = MaterialTheme.typography.bodySmall,
        )
        if (rewardReturned) {
            Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) { Text("Confirm Ad Viewed") }
        } else {
            OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Watch Reward Ad") }
        }
    }
}

@Composable
private fun BannerAd(onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x99203346)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SPONSORED", color = Color(0xFF7B91A9), style = MaterialTheme.typography.labelSmall)
                Text("Tap to view partner content", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            Text("OPEN", color = Color(0xFF63E6BE), style = MaterialTheme.typography.labelLarge)
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
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back") }
                Text("Settings", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("ACCOUNT", color = Color(0xFF63E6BE), style = MaterialTheme.typography.labelLarge, letterSpacing = 1.5.sp)
            GlassCard {
                Text(user.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(user.email, color = Color(0xFF9CB4C8))
                Text("Status: ${user.status}", color = Color(0xFF9CB4C8))
            }
            Spacer(Modifier.weight(1f))
            Text(
                "AUTOPILOT v2.1.0",
                color = Color(0xFF4F6B83),
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                    val now = SystemClock.elapsedRealtime()
                    val nextTaps = if (now - lastTap <= 1_500L) taps + 1 else 1
                    taps = nextTaps
                    lastTap = now
                    if (nextTaps >= 10) {
                        taps = 0
                        onAdmin()
                    }
                },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Local session", tint = Color(0xFF9CB4C8))
                Text(" Local session", color = Color(0xFF9CB4C8))
            }
        }
    }
}

@Composable
private fun PulseAnimation(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = {
            LottieAnimationView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setAnimation(R.raw.autopilot_pulse)
                repeatCount = LottieDrawable.INFINITE
                playAnimation()
            }
        },
    )
}

private fun formatRemaining(expiry: Long, now: Long): String {
    val remaining = (expiry - now).coerceAtLeast(0L)
    val days = remaining / DAY_MILLIS
    val hours = (remaining / HOUR_MILLIS) % 24
    val minutes = (remaining / MINUTE_MILLIS) % 60
    return when {
        days > 0 -> "${days}d ${hours}h left"
        hours > 0 -> "${hours}h ${minutes}m left"
        else -> "${minutes}m left"
    }
}

private fun autopilotColors() = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF63E6BE),
    secondary = Color(0xFFFFCE6A),
    tertiary = Color(0xFFFF7D8A),
    background = Color(0xFF0B0F19),
    surface = Color(0xFF132237),
    onPrimary = Color(0xFF07151A),
    onBackground = Color.White,
    onSurface = Color.White,
)

private const val NETWORK_SYNC_INTERVAL = 60_000L
private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
private const val HOUR_MILLIS = 60L * 60L * 1_000L
private const val MINUTE_MILLIS = 60L * 1_000L