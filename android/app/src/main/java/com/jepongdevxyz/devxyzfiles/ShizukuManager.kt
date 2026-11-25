package com.jepongdevxyz.devxyzfiles

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import com.jepongdevxyz.devxyzfiles.shizuku.IDevxyzFilesService
import com.jepongdevxyz.devxyzfiles.shizuku.ShizukuFileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Owns the Shizuku client side: install detection, binder liveness,
 * the one-time permission grant, and the user-service binding.
 *
 * The Shizuku server itself is started by the user in the Shizuku app via
 * Wireless Debugging — phone-only, one-time pairing, no PC. This class
 * never tries to start the server; it only connects to it.
 */
data class ShizukuState(
    val installed: Boolean = false,
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val serviceBound: Boolean = false,
    val serviceUid: Int = -1,
    val lastError: String? = null
)

object ShizukuManager {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val PERMISSION_REQUEST_CODE = 1001

    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    @Volatile
    var service: IDevxyzFilesService? = null
        private set

    private var listenersAttached = false

    // The args used for the current bind — unbindUserService in API 13.1.5
    // needs the SAME args back: unbindUserService(args, conn, remove).
    private var boundArgs: Shizuku.UserServiceArgs? = null

    // 13.1.5 takes android.content.ServiceConnection (imported above), NOT
    // a Shizuku.ServiceConnection — no such nested type exists. Callbacks
    // are onServiceConnected(ComponentName, IBinder) /
    // onServiceDisconnected(ComponentName); the old onConnected/onDisconnected
    // overrode nothing (CI Run #2).
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IDevxyzFilesService.Stub.asInterface(binder)
            val uid = try {
                service?.uid ?: -1
            } catch (e: Exception) {
                -1
            }
            _state.value = _state.value.copy(serviceBound = true, serviceUid = uid, lastError = null)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            _state.value = _state.value.copy(serviceBound = false, serviceUid = -1)
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshInternal()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        service = null
        _state.value = _state.value.copy(
            binderAlive = false,
            serviceBound = false,
            serviceUid = -1,
            lastError = "Shizuku stopped — open Shizuku and tap Start again."
        )
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        _state.value = _state.value.copy(
            permissionGranted = granted,
            lastError = if (granted) null else "Shizuku permission denied in the Shizuku app."
        )
        if (granted) {
            bindService()
        }
    }

    /** Call once from MainActivity.onCreate with the application context. */
    fun attach(context: Context) {
        if (!listenersAttached) {
            listenersAttached = true
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        }
        refresh(context)
    }

    fun detach() {
        // Listeners are static and cheap; the service is unbound on destroy.
        // 13.1.5 signature: unbindUserService(args, conn, remove) — the old
        // 2-arg call (conn, false) matched no overload. remove=false only
        // detaches our connection; with daemon(false) the remote service
        // stops when the app process dies anyway.
        try {
            val args = boundArgs
            if (args != null) {
                Shizuku.unbindUserService(args, serviceConnection, false)
            }
        } catch (e: Exception) {
            // Not bound — nothing to do.
        }
        boundArgs = null
        service = null
    }

    fun refresh(context: Context) {
        refreshInternal(isInstalled(context))
    }

    private fun refreshInternal(installed: Boolean = _state.value.installed) {
        val alive = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
        val granted = try {
            alive && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
        _state.value = _state.value.copy(
            installed = installed,
            binderAlive = alive,
            permissionGranted = granted
        )
    }

    fun isInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    SHIZUKU_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /** Asks Shizuku for the runtime grant. The user answers in the Shizuku app. */
    fun requestPermission() {
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(lastError = "Shizuku is not running: ${e.message}")
        }
    }

    /** Binds the file user-service. No-op when the binder is dead or ungranted. */
    fun bindService() {
        val current = _state.value
        if (!current.binderAlive) {
            _state.value = current.copy(lastError = "Shizuku is not running — open Shizuku and tap Start.")
            return
        }
        if (!current.permissionGranted) {
            requestPermission()
            return
        }
        if (current.serviceBound && service != null) return
        try {
            val context = AppContext.get()
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ShizukuFileService::class.java.name)
            )
                // 13.1.5 builder method is daemon(boolean) — there is NO
                // daemonMode() in the API. daemon(false) = the service
                // stops when the app process dies (default is daemon=true).
                .daemon(false)
                .processNameSuffix("devxyzfiles")
                .debuggable(BuildConfig.DEBUG)
                .version(1)
            boundArgs = args
            // 13.1.5: bindUserService(UserServiceArgs, ServiceConnection)
            // returns void, not Boolean. Success is reported async via
            // onServiceConnected, which sets serviceBound=true.
            Shizuku.bindUserService(args, serviceConnection)
        } catch (e: Exception) {
            boundArgs = null
            _state.value = _state.value.copy(lastError = "Bind failed: ${e.message}")
        }
    }

    fun connect(context: Context) {
        refresh(context)
        bindService()
    }

    /** True only when Android/data and Android/obb can be opened via Shizuku. */
    fun isReady(): Boolean {
        val s = _state.value
        return s.binderAlive && s.permissionGranted && s.serviceBound && service != null
    }
}
