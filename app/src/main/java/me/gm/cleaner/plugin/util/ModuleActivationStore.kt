package me.gm.cleaner.plugin.util

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local framework connection; never persist an activation claim across launches. */
object ModuleActivationStore {
    private val services = mutableMapOf<XposedService, Boolean>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _frameworkConnected = MutableStateFlow(false)
    val frameworkConnected = _frameworkConnected.asStateFlow()
    private var registered = false

    @Synchronized
    fun initialize() {
        if (registered) return
        registered = true
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                synchronized(services) { services[service] = false }
                scope.launch {
                    val compatible = runCatching {
                        service.apiVersion >= XposedService.API_102 &&
                            service.frameworkProperties and XposedService.PROP_CAP_SYSTEM != 0L
                    }.getOrDefault(false)
                    synchronized(services) {
                        // A death callback can arrive while the Binder calls are in flight.
                        if (services.containsKey(service)) services[service] = compatible
                        _frameworkConnected.value = services.values.any { it }
                    }
                }
            }

            override fun onServiceDied(service: XposedService) {
                synchronized(services) {
                    services.remove(service)
                    _frameworkConnected.value = services.values.any { it }
                }
            }
        })
    }
}
