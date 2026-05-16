package com.pmgaurav.safestrideai.ui

import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberNodes
import com.pmgaurav.safestrideai.BuildConfig
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pmgaurav.safestrideai.R
import com.pmgaurav.safestrideai.location.GPSLocationEngine
import com.pmgaurav.safestrideai.ui.onboarding.OnboardingScreen
import com.pmgaurav.safestrideai.ui.theme.*
import com.pmgaurav.safestrideai.utils.*
import androidx.compose.ui.input.pointer.pointerInteropFilter
import com.pmgaurav.safestrideai.detection.TrackedObject
import com.pmgaurav.safestrideai.detection.RiskTier
import com.pmgaurav.safestrideai.map.OfflineMapViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.*
import kotlin.math.sqrt

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()


    val startDestination = if (uiState.onboardingComplete) "main" else "onboarding"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onFinished = {
                    viewModel.completeOnboarding()
                    navController.navigate("main") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable("main") {
            MainAppContent(viewModel, navController)
        }

        composable(
            route = "settings",
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
        ) {
            SettingsScreen(viewModel, onBackClick = { navController.popBackStack() })
        }

        composable(
            route = "analytics",
            enterTransition = { slideInVertically(initialOffsetY = { it }, animationSpec = tween(350)) },
            exitTransition = { slideOutVertically(targetOffsetY = { it }, animationSpec = tween(350)) }
        ) {
            AnalyticsScreen(viewModel, onBackClick = { navController.popBackStack() })
        }

        composable(
            route = "hazard_map",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            HazardMapScreen(viewModel, onBackClick = { navController.popBackStack() })
        }

        composable(
            route = "offline_maps",
            enterTransition = { slideInVertically(initialOffsetY = { it }, animationSpec = tween(350)) },
            exitTransition = { slideOutVertically(targetOffsetY = { it }, animationSpec = tween(350)) }
        ) {
            OfflineMapScreen(onBackClick = { navController.popBackStack() }, mainViewModel = viewModel)
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel, navController: NavController) {
    val uiState by viewModel.uiState.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasNow = PermissionManager.hasPermissions(context)
                viewModel.onPermissionsResult(hasNow)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!uiState.onboardingComplete || !permissionsGranted) {
        OnboardingScreen {
            val hasNow = PermissionManager.hasPermissions(context)
            if (hasNow) {
                viewModel.onPermissionsResult(granted = true)
                viewModel.completeOnboarding()
            }
        }
    } else if (!uiState.isAppReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ElectricCyan)
            Text("Initializing AI Safety Stride...", modifier = Modifier.padding(top = 80.dp), color = OffWhite)
        }
    } else {
        MainAppLayout(viewModel, uiState, navController)
    }
}

@Composable
fun OfflineMapScreen(
    onBackClick: () -> Unit,
    mainViewModel: MainViewModel,
    viewModel: OfflineMapViewModel = hiltViewModel()
) {
    val tileCount by viewModel.tileCount.collectAsState()
    val progress by viewModel.downloadProgress.collectAsState()
    val mainUiState by mainViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Offline Map Caching") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Offline Maps", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Cache Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Stored Tiles: $tileCount", style = MaterialTheme.typography.bodyMedium)

                    if (progress > 0f && progress < 1f) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            mainUiState.currentLocation?.let { loc ->
                                viewModel.downloadArea(loc.latitude, loc.longitude)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = mainUiState.currentLocation != null
                    ) {
                        Text(if (mainUiState.currentLocation != null) "Download Current Area" else "Waiting for GPS...")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.clearCache() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Cache", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Caching map tiles locally allows the safety system to provide navigation guidance even when data signal is lost in urban canyons.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainAppLayout(viewModel: MainViewModel, uiState: MainUiState, navController: NavController) {
    val settingsState by viewModel.settings.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInteropFilter { event ->
                viewModel.handleTouchEvent(event)
            }
    ) {
        if (settingsState.arOverlays) {
            ARScenePreview(viewModel, uiState)
        } else {
            CameraPreview(viewModel, uiState.isPaused)
        }

        BoundingBoxOverlay(
            trackedObjects = uiState.trackedObjects,
            frameWidth = uiState.frameWidth,
            frameHeight = uiState.frameHeight
        )

        DangerFlashOverlay(uiState.riskTier)

        TopStatusBar(
            modifier = Modifier.align(Alignment.TopCenter).zIndex(10f),
            fps = uiState.fps,
            gpsStatus = uiState.currentLocation?.locationQuality ?: GPSLocationEngine.LocationQuality.LOW,
            memoryUsageMb = uiState.memoryUsageMb,
            onSettingsClick = { navController.navigate("settings") },
            onStatsClick = { navController.navigate("analytics") }
        )

        uiState.error?.let { error ->
            ErrorBanner(error) { viewModel.clearError() }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp).zIndex(11f)) {
            AlertBanner(
                tier = uiState.riskTier,
                nearbyHazardsCount = uiState.nearbyHazardsCount
            )
        }

        BottomControlPanel(
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(10f),
            isPaused = uiState.isPaused,
            onMapClick = { navController.navigate("hazard_map") },
            onOfflineMapClick = { navController.navigate("offline_maps") },
            onPauseClick = { viewModel.togglePause() }
        )


        DebugHud(
            fps = uiState.fps,
            objectsCount = uiState.trackedObjects.size,
            stats = uiState.pipelineStats,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 80.dp)
        )

        DetectionDebugOverlay(
            tracked = uiState.trackedObjects,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 140.dp, end = 16.dp)
        )
    }
}

@Composable
fun DetectionDebugOverlay(
    tracked: List<TrackedObject>,
    modifier: Modifier = Modifier
) {
    if (!BuildConfig.DEBUG) return

    Column(
        modifier = modifier
            .widthIn(max = 240.dp)
            .heightIn(max = 180.dp)
            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            "🔍 DETECTIONS: ${tracked.size}",
            color      = Color(0xFF00E5FF),
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(4.dp))

        if (tracked.isEmpty()) {
            Text(
                "No detections passing filters",
                color = Color(0xFF8B949E),
                fontSize = 9.sp
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(tracked, key = { it.id }) { obj ->
                    val tierColor = when (obj.riskTier) {
                        RiskTier.DANGER  -> Color(0xFFFF1744)
                        RiskTier.CAUTION -> Color(0xFFFF6D00)
                        RiskTier.ADVISORY -> Color(0xFFFFD600)
                        else             -> Color(0xFF8B949E)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "• ${obj.label} #${obj.id}",
                            color    = tierColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${"%.1f".format(obj.depthMeters)}m | " +
                                    "${"%.0f".format(obj.speedKmph)}km/h | " +
                                    "TTC:${"%.1f".format(obj.ttcSeconds.coerceAtMost(99f))}s",
                            color    = Color.White,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DebugHud(fps: Int, objectsCount: Int, stats: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(16.dp),
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("System: ACTIVE", color = ElectricCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Inference: $fps FPS", color = Color.White, fontSize = 10.sp)
            Text("Tracked: $objectsCount", color = Color.White, fontSize = 10.sp)
            if (stats.isNotEmpty()) {
                Text(stats, color = OffWhite.copy(alpha = 0.7f), fontSize = 8.sp)
            }
        }
    }
}

@Composable
fun ARScenePreview(viewModel: MainViewModel, uiState: MainUiState) {
    val engine = rememberEngine()
    val nodes = rememberNodes()
    var arError by remember { mutableStateOf<String?>(null) }

    if (arError != null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("AR Error: $arError", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                Button(onClick = { viewModel.updateSetting("ar", false) }) {
                    Text("Switch to Standard Camera")
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            childNodes = nodes,
            sessionConfiguration = { session, config ->
                try {
                    viewModel.arOverlayManager.configureSession(session, config)
                } catch (e: Exception) {
                    Log.e("ARScene", "Config error: ${e.message}")
                    arError = e.message
                }
            },
            onSessionUpdated = { session, frame ->
                try {
                    viewModel.onArFrame(session, frame)

                    uiState.trackedObjects.forEach { obj ->
                        val anchor = viewModel.arOverlayManager.getAnchorForDetection(obj.id)
                        if (anchor != null) {
                            val existingNode = nodes.find { it is AnchorNode && it.anchor == anchor }
                            if (existingNode == null) {
                                val anchorNode = AnchorNode(engine, anchor)
                                nodes.add(anchorNode)
                            }
                        }
                    }

                    val activeAnchors = uiState.trackedObjects.mapNotNull { viewModel.arOverlayManager.getAnchorForDetection(it.id) }
                    nodes.removeAll { node ->
                        node is AnchorNode && node.anchor !in activeAnchors
                    }
                } catch (e: Exception) {
                    Log.e("ARScene", "Update error: ${e.message}")

                }
            }
        )

        if (uiState.isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text("AR PAUSED", color = Color.White)
            }
        }
    }
}

@Composable
fun CameraPreview(viewModel: MainViewModel, isPaused: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        try {
            viewModel.cameraManager.startCamera(
                lifecycleOwner = lifecycleOwner,
                previewView = previewView
            )
        } catch (_: Exception) {
            viewModel.reportError(AppError.CameraError)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        if (isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.system_paused),
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}


@Composable
fun DangerFlashOverlay(tier: RiskTier) {
    if (tier == RiskTier.DANGER) {
        val infiniteTransition = rememberInfiniteTransition(label = "flash")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Red.copy(alpha = alpha)))
    }
}

@Composable
fun TopStatusBar(
    modifier: Modifier = Modifier,
    fps: Int,
    gpsStatus: GPSLocationEngine.LocationQuality,
    memoryUsageMb: Long,
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SAFE", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text("GPS: ${gpsStatus.name}", color = OffWhite, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Text("FPS: $fps", color = OffWhite, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Text("MEM: ${memoryUsageMb}MB", color = OffWhite, fontSize = 10.sp)
            }
        }

        Row {
            IconButton(
                onClick = onStatsClick,
                modifier = Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.BarChart, "Stats", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.Settings, "Settings", tint = Color.White)
            }
        }
    }
}

@Composable
fun AlertBanner(
    tier: RiskTier,
    nearbyHazardsCount: Int = 0
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        if (nearbyHazardsCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("$nearbyHazardsCount nearby community reports", color = OffWhite, fontSize = 12.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = tier != RiskTier.SAFE,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            val (color, announcement) = when(tier) {
                RiskTier.DANGER  -> SafetyRed to stringResource(R.string.announcement_tier_3)
                RiskTier.CAUTION -> SafetyOrange to stringResource(R.string.announcement_tier_2)
                RiskTier.ADVISORY -> ElectricCyan to stringResource(R.string.announcement_tier_1)
                else -> Color.Gray to ""
            }

            val displayText = when(tier) {
                RiskTier.DANGER  -> stringResource(R.string.danger_collision_imminent)
                RiskTier.CAUTION -> stringResource(R.string.caution_vehicle_approaching)
                RiskTier.ADVISORY -> stringResource(R.string.stay_alert_object_detected)
                else -> ""
            }

            Card(
                modifier = Modifier.fillMaxWidth()
                    .semantics { contentDescription = announcement },
                colors = CardDefaults.cardColors(containerColor = color),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val icon = if (tier == RiskTier.DANGER) "🔴" else if (tier == RiskTier.CAUTION) "🟠" else "🟡"
                    Text(icon, fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(displayText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun ErrorBanner(error: AppError, onDismiss: () -> Unit) {
    val message = when(error) {
        AppError.CameraError -> stringResource(R.string.error_camera)
        AppError.ModelLoadError -> stringResource(R.string.error_model)
        AppError.LocationError -> stringResource(R.string.error_location)
        AppError.RootedDeviceError -> stringResource(R.string.error_root)
        is AppError.UnknownError -> error.message
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Pause, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(message, modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss), fontSize = 12.sp) }
        }
    }
}

@Composable
fun BottomControlPanel(
    modifier: Modifier = Modifier,
    isPaused: Boolean,
    onMapClick: () -> Unit,
    onOfflineMapClick: () -> Unit,
    onPauseClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkBackground.copy(alpha = 0.9f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp, top = 16.dp).navigationBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ControlButton(
                    icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    label = if (isPaused) stringResource(R.string.resume) else stringResource(R.string.pause),
                    onClick = onPauseClick
                )
                ControlButton(
                    icon = Icons.Default.Map,
                    label = stringResource(R.string.map),
                    onClick = onMapClick
                )
                ControlButton(
                    icon = Icons.Default.Download,
                    label = "Offline",
                    onClick = onOfflineMapClick
                )
            }
        }
    }
}

@Composable
fun ControlButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = ElectricCyan)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = OffWhite, fontSize = 12.sp)
    }
}


@Composable
fun AnalyticsScreen(viewModel: MainViewModel, onBackClick: () -> Unit) {
    val overallStats by viewModel.overallStats.collectAsState(OverallStats())
    val weeklyData by viewModel.weeklyData.collectAsState()

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Safety Analytics") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            item {
                Text("Overall Safety", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Score: ${overallStats.avgSafetyScore}%", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (overallStats.avgSafetyScore > 80) Color.Green else Color.Yellow
                            )
                        }
                        LinearProgressIndicator(
                            progress = { overallStats.avgSafetyScore / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = if (overallStats.avgSafetyScore > 80) Color.Green else if (overallStats.avgSafetyScore > 50) Color.Yellow else Color.Red
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                Text("Weekly Activity", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    weeklyData.forEach { day: DayData ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .width(24.dp)
                                    .height((day.sessionCount * 20).dp.coerceAtMost(100.dp))
                                    .background(
                                        if (day.isToday) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                    )
                            )
                            Text(day.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                Text("Historical Stats", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        StatRow("Total Sessions", overallStats.totalSessions.toString())
                        StatRow("Distance Walked", "${String.format(Locale.getDefault(), "%.1f", overallStats.totalDistanceKm)} km")
                        StatRow("Total Alerts Prevented", overallStats.totalAlerts.toString())
                        StatRow("Critical Risks Avoided", overallStats.tier3Total.toString())
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                Text("Top Hazards Detected", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }

            items(overallStats.topClasses) { stat: ClassStat ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stat.label, Modifier.weight(1f))
                    Text("${stat.count} times", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel, onBackClick: () -> Unit) {
    val settingsState by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                SettingsHeader("Alerts & Feedback")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Detection Confidence Threshold: ${"%.2f".format(settingsState.detectionConfidence)}", style = MaterialTheme.typography.bodyMedium, color = OffWhite)
                    Slider(
                        value = settingsState.detectionConfidence,
                        onValueChange = { viewModel.setConfidence(it) },
                        colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                    )
                }
                SettingsSwitchItem(
                    title = "Audio Alerts",
                    subtitle = "Voice warnings for nearby hazards",
                    checked = settingsState.audioEnabled,
                    onCheckedChange = { viewModel.updateSetting("audio", it) }
                )
                SettingsSwitchItem(
                    title = "Haptic Feedback",
                    subtitle = "Vibrate on critical danger",
                    checked = settingsState.hapticFeedback,
                    onCheckedChange = { viewModel.updateSetting("haptic", it) }
                )
            }

            item {
                SettingsHeader("Augmented Reality")
                SettingsSwitchItem(
                    title = "AR Overlays",
                    subtitle = "Show bounding boxes in camera view",
                    checked = settingsState.arOverlays,
                    onCheckedChange = { viewModel.updateSetting("ar", it) }
                )
            }

            item {
                SettingsHeader("Advanced Features")

                SettingsSwitchItem(
                    title = "V2X Integration",
                    subtitle = "Communicate with nearby vehicles/infrastructure",
                    checked = settingsState.v2xEnabled,
                    onCheckedChange = { viewModel.updateSetting("v2x", it) }
                )
                SettingsSwitchItem(
                    title = "Cloud Sync",
                    subtitle = "Back up safety analytics to the cloud",
                    checked = settingsState.cloudSync,
                    onCheckedChange = { viewModel.updateSetting("cloud", it) }
                )
                SettingsSwitchItem(
                    title = "Battery Saver",
                    subtitle = "Reduce frame rate to save power",
                    checked = settingsState.batterySaver,
                    onCheckedChange = { viewModel.updateSetting("battery", it) }
                )
                SettingsSwitchItem(
                    title = "Stability Mode",
                    subtitle = "Force CPU inference to prevent crashes",
                    checked = settingsState.forceCpu,
                    onCheckedChange = { viewModel.updateSetting("force_cpu", it) }
                )
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Version 1.0.8",
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun SettingsSwitchItem(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun HazardMapScreen(viewModel: MainViewModel, onBackClick: () -> Unit) {
    val hazards by viewModel.hazardRepo.getAllHazards().collectAsState(initial = emptyList())
    val uiState by viewModel.uiState.collectAsState()


    val clusteredHazards = remember(hazards) {
        SafetyUtils.clusterHazards(hazards)
    }

    Box(Modifier.fillMaxSize().background(DarkBackground)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "Active Hazard Radar",
                style = MaterialTheme.typography.headlineSmall,
                color = ElectricCyan,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${hazards.size} hazards synced | ${clusteredHazards.size} clusters",
                style = MaterialTheme.typography.bodySmall,
                color = OffWhite.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkSurface)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val currentLoc = uiState.currentLocation

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = center
                    val maxRadius = size.minDimension / 2

                    drawCircle(ElectricCyan, radius = maxRadius * 0.33f, style = Stroke(1f), alpha = 0.2f)
                    drawCircle(ElectricCyan, radius = maxRadius * 0.66f, style = Stroke(1f), alpha = 0.2f)
                    drawCircle(ElectricCyan, radius = maxRadius, style = Stroke(2f), alpha = 0.4f)

                    drawLine(ElectricCyan, start = center.copy(x = center.x - maxRadius), end = center.copy(x = center.x + maxRadius), strokeWidth = 1f, alpha = 0.2f)
                    drawLine(ElectricCyan, start = center.copy(y = center.y - maxRadius), end = center.copy(y = center.y + maxRadius), strokeWidth = 1f, alpha = 0.2f)

                    if (currentLoc != null) {
                        clusteredHazards.forEach { cluster ->

                            val hazard = cluster.first()
                            val dLat = (hazard.latitude - currentLoc.latitude) * 111111f
                            val dLng = (hazard.longitude - currentLoc.longitude) * 111111f

                            val radarScale = maxRadius / 100f
                            val offsetX = (dLng * radarScale).toFloat()
                            val offsetY = -(dLat * radarScale).toFloat()

                            val dist = sqrt((offsetX * offsetX + offsetY * offsetY).toDouble()).toFloat()
                            if (dist < maxRadius) {

                                val baseRadius = 6.dp.toPx()
                                val clusterRadius = baseRadius + (cluster.size * 2).toFloat()

                                drawCircle(
                                    color = if (hazard.severity > 3) SafetyRed else SafetyOrange,
                                    radius = clusterRadius,
                                    center = center.copy(x = center.x + offsetX, y = center.y + offsetY)
                                )

                                drawCircle(
                                    color = (if (hazard.severity > 3) SafetyRed else SafetyOrange).copy(alpha = 0.3f),
                                    radius = clusterRadius * 2,
                                    center = center.copy(x = center.x + offsetX, y = center.y + offsetY)
                                )
                            }
                        }

                        drawCircle(ElectricCyan, radius = 8.dp.toPx(), center = center)
                        drawCircle(ElectricCyan.copy(alpha = 0.4f), radius = 12.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                    }
                }

                if (currentLoc == null) {
                    Text("Awaiting GPS Signal...", color = OffWhite)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Nearby Incidents", style = MaterialTheme.typography.titleMedium, color = OffWhite)
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.height(200.dp).fillMaxWidth()) {
                items(hazards) { hazard ->
                    Card(
                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.1f))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val icon = when(hazard.hazardType) {
                                "CRITICAL_COLLISION_RISK" -> "🔴"
                                "CONSTRUCTION" -> "🚧"
                                "POTHOLE" -> "🕳️"
                                else -> "⚠️"
                            }
                            Text(icon, fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(hazard.hazardType, fontWeight = FontWeight.Bold, color = OffWhite)
                                Text("Severity: ${hazard.severity}", style = MaterialTheme.typography.bodySmall, color = OffWhite.copy(alpha = 0.6f))
                            }

                            Row {
                                IconButton(onClick = { viewModel.confirmHazard(hazard.id) }) {
                                    Icon(Icons.Default.Check, "Confirm", tint = Color.Green)
                                }
                                IconButton(onClick = { viewModel.disputeHazard(hazard.id) }) {
                                    Icon(Icons.Default.Close, "Dispute", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.reportHazard("MANUAL_HAZARD", 2) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
            ) {
                Text("Report New Hazard Here")
            }
        }

        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .statusBarsPadding()
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Default.Close, "Close", tint = Color.White)
        }
    }
}