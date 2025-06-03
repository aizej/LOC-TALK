package com.example.test

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.test.ui.theme.TESTTheme
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GroundOverlay
import com.google.maps.android.compose.GroundOverlayPosition
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.net.ConnectException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.exp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


class MainActivity : ComponentActivity() {

    val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted.
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted.
            } else -> {
            // No location access granted.

            }
        }
    }
    val messages_fetching_tick_duration: Duration = 2000.milliseconds
    val server_ip = "158.101.167.252"
    private val OkHttpclient = OkHttpClient()
    var last_lat : String = "0.0"
    var last_lon : String = "0.0"

    var e_message_request = "Cannot fetch messages :( Error: "
    var e_message_post = "Cannot post :( Error: "
    var e_no_location = "Try to turn on location!"
    var e_cant_connect_to_server = "Server may be ofline. Or your app version is too old!"
    var e_wrong_location_response = "Try to install the newest version of the app! And stay in the zone: Czechia."
    var e_message_too_long_response = "Your message was too long!"
    var e_server_not_reachable = "Server not reachable! Check internet connection!"
    val e_map_request_fail = "Map fetching error:"
    val e_map_needs_gps = "You need gps to look at the activity map"

    private val activityJob = SupervisorJob()
    private val activityScope = CoroutineScope(Dispatchers.Main + activityJob)

    var screen_on_map = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        enableEdgeToEdge()
        setContentView(R.layout.activity_main)


        val locationHelper = Location(this)
        val locationTextView: TextView = findViewById(R.id.messagesTextView)
        val sendButton: Button = findViewById(R.id.send_button)
        val messageTextWindow : EditText = findViewById(R.id.message_text)
        val timeTextView : TextView = findViewById(R.id.textView_time)
        val composeButton: Button = findViewById(R.id.compose_button)



        locationTextView.movementMethod = ScrollingMovementMethod() // for some reason this disables copying
        locationTextView.setTextIsSelectable(true)


        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET
        ))

        val hasLocationPermissions = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED


        if (hasLocationPermissions == false){
            locationTextView.text = "No location permisions!"
        }
        else{
            locationTextView.text = "Waiting for location!"
        }



        sendButton.setOnClickListener {
            val message = messageTextWindow.text.toString().replace("\n", "")
            val time = (System.currentTimeMillis()).toLong()

            if (message.length > 250) {
                val text = "Message too long:${message.length} max=250"
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (message == ""){
                val text = "Type something worth sending!"
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            send_message_and_updatewiew(message, arrayOf(last_lat, last_lon), time)
            messageTextWindow.text.clear()
        }

        locationHelper.getCurrentLocation{ latitude, longitude ->
            //locationTextView.text = "${latitude}, ${longitude}"
            //requestMessages_and_updatewiew(arrayOf(latitude,longitude))//server request only when we get new location
            last_lat = latitude
            last_lon = longitude
        }

        val chat_activity = activityScope.launch {
            while (isActive) {
                if (last_lat != "0.0" && last_lon != "0.0") {
                    requestMessages_and_updatewiew(arrayOf(last_lat, last_lon))
                }
                else{
                    locationTextView.text = e_no_location
                }
                timeTextView.text = convertUnixTimestampToHHmm(System.currentTimeMillis())

                delay(messages_fetching_tick_duration)
            }
        }


        composeButton.setOnClickListener {
            if (last_lat != "0.0" && last_lon != "0.0")
            {
                screen_on_map = true
                chat_activity.cancel()
                // Switch from View-based UI to Compose-based UI
                setContent {
                    TESTTheme {
                        val activity = LocalContext.current as? Activity
                        Column {
                            Spacer(modifier = Modifier.height(50.dp))
                            Button(onClick = {
                                screen_on_map = false
                                // Return to original activity UI
                                activity?.recreate() // restarts activity with the original layout and that causes issues
                                //activity?.setContentView(R.layout.activity_main)
                            }) {
                                Text("Back to chat")
                            }
                            val location = remember { mutableStateOf(LatLng(last_lat.toDouble(), last_lon.toDouble())) }

                            val cameraPositionState = rememberCameraPositionState {
                                position = CameraPosition.fromLatLngZoom(location.value, 15f)
                            }
                            val activityMap = remember { mutableStateOf(emptyArray<Array<Double>>()) }
                            var good_result = remember {mutableStateOf(false)}



                            LaunchedEffect(screen_on_map) {
                                while (screen_on_map) {
                                    requestMap(arrayOf(last_lat, last_lon)) { result ->
                                        if (result.startsWith("ERROR")) {
                                            Toast.makeText(this@MainActivity, "$e_map_request_fail$result", Toast.LENGTH_LONG).show()
                                            good_result.value = false
                                        } else {
                                            good_result.value = true
                                            activityMap.value = parseJsonTo2DDoubleArray(result)
                                        }
                                    }
                                    delay(30_000) // 30 seconds
                                }
                            }


                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = cameraPositionState
                            ) {
                                Marker(
                                    state = MarkerState(position = location.value),
                                    title = "Your location",
                                    snippet = "This is where you are"
                                )



                                if (good_result.value) {
                                    for (info in activityMap.value) {
                                        val latLng = LatLng(info[0], info[1])
                                        val transparency = (0.9*(exp(-0.3*info[2]))).toFloat()

                                        GroundOverlay(
                                            position = GroundOverlayPosition.create(
                                                latLng,
                                                100f, // width in meters
                                                100f  // height in meters
                                            ),
                                            image = BitmapDescriptorFactory.fromResource(R.drawable.blue_square),
                                            transparency = transparency, // 0 = opaque, 1 = invisible
                                            zIndex = 1f
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else
            {
                Toast.makeText(this@MainActivity, e_map_needs_gps, Toast.LENGTH_LONG).show()
            }
        }




    }
    override fun onDestroy() {
        super.onDestroy()
        activityJob.cancel()
    }



    private fun request_messages_raw(location: Array<String> ): String {

        val request = Request.Builder()
            .url("http://${server_ip}:5000/files?lat=${location[0]}&lon=${location[1]}")
            .method("GET", null)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        try {
            OkHttpClient().newCall(request).execute().use { response ->
                return check_for_error(response)
            }

        } catch (e: ConnectException) {
            Log.e("request_tag", "Connection failed: ${e.message}")
            return "ERROR: ${e_server_not_reachable} \n ${e.message}"
        } catch (e: Exception) {
            Log.e("request_tag", "An error occurred: ${e.message}")
            return "ERROR: ${e.message}"
        }
    }

    private fun request_map_raw(location: Array<String> ): String {

        val request = Request.Builder()
            .url("http://${server_ip}:5000/map?lat=${location[0]}&lon=${location[1]}")
            .method("GET", null)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        try {
            OkHttpClient().newCall(request).execute().use { response ->
                return check_for_error(response)
            }

        } catch (e: ConnectException) {
            Log.e("request_map_tag", "Connection failed: ${e.message}")
            return "ERROR: ${e_server_not_reachable} \n ${e.message}"
        } catch (e: Exception) {
            Log.e("request_mag_tag", "An error occurred: ${e.message}")
            return "ERROR: ${e.message}"
        }
    }

    private suspend fun request_messages_in_diferent_thread(location: Array<String>): String {
        // This runs on a background thread (IO dispatcher)
        return withContext(Dispatchers.IO) {
            // Simulate network request here (replace with actual network code)
            // For example, using OkHttp, HttpURLConnection, etc.
            try {
                request_messages_raw(location)
            } catch (e: Exception) {
                e.printStackTrace()
                "Network request failed"
            }
        }
    }


    private suspend fun request_map_in_diferent_thread(location: Array<String>): String {
        // This runs on a background thread (IO dispatcher)
        return withContext(Dispatchers.IO) {
            // Simulate network request here (replace with actual network code)
            // For example, using OkHttp, HttpURLConnection, etc.
            try {
                request_map_raw(location)
            } catch (e: Exception) {
                e.printStackTrace()
                "Network request failed"
            }
        }
    }


    private fun requestMap(location: Array<String>, onResult: (String) -> Unit) {
        activityScope.launch {
            try {
                val result = request_map_in_diferent_thread(location)
                onResult(result)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult("ERROR: ${e.message}")
            }
        }
    }

    private fun requestMessages_and_updatewiew(location: Array<String>){
        activityScope.launch {
            try {
                // Call the suspend function and wait for its result
                var result = request_messages_in_diferent_thread(location)
                val locationTextView: TextView = findViewById(R.id.messagesTextView)
                locationTextView.movementMethod = ScrollingMovementMethod() // for some reason this disables copying
                locationTextView.setTextIsSelectable(true)

                val scrollY = locationTextView.scrollY
                if (result.startsWith("ERROR")){
                    locationTextView.text = e_message_request + result
                }
                else{
                    locationTextView.text = get_response_to_text(result)
                    locationTextView.post {
                        locationTextView.scrollTo(0, scrollY)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // Handle any errors, e.g., show a message to the user
            }
        }
    }

    private fun get_response_to_text(result: String): CharSequence {
        // Your input string
        val jsonString = result

        // Parse the string into a JSONArray
        val jsonArray = JSONArray(jsonString)

        // Create a list to hold the results
        val resultList = mutableListOf<Pair<Long, String>>()

        // Iterate over the JSONArray
        for (i in 0 until jsonArray.length()) {
            val innerArray = jsonArray.getJSONArray(i)

            // Get the values and add them as a Pair to the resultList
            val number = innerArray.getLong(0) // Get the first element (Int)
            val text = innerArray.getString(1) // Get the second element (String)
            resultList.add(Pair(number, text))
        }
        val sortedList = resultList.sortedBy { it.first }
        val combinedString = sortedList.joinToString("\n") { "${convertUnixTimestampToHHmm(it.first)}\n${it.second}" }

        // Print the result
        return combinedString
    }



    private fun send_message_and_updatewiew(message: String , location: Array<String> ,time: Long){
        activityScope.launch {
            try {
                // Call the suspend function and wait for its result
                var result = send_message_in_diferent_thread(message=message, location = location, time=time)

                if (result.startsWith("ERROR")){
                    val locationTextView: TextView = findViewById(R.id.messagesTextView)
                    locationTextView.movementMethod = ScrollingMovementMethod() // for some reason this disables copying
                    locationTextView.setTextIsSelectable(true)
                    locationTextView.text = e_message_post +  result
                    Toast.makeText(this@MainActivity, e_message_post, Toast.LENGTH_SHORT).show()
                }
                else{
                    val text = "Message sent!"
                    Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
                }


            } catch (e: Exception) {
                e.printStackTrace()
                // Handle any errors, e.g., show a message to the user
            }
        }
    }



    private suspend fun send_message_in_diferent_thread(message: String , location: Array<String> ,time: Long): String
    {
        // This runs on a background thread (IO dispatcher)
        return withContext(Dispatchers.IO) {
            // Simulate network request here (replace with actual network code)
            // For example, using OkHttp, HttpURLConnection, etc.
            try {
                send_message_raw(message=message, location = location, time=time)
            } catch (e: Exception) {
                e.printStackTrace()
                "ERROR Network request failed"
            }
        }
    }


    private fun send_message_raw(message: String, location: Array<String>, time: Long): String {

        val formBody = FormBody.Builder()
            .add("lat", location[0])
            .add("lon", location[1])
            .add("time", time.toString())
            .add("content", message)
            .build()

        val request = Request.Builder()
            .url("http://${server_ip}:5000/update")
            .post(formBody)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()
        try {
            OkHttpclient.newCall(request).execute().use { response ->
                return check_for_error(response)
            }
        }

        catch (e: ConnectException) {
        Log.e("request_tag", "Connection failed: ${e.message}")
        return "ERROR: ${e_server_not_reachable} \n ${e.message}"
        } catch (e: Exception) {
        Log.e("request_tag", "An error occurred: ${e.message}")
        return "ERROR: ${e.message}"
        }
    }

    fun convertUnixTimestampToHHmm(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

        return format.format(date)
    }

    fun check_for_error(response: Response): String{
        when (response.code) {
            400 -> {
                Log.w("request_tag", "ERROR 400: Bad Request")
                return "ERROR 400: ${e_wrong_location_response}\n ${response}"
            }

            401 -> {
                Log.w("request_tag", "ERROR 401: Unauthorized")
                return "ERROR 401: ${e_message_too_long_response}"
            }

            in 400..499 -> {
                Log.w("request_tag", "Client Error: ${response.code}")
                return "ERROR Client Error: ${response.code}"
            }

            in 500..599 -> {
                Log.w("request_tag", "Server Error: ${response.code}")
                return "ERROR Server Error: ${response.code}"
            }

            else -> {
                if (!response.isSuccessful) {
                    Log.w("request_tag", "ERROR: ${response}")
                    return "ERROR: ${e_cant_connect_to_server}\n${response}"
                } else {
                    Log.w("request_tag", "Response: ${response}")
                    return response.body!!.string()

                }
            }
        }
    }
}


fun parseJsonTo2DDoubleArray(json: String): Array<Array<Double>> {
    val outerArray = JSONArray(json)
    return Array(outerArray.length()) { i ->
        val innerArray = outerArray.getJSONArray(i)
        Array(3) { j ->
            innerArray.getDouble(j)
        }
    }
}




