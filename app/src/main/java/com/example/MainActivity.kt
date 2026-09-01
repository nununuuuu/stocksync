package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.IntradayAlert
import com.example.ui.MainAppViewModel
import com.example.ui.screens.*
import com.example.ui.theme.*

enum class AppDestination(
    val title: String,
    val shortLabel: String,
    val icon: ImageVector
) {
    DASHBOARD("台股研究面板", "研究面板", Icons.Default.CandlestickChart),
    AI_ANALYSTS("AI 策略研究團隊", "AI 團隊", Icons.Default.Psychology),
    WATCHLIST_CHIPS("籌碼選股與自選", "籌碼選股", Icons.Default.AccountBalance),
    ALERTS_RADAR("盤中即時預警雷達", "即時預警", Icons.Default.NotificationsActive),
    RESEARCH_NOTES("專業研究筆記庫", "研究筆記", Icons.Default.MenuBook)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainAppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppLayout(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppLayout(viewModel: MainAppViewModel) {
    var currentDestination by remember { mutableStateOf(AppDestination.DASHBOARD) }
    val latestAlert by viewModel.latestAlert.collectAsState(initial = null)
    var activeBannerAlert by remember { mutableStateOf<IntradayAlert?>(null) }

    LaunchedEffect(latestAlert) {
        if (latestAlert != null) {
            activeBannerAlert = latestAlert
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(StockBackgroundDark)) {
        val isWideScreen = maxWidth >= 700.dp

        if (isWideScreen) {
            // Tablet / Desktop / Wide screen layout with NavigationRail
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = StockSurfaceDark,
                    contentColor = TextPrimaryDark,
                    modifier = Modifier.width(88.dp).testTag("navigation_rail")
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.Default.AutoGraph,
                        contentDescription = "Logo",
                        tint = StockPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    AppDestination.values().forEach { destination ->
                        val selected = currentDestination == destination
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text(destination.shortLabel, fontSize = 10.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = StockPrimary,
                                selectedTextColor = StockPrimary,
                                unselectedIconColor = TextSecondaryDark,
                                unselectedTextColor = TextSecondaryDark,
                                indicatorColor = StockSurfaceVariantDark
                            ),
                            modifier = Modifier.testTag("nav_rail_${destination.name}")
                        )
                    }
                }

                VerticalDivider(color = StockBorderDark, thickness = 1.dp)

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        currentDestination.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimaryDark
                                    )
                                    Surface(
                                        color = Color(0x3010B981),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(StockDownGreen))
                                            Text("台股即時連線", fontSize = 10.sp, color = StockDownGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = StockSurfaceDark
                            )
                        )
                    },
                    containerColor = StockBackgroundDark
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        MainScreenContent(
                            destination = currentDestination,
                            viewModel = viewModel,
                            onNavigateToAI = { currentDestination = AppDestination.AI_ANALYSTS },
                            onNavigateToNotes = { currentDestination = AppDestination.RESEARCH_NOTES },
                            onSelectStock = { currentDestination = AppDestination.DASHBOARD }
                        )

                        // Floating In-App Push Banner
                        AlertBannerOverlay(
                            activeBannerAlert = activeBannerAlert,
                            onDismiss = { activeBannerAlert = null },
                            onSelectStock = { sym ->
                                viewModel.selectStock(sym)
                                currentDestination = AppDestination.DASHBOARD
                                activeBannerAlert = null
                            },
                            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
                        )
                    }
                }
            }
        } else {
            // Mobile standard layout with BottomNavigationBar
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ShowChart, contentDescription = null, tint = StockPrimary, modifier = Modifier.size(20.dp))
                                Text(
                                    currentDestination.title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryDark
                                )
                            }
                        },
                        actions = {
                            Surface(
                                color = Color(0x3010B981),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(StockDownGreen))
                                    Text("即時連線", fontSize = 10.sp, color = StockDownGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = StockSurfaceDark
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = StockSurfaceDark,
                        contentColor = TextPrimaryDark,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("bottom_navigation_bar")
                    ) {
                        AppDestination.values().forEach { destination ->
                            val selected = currentDestination == destination
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentDestination = destination },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = { Text(destination.shortLabel, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = StockPrimary,
                                    selectedTextColor = StockPrimary,
                                    unselectedIconColor = TextSecondaryDark,
                                    unselectedTextColor = TextSecondaryDark,
                                    indicatorColor = StockSurfaceVariantDark
                                ),
                                modifier = Modifier.testTag("nav_item_${destination.name}")
                            )
                        }
                    }
                },
                containerColor = StockBackgroundDark
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    MainScreenContent(
                        destination = currentDestination,
                        viewModel = viewModel,
                        onNavigateToAI = { currentDestination = AppDestination.AI_ANALYSTS },
                        onNavigateToNotes = { currentDestination = AppDestination.RESEARCH_NOTES },
                        onSelectStock = { currentDestination = AppDestination.DASHBOARD }
                    )

                    // Floating In-App Push Banner
                    AlertBannerOverlay(
                        activeBannerAlert = activeBannerAlert,
                        onDismiss = { activeBannerAlert = null },
                        onSelectStock = { sym ->
                            viewModel.selectStock(sym)
                            currentDestination = AppDestination.DASHBOARD
                            activeBannerAlert = null
                        },
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertBannerOverlay(
    activeBannerAlert: IntradayAlert?,
    onDismiss: () -> Unit,
    onSelectStock: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = activeBannerAlert != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        activeBannerAlert?.let { alert ->
            AlertBannerPopup(
                alert = alert,
                onDismiss = onDismiss,
                onClick = { onSelectStock(alert.stockSymbol) }
            )
        }
    }
}

@Composable
private fun MainScreenContent(
    destination: AppDestination,
    viewModel: MainAppViewModel,
    onNavigateToAI: () -> Unit,
    onNavigateToNotes: () -> Unit,
    onSelectStock: () -> Unit
) {
    when (destination) {
        AppDestination.DASHBOARD -> DashboardScreen(
            viewModel = viewModel,
            onNavigateToAI = onNavigateToAI
        )
        AppDestination.AI_ANALYSTS -> AIAnalystsScreen(
            viewModel = viewModel,
            onViewNotes = onNavigateToNotes,
            onNavigateToChart = { _ -> onSelectStock() }
        )
        AppDestination.WATCHLIST_CHIPS -> WatchlistChipsScreen(
            viewModel = viewModel,
            onSelectStockAndGoDashboard = { _ -> onSelectStock() }
        )
        AppDestination.ALERTS_RADAR -> AlertsRadarScreen(
            viewModel = viewModel
        )
        AppDestination.RESEARCH_NOTES -> ResearchNotesScreen(
            viewModel = viewModel
        )
    }
}

@Composable
private fun AlertBannerPopup(
    alert: IntradayAlert,
    onDismiss: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StockSurfaceDark,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockTertiary)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("in_app_alert_banner")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x30F59E0B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = StockTertiary, modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚡ 盤中即時訊號預警", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StockTertiary)
                    Text(alert.dateFormatted, fontSize = 10.sp, color = TextMutedDark)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(alert.message, fontSize = 12.sp, color = TextPrimaryDark, maxLines = 2)
            }

            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "關閉", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
            }
        }
    }
}
