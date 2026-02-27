package it.sapienza.smartpantry.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.smartpantry.R
import it.sapienza.smartpantry.model.User
import it.sapienza.smartpantry.model.Biometrics
import it.sapienza.smartpantry.model.Goals
import it.sapienza.smartpantry.model.UpdateUserResponse
import it.sapienza.smartpantry.service.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val user = intent.getParcelableExtra<User>("user_extra") ?: User()

        setContent {
            MaterialTheme {
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
fun MainScreen(initialUser: User, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    var user by remember { mutableStateOf(initialUser) }

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

@Composable
fun ProfileScreen(user: User, onUserUpdate: (User) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    
    var name by remember { mutableStateOf(user.name) }
    var age by remember { mutableStateOf(user.biometrics.age.toString()) }
    var height by remember { mutableStateOf(user.biometrics.height.toString()) }
    var weight by remember { mutableStateOf(user.biometrics.weight.toString()) }
    var gender by remember { mutableStateOf(user.biometrics.gender) }
    var activityLevel by remember { mutableStateOf(user.biometrics.activityLevel) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "User Profile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isEditing) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Height (cm)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (kg)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = gender, onValueChange = { gender = it }, label = { Text("Gender (m/f)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = activityLevel, onValueChange = { activityLevel = it }, label = { Text("Activity Level (e.g. 1.2)") }, modifier = Modifier.fillMaxWidth())
                } else {
                    ProfileInfoRow(label = "Name", value = user.name)
                    ProfileInfoRow(label = "Email", value = user.email)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    ProfileInfoRow(label = "Age", value = user.biometrics.age.toString())
                    ProfileInfoRow(label = "Gender", value = user.biometrics.gender)
                    ProfileInfoRow(label = "Height", value = "${user.biometrics.height} cm")
                    ProfileInfoRow(label = "Weight", value = "${user.biometrics.weight} kg")
                    ProfileInfoRow(label = "Activity", value = user.biometrics.activityLevel)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(text = "Daily Goals", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    ProfileInfoRow(label = "Daily Kcal", value = "${user.goals.dailyKcal} kcal")
                    user.goals.macrosTarget.forEach { (macro, value) ->
                        ProfileInfoRow(label = macro.replaceFirstChar { it.uppercase() }, value = "$value g")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isEditing) {
                    val updatedUser = user.copy(
                        name = name,
                        biometrics = user.biometrics.copy(
                            age = age.toIntOrNull() ?: user.biometrics.age,
                            height = height.toDoubleOrNull() ?: user.biometrics.height,
                            weight = weight.toDoubleOrNull() ?: user.biometrics.weight,
                            gender = gender,
                            activityLevel = activityLevel
                        )
                    )
                    
                    // Chiamata al backend per salvare i cambiamenti
                    RetrofitClient.instance.updateUser(updatedUser).enqueue(object : Callback<UpdateUserResponse> {
                        override fun onResponse(call: Call<UpdateUserResponse>, response: Response<UpdateUserResponse>) {
                            if (response.isSuccessful) {
                                val body = response.body()
                                body?.let {
                                    val finalUser = updatedUser.copy(
                                        goals = updatedUser.goals.copy(
                                            dailyKcal = it.dailyKcal,
                                            macrosTarget = it.macros
                                        )
                                    )
                                    onUserUpdate(finalUser)
                                }
                            } else {
                                Log.e("UPDATE_ERROR", "Code: ${response.code()}")
                            }
                        }

                        override fun onFailure(call: Call<UpdateUserResponse>, t: Throwable) {
                            Log.e("UPDATE_ERROR", "Failure: ${t.message}")
                        }
                    })
                }
                isEditing = !isEditing
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isEditing) "Save Changes" else "Edit Profile")
        }
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Text(text = value)
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.headlineMedium)
    }
}