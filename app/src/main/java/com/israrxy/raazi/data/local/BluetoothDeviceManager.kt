package com.israrxy.raazi.data.local

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detects the currently active audio output device (Bluetooth earbuds/headphones,
 * wired headphones, speaker, etc.) and exposes it as a [StateFlow] so the UI can
 * show connection status and apply per-device EQ profiles.
 */
class BluetoothDeviceManager(private val context: Context) {

    sealed class AudioDevice(
        val name: String,
        val address: String,
        val type: Type
    ) {
        enum class Type { EARBUDS, HEADPHONES, SPEAKER, CAR, WIRED, BUILTIN_SPEAKER, UNKNOWN }

        val isBluetooth: Boolean
            get() = type != Type.WIRED && type != Type.BUILTIN_SPEAKER

        class Earbuds(name: String, address: String) : AudioDevice(name, address, Type.EARBUDS)
        class Headphones(name: String, address: String) : AudioDevice(name, address, Type.HEADPHONES)
        class Speaker(name: String, address: String) : AudioDevice(name, address, Type.SPEAKER)
        class Car(name: String, address: String) : AudioDevice(name, address, Type.CAR)
        class Wired(name: String, address: String) : AudioDevice(name, address, Type.WIRED)
        class BuiltInSpeaker(name: String, address: String) : AudioDevice(name, address, Type.BUILTIN_SPEAKER)
        class Unknown(name: String, address: String) : AudioDevice(name, address, Type.UNKNOWN)
    }

    private val _connectedDevice = MutableStateFlow<AudioDevice?>(null)
    val connectedDevice: StateFlow<AudioDevice?> = _connectedDevice.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var a2dpProfile: BluetoothProfile? = null
    private var headsetProfile: BluetoothProfile? = null

    private val audioDeviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                refreshFromAudioDevices()
            }

            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                refreshFromAudioDevices()
            }
        }
    } else null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.A2DP) a2dpProfile = proxy
            else if (profile == BluetoothProfile.HEADSET) headsetProfile = proxy
            refreshFromBluetooth()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) a2dpProfile = null
            else if (profile == BluetoothProfile.HEADSET) headsetProfile = null
            refreshFromBluetooth()
        }
    }

    private val btAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioDeviceCallback?.let { audioManager.registerAudioDeviceCallback(it, null) }
        }
        btAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        btAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
        refresh()
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioDeviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        }
        a2dpProfile?.let { btAdapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        headsetProfile?.let { btAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        a2dpProfile = null
        headsetProfile = null
    }

    private fun refresh() {
        refreshFromAudioDevices()
        refreshFromBluetooth()
    }

    private fun refreshFromAudioDevices() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val isOutput = { d: AudioDeviceInfo ->
            when (d.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_DOCK,
                AudioDeviceInfo.TYPE_LINE_DIGITAL,
                AudioDeviceInfo.TYPE_LINE_ANALOG -> true
                else -> false
            }
        }
        val active = devices.firstOrNull { isOutput(it) && it.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (active == null) {
            _connectedDevice.value = null
            return
        }
        val label = active.productName?.toString().takeIf { !it.isNullOrBlank() }
            ?: when (active.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headphones"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth device"
                else -> "Audio device"
            }
        val address = active.address ?: ""
        _connectedDevice.value = when (active.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                AudioDevice.Wired(label, address)
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE ->
                AudioDevice.Wired(label, address)
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ->
                AudioDevice.BuiltInSpeaker("Phone speaker", "")
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                val kind = classifyBluetoothName(label)
                buildBluetoothDevice(kind, label, address)
            }
            else -> AudioDevice.Unknown(label, address)
        }
    }

    private fun refreshFromBluetooth() {
        val bonded = (a2dpProfile?.connectedDevices ?: emptyList()) +
                (headsetProfile?.connectedDevices ?: emptyList())
        val device = bonded.firstOrNull() ?: return
        if (_connectedDevice.value?.isBluetooth == true) return
        val name = device.name ?: "Bluetooth device"
        val kind = classifyBluetoothName(name)
        _connectedDevice.value = buildBluetoothDevice(kind, name, device.address)
    }

    private fun classifyBluetoothName(name: String): AudioDevice.Type {
        val n = name.lowercase()
        return when {
            n.contains("bud") || n.contains("airpod") || n.contains("ear") || n.contains("galaxy") && n.contains("pro") ->
                AudioDevice.Type.EARBUDS
            n.contains("car") || n.contains("auto") || n.contains("veh") ->
                AudioDevice.Type.CAR
            n.contains("speaker") || n.contains("soundlink") || n.contains("flip") || n.contains("charge") ->
                AudioDevice.Type.SPEAKER
            n.contains("headphone") || n.contains("wh-1000") || n.contains("qc") || n.contains("studio") ->
                AudioDevice.Type.HEADPHONES
            else -> AudioDevice.Type.EARBUDS
        }
    }

    private fun buildBluetoothDevice(type: AudioDevice.Type, name: String, address: String): AudioDevice {
        return when (type) {
            AudioDevice.Type.EARBUDS -> AudioDevice.Earbuds(name, address)
            AudioDevice.Type.HEADPHONES -> AudioDevice.Headphones(name, address)
            AudioDevice.Type.SPEAKER -> AudioDevice.Speaker(name, address)
            AudioDevice.Type.CAR -> AudioDevice.Car(name, address)
            else -> AudioDevice.Earbuds(name, address)
        }
    }
}
