package com.pmgaurav.safestrideai.ui.onboarding

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pmgaurav.safestrideai.utils.PermissionManager
import com.pmgaurav.safestrideai.utils.rememberPermissionState
import kotlinx.coroutines.launch

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String,
    val bulletPoints: List<String>
)

private val ONBOARDING_PAGES = listOf(
    OnboardingPage(
        emoji = "🛡️",
        title = "SafeStrideAI",
        description = "AI-powered pedestrian safety companion.",
        bulletPoints = listOf("Hold phone pointing toward traffic", "Keep app open while crossing", "Enable sound for alerts")
    ),
    OnboardingPage(
        emoji = "📷",
        title = "AI Detection",
        description = "Uses camera to detect vehicles in real time.",
        bulletPoints = listOf("On-device TFLite AI", "Works 100% offline", "Detects Cars, Buses, Bikes")
    ),
    OnboardingPage(
        emoji = "⚠️",
        title = "Collision Alerts",
        description = "Predicts danger 2-5 seconds in advance.",
        bulletPoints = listOf("🟢 Green: Safe", "🟡 Yellow: Caution", "🔴 Red: DANGER")
    ),
    OnboardingPage(
        emoji = "📍",
        title = "Permissions",
        description = "Required to keep you safe while walking.",
        bulletPoints = listOf("📷 Camera for detection", "📍 GPS for hazard tagging", "📳 Haptic for alerts")
    )
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGES.size })
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == ONBOARDING_PAGES.size - 1

    val permissionLauncher = rememberPermissionState { granted ->
        Log.d("Onboarding", "Permissions result: $granted")
        if (granted) {
            onFinished()
        } else {
            Log.w("Onboarding", "User denied permissions!")
        }
    }

    val handleStart = {
        Log.d("Onboarding", "Handle start clicked")
        if (PermissionManager.hasPermissions(context)) {
            onFinished()
        } else {
            permissionLauncher.launch(PermissionManager.REQUIRED_PERMISSIONS)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1B2A))) {
        if (!isLastPage) {
            TextButton(
                onClick = { handleStart() },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 16.dp)
            ) {
                Text("Skip", color = Color(0xFF00C2FF), fontSize = 15.sp)
            }
        }

        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(80.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                userScrollEnabled = true 
            ) { pageIndex ->
                val page = ONBOARDING_PAGES[pageIndex]
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(page.emoji, fontSize = 72.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(page.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(page.description, fontSize = 15.sp, color = Color(0xFFBDC8D4), textAlign = TextAlign.Center, lineHeight = 22.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2A3B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            page.bulletPoints.forEach { bullet ->
                                Text(bullet, fontSize = 14.sp, color = Color(0xFFE8EEF4), lineHeight = 20.sp, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(ONBOARDING_PAGES.size) { index ->
                    val isActive = pagerState.currentPage == index
                    val color by animateColorAsState(if (isActive) Color(0xFF00C2FF) else Color(0xFF3A4A5A))
                    Box(modifier = Modifier.size(if (isActive) 10.dp else 8.dp).clip(CircleShape).background(color))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00C2FF))
                    ) { Text("Back", fontSize = 15.sp) }
                } else { Spacer(modifier = Modifier.weight(1f)) }

                Button(
                    onClick = {
                        if (isLastPage) handleStart()
                        else coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier.weight(2f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077B6)),
                    shape = RoundedCornerShape(10.dp)
                ) { Text(if (isLastPage) "Get Started →" else "Next →", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }
        }
    }
}

