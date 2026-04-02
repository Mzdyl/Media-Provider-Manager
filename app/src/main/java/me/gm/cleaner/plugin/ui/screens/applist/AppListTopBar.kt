package me.gm.cleaner.plugin.ui.screens.applist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.dao.RootPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListTopBar(
    searchQuery: String,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(stringResource(R.string.app_management)) },
        actions = {
            IconButton(onClick = onSearchToggle) {
                Icon(Icons.Default.Search, contentDescription = stringResource(android.R.string.search_go))
            }
            if (isSearching) {
                androidx.compose.material3.TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                )
            }
            IconButton(onClick = { showSortMenu = true }) {
                Icon(Icons.Default.Sort, contentDescription = "Sort")
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
            ) {
                val currentSort = RootPreferences.sortByFlowable.value
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_by_name)) },
                    onClick = {
                        RootPreferences.sortByFlowable.value = RootPreferences.SORT_BY_APP_NAME
                        showSortMenu = false
                    },
                    trailingIcon = {
                        Checkbox(
                            checked = currentSort == RootPreferences.SORT_BY_APP_NAME,
                            onCheckedChange = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_by_update_time)) },
                    onClick = {
                        RootPreferences.sortByFlowable.value = RootPreferences.SORT_BY_UPDATE_TIME
                        showSortMenu = false
                    },
                    trailingIcon = {
                        Checkbox(
                            checked = currentSort == RootPreferences.SORT_BY_UPDATE_TIME,
                            onCheckedChange = null,
                        )
                    },
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
