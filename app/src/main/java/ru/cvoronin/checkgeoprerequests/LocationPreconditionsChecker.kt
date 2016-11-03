package ru.cvoronin.checkgeoprerequests

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log

interface LocationPreconditionsChecker {

    enum class CheckResult {
        NULL, SUCCESS, ERROR_PERMISSIONS, ERROR_GOOGLE_PLAY_SERVICES, ERROR_LOCATION_SETTINGS
    }

    fun check()
    fun getCheckResult(): CheckResult
    fun getStateBundle(): Bundle

    fun onRationaleResult(isAccepted : Boolean)
}

interface LocationPreconditionsCheckActivity {

    fun doShowPermissionRationale()
    fun doRequestPermission()

    fun onLocationPreconditionsCheckCompleted(checkResult: LocationPreconditionsChecker.CheckResult)
}

class LocationPreconditionsCheckerImpl(
        val view: LocationPreconditionsCheckActivity,
        savedState: Bundle? = null) : LocationPreconditionsChecker {

    companion object {
        val TAG = "LocationChecker"
    }

    private class State(savedState: Bundle? = null) {

        companion object {
            val KEY_IS_PERMISSION_GRANTED = "isPermissionGranted"
            val KEY_PERMISSION_REQUEST_DIALOG_IS_ACTIVE = "isPermissionRequestDialogActive"
            val KEY_GOOGLE_PLAY_SERVICES_READY = "isGooglePlayServicesReady"
            val KEY_GOOGLE_PLAY_SERVICES_RESOLVING = "isGooglePlayServicesResolving"
        }

        var isPermissionGranted: Boolean? = null
        var isGooglePlayServicesReady: Boolean? = null

        var isPermissionRequestDialogActive = false
        var isGooglePlayServicesResolutionInProgress = false

        init {

            if (savedState != null) {
                restoreState(savedState)
            }
        }

        private fun restoreState(savedState: Bundle) = with(savedState) {
            isPermissionGranted = getBoolean(KEY_IS_PERMISSION_GRANTED)
            isPermissionRequestDialogActive = getBoolean(KEY_PERMISSION_REQUEST_DIALOG_IS_ACTIVE)
            isGooglePlayServicesReady = getBoolean(KEY_GOOGLE_PLAY_SERVICES_READY)
            isGooglePlayServicesResolutionInProgress = getBoolean(KEY_GOOGLE_PLAY_SERVICES_RESOLVING)
        }

        fun getStateAsBundle(): Bundle = Bundle().apply {

            if (isPermissionGranted != null) {
                putBoolean(KEY_IS_PERMISSION_GRANTED, isPermissionGranted!!)
            }

            if (isGooglePlayServicesReady != null) {
                putBoolean(KEY_GOOGLE_PLAY_SERVICES_READY, isGooglePlayServicesReady!!)
            }

            putBoolean(KEY_PERMISSION_REQUEST_DIALOG_IS_ACTIVE, isPermissionRequestDialogActive)
            putBoolean(KEY_GOOGLE_PLAY_SERVICES_RESOLVING, isGooglePlayServicesResolutionInProgress)
        }
    }

    private var state: State

    init {
        state = State(savedState)
    }

    override fun check() {
        if (state.isGooglePlayServicesResolutionInProgress || state.isPermissionRequestDialogActive) {
            // Waiting for user action, do nothing
            return
        }

        when (state.isPermissionGranted) {
            null -> {
                doCheckPermissions()
                return
            }

            false -> {
                view.onLocationPreconditionsCheckCompleted(LocationPreconditionsChecker.CheckResult.ERROR_PERMISSIONS)
                return
            }
        }
    }

    private fun doCheckPermissions() {
        when (isGeoLocationPermissionGranted()) {
            true -> onPermissionsGranted()
            else -> {

                val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                        view as Activity, Manifest.permission.ACCESS_FINE_LOCATION)

                when (shouldShowRationale) {
                    true -> {
                        state.isPermissionRequestDialogActive = true
                        view.doShowPermissionRationale()
                    }
                    else -> {
                        state.isPermissionRequestDialogActive = true
                        view.doRequestPermission()
                    }
                }
            }
        }
    }

    override fun getCheckResult(): LocationPreconditionsChecker.CheckResult = LocationPreconditionsChecker.CheckResult.NULL

    override fun getStateBundle(): Bundle = state.getStateAsBundle()

    //.............................................................................................

    private fun onPermissionsGranted() {
        Log.d(TAG, "... onPermissionsGranted")
        state.isPermissionGranted = true

        doCheckGooglePlayServices()
    }

    override fun onRationaleResult(isAccepted: Boolean) {
        Log.d(TAG, "... onRationaleResult $isAccepted")
        state.isPermissionRequestDialogActive = false

        when (isAccepted) {
            true -> {
//                onPermissionsRejected()
            }

            false -> {

            }
        }
    }

    //.............................................................................................

    private fun doCheckGooglePlayServices() {

    }

    //.............................................................................................

    private fun isGeoLocationPermissionGranted(): Boolean =
            ContextCompat.checkSelfPermission(
                            view as Activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    //.............................................................................................
}