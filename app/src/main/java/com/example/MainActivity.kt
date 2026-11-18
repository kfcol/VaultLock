package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.VaultLockViewModel
import com.example.ui.screens.AllowlistScreen
import com.example.ui.screens.LockNowScreen
import com.example.ui.screens.SchedulesScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.VaultLockTheme

const val ROUTE_LOCK_NOW = "lock_now"
const val ROUTE_SCHEDULES = "schedules"
const val ROUTE_ALLOWLIST = "allowlist"
const val ROUTE_SETUP = "setup"

private data class NavDestinationItem(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

private val NAV_ITEMS =
  listOf(
    NavDestinationItem(
      route = ROUTE_LOCK_NOW,
      label = "Lock",
      selectedIcon = Icons.Filled.Lock,
      unselectedIcon = Icons.Outlined.Lock,
      testTag = "nav_lock_now",
    ),
    NavDestinationItem(
      route = ROUTE_SCHEDULES,
      label = "Schedules",
      selectedIcon = Icons.Filled.Schedule,
      unselectedIcon = Icons.Outlined.Schedule,
      testTag = "nav_schedules",
    ),
    NavDestinationItem(
      route = ROUTE_ALLOWLIST,
      label = "Allowlist",
      selectedIcon = Icons.Filled.Apps,
      unselectedIcon = Icons.Outlined.Apps,
      testTag = "nav_allowlist",
    ),
    NavDestinationItem(
      route = ROUTE_SETUP,
      label = "Setup",
      selectedIcon = Icons.Filled.Security,
      unselectedIcon = Icons.Outlined.Security,
      testTag = "nav_setup",
    ),
  )

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      VaultLockTheme {
        VaultLockRoot()
      }
    }
  }
}

@Composable
fun VaultLockRoot(viewModel: VaultLockViewModel = viewModel()) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val snackbarHostState = remember { SnackbarHostState() }

  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSystemStatus(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(Unit) {
    viewModel.messages.collect { message ->
      snackbarHostState.showSnackbar(message)
    }
  }

  val navigateToTab: (String) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar {
            NAV_ITEMS.forEach { item ->
              val selected =
                currentDestination?.hierarchy?.any { it.route == item.route } == true
              val showSetupBadge =
                item.route == ROUTE_SETUP && !uiState.systemStatus.canEnforceLock
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(item.route) },
                icon = {
                  BadgedBox(
                    badge = {
                      if (showSetupBadge) {
                        Badge(containerColor = MaterialTheme.colorScheme.error)
                      }
                    }
                  ) {
                    Icon(
                      imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                      contentDescription = item.label,
                    )
                  }
                },
                label = { Text(item.label) },
                modifier = Modifier.testTag(item.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavRail) {
          NavigationRail {
            NAV_ITEMS.forEach { item ->
              val selected =
                currentDestination?.hierarchy?.any { it.route == item.route } == true
              val showSetupBadge =
                item.route == ROUTE_SETUP && !uiState.systemStatus.canEnforceLock
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(item.route) },
                icon = {
                  BadgedBox(
                    badge = {
                      if (showSetupBadge) {
                        Badge(containerColor = MaterialTheme.colorScheme.error)
                      }
                    }
                  ) {
                    Icon(
                      imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                      contentDescription = item.label,
                    )
                  }
                },
                label = { Text(item.label) },
                modifier = Modifier.testTag(item.testTag),
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = ROUTE_LOCK_NOW,
          modifier = Modifier.fillMaxSize(),
        ) {
          composable(ROUTE_LOCK_NOW) {
            LockNowScreen(
              uiState = uiState,
              onDurationChange = viewModel::setDurationMinutes,
              onStartLockNow = viewModel::startLockNowSession,
              onCancelLockNow = viewModel::cancelLockNowSession,
              onOpenSetup = { navigateToTab(ROUTE_SETUP) },
              onOpenAllowlist = { navigateToTab(ROUTE_ALLOWLIST) },
            )
          }
          composable(ROUTE_SCHEDULES) {
            SchedulesScreen(
              uiState = uiState,
              onSaveSchedule = viewModel::saveSchedule,
              onToggleSchedule = viewModel::toggleScheduleEnabled,
              onDeleteSchedule = viewModel::deleteSchedule,
            )
          }
          composable(ROUTE_ALLOWLIST) {
            AllowlistScreen(
              uiState = uiState,
              onSelectScope = viewModel::setAllowlistTab,
              onSearchQueryChange = viewModel::setAllowlistSearchQuery,
              onToggleShowSystemApps = viewModel::setShowSystemApps,
              onToggleShowOnlyAllowed = viewModel::setShowOnlyAllowed,
              onToggleAppAllowed = viewModel::toggleAppAllowed,
              onSelectAll = viewModel::selectAllFilteredApps,
              onSelectNone = viewModel::selectNoApps,
              onCopyAllowlist = viewModel::copyAllowlist,
            )
          }
          composable(ROUTE_SETUP) {
            SetupScreen(
              uiState = uiState,
              onRefreshStatus = { viewModel.refreshSystemStatus(context) },
              onSetSeparateAllowlists = viewModel::setSeparateAllowlists,
              onSetLockAtScheduleStart = viewModel::setLockAtScheduleStart,
              onSetPreferDeviceAdminLock = viewModel::setPreferDeviceAdminLock,
              onSetCooldownMillis = viewModel::setCooldownMillis,
            )
          }
        }
      }
    }
  }
}
