package me.gm.cleaner.plugin.ui.screens.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import me.gm.cleaner.plugin.data.github.ReadmeRepository
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val repository: ReadmeRepository,
) : ViewModel() {
    private var rawReadme: Result<String> = Result.failure(UninitializedPropertyAccessException())

    fun getRawReadmeAsync() = viewModelScope.async {
        if (rawReadme.isFailure) {
            withContext(Dispatchers.IO) {
                rawReadme = repository.getRawReadme(Locale.getDefault().toLanguageTag())
            }
        }
        rawReadme
    }
}
