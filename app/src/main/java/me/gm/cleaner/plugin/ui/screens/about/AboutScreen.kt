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

package me.gm.cleaner.plugin.ui.screens.about

import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.ui.drawer.about.AboutViewModel

@Composable
fun AboutScreen(
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var readmeContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        try {
            val result = viewModel.getRawReadmeAsync().await()
            readmeContent = result.getOrThrow()
            isLoading = false
        } catch (e: Exception) {
            errorMessage = e.message ?: e::class.java.simpleName
            isLoading = false
        }
    }

    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .build()
    }

    Scaffold { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Text(
                    text = stringResource(R.string.about_load_error, errorMessage!!),
                    modifier = Modifier.padding(paddingValues).padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            readmeContent != null -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    factory = { ctx ->
                        ScrollView(ctx).apply {
                            isFillViewport = true
                            addView(
                                TextView(ctx).apply {
                                    setTextIsSelectable(true)
                                    setPadding(
                                        (16 * ctx.resources.displayMetrics.density).toInt(),
                                        (16 * ctx.resources.displayMetrics.density).toInt(),
                                        (16 * ctx.resources.displayMetrics.density).toInt(),
                                        (16 * ctx.resources.displayMetrics.density).toInt(),
                                    )
                                }
                            )
                        }
                    },
                    update = { scrollView ->
                        val textView = scrollView.getChildAt(0) as TextView
                        markwon.setMarkdown(textView, readmeContent!!)
                    },
                )
            }
        }
    }
}
