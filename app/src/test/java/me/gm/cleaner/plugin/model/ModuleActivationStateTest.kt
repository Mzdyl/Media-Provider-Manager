package me.gm.cleaner.plugin.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleActivationStateTest {
    @Test
    fun frameworkServiceDoesNotProveProviderHooksAreReady() {
        assertEquals(ModuleActivationState.ScopeRestartRequired, ModuleActivationState.resolve(true, false, 0, 643))
    }

    @Test
    fun oldProviderAfterAnAppUpdateNeedsRestart() {
        assertEquals(ModuleActivationState.ScopeRestartRequired, ModuleActivationState.resolve(true, true, 642, 643))
    }

    @Test
    fun activeProviderDoesNotDependOnAppFrameworkServiceTiming() {
        assertEquals(ModuleActivationState.Active, ModuleActivationState.resolve(false, true, 643, 643))
    }

    @Test
    fun noConnectionsIsInactiveAndInvalidProviderVersionIsNotActive() {
        assertEquals(ModuleActivationState.NotActive, ModuleActivationState.resolve(false, false, 0, 643))
        assertEquals(ModuleActivationState.ScopeRestartRequired, ModuleActivationState.resolve(true, true, 0, 643))
    }
}
