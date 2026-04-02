package me.gm.cleaner.plugin.ui.screens.templating

import android.content.Context
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.model.Template

fun hookOperationLabel(context: Context, value: String): String = when (value) {
    "query" -> context.getString(R.string.hook_operation_query_label)
    "insert" -> context.getString(R.string.hook_operation_insert_label)
    else -> value
}

fun mediaTypeLabel(context: Context, value: Int): String = when (value) {
    4 -> context.getString(R.string.media_type_playlist_label)
    5 -> context.getString(R.string.media_type_subtitle_label)
    2 -> context.getString(R.string.audio)
    3 -> context.getString(R.string.video)
    1 -> context.getString(R.string.image)
    6 -> context.getString(R.string.media_type_document_label)
    0 -> context.getString(R.string.media_type_none_label)
    else -> value.toString()
}

fun templateOperationSummary(context: Context, template: Template): String =
    context.getString(
        R.string.info_item,
        context.getString(R.string.hook_operation_title),
        template.hookOperation.joinToString(" / ") { hookOperationLabel(context, it) },
    )

fun templateMediaTypeSummary(context: Context, template: Template): String? =
    template.permittedMediaTypes
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(" / ") { mediaTypeLabel(context, it) }
        ?.let {
            context.getString(
                R.string.info_item,
                context.getString(R.string.permitted_media_types_title),
                it,
            )
        }

fun templateFilterPathSummary(context: Context, template: Template): String? =
    template.filterPath
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            context.getString(
                R.string.info_item,
                context.getString(R.string.filter_path_title),
                it.joinToString(" / "),
            )
        }
