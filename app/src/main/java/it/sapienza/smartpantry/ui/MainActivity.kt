package it.sapienza.smartpantry.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.smartpantry.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                MainScreen(onLogout = {
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                })
            }
        }
    }
}

sealed class Screen(val route: String, val titleRes: Int, val iconRes: Int) {
    object Home : Screen("home", R.string.title_home, R.drawable.home_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
    object Pantry : Screen("pantry", R.string.title_pantry, R.drawable.briefcase_meal_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
    object ShopList : Screen("list", R.string.title_shop_list, R.drawable.list_alt_add_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
    object Diet : Screen("diet", R.string.title_diet, R.drawable.menu_book_2_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
    object Stats : Screen("stats", R.string.title_stats, R.drawable.bar_chart_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
    object Profile : Screen("profile", R.string.title_profile, R.drawable.account_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
    object Notifications : Screen("notifications", R.string.title_notifications, R.drawable.notifications_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onLogout: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val bottomItems = listOf(Screen.Home, Screen.Pantry, Screen.ShopList, Screen.Diet, Screen.Stats)
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        if (currentDestination?.route != Screen.Notifications.route) {
                            navController.navigate(Screen.Notifications.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }) {
                        Icon(
                            painter = painterResource(id = Screen.Notifications.iconRes),
                            contentDescription = stringResource(id = Screen.Notifications.titleRes)
                        )
                    }
                    IconButton(onClick = { 
                        if (currentDestination?.route != Screen.Profile.route) {
                            navController.navigate(Screen.Profile.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }) {
                        Icon(
                            painter = painterResource(id = Screen.Profile.iconRes),
                            contentDescription = stringResource(id = Screen.Profile.titleRes)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                bottomItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = { Icon(painterResource(id = screen.iconRes), contentDescription = null) },
                        label = { Text(stringResource(id = screen.titleRes)) },
                        selected = selected,
                        onClick = {
                            if (currentDestination?.route != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { PlaceholderScreen(stringResource(id = R.string.text_home_screen)) }
            composable(Screen.Pantry.route) { PlaceholderScreen(stringResource(id = R.string.text_pantry_screen)) }
            composable(Screen.ShopList.route) { PlaceholderScreen(stringResource(id = R.string.text_shop_list_screen)) }
            composable(Screen.Diet.route) { PlaceholderScreen(stringResource(id = R.string.text_diet_screen)) }
            composable(Screen.Stats.route) { PlaceholderScreen(stringResource(id = R.string.text_stats_screen)) }
            composable(Screen.Profile.route) { PlaceholderScreen(stringResource(id = R.string.text_profile_screen)) }
            composable(Screen.Notifications.route) { PlaceholderScreen(stringResource(id = R.string.title_notifications)) }
        }
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.headlineMedium)
    }
}