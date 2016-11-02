package ru.cvoronin.checkgeoprerequests

import android.Manifest
import android.app.Dialog
import android.content.Context
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

class MainActivity : AppCompatActivity(), PermissionDialogFragment.PermissionRationaleDialogListener {

    companion object {
        val KEY_STATE_RATIONALE_DIALOG_ACTIVE = "isRationaleDialogActive"
        val KEY_IS_PERMISSION_GRANTED = "isPermissionGranted"
        val KEY_IS_PERMISSION_REQUEST_ACTIVE = "isPermissionRequestActive"

        var TAG = "MainActivity"
        val REQ_PERMISSION_LOCATION = 100
        val LOCATION_PERMISSION_RATIONALE = "Location permission is... "
    }

    //............................................................................................

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState != null) {
            isRationaleDialogActive = savedInstanceState.getBoolean(KEY_STATE_RATIONALE_DIALOG_ACTIVE)
            isPermissionGranted = savedInstanceState.getBoolean(KEY_IS_PERMISSION_GRANTED)
            isPermissionRequestActive = savedInstanceState.getBoolean(KEY_IS_PERMISSION_REQUEST_ACTIVE)
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

            true -> {}
        }
    }

    override fun onPause() {
        Log.d(TAG, "... onPause")
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        with(outState!!) {

            if (isPermissionGranted != null) {
                outState.putBoolean(KEY_IS_PERMISSION_GRANTED, isPermissionGranted!!)
            }

            outState.putBoolean(KEY_STATE_RATIONALE_DIALOG_ACTIVE, isRationaleDialogActive)
            outState.putBoolean(KEY_IS_PERMISSION_REQUEST_ACTIVE, isPermissionRequestActive)
        }
    }

    //.............................................................................................

    private var isPermissionGranted: Boolean? = null
    private var isRationaleDialogActive: Boolean = false
    private var isPermissionRequestActive : Boolean = false

    private fun doCheckPermissions() {
        val isGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        when (isGranted) {
            true -> onPermissionsGranted()
            else -> {
                val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
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
}

//................................................................................................

class PermissionDialogFragment : DialogFragment() {

    companion object {
        val KEY_MESSAGE = "dialog-message"
        val TAG = "PermissionRationaleDialog"

        fun show(fragmentManager: FragmentManager, message: String) {
            val args = Bundle()
            args.putString(KEY_MESSAGE, message)

            val fragment = PermissionDialogFragment()
            fragment.arguments = args
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

//................................................................................................