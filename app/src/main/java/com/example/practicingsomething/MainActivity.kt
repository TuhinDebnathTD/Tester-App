package com.example.practicingsomething
import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Settings.Secure
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.practicingsomething.databinding.ActivityMainBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private lateinit var batteryReceiver: BatteryReceiver
    private lateinit var viewModel: MyViewModel

    // for database
    private lateinit var database:DatabaseReference
    //
    var imei:String? = null
    var internetConnectivityStatus:String? = null
    var chargingStatus:String? = null
    var chargePercent:String? = null
    var locationLatitude:String? = null
    var locationLongitude:String? = null
    var imageUri:String? = null

    private val batteryLevelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                context?.registerReceiver(null, ifilter)
            }
            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            if (isCharging) {
                chargingStatus = "true"
                binding.textViewCharging.text = "Charging state: Charger is connected"
            } else {
                chargingStatus = "false"
                binding.textViewCharging.text = "Charging state: Charger is Not connected"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MyViewModel::class.java]
        database = FirebaseDatabase.getInstance().getReference("Data")
        val data = Data(
            imei,
            internetConnectivityStatus,
            chargingStatus,
            chargePercent,
            locationLatitude,
            locationLongitude,
            imageUri
        )
        database.child("UserData").setValue(data).addOnSuccessListener {
           Toast.makeText(this,"Successfully saved data in the firebase",Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this,"Failed in saving the the data to the database",Toast.LENGTH_SHORT).show()
        }

        ///code for timestamp
        val simpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timer = Timer()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    binding.textViewTimestamp.text = "Time: " +simpleDateFormat.format(Date())
                }
            }
        }, 0, 1000)
        ///CODE for IMEI
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                101
            )
        } else {
            displayIMEI()
        }
        viewModel.data.observe(this) { myData ->
            myData?.let {
                // on below line we are updating our data.
                imei = data.imei
                internetConnectivityStatus = data.internetConnectivityStatus
                chargingStatus = data.chargingStatus
                chargePercent = data.chargePercent
                locationLongitude = data.locationLongitude
                locationLatitude = data.locationLongitude
                imageUri = data.imageUri
            }
        }
        ///CODE for location
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
         // Do something with the updated location

                val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                locationLatitude = location.latitude.toString()
                locationLongitude = location.longitude.toString()
                if (addresses != null) {
                    if (addresses.isNotEmpty()) {
                        val address = addresses?.get(0)
                        if (address != null) {
                            //Log.d("Location", "Area Name: ${address.getAddressLine(0)}")
                            binding.textViewLocation.text = "Location :"+address.getAddressLine(0)
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Geocoder service is not available.", Toast.LENGTH_SHORT).show()

                    }
                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        requestLocationUpdates()

        ///CODE for camera
        binding.buttonCapture.setOnClickListener {
            takePicture()
        }

        batteryReceiver = BatteryReceiver()
        val batteryStatusFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, batteryStatusFilter)


        ///code for network connectivity status
        if (isConnected()) {
            //Toast.makeText(this, "Connected to the Internet", Toast.LENGTH_SHORT).show()
            internetConnectivityStatus = "true"
            binding.textViewConnectivity.text = "Internet Connectivity Status: Connected"
        } else {
            //Toast.makeText(this, "No Internet Connection", Toast.LENGTH_SHORT).show()
            internetConnectivityStatus = "false"
            binding.textViewConnectivity.text = "Internet Connectivity Status: Not Connected"
        }

        ///code for chraging status
        registerReceiver(batteryLevelReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        //This button is for initialization of data
        binding.buttonInitiation.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            Handler().postDelayed({
                binding.progressBar.visibility = View.GONE
            }, 3000)
            binding.imageView.setImageBitmap(null)
        }

    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(batteryLevelReceiver)
    }

    private inner class BatteryReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val batteryPercentage = level * 100 / scale
            chargePercent = "$batteryPercentage"
            binding.textViewChargePercent.text = "Charging %: "+batteryPercentage.toString()
        }
    }
    private fun isConnected(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }
    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
            return
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, locationListener)
    }



    private fun takePicture() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            val bitmap = data?.extras?.get("data") as Bitmap
                binding.imageView.setImageBitmap(bitmap)
            //
            val baos = ByteArrayOutputStream()
            imageUri = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos).toString()

        }
    }
    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // If the permission is granted, call the displayIMEI method
            displayIMEI()
        }
        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestLocationUpdates()
        }
    }

     fun displayIMEI() {

         if (ActivityCompat.checkSelfPermission(
                 this,
                 Manifest.permission.READ_PHONE_STATE
             ) != PackageManager.PERMISSION_GRANTED
         ) {

             return
         }
             imei = Settings.Secure.getString(this.contentResolver,Secure.ANDROID_ID)
             binding.textViewIMEI.text ="Imei:"+ imei.toString()
    }
}
