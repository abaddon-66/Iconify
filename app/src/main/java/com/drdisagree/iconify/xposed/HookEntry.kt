package com.drdisagree.iconify.xposed

import android.annotation.SuppressLint
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.os.UserManager
import com.drdisagree.iconify.BuildConfig
import com.drdisagree.iconify.IRootProviderProxy
import com.drdisagree.iconify.R
import com.drdisagree.iconify.common.Const.FRAMEWORK_PACKAGE
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.utils.BootLoopProtector
import com.drdisagree.iconify.xposed.utils.SystemUtils
import com.drdisagree.iconify.xposed.utils.XPrefs
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationTargetException
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.CompletableFuture

class HookEntry : ServiceConnection {

    private lateinit var mContext: Context

    init {
        instance = this
    }

    fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        isChildProcess = try {
            loadPackageParam.processName.contains(":")
        } catch (ignored: Throwable) {
            false
        }

        when (loadPackageParam.packageName) {
            FRAMEWORK_PACKAGE -> {
                val phoneWindowManagerClass =
                    findClass("com.android.server.policy.PhoneWindowManager")

                phoneWindowManagerClass
                    .hookMethod("init")
                    .runBefore { param ->
                        try {
                            if (!::mContext.isInitialized) {
                                mContext = param.args[0] as Context

                                HookRes.modRes = mContext.createPackageContext(
                                    BuildConfig.APPLICATION_ID,
                                    Context.CONTEXT_IGNORE_SECURITY
                                ).resources

                                XPrefs.init(mContext)

                                CompletableFuture.runAsync { waitForXprefsLoad(loadPackageParam) }
                            }
                        } catch (throwable: Throwable) {
                            log(this@HookEntry, throwable)
                        }
                    }
            }

            else -> {
                if (!isChildProcess) {
                    Instrumentation::class.java
                        .hookMethod("newApplication")
                        .parameters(
                            ClassLoader::class.java,
                            String::class.java,
                            Context::class.java
                        )
                        .runAfter { param ->
                            try {
                                if (!::mContext.isInitialized) {
                                    mContext = param.args[2] as Context

                                    HookRes.modRes = mContext.createPackageContext(
                                        BuildConfig.APPLICATION_ID,
                                        Context.CONTEXT_IGNORE_SECURITY
                                    ).resources

                                    XPrefs.init(mContext)

                                    waitForXprefsLoad(loadPackageParam)
                                }
                            } catch (throwable: Throwable) {
                                log(this@HookEntry, throwable)
                            }
                        }
                }
            }
        }
    }

    private fun onXPrefsReady(loadPackageParam: LoadPackageParam) {
        if (!isChildProcess && BootLoopProtector.isBootLooped(loadPackageParam.packageName)) {
            log("Possible crash in ${loadPackageParam.packageName} ; Iconify will not load for now...")
            return
        }

        SystemUtils(mContext)

        loadModPacks(loadPackageParam)
    }

    private fun loadModPacks(loadPackageParam: LoadPackageParam) {
        if (HookRes.modRes
                .getStringArray(R.array.root_requirement)
                .toList()
                .contains(loadPackageParam.packageName)
        ) {
            forceConnectRootService()
        }

        for (mod in EntryList.getEntries(loadPackageParam.packageName)) {
            try {
                val instance = mod.getConstructor(Context::class.java).newInstance(mContext)

                try {
                    instance.updatePrefs()
                } catch (throwable: Throwable) {
                    log(this@HookEntry, "Failed to update prefs in ${mod.name}")
                    log(this@HookEntry, throwable)
                }

                instance.handleLoadPackage(loadPackageParam)
                runningMods.add(instance)
            } catch (invocationTargetException: InvocationTargetException) {
                log(this@HookEntry, "Start Error Dump - Occurred in ${mod.name}")
                log(this@HookEntry, invocationTargetException.cause)
            } catch (throwable: Throwable) {
                log(this@HookEntry, "Start Error Dump - Occurred in ${mod.name}")
                log(this@HookEntry, throwable)
            }
        }
    }

    private fun waitForXprefsLoad(loadPackageParam: LoadPackageParam) {
        while (true) {
            try {
                Xprefs.getBoolean("LoadTestBooleanValue", false)
                break
            } catch (ignored: Throwable) {
                SystemUtils.sleep(1000);
            }
        }

        log("Iconify Version: ${BuildConfig.VERSION_NAME}")

        onXPrefsReady(loadPackageParam)
    }

    private fun forceConnectRootService() {
        CoroutineScope(Dispatchers.Main).launch {
            val mUserManager = mContext.getSystemService(Context.USER_SERVICE) as UserManager?

            withContext(Dispatchers.IO) {
                while (mUserManager == null || !mUserManager.isUserUnlocked) {
                    // device is still CE encrypted
                    delay(2000)
                }

                delay(5000) // wait for the unlocked account to settle down a bit

                while (rootProxyIPC == null) {
                    connectRootService()
                    delay(5000)
                }
            }
        }
    }

    private fun connectRootService() {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    BuildConfig.APPLICATION_ID,
                    "${
                        BuildConfig.APPLICATION_ID.replace(
                            ".debug",
                            ""
                        )
                    }.services.RootProviderProxy"
                )
            }

            mContext.bindService(
                intent,
                instance!!,
                Context.BIND_AUTO_CREATE or Context.BIND_ADJUST_WITH_ACTIVITY
            )
        } catch (throwable: Throwable) {
            log(this@HookEntry, throwable)
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        rootProxyIPC = IRootProviderProxy.Stub.asInterface(service)

        synchronized(proxyQueue) {
            while (!proxyQueue.isEmpty()) {
                try {
                    proxyQueue.poll()!!.run(rootProxyIPC!!)
                } catch (ignored: Throwable) {
                }
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        rootProxyIPC = null
        forceConnectRootService()
    }

    fun interface ProxyRunnable {
        @Throws(RemoteException::class)
        fun run(proxy: IRootProviderProxy)
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        var instance: HookEntry? = null
        val runningMods = ArrayList<ModPack>()
        var isChildProcess = false
        var rootProxyIPC: IRootProviderProxy? = null
        val proxyQueue: Queue<ProxyRunnable> = LinkedList()

        fun enqueueProxyCommand(runnable: ProxyRunnable) {
            rootProxyIPC?.let {
                try {
                    runnable.run(it)
                } catch (ignored: RemoteException) {
                }
            } ?: run {
                synchronized(proxyQueue) {
                    proxyQueue.add(runnable)
                }

                instance!!.forceConnectRootService()
            }
        }
    }
}
