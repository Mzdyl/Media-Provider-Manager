package me.gm.cleaner.plugin.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import me.gm.cleaner.plugin.model.Template
import me.gm.cleaner.plugin.model.Templates
import me.gm.cleaner.plugin.ui.module.BinderViewModel
import me.gm.cleaner.plugin.ui.screens.about.AboutScreen
import me.gm.cleaner.plugin.ui.screens.appdetail.AppDetailScreen
import me.gm.cleaner.plugin.ui.screens.applist.AppListScreen
import me.gm.cleaner.plugin.ui.screens.createtemplate.CreateTemplateScreen
import me.gm.cleaner.plugin.ui.screens.settings.SettingsScreen
import me.gm.cleaner.plugin.ui.screens.templates.TemplatesScreen
import me.gm.cleaner.plugin.ui.screens.usagerecord.UsageRecordScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Any = AppRoute.AppList,
    binderViewModel: BinderViewModel = hiltViewModel(),
) {
    LaunchedEffect(binderViewModel) {
        while (!binderViewModel.pingBinder()) {
            kotlinx.coroutines.delay(500)
        }
        binderViewModel.readTemplateSp()
        binderViewModel.readRootSp()
    }

    val sparseArray by binderViewModel.remoteSpCacheLiveData.observeAsState()
    val templateJson = sparseArray?.get(me.gm.cleaner.plugin.model.SpIdentifiers.TEMPLATE_PREFERENCES)
    val rootSpJson = sparseArray?.get(me.gm.cleaner.plugin.model.SpIdentifiers.ROOT_PREFERENCES)
    val templateList: List<Template> = remember(templateJson) {
        runCatching { Templates(templateJson).values }.getOrDefault(emptyList())
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<AppRoute.AppList> {
            AppListScreen(
                binderViewModel = binderViewModel,
                onAppClick = { pkg, label ->
                    navController.navigate(AppRoute.AppDetail(packageName = pkg, label = label))
                },
            )
        }
        composable<AppRoute.AppDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.AppDetail>()
            AppDetailScreen(
                packageName = route.packageName,
                label = route.label,
                templates = templateList,
                onNavigateBack = { navController.popBackStack() },
                onCreateTemplate = {
                    navController.navigate(AppRoute.CreateTemplate())
                },
                binderViewModel = binderViewModel,
            )
        }
        composable<AppRoute.UsageRecord> {
            UsageRecordScreen(binderViewModel = binderViewModel)
        }
        composable<AppRoute.Settings> {
            val context = androidx.compose.ui.platform.LocalContext.current
            SettingsScreen(
                rootSpJson = rootSpJson,
                onTemplatesClick = { navController.navigate(AppRoute.Templates) },
                onBackup = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("MediaProviderManagerRules", templateJson)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, me.gm.cleaner.plugin.R.string.backup_ok, android.widget.Toast.LENGTH_SHORT).show()
                },
                onRootSettingsChange = { newJson ->
                    binderViewModel.writeRootSp(newJson)
                },
                onTemplateRestore = { newJson ->
                    binderViewModel.writeTemplateSp(newJson)
                },
            )
        }
        composable<AppRoute.Templates> {
            TemplatesScreen(
                templates = templateList,
                onNavigateBack = { navController.popBackStack() },
                onCreateTemplate = { navController.navigate(AppRoute.CreateTemplate()) },
                onEditTemplate = { template ->
                    navController.navigate(
                        AppRoute.CreateTemplate(
                            templateName = template.templateName,
                            hookOperation = template.hookOperation,
                            packageNames = template.applyToApp,
                            permittedMediaTypes = template.permittedMediaTypes?.map { it.toString() },
                        )
                    )
                },
            )
        }
        composable<AppRoute.CreateTemplate> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.CreateTemplate>()
            CreateTemplateScreen(
                templateName = route.templateName,
                hookOperation = route.hookOperation,
                packageNames = route.packageNames,
                permittedMediaTypes = route.permittedMediaTypes,
                onNavigateBack = { navController.popBackStack() },
                onSave = { navController.popBackStack() },
                binderViewModel = binderViewModel,
            )
        }
        composable<AppRoute.About> {
            AboutScreen()
        }
    }
}
