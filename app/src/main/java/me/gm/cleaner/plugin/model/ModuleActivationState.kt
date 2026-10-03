package me.gm.cleaner.plugin.model

enum class ModuleActivationState {
    NotActive,
    ScopeRestartRequired,
    Active;

    companion object {
        /** A framework connection alone does not prove that MediaProvider has the current hooks. */
        fun resolve(
            frameworkConnected: Boolean,
            providerConnected: Boolean,
            providerVersion: Int,
            installedVersion: Int,
        ): ModuleActivationState = when {
            providerConnected && providerVersion > 0 && providerVersion == installedVersion -> Active
            frameworkConnected || providerConnected -> ScopeRestartRequired
            else -> NotActive
        }
    }
}
