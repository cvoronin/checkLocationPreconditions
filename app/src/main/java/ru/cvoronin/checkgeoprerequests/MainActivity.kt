package ru.cvoronin.checkgeoprerequests

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

class MainActivity : AppCompatActivity(),
        PermissionDialogFragment.PermissionRationaleDialogListener,
        HandleGooglePlayServicesErrorDialog.GooglePlayServicesResolutionDialogListener {

    companion object {
        var TAG = "MainActivity"
        val LOCATION_PERMISSION_RATIONALE = "Location permission is... "

        val KEY_STATE_RATIONALE_DIALOG_ACTIVE = "isRationaleDialogActive"
        val KEY_IS_PERMISSION_GRANTED = "isPermissionGranted"
        val KEY_IS_PERMISSION_REQUEST_ACTIVE = "isPermissionRequestActive"
        val KEY_GOOGLE_PLAY_SERVICES_READY = "isGooglePlayServicesReady"
        val KEY_GOOGLE_PLAY_SERVICES_RESOLVING = "isGooglePlayServicesResolving"

        val REQ_PERMISSION_LOCATION = 100
        val REQ_GOOGLE_PLAY_SERVICES_RESOLVE = 101
    }

    //............................................................................................

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState != null) {
            with(savedInstanceState) {
                isRationaleDialogActive = getBoolean(KEY_STATE_RATIONALE_DIALOG_ACTIVE)
                isPermissionGranted = getBoolean(KEY_IS_PERMISSION_GRANTED)
                isPermissionRequestActive = getBoolean(KEY_IS_PERMISSION_REQUEST_ACTIVE)

                isGooglePlayServicesReady = getBoolean(KEY_GOOGLE_PLAY_SERVICES_READY)
                isGooglePlayServicesResolutionInProgress = getBoolean(KEY_GOOGLE_PLAY_SERVICES_RESOLVING)
            }
        }
    }

    override fun onResume() {
        Log.d(TAG, "... onResume")
        super.onResume()

        /* Check if permission is granted or if request permission dialog is active */
        if (isRationaleDialogActive || isPermissionRequestActive) {

            // Dialog is active, do nothing, waiting for result
            return
        }

        when (isPermissionGranted) {
            null -> {
                doCheckPermissions()
                return
            }

            false -> {
                onPermissionsRejected()
                return
            }

            true -> {
            }
        }

        /* OK, Permission is granted, check Google Play Services version */

        if (isGooglePlayServicesResolutionInProgress) {
            // Resolution in progress, do not touch it
            return
        }

        if (isGooglePlayServicesReady == null) {
            doCheckGooglePlayServices()
        }


    }

    override fun onPause() {
        Log.d(TAG, "... onPause")
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        with(outState!!) {

            if (isPermissionGranted != null) putBoolean(KEY_IS_PERMISSION_GRANTED, isPermissionGranted!!)

            putBoolean(KEY_STATE_RATIONALE_DIALOG_ACTIVE, isRationaleDialogActive)
            putBoolean(KEY_IS_PERMISSION_REQUEST_ACTIVE, isPermissionRequestActive)

            if (isGooglePlayServicesReady != null) {
                putBoolean(KEY_GOOGLE_PLAY_SERVICES_READY, isGooglePlayServicesReady!!)
            }

            putBoolean(KEY_GOOGLE_PLAY_SERVICES_RESOLVING, isGooglePlayServicesResolutionInProgress)
        }
    }

    //... CHECK LOCATION PERMISSION SECTION .......................................................

    // Until check is completed value is nor true, nor false
    private var isPermissionGranted: Boolean? = null

    private var isRationaleDialogActive: Boolean = false
    private var isPermissionRequestActive: Boolean = false

    private fun doCheckPermissions() {
        val isGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        when (isGranted) {
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
        Log.d(TAG, "... doShowPermissionRationale")
        isRationaleDialogActive = true
        PermissionDialogFragment.show(supportFragmentManager, LOCATION_PERMISSION_RATIONALE)
    }

    override fun onRationaleAccepted() {
        Log.d(TAG, "... onRationaleAccepted")
        isRationaleDialogActive = false
        doRequestPermission()
    }

    override fun onRationaleRejected() {
        Log.d(TAG, "... onRationaleRejected")
        isRationaleDialogActive = false
        onPermissionsRejected()
    }

    private fun doRequestPermission() {
        Log.d(TAG, "... doRequestPermission")
        isPermissionRequestActive = true
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_PERMISSION_LOCATION)
    }

    private fun onPermissionsGranted() {
        Log.d(TAG, "... onPermissionsGranted")
        Toast.makeText(this, "PERMISSION GRANTED", Toast.LENGTH_SHORT).show()
        isPermissionGranted = true

        doCheckGooglePlayServices()
    }

    private fun onPermissionsRejected() {
        Log.d(TAG, "... onPermissionsRejected")
        Toast.makeText(this, "PERMISSION REJECTED", Toast.LENGTH_SHORT).show()
        isPermissionGranted = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.d(TAG, "... onRequestPermissionsResult")

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_PERMISSION_LOCATION) {
            isPermissionRequestActive = false

            when (permissions.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                true -> onPermissionsGranted()
                else -> onPermissionsRejected()
            }
        }
    }

    //... CHECK GOOGLE PLAY SERVICES SECTION ......................................................


    private var isGooglePlayServicesReady: Boolean? = null
    private var isGooglePlayServicesResolutionInProgress = false

    fun doCheckGooglePlayServices() {
        Log.d(TAG, "... doCheckGooglePlayServices")

        if (isGooglePlayServicesResolutionInProgress) {
            Log.d(TAG, "... ... resolution is in progress, do nothing")
            return
        }

        val checkResult = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        when (checkResult) {
            ConnectionResult.SUCCESS -> {
                onGooglePlayServicesCheckSuccess()
            }

            else -> {
                isGooglePlayServicesResolutionInProgress = true
                HandleGooglePlayServicesErrorDialog.show(supportFragmentManager, checkResult, REQ_GOOGLE_PLAY_SERVICES_RESOLVE)
            }
        }
    }

    private fun onGooglePlayServicesCheckSuccess() {
        Log.d(TAG, "... onGooglePlayServicesCheckSuccess")
        isGooglePlayServicesReady = true
        isGooglePlayServicesResolutionInProgress = false
    }

    private fun onGooglePlayServicesCheckFail() {
        Log.d(TAG, "... onGooglePlayServicesCheckFail")
        isGooglePlayServicesReady = false
        isGooglePlayServicesResolutionInProgress = false
    }

    override fun onGooglePlayServicesResolutionDialogDismissed() {
        Log.d(TAG, "... onResolutionDialogDismissed")

        isGooglePlayServicesResolutionInProgress = false

        // Check if problem was fixed
        val checkResult = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        when (checkResult) {
            ConnectionResult.SUCCESS -> onGooglePlayServicesCheckSuccess()
            else -> onGooglePlayServicesCheckFail()
        }
    }

    //.............................................................................................

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "... onActivityResult")

        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_GOOGLE_PLAY_SERVICES_RESOLVE) {
            onGooglePlayServicesResolutionDialogDismissed()
            return
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
        fun onRationaleAccepted()
        fun onRationaleRejected()
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
                .onPositive { materialDialog, dialogAction -> listener.onRationaleAccepted() }
                .onNegative { materialDialog, dialogAction -> listener.onRationaleRejected() }
                .show()
    }
}

//.................................................................................................

class HandleGooglePlayServicesErrorDialog : DialogFragment() {

    interface GooglePlayServicesResolutionDialogListener {
        fun onGooglePlayServicesResolutionDialogDismissed()
    }

    companion object {
        val TAG = "ServicesResolutionDialog"
        val KEY_ERROR_CODE = "errorCode"
        val KEY_REQ_CODE = "requestCode"

        fun show(fragmentManager: FragmentManager, errorCode: Int, requestCode: Int) {
            val fragment = HandleGooglePlayServicesErrorDialog()

            fragment.arguments = Bundle().apply {
                putInt(KEY_ERROR_CODE, errorCode)
                putInt(KEY_REQ_CODE, requestCode)
            }

            fragment.show(fragmentManager, TAG)
        }
    }

    lateinit private var listener: GooglePlayServicesResolutionDialogListener

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        listener = activity as GooglePlayServicesResolutionDialogListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val errorCode = arguments.getInt(KEY_ERROR_CODE)
        val requestCode = arguments.getInt(KEY_REQ_CODE)

        return GoogleApiAvailability.getInstance().getErrorDialog(activity, errorCode, requestCode)
    }

    override fun onDismiss(dialog: DialogInterface?) {
        listener.onGooglePlayServicesResolutionDialogDismissed()
    }
}

