/*
 * Copyright 2021 Green Mushroom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.gm.cleaner.plugin.ui.module.appmanagement

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.gm.cleaner.plugin.dao.RootPreferences
import me.gm.cleaner.plugin.dao.RootPreferences.SORT_BY_APP_NAME
import me.gm.cleaner.plugin.dao.RootPreferences.SORT_BY_UPDATE_TIME
import me.gm.cleaner.plugin.ktx.getValue
import me.gm.cleaner.plugin.ktx.setValue
import me.gm.cleaner.plugin.ui.module.BinderViewModel
import me.gm.cleaner.plugin.util.collatorComparator

class AppListViewModel(
    application: Application,
    private val binderViewModel: BinderViewModel
) : AndroidViewModel(application) {
    private val _isSearchingFlow = MutableStateFlow(false)
    var isSearching: Boolean by _isSearchingFlow
    val isSearchingFlow: StateFlow<Boolean> = _isSearchingFlow.asStateFlow()
    private val _queryTextFlow = MutableStateFlow("")
    var queryText: String by _queryTextFlow
    val queryTextFlow: StateFlow<String> = _queryTextFlow.asStateFlow()
    val isLoading: Boolean
        get() = _appsFlow.value is AppListState.Loading
    private val _appsFlow = MutableStateFlow<AppListState>(AppListState.Loading(0))

    private val isHideSystemAppFlow = RootPreferences.isHideSystemAppFlowable.asFlow()
    private val sortByFlow = RootPreferences.sortByFlowable.asFlow()
    private val ruleCountFlow = RootPreferences.ruleCountFlowable.asFlow()
    private val filtersFlow =
        combine(
            _isSearchingFlow,
            _queryTextFlow,
            isHideSystemAppFlow,
            sortByFlow,
            ruleCountFlow,
        ) { isSearching, queryText, isHideSystemApp, sortBy, ruleCount ->
            AppListFilterState(
                isSearching = isSearching,
                queryText = queryText.trim(),
                isHideSystemApp = isHideSystemApp,
                sortBy = sortBy,
                ruleCount = ruleCount,
            )
        }

    val appsFlow =
        combine(_appsFlow, filtersFlow) { apps, filters ->
            when (apps) {
                is AppListState.Loading -> AppListState.Loading(
                    progress = apps.progress,
                    list = apps.list?.let {
                        transformList(it, filters)
                    },
                )
                is AppListState.Done -> AppListState.Done(
                    transformList(apps.list, filters)
                )
            }
        }

    private fun transformList(
        list: List<AppListModel>,
        filters: AppListFilterState,
    ): List<AppListModel> {
        var sequence = list.asSequence()
        if (filters.isHideSystemApp) {
            sequence = sequence.filter {
                val flags = it.packageInfo.applicationInfo?.flags ?: 0
                val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                flags and systemFlags == 0
            }
        }
        if (filters.isSearching && filters.queryText.isNotBlank()) {
            sequence = sequence.filter {
                it.label.contains(filters.queryText, true) ||
                    it.packageInfo.packageName.contains(filters.queryText, true)
            }
        }
        sequence = when (filters.sortBy) {
            SORT_BY_APP_NAME -> sequence.sortedWith(collatorComparator { it.label })
            SORT_BY_UPDATE_TIME -> sequence.sortedBy { -it.packageInfo.lastUpdateTime }
            else -> throw IllegalArgumentException()
        }
        if (filters.ruleCount) {
            sequence = sequence.sortedBy { -it.ruleCount }
        }
        return sequence.toList()
    }

    private fun currentVisibleList(): List<AppListModel>? = when (val state = _appsFlow.value) {
        is AppListState.Done -> state.list
        is AppListState.Loading -> state.list
    }

    fun load(
        l: AppListLoader.ProgressListener? = object : AppListLoader.ProgressListener {
            override fun onProgress(progress: Int) {
                _appsFlow.value = AppListState.Loading(progress, currentVisibleList())
            }
        }
    ) {
        viewModelScope.launch {
            _appsFlow.value = AppListState.Loading(0, currentVisibleList())
            while (!binderViewModel.pingBinder()) {
                kotlinx.coroutines.delay(500)
            }
            val list = AppListLoader().load(
                binderViewModel, getApplication<Application>().packageManager, l
            )
            _appsFlow.value = AppListState.Done(list)
        }
    }

    fun update() {
        viewModelScope.launch {
            if (!isLoading) {
                val list = AppListLoader().update(
                    (_appsFlow.value as AppListState.Done).list, binderViewModel
                )
                _appsFlow.value = AppListState.Loading(0, currentVisibleList())
                _appsFlow.value = AppListState.Done(list)
            }
        }
    }

    init {
        load()
    }

    companion object {
        fun provideFactory(
            application: Application, binderViewModel: BinderViewModel
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppListViewModel(application, binderViewModel) as T
            }
        }
    }
}

private data class AppListFilterState(
    val isSearching: Boolean,
    val queryText: String,
    val isHideSystemApp: Boolean,
    val sortBy: Int,
    val ruleCount: Boolean,
)

sealed class AppListState {
    data class Loading(
        val progress: Int,
        val list: List<AppListModel>? = null,
    ) : AppListState()

    data class Done(val list: List<AppListModel>) : AppListState()
}
