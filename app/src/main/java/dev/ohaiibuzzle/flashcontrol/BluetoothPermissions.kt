package dev.ohaiibuzzle.flashcontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

fun requiredBlePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyArray()
    }
}

fun Context.hasBlePermissions(): Boolean {
    return requiredBlePermissions().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
}
