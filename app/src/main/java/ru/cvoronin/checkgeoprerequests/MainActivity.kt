package ru.cvoronin.checkgeoprerequests

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.location.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.lang.ref.WeakReference
import java.util.*

class MainActivity : AppCompatActivity(),
        PermissionDialogFragment.PermissionRationaleDialogListener,
        GoogleErrorDialog.GoogleErrorDialogListener {

    companion object {
        val LOCATION_PERMISSION_RATIONALE_TEXT = "Location permission is... "
        val REQ_PERMISSION_LOCATION = 100
        val REQ_GOOGLE_PLAY_SERVICES_RESOLVE = 101
        val REQ_RESOLVE_SETTINGS_ERROR = 102
        var REQ_RESOLVE_CONNECTION_FAILED_ERROR = 103

        val KEY_IS_PERMISSION_GRANTED = "isPermissionGranted"
        val KEY_IS_RESOLVING = "isResolving"
        val KEY_IS_GEO_FUNCTIONS_ENABLED = "isGeoFunctionsEnabled"
        val KEY_IS_LOCATION_SEARCH_IN_PROGRESS = "isLocationSearchInProgress"
        val KEY_LOCATION = "location"
    }

    var TAG = "MainActivity @${Integer.toHexString(hashCode())}"

    private var isResolving = false
    private var isPermissionGranted: Boolean? = null
    private var isGeoFunctionsEnabled: Boolean = false
    private var isLocationSearchInProgress: Boolean = false

    // This value is not stored in saveInstanceState
    private var isViewInitialized: Boolean = false

    private var location: Location? = null

    lateinit private var apiClient: GoogleApiClient
    lateinit private var locationRequest: LocationRequest

    //............................................................................................

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "... onCreate")

        setContentView(R.layout.activity_main)

        if (savedInstanceState != null) {
            with(savedInstanceState) {
                isPermissionGranted = getBoolean(KEY_IS_PERMISSION_GRANTED)
                isResolving = getBoolean(KEY_IS_RESOLVING)
                isGeoFunctionsEnabled = getBoolean(KEY_IS_GEO_FUNCTIONS_ENABLED)
                isLocationSearchInProgress = getBoolean(KEY_IS_LOCATION_SEARCH_IN_PROGRESS)
                location = getParcelable(KEY_LOCATION)
            }
        }

        apiClient = GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(connectionCallbacks)
                .addOnConnectionFailedListener(connectionFailedListener)
                .build()

        locationRequest = LocationRequest().apply {
            interval = 1000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    override fun onResume() {
        Log.d(TAG, "... onResume")
        super.onResume()

        if (isResolving) return

        if (isPermissionGranted == null) {
            doCheckPermissions()
            return
        }

        if (isLocationSearchInProgress) {
            findLocation()
            return
        }

        if (!isViewInitialized) {
            initView()
        }

        //presenter.onResume
    }

    override fun onPause() {
        Log.d(TAG, "... onPause")

        if (apiClient.isConnected) {
            stopLocationUpdates()
        }

        super.onPause()
    }

    override fun onStart() {
        super.onStart()

        if (isGeoFunctionsEnabled) {
            apiClient.connect()
        }
    }

    override fun onStop() {
        if (apiClient.isConnected) {
            apiClient.disconnect()
        }

        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        Log.d(TAG, "... onSaveInstanceState")

        super.onSaveInstanceState(outState)
        with(outState!!) {
            if (isPermissionGranted != null) putBoolean(KEY_IS_PERMISSION_GRANTED, isPermissionGranted!!)
            putBoolean(KEY_IS_RESOLVING, isResolving)
            putBoolean(KEY_IS_GEO_FUNCTIONS_ENABLED, isGeoFunctionsEnabled)
            putBoolean(KEY_IS_LOCATION_SEARCH_IN_PROGRESS, isLocationSearchInProgress)
            putParcelable(KEY_LOCATION, location)
        }
    }

    private fun initView() {
        println("... initView $isGeoFunctionsEnabled $location")

        // create presenter, inject etc
        isViewInitialized = true
    }

    //... Quick Check Methods .................................................................

    fun isGeoLocationPermissionGranted(): Boolean = ContextCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isGooglePlayServicesAvailable(): Boolean =
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS



    //...  CHECK PERMISSION .......................................................................

    private fun doCheckPermissions() {
        when (isGeoLocationPermissionGranted()) {
            true -> onPermissionsGranted()
            else -> {
                val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                when (shouldShowRationale) {
                    true -> doShowPermissionRationale()
                    else -> doRequestPermission()
                }
            }
        }
    }

    private fun doShowPermissionRationale() {
        isResolving = true
        PermissionDialogFragment.show(supportFragmentManager, LOCATION_PERMISSION_RATIONALE_TEXT)
    }

    override fun onRationaleResult(isAccepted: Boolean) {
        isResolving = false
        when (isAccepted) {
            true -> doRequestPermission()
            false -> onPermissionsRejected()
        }
    }

    private fun doRequestPermission() {
        isResolving = true
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_PERMISSION_LOCATION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_PERMISSION_LOCATION) {
            isResolving = false
            when (permissions.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                true -> onPermissionsGranted()
                else -> onPermissionsRejected()
            }
        }
    }

    private fun onPermissionsGranted() {
        Log.d(TAG, "... onPermissionsGranted")
        Toast.makeText(this, "PERMISSION GRANTED", Toast.LENGTH_SHORT).show()

        isPermissionGranted = true
        apiClient.connect()
    }

    private fun onPermissionsRejected() {
        Toast.makeText(this, "PERMISSION REJECTED", Toast.LENGTH_SHORT).show()
        isPermissionGranted = false
        isGeoFunctionsEnabled = false
        initView()
    }

    //... CHECK GOOGLE PLAY SERVICES ..............................................................

    private val connectionCallbacks = object : GoogleApiClient.ConnectionCallbacks {
        override fun onConnected(p0: Bundle?) {
            Log.d(TAG, "... ConnectionCallbacks/onConnected")
            checkSettings()
        }

        override fun onConnectionSuspended(p0: Int) {
            Log.d(TAG, "... ConnectionCallbacks/onConnectionSuspended")
        }
    }

    private val connectionFailedListener = GoogleApiClient.OnConnectionFailedListener { connectionResult ->
        Log.d(TAG, "!!! OnConnectionFailedListener/onConnectionFailed $connectionResult")

        if (isResolving) {
            return@OnConnectionFailedListener
        }

        when (connectionResult.hasResolution()) {
            true -> {
                isResolving = true
                connectionResult.startResolutionForResult(this, REQ_RESOLVE_CONNECTION_FAILED_ERROR)
            }

            else -> {
                isResolving = true
                GoogleErrorDialog.show(supportFragmentManager, connectionResult.errorCode, REQ_RESOLVE_CONNECTION_FAILED_ERROR)
            }
        }
    }



    //... CHECK SETTINGS ..........................................................................

    private fun checkSettings() {

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val resultPending: PendingResult<LocationSettingsResult> =
                LocationServices.SettingsApi.checkLocationSettings(apiClient, builder.build())

        resultPending.setResultCallback { result ->
            println("... LocationSettingsRequest: ${result.status.statusMessage}")

            when (result.status.statusCode) {
                LocationSettingsStatusCodes.SUCCESS -> {
                    Log.d(TAG, "... LocationSettingsStatusCodes/SUCCESS")
                    onLocationSettingsSuccess()
                }

                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                    Log.d(TAG, "... LocationSettingsStatusCodes/RESOLUTION_REQUIRED")

                    try {
                        isResolving = true
                        result.status.startResolutionForResult(this@MainActivity, REQ_RESOLVE_SETTINGS_ERROR)
                    } catch (e: Exception) {
                        Log.e(TAG, Log.getStackTraceString(e))

                        isGeoFunctionsEnabled = false
                        initView()
                    }
                }

                LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                    Log.d(TAG, "... LocationSettingsStatusCodes/SETTINGS_CHANGE_UNAVAILABLE")

                    isGeoFunctionsEnabled = false
                    initView()
                }
            }
        }
    }



    //.............................................................................................

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "... onActivityResult")

        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_GOOGLE_PLAY_SERVICES_RESOLVE) {
            onGoogleErrorDialogDismissed()
            return
        }

        if (requestCode == REQ_RESOLVE_CONNECTION_FAILED_ERROR) {
            isResolving = false
            if (resultCode == Activity.RESULT_OK) {
                if (!apiClient.isConnected && !apiClient.isConnecting) {
                    apiClient.connect()
                }
            } else {
                isGeoFunctionsEnabled = false
                initView()
            }
        }

        if (requestCode == REQ_RESOLVE_SETTINGS_ERROR) {
            if (requestCode == Activity.RESULT_OK) {
                onLocationSettingsSuccess()
            } else {
                isGeoFunctionsEnabled = false
                initView()
            }
        }
    }

    override fun onGoogleErrorDialogDismissed() {
        /*
            When activity is recreated, this method of is called for "old" activity
            after the old#saveInstatnceState and new@onCreate are called
         */

        Log.d(TAG, "... onResolutionDialogDismissed")
        isResolving = false

        if (isResolving) {
            isResolving = false
            isGeoFunctionsEnabled = false
            initView()
        }
    }



    //.............................................................................................

    private fun onLocationSettingsSuccess() {
        Log.d(TAG, "... onLocationSettingsSuccess")

        when (this.location) {
            null -> {
                isGeoFunctionsEnabled = true
                findLocation()
            }

            else -> {
                Log.d(TAG, "... ... location is $location, do nothing")
            }
        }
    }

    private fun findLocation() {
        Log.d(TAG, "... findLocation")

        isLocationSearchInProgress = true
        locationTimeoutHandler.sendEmptyMessageDelayed(0, 30 * 1000)

        val lastKnownLocation = LocationServices.FusedLocationApi.getLastLocation(apiClient)
        println("... lastKnowLocation: $lastKnownLocation")

        location = lastKnownLocation
        LocationServices.FusedLocationApi.requestLocationUpdates(apiClient, locationRequest, locationListener)
    }

    private fun stopLocationUpdates() {

        Log.d(TAG, "... stopLocationUpdates")

        // Do not change value here - it should be changed only when location is found - or in
        // case of location search timeout
        // isLocationSearchInProgress = false

        locationTimeoutHandler.removeMessages(0)
        LocationServices.FusedLocationApi.removeLocationUpdates(apiClient, locationListener)
    }

    private fun onFindLocationTimedOut() {
        Log.d(TAG, "... onFindLocationTimedOut")
        stopLocationUpdates()

        location = null

        isLocationSearchInProgress = false
        isGeoFunctionsEnabled = false
        initView()
    }

    val locationListener: LocationListener = LocationListener { aLocation ->
        if (this.location == null) {
            this.location = aLocation
            return@LocationListener
        }

        val prevLocation = this.location!!
        this.location = aLocation
        val distanceResults = FloatArray(1)
        Location.distanceBetween(prevLocation.latitude, prevLocation.longitude,
                aLocation.latitude, aLocation.longitude, distanceResults)

        if (distanceResults[0] < 30) {
            stopLocationUpdates()
            onLocationFound()
        }
    }

    private fun onLocationFound() {
        locationTimeoutHandler.removeMessages(0)

        isLocationSearchInProgress = false
        isGeoFunctionsEnabled = true
        initView()

        // Just for fun
        findAddress(this.location!!)
    }

    private fun findAddress(location : Location) {
        val geoCoder = Geocoder(this, Locale.getDefault())

        doAsync {
            val result : List<Address> = try {
                geoCoder.getFromLocation(location.latitude, location.longitude, 1)
            } catch (e : Exception) {
                Log.e(TAG, Log.getStackTraceString(e))
                emptyList()
            }

            uiThread {
                println("... Geocoder results for $location")
                result.forEach { address ->
                    println("... countryCode: ${address.countryCode}")
                    println("... countryName: ${address.countryName}")
                    for (i in 0..address.maxAddressLineIndex) {
                        println("... ... ${address.getAddressLine(i)}")
                    }
                    println("")
                }
            }
        }
    }


    //.............................................................................................

    val locationTimeoutHandler = LocationTimeoutHandler(this)

    class LocationTimeoutHandler(activity: MainActivity) : Handler() {

        val activityRef: WeakReference<MainActivity>

        init {
            activityRef = WeakReference(activity)
        }

        override fun handleMessage(msg: Message?) {
            activityRef.get()?.onFindLocationTimedOut()
        }
    }
}


//.................................................................................................

class PermissionDialogFragment : DialogFragment() {

    companion object {
        val KEY_MESSAGE = "dialog-message"
        val TAG = "PermissionRationaleDialog"

        fun show(fragmentManager: FragmentManager, message: String) {
            val fragment = PermissionDialogFragment()

            fragment.arguments = Bundle().apply {
                putString(KEY_MESSAGE, message)
            }

            fragment.show(fragmentManager, TAG)
        }
    }

    interface PermissionRationaleDialogListener {
        fun onRationaleResult(isAccepted: Boolean)
    }

    lateinit var listener: PermissionRationaleDialogListener

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        listener = activity as PermissionRationaleDialogListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false

        val message: String = arguments.getString(KEY_MESSAGE)

        return MaterialDialog.Builder(context)
                .content(message)
                .positiveText("ОК")
                .negativeText("Отказаться")
                .onPositive { materialDialog, dialogAction -> listener.onRationaleResult(true) }
                .onNegative { materialDialog, dialogAction -> listener.onRationaleResult(false) }
                .show()
    }
}

//.................................................................................................

class GoogleErrorDialog : DialogFragment() {

    interface GoogleErrorDialogListener {
        fun onGoogleErrorDialogDismissed()
    }

    companion object {
        val TAG = "ServicesResolutionDialog"
        val KEY_ERROR_CODE = "errorCode"
        val KEY_REQ_CODE = "requestCode"

        fun show(fragmentManager: FragmentManager, errorCode: Int, requestCode: Int) {
            val fragment = GoogleErrorDialog()

            fragment.arguments = Bundle().apply {
                putInt(KEY_ERROR_CODE, errorCode)
                putInt(KEY_REQ_CODE, requestCode)
            }

            fragment.show(fragmentManager, TAG)
        }
    }

    lateinit private var listener: GoogleErrorDialogListener

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        listener = activity as GoogleErrorDialogListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val errorCode = arguments.getInt(KEY_ERROR_CODE)
        val requestCode = arguments.getInt(KEY_REQ_CODE)

        return GoogleApiAvailability.getInstance().getErrorDialog(activity, errorCode, requestCode)
    }

    override fun onDismiss(dialog: DialogInterface?) {
        listener.onGoogleErrorDialogDismissed()
    }
}

