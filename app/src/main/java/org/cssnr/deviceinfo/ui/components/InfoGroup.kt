package org.cssnr.deviceinfo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.cssnr.deviceinfo.R
import org.cssnr.deviceinfo.data.InfoCategory
import org.cssnr.deviceinfo.data.InfoItem
import org.cssnr.deviceinfo.data.InfoValue

/**
 * A row's value as text to draw, resolving a localized resource where that is what the value is.
 *
 * Lives here rather than on [InfoValue] so the data layer stays free of Compose; there is one of
 * these per screen that reads a row, which is two, and both must agree.
 */
@Composable
fun InfoValue.text(): String = when (this) {
    is InfoValue.Text -> value
    is InfoValue.Resource -> stringResource(res)
}

/**
 * A titled group of [InfoItem]s, drawn the way [SettingsGroup] draws its tiles: one rounded
 * surface, hairline gaps instead of dividers, and only the first and last rows rounding the outer
 * corners.
 *
 * The header's two icons act on the whole category as a `Label: Value` report: share it, or copy
 * it. Copy is last so it sits in the far right corner, which is the same order the app bar uses.
 *
 * A row is pressable in two ways. A tap copies the row's value on its own, which is what you want
 * when pasting it into a field. A long press enters selection mode instead, where a tap toggles the
 * row and a top-bar action copies or shares the whole selection as labelled lines.
 */
@Composable
fun InfoGroup(
    category: InfoCategory,
    selectedIds: Set<String>,
    onItemClick: (InfoItem) -> Unit,
    onItemLongClick: (InfoItem) -> Unit,
    onCategoryCopy: (InfoCategory) -> Unit,
    onCategoryShare: (InfoCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = category.items
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GROUP_MARGIN),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(category.titleRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = GROUP_TITLE_START_PADDING,
                        end = ROW_PADDING,
                        //top = GROUP_TITLE_TOP_PADDING,
                        //bottom = GROUP_TITLE_BOTTOM_PADDING,
                    ),
            )
            IconButton(
                onClick = { onCategoryShare(category) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(
                        R.string.info_row_share_label,
                        stringResource(category.titleRes),
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { onCategoryCopy(category) },
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(
                        R.string.info_row_copy_label,
                        stringResource(category.titleRes),
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(ROW_GAP))
            }
            InfoRow(
                item = item,
                shape = rowShape(index = index, count = items.size),
                selected = item.id in selectedIds,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
            )
        }
    }
}

@Composable
private fun InfoRow(
    item: InfoItem,
    shape: Shape,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(item.labelRes)
    val value = item.value.text()
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val onBackground = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val onBackgroundVariant = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .combinedClickable(
                onClickLabel = stringResource(R.string.info_row_copy_label, label),
                onClick = onClick,
                onLongClickLabel = stringResource(R.string.info_row_select_label, label),
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_MIN_HEIGHT)
                .padding(horizontal = ROW_PADDING, vertical = ROW_VERTICAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = onBackground,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(ROW_VALUE_GAP))
            // Values like a fingerprint or a list of ABIs are long and wrap, so the value takes the
            // trailing half on its own line rather than being truncated to fit beside the label.
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = onBackgroundVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(VALUE_WEIGHT),
            )
        }
    }
}

private fun rowShape(index: Int, count: Int): Shape {
    val top = if (index == 0) GROUP_CORNER_RADIUS else ROW_CORNER_RADIUS
    val bottom = if (index == count - 1) GROUP_CORNER_RADIUS else ROW_CORNER_RADIUS
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

private val GROUP_CORNER_RADIUS = 20.dp
private val ROW_CORNER_RADIUS = 2.dp
private val ROW_GAP = 2.dp
private val ROW_PADDING = 16.dp
private val ROW_VERTICAL_PADDING = 16.dp
private val ROW_MIN_HEIGHT = 56.dp
private val ROW_VALUE_GAP = 16.dp
private val GROUP_MARGIN = 16.dp
private val GROUP_TITLE_START_PADDING = 8.dp
//private val GROUP_TITLE_TOP_PADDING = 26.dp
//private val GROUP_TITLE_BOTTOM_PADDING = 0.dp

/**
 * Keeps the header's copy button clear of the first row. The title's own bottom padding does not
 * apply to the icon, which is why this is separate.
 */

/** The label gets a little under half the row so a wrapping value stays the shorter side. */
private const val VALUE_WEIGHT = 1f