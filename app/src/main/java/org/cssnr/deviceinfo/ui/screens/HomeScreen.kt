package org.cssnr.deviceinfo.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.cssnr.deviceinfo.R
import org.cssnr.deviceinfo.data.DeviceInfoCollector
import org.cssnr.deviceinfo.data.InfoCategory
import org.cssnr.deviceinfo.data.InfoItem
import org.cssnr.deviceinfo.ui.components.InfoGroup
import org.cssnr.deviceinfo.ui.components.InfoHeader
import org.cssnr.deviceinfo.ui.components.text
import org.cssnr.deviceinfo.ui.theme.DeviceInfoTheme
import org.cssnr.deviceinfo.ui.viewmodel.HomeState
import org.cssnr.deviceinfo.ui.viewmodel.HomeViewModel

/**
 * Owns everything the Info screen needs that is not presentation: the collected snapshot and the
 * clipboard.
 *
 * Everything this screen can act on differs only in scope, and each scope has both a copy and a
 * share where sharing is meaningful:
 * - A single row: a tap copies that row, as a `Label: Value` line by default and as the bare value
 *   when the Copy Key and Value setting is off. There is no per-row share; a lone value is
 *   something you paste, not send.
 * - A category: the header's two buttons share or copy every row in that group.
 * - The whole screen, or the selection when one exists: the app bar's two buttons, which swap scope
 *   with the selection the same way Copy does.
 *
 * There is no refresh. Every value the collector reads is fixed for the life of the process, so
 * re-reading it would return the same strings; see [DeviceInfoCollector].
 *
 * Anything copying more than one row produces a `Label: Value` report, which is the useful form
 * when passing a set of facts to somebody else.
 *
 * No copy is confirmed in-app. Android 13 and higher shows a system confirmation with a preview of
 * the copied content, and the guidance is to remove any app toast or snackbar rather than duplicate
 * it: https://developer.android.com/develop/ui/views/touch-and-input/copy-paste
 *
 * A long press enters selection mode, where a tap toggles rows instead of copying, so a tap can
 * never quietly replace what the user is assembling. Back clears the selection rather than leaving
 * the screen.
 */
@Composable
fun HomeRoute(
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val copyKeyAndValue by viewModel.copyKeyAndValue.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Reports are built here, during composition, rather than inside the tap handlers: each line
    // needs its row's label, and reading resources from a coroutine would be a composition call in
    // the wrong place. A row whose value is a fixed word needs the same treatment, so every row's
    // displayed value is resolved to text up front and the handlers below only ever read strings.
    val values: Map<String, String> = state.allItems.associate { item ->
        item.id to item.value.text()
    }

    fun report(items: List<InfoItem>): String =
        items.joinToString(separator = REPORT_SEPARATOR) { item ->
            context.reportLine(item, values.getValue(item.id))
        }

    /** Copies [text] to the clipboard. */
    fun copyText(text: String) {
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText(CLIPBOARD_LABEL, text).toClipEntry())
        }
    }

    HomeScreen(
        state = state,
        onItemClick = { item ->
            // Once a selection exists, taps toggle rows rather than copying, so a tap can never
            // quietly replace what the user is assembling.
            if (state.isSelecting) {
                viewModel.toggleSelection(item.id)
            } else if (copyKeyAndValue) {
                // The same `Label: Value` line the copy and share buttons produce, so a single row
                // reads the same as it would inside a whole report.
                copyText(text = context.reportLine(item, values.getValue(item.id)))
            } else {
                // The bare value, which is what you want when pasting it into a field.
                copyText(text = values.getValue(item.id))
            }
        },
        onItemLongClick = { item -> viewModel.toggleSelection(item.id) },
        onCopyAll = { copyText(text = report(state.allItems)) },
        onCategoryCopy = { category -> copyText(text = report(category.items)) },
        onCategoryShare = { category -> context.shareText(report(category.items)) },
        onCopySelection = { copyText(text = report(state.selectedItems)) },
        onShareAll = { context.shareText(report(state.allItems)) },
        onShareSelection = { context.shareText(report(state.selectedItems)) },
        onClearSelection = viewModel::clearSelection,
        onNavigateToSettings = onNavigateToSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onItemClick: (InfoItem) -> Unit,
    onItemLongClick: (InfoItem) -> Unit,
    onCopyAll: () -> Unit,
    onCategoryCopy: (InfoCategory) -> Unit,
    onCategoryShare: (InfoCategory) -> Unit,
    onCopySelection: () -> Unit,
    onShareAll: () -> Unit,
    onShareSelection: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
) {
    var showTipsDialog by rememberSaveable { mutableStateOf(false) }
    // Composed unconditionally and toggled with `enabled`, which is what BackHandler documents:
    // a conditional call would move it in and out of composition and change handler ordering.
    BackHandler(enabled = state.isSelecting, onBack = onClearSelection)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isSelecting) {
                            stringResource(R.string.info_selected_count, state.selectedItems.size)
                        } else {
                            stringResource(R.string.app_name)
                        },
                    )
                },
                navigationIcon = {
                    // Only present while selecting, so the bar's left side stays empty otherwise.
                    if (state.isSelecting) {
                        IconButton(onClick = onClearSelection) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.info_action_clear),
                            )
                        }
                    }
                },
                actions = {
                    // Copy is always the last action, which puts it in the far right corner. Share
                    // sits beside it and follows the same scope as copy: the whole screen, or the
                    // selection when one exists.
                    IconButton(
                        onClick = if (state.isSelecting) onShareSelection else onShareAll,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.info_action_share),
                        )
                    }
                    IconButton(
                        onClick = if (state.isSelecting) onCopySelection else onCopyAll,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.info_action_copy),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // contentPadding rather than a trailing Spacer, which is what SettingsScreen uses: that
            // one scrolls a plain Column, where a Spacer is the whole job. A lazy list needs the
            // space as content padding so the final row still scrolls clear of the navigation bar
            // instead of sitting permanently short of it.
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + CONTENT_BOTTOM_PADDING,
            ),
        ) {
            item(key = HEADER_KEY) {
                InfoHeader(
                    onClick = { showTipsDialog = true },
                    modifier = Modifier.padding(
                        top = HEADER_TOP_PADDING,
                        bottom = HEADER_BOTTOM_PADDING,
                    ),
                )
            }
            items(state.categories, key = { category -> category.id }) { category ->
                InfoGroup(
                    category = category,
                    selectedIds = state.selectedIds,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    onCategoryCopy = onCategoryCopy,
                    onCategoryShare = onCategoryShare,
                )
            }
        }
    }

    if (showTipsDialog) {
        AlertDialog(
            onDismissRequest = { showTipsDialog = false },
            title = { Text(stringResource(R.string.info_usage_tips_title)) },
            text = { Text(stringResource(R.string.info_usage_tips_message)) },
            confirmButton = {
                TextButton(onClick = { showTipsDialog = false }) {
                    Text(stringResource(R.string.info_usage_tips_close))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTipsDialog = false
                        onNavigateToSettings()
                    }
                ) {
                    Text(stringResource(R.string.info_usage_tips_settings))
                }
            },
        )
    }
}

/** A single `Label: Value` line, for the copied and shared report. */
private fun Context.reportLine(item: InfoItem, value: String): String =
    getString(R.string.info_report_line, getString(item.labelRes), value)

private fun Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.info_share_title)))
}

/**
 * Breathing room below the last group so the final row never sits flush against the navigation bar.
 * Added on top of whatever bottom inset survives, rather than instead of it.
 */
private val CONTENT_BOTTOM_PADDING = 32.dp

/**
 * The banner's own spacing. The list's content padding already clears the app bar, so the top is on
 * top of that: the bar and the banner are both chrome, and without it the two run together. The
 * bottom gap is needed for a different reason, as [InfoGroup]'s title carries no vertical padding
 * of its own, so without it the first group title sits flush against the banner.
 */
private val HEADER_TOP_PADDING = 8.dp
private val HEADER_BOTTOM_PADDING = 16.dp

/**
 * The banner's key in the [LazyColumn]. A string rather than an object because the key exists only
 * to keep this one item from being reused as a category row, and a category key is a category id.
 */
private const val HEADER_KEY = "header"

/**
 * The `ClipData` label, which is user-visible: Android 13's copy confirmation shows the source
 * alongside the preview, so it identifies where the clip came from.
 */
private const val CLIPBOARD_LABEL = "Device Info"

private const val REPORT_SEPARATOR = "\n"

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    DeviceInfoTheme {
        HomeScreen(
            state = HomeState(categories = DeviceInfoCollector.collect(LocalContext.current)),
            onItemClick = {},
            onItemLongClick = {},
            onCopyAll = {},
            onCategoryCopy = {},
            onCategoryShare = {},
            onCopySelection = {},
            onShareAll = {},
            onShareSelection = {},
            onClearSelection = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenSelectingPreview() {
    DeviceInfoTheme {
        val categories = DeviceInfoCollector.collect(LocalContext.current)
        HomeScreen(
            state = HomeState(
                categories = categories,
                selectedIds = setOfNotNull(categories.firstOrNull()?.items?.firstOrNull()?.id),
            ),
            onItemClick = {},
            onItemLongClick = {},
            onCopyAll = {},
            onCategoryCopy = {},
            onCategoryShare = {},
            onCopySelection = {},
            onShareAll = {},
            onShareSelection = {},
            onClearSelection = {},
        )
    }
}