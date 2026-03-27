package it.sapienza.smartpantry.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.imageLoader
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.smartpantry.R
import it.sapienza.smartpantry.model.User
import it.sapienza.smartpantry.model.DietViewModel
import it.sapienza.smartpantry.ui.screens.DietScreen
import it.sapienza.smartpantry.ui.screens.HomeScreen
import it.sapienza.smartpantry.ui.screens.PantryScreen
import it.sapienza.smartpantry.ui.screens.ProfileScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val user = intent.getParcelableExtra<User>("user_extra") ?: User()

        setContent {
            SmartPantryMainTheme {
                MainScreen(
                    initialUser = user,
                    onLogout = {
                        FirebaseAuth.getInstance().signOut()
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun SmartPantryMainTheme(content: @Composable () -> Unit) {
    val darkGreenBg = Color(0xFF0A120E)
    val neonGreen = Color(0xFF00E676)
    val surfaceColor = Color(0xFF1A2421)

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = darkGreenBg,
            surface = surfaceColor,
            primary = neonGreen,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color.Gray
        ),
        content = content
    )
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
fun MainScreen(initialUser: User, onLogout: () -> Unit, dietViewModel: DietViewModel = viewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var user by remember { mutableStateOf(initialUser) }
    val bottomItems = listOf(Screen.Home, Screen.Pantry, Screen.ShopList, Screen.Diet, Screen.Stats)
    val neonGreen = Color(0xFF00E676)
    val unselectedGrey = Color.Gray

    // Prefetch profile image to avoid delay in ProfileScreen
    val context = LocalContext.current
    LaunchedEffect(user.uid, user.profileImageUrl) {
        if (user.uid.isNotBlank()) {
            dietViewModel.initialize(user.uid)
        }
        if (user.profileImageUrl.isNotEmpty()) {
            val request = ImageRequest.Builder(context)
                .data(user.profileImageUrl)
                .build()
            context.imageLoader.enqueue(request)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout",
                            tint = unselectedGrey
                        )
                    }
                },
                actions = {
                    val isNotificationsSelected = currentDestination?.hierarchy?.any { it.route == Screen.Notifications.route } == true
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
                            contentDescription = stringResource(id = Screen.Notifications.titleRes),
                            tint = if (isNotificationsSelected) neonGreen else unselectedGrey
                        )
                    }
                    val isProfileSelected = currentDestination?.hierarchy?.any { it.route == Screen.Profile.route } == true
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
                            contentDescription = stringResource(id = Screen.Profile.titleRes),
                            tint = if (isProfileSelected) neonGreen else unselectedGrey
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                bottomItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = {
                            Icon(
                                painterResource(id = screen.iconRes),
                                contentDescription = null,
                                tint = if (selected) neonGreen else unselectedGrey
                            )
                        },
                        label = {
                            Text(
                                stringResource(id = screen.titleRes),
                                fontSize = 10.sp,
                                color = if (selected) neonGreen else unselectedGrey
                            )
                        },
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
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = neonGreen,
                            selectedTextColor = neonGreen,
                            unselectedIconColor = unselectedGrey,
                            unselectedTextColor = unselectedGrey,
                            indicatorColor = Color.Transparent // Rimuove il cerchio di selezione di default di M3
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) { HomeScreen(user) }
                composable(Screen.Pantry.route) { PantryScreen(
                    uid = user.uid,
                    onOpenSearchFood = {
                        val intent = Intent(context, SearchFoodActivity::class.java)
                        intent.putExtra(SearchFoodActivity.EXTRA_UID, user.uid)
                        context.startActivity(intent)
                    }
                ) }
                composable(Screen.ShopList.route) { PlaceholderScreen(stringResource(id = R.string.text_shop_list_screen)) }
                composable(Screen.Diet.route) { DietScreen(uid = user.uid, dietViewModel = dietViewModel) }
                composable(Screen.Stats.route) { PlaceholderScreen(stringResource(id = R.string.text_stats_screen)) }
                composable(Screen.Profile.route) {
                    ProfileScreen(
                        user = user,
                        onUserUpdate = { updatedUser ->
                            user = updatedUser
                        }
                    )
                }
                composable(Screen.Notifications.route) { PlaceholderScreen(stringResource(id = R.string.title_notifications)) }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.headlineMedium, color = Color.White)
    }
}
