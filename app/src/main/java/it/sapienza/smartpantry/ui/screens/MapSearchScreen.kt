package it.sapienza.smartpantry.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import it.sapienza.smartpantry.model.NearbySupermarket
import it.sapienza.smartpantry.model.NearbySupermarketsRequest
import it.sapienza.smartpantry.model.NearbySupermarketsResponse
import it.sapienza.smartpantry.service.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun MapSearchScreen() {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    var supermarkets by remember { mutableStateOf<List<NearbySupermarket>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val isFineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val isCoarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            
            if (isFineLocationGranted || isCoarseLocationGranted) {
                fetchLocationAndSupermarkets(fusedLocationClient, { supermarkets = it }, { isLoading = it }, { errorMessage = it })
            } else {
                permissionDenied = true
            }
        }
    )

    LaunchedEffect(Unit) {
        val finePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        
        if (finePermission == PackageManager.PERMISSION_GRANTED || coarsePermission == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndSupermarkets(fusedLocationClient, { supermarkets = it }, { isLoading = it }, { errorMessage = it })
        } else {
            launcher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A120E))) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Nearby Supermarkets",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E676))
                }
            } else if (permissionDenied) {
                ErrorMessage("Location permission is required to find nearby supermarkets.")
            } else if (errorMessage != null) {
                ErrorMessage(errorMessage!!)
            } else if (supermarkets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No supermarkets found nearby.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(supermarkets) { supermarket ->
                        SupermarketItem(supermarket)
                    }
                }
            }
        }
        
        FloatingActionButton(
            onClick = {
                fetchLocationAndSupermarkets(fusedLocationClient, { supermarkets = it }, { isLoading = it }, { errorMessage = it })
            },
            containerColor = Color(0xFF00E676),
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Black)
        }
    }
}

@Composable
fun SupermarketItem(supermarket: NearbySupermarket) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2421)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = supermarket.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                
                supermarket.openNow?.let { isOpen ->
                    Surface(
                        color = if (isOpen) Color(0xFF00E676).copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isOpen) "OPEN" else "CLOSED",
                            color = if (isOpen) Color(0xFF00E676) else Color.Red,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = supermarket.address, color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "${(supermarket.distance / 1000).format(1)} km away", color = Color(0xFF00E676), fontWeight = FontWeight.Medium)
                if (supermarket.rating != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
                        Text(text = " ${supermarket.rating}", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, color = Color.Red, modifier = Modifier.padding(16.dp))
    }
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)

@SuppressLint("MissingPermission")
private fun fetchLocationAndSupermarkets(
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    onSuccess: (List<NearbySupermarket>) -> Unit,
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    onLoading(true)
    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener { location ->
            if (location != null) {
                val request = NearbySupermarketsRequest(location.latitude, location.longitude)
                RetrofitClient.instance.getNearbySupermarkets(request).enqueue(object : Callback<NearbySupermarketsResponse> {
                    override fun onResponse(call: Call<NearbySupermarketsResponse>, response: Response<NearbySupermarketsResponse>) {
                        onLoading(false)
                        if (response.isSuccessful) {
                            onSuccess(response.body()?.results ?: emptyList())
                        } else {
                            onError("Failed to fetch supermarkets from server.")
                        }
                    }

                    override fun onFailure(call: Call<NearbySupermarketsResponse>, t: Throwable) {
                        onLoading(false)
                        onError("Network error: ${t.message}")
                    }
                })
            } else {
                onLoading(false)
                onError("Unable to retrieve current location.")
            }
        }
        .addOnFailureListener {
            onLoading(false)
            onError("Error getting location: ${it.message}")
        }
}
