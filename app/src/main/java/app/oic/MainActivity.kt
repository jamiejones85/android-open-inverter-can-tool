package app.oic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import app.oic.ui.OicApp
import app.oic.ui.OicTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                MainViewModel.ACTION_PERM -> vm.onPermission(
                    usbExtra(i),
                    i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                )
                UsbManager.ACTION_USB_DEVICE_DETACHED -> vm.disconnect()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun usbExtra(i: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else i.getParcelableExtra(UsbManager.EXTRA_DEVICE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(MainViewModel.ACTION_PERM).apply { addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { OicTheme { OicApp(vm) } }
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}
