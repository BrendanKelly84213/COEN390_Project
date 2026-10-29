package com.example.frontend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

data class SensorReading(
    val id: String,
    val busNum: Int,
    val address: String,
    val temperatureC: Double,
    val humidityPct: Double
)

data class SensorBundle(
    val sensors: List<SensorReading>,
    val timestamp: String
)

suspend fun fetchSensorData(baseUrl: String): SensorBundle? = withContext(Dispatchers.IO) {
    try {
        val url = URL("$baseUrl/api/v1/sensor_data")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            
            val sensorsArray = json.getJSONArray("sensors")
            val sensorsList = mutableListOf<SensorReading>()
            
            for (i in 0 until sensorsArray.length()) {
                val sensorObj = sensorsArray.getJSONObject(i)
                sensorsList.add(
                    SensorReading(
                        id = sensorObj.getString("id"),
                        busNum = sensorObj.getInt("bus_num"),
                        address = sensorObj.getString("address"),
                        temperatureC = sensorObj.getDouble("temperature_c"),
                        humidityPct = sensorObj.getDouble("humidity_pct")
                    )
                )
            }
            
            SensorBundle(
                sensors = sensorsList,
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
    var sensorBundle by remember { mutableStateOf<SensorBundle?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // This will need to be changed, just the local ip address got from ipconfig on windows.
    val serverUrl = "http://172.30.109.101:8000"

    fun loadData() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            val data = fetchSensorData(serverUrl)
            if (data != null) {
                sensorBundle = data
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
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = if (sensorBundle == null) Arrangement.Center else Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading && sensorBundle == null) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading sensor data...")
        } else if (errorMessage != null && sensorBundle == null) {
            Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { loadData() }) {
                Text("Retry")
            }
        } else if (sensorBundle != null) {
            Text(
                text = "Sensor Readings",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Last Updated: ${sensorBundle!!.timestamp}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            sensorBundle!!.sensors.forEach { sensor ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Sensor: ${sensor.id}", 
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Bus: ${sensor.busNum} | Address: ${sensor.address}", 
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Temperature: ${sensor.temperatureC} °C", 
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Humidity: ${sensor.humidityPct} %", 
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { loadData() },
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Refreshing..." else "Refresh")
            }
        }
    }
}
