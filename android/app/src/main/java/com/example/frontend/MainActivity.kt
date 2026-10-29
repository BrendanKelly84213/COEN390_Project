package com.example.frontend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.frontend.ui.theme.FrontendTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrontendTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SensorDataScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

data class SensorData(
    val temperatureC: Double,
    val humidityPct: Double,
    val timestamp: String
)

suspend fun fetchSensorData(baseUrl: String): SensorData? = withContext(Dispatchers.IO) {
    try {

        val url = URL("$baseUrl/api/v1/sensor_data")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            SensorData(
                temperatureC = json.getDouble("temperature_c"),
                humidityPct = json.getDouble("humidity_pct"),
                timestamp = json.getString("timestamp")
            )
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun SensorDataScreen(modifier: Modifier = Modifier) {
    var sensorData by remember { mutableStateOf<SensorData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // This will need to be changed, just the local ip address got from ipconfig on windows.
    val serverUrl = "http://132.205.95.30:8000"

    fun loadData() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            val data = fetchSensorData(serverUrl)
            if (data != null) {
                sensorData = data
            } else {
                errorMessage = "Failed to load sensor data"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading sensor data...")
        } else if (errorMessage != null) {
            Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { loadData() }) {
                Text("Retry")
            }
        } else if (sensorData != null) {
            Text(
                text = "Sensor Readings",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Temperature: ${sensorData!!.temperatureC} °C", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Humidity: ${sensorData!!.humidityPct} %", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Last Updated: ${sensorData!!.timestamp}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { loadData() }) {
                Text("Refresh")
            }
        }
    }
}
