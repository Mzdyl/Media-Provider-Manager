package me.gm.cleaner.plugin.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable
    data object AppList : AppRoute

    @Serializable
    data object UsageRecord : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object About : AppRoute

    @Serializable
    data class AppDetail(
        val packageName: String,
        val label: String? = null,
    ) : AppRoute

    @Serializable
    data class CreateTemplate(
        val templateName: String? = null,
        val hookOperation: List<String>? = null,
        val packageNames: List<String>? = null,
        val permittedMediaTypes: List<String>? = null,
    ) : AppRoute

    @Serializable
    data object Templates : AppRoute
}

val topLevelDestinations = setOf(
    AppRoute.AppList,
    AppRoute.UsageRecord,
    AppRoute.Settings,
    AppRoute.About,
)
