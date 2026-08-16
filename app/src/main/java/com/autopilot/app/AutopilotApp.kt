package com.autopilot.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.viewinterop.AndroidView
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
private fun HomeScreen(
    user: User,
    storage: SecureStorage,
    networkTime: NetworkTimeProvider,
    onUserChanged: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
        if (!result.completed) {
            message = "Please keep the ad open for a few seconds, then return and confirm."
        } else if (result.rewarded) {
            message = "Reward complete. One day of access has been added."
        } else {
            message = "Reward counted. Ads completed: ${result.count} / 10."
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
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as? MediaProjectionManager
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "AUTOPILOT",
                        tint = Color(0xFF55D6BE),
                        modifier = Modifier.size(28.dp),
                    )
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("AUTOPILOT", color = Color(0xFF55D6BE), style = MaterialTheme.typography.labelLarge)
                        Text("Control center", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101D2D)) {
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(user, networkTime.currentTimeMillis())
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF132338)),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Automation controls", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (user.hasActiveAccess) "Capture is protected by network-synced access checks."
                        else "Controls are locked until valid access is available.",
                        color = Color(0xFF9CB4C8),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ActionIcon(
                            label = "Start",
                            tint = Color(0xFF55D6BE),
                            enabled = user.hasActiveAccess,
                            icon = { Icon(Icons.Outlined.PlayArrow, contentDescription = "Start") },
                        ) { runUserAction(::startCapture) }
                        ActionIcon(
                            label = "Pause",
                            tint = Color(0xFFFFCE6A),
                            enabled = user.hasActiveAccess,
                            icon = { Icon(Icons.Outlined.Pause, contentDescription = "Pause") },
                        ) {
                            runUserAction {
                                context.startService(
                                    Intent(context, ScreenCaptureService::class.java)
                                        .setAction(ScreenCaptureService.ACTION_PAUSE),
                                )
                            }
                        }
                        ActionIcon(
                            label = "Stop",
                            tint = Color(0xFFFF7D8A),
                            enabled = user.hasActiveAccess,
                            icon = { Icon(Icons.Outlined.Stop, contentDescription = "Stop") },
                        ) {
                            runUserAction {
                                context.startService(
                                    Intent(context, ScreenCaptureService::class.java)
                                        .setAction(ScreenCaptureService.ACTION_STOP),
                                )
                            }
                        }
                    }
                }
            }
            if (user.shouldShowAds) {
                RewardCard(
                    user = user,
                    rewardReturned = rewardReturned,
                    onStart = ::startRewardSession,
                    onComplete = ::completeRewardSession,
                )
                BannerAd(onClick = { AdService.openUserInitiatedAd(context) })
            }
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
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO).apply {
                                    data = android.net.Uri.parse("mailto:")
                                    putExtra(Intent.EXTRA_SUBJECT, "AUTOPILOT access request")
                                },
                            )
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
private fun ActionIcon(
    label: String,
    tint: Color,
    enabled: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(58.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides tint,
            ) {
                icon()
            }
        }
        Text(label, color = if (enabled) tint else Color(0xFF4F6B83))
    }
}

@Composable
private fun RewardCard(
    user: User,
    rewardReturned: Boolean,
    onStart: () -> Unit,
    onComplete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101D2D)), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Earn +1 Day Free Access", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("Ads completed: ${user.rewardAdsCompleted} / 10", color = Color(0xFF55D6BE))
            Text(
                if (rewardReturned) "Returned from the ad? Confirm the session to count it."
                else "Complete ten voluntary ad-view sessions to add one day.",
                color = Color(0xFF9CB4C8),
            )
            if (rewardReturned) {
                Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                    Text("Confirm Ad Viewed")
                }
            } else {
                OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Watch & Claim Reward Ad")
                }
            }
        }
    }
}

@Composable
private fun BannerAd(onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF182B3C)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Sponsored", color = Color(0xFF9CB4C8), style = MaterialTheme.typography.labelSmall)
                Text("Tap to view partner content", color = Color.White)
            }
            Text("OPEN", color = Color(0xFF55D6BE), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatusCard(user: User, networkTimeMillis: Long?) {
    val statusColor = when (user.status) {
        UserStatus.TRIAL, UserStatus.APPROVED, UserStatus.LIFETIME -> Color(0xFF55D6BE)
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
                Box(Modifier.size(12.dp).clip(CircleShape).background(statusColor))
            }
            Column {
                Text("Access status", color = Color(0xFF9CB4C8))
                Text(user.status.name.replace('_', ' '), color = statusColor, style = MaterialTheme.typography.titleMedium)
                when {
                    user.status == UserStatus.LIFETIME -> Text("Lifetime access", color = Color(0xFF9CB4C8))
                    user.expiryTimestamp > 0L && networkTimeMillis != null -> Text(
                        "${accessLabel(user.status)} ${formatRemaining(user.expiryTimestamp, networkTimeMillis)}",
                        color = Color(0xFF9CB4C8),
                    )
                    user.status in setOf(UserStatus.APPROVED, UserStatus.TRIAL) && !user.timeValidated ->
                        Text("Network time required", color = Color(0xFFFFCE6A))
                }
                if (user.adFreeOverride) {
                    Text("Admin: AD-FREE MODE ON", color = Color(0xFF55D6BE))
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
                text = "AUTOPILOT v2.1.0",
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

private fun accessLabel(status: UserStatus): String =
    if (status == UserStatus.TRIAL) "Trial:" else "Active:"

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
    primary = Color(0xFF55D6BE),
    secondary = Color(0xFFFFCE6A),
    background = Color(0xFF08111F),
    surface = Color(0xFF101D2D),
    onPrimary = Color(0xFF07151A),
    onBackground = Color.White,
    onSurface = Color.White,
)

private const val NETWORK_SYNC_INTERVAL = 60_000L
private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
private const val HOUR_MILLIS = 60L * 60L * 1_000L
private const val MINUTE_MILLIS = 60L * 1_000L