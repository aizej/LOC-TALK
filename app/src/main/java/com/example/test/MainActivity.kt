package com.example.test

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.test.ui.theme.TESTTheme
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONArray
import java.net.ConnectException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)


        val locationHelper = Location(this)
        val locationTextView: TextView = findViewById(R.id.messagesTextView)
        val sendButton: Button = findViewById(R.id.send_button)
        val messageTextWindow : EditText = findViewById(R.id.message_text)
        val timeTextView : TextView = findViewById(R.id.textView_time)


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

        val timer = object: CountDownTimer(1000*60*60*2, 2000) {
            override fun onTick(millisUntilFinished: Long) {
                if (last_lat != "0.0" && last_lon != "0.0") {
                    requestMessages_and_updatewiew(arrayOf(last_lat, last_lon))
                }
                else{
                    locationTextView.text = e_no_location
                }
                timeTextView.text = convertUnixTimestampToHHmm(System.currentTimeMillis())
            }
            override fun onFinish() {
                // do something
            }
        }
        timer.start()
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

    private fun requestMessages_and_updatewiew(location: Array<String>){
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Call the suspend function and wait for its result
                var result = request_messages_in_diferent_thread(location)
                if (result.startsWith("ERROR")){
                    val locationTextView: TextView = findViewById(R.id.messagesTextView)
                    locationTextView.text = e_message_request + result
                }
                else{
                    val locationTextView: TextView = findViewById(R.id.messagesTextView)
                    locationTextView.text = get_response_to_text(result)
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
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Call the suspend function and wait for its result
                var result = send_message_in_diferent_thread(message=message, location = location, time=time)

                if (result.startsWith("ERROR")){
                    val locationTextView: TextView = findViewById(R.id.messagesTextView)
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



    private suspend fun send_message_in_diferent_thread(message: String , location: Array<String> ,time: Long): String{
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
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
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

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TESTTheme {
        Greeting("Android")
    }
}


