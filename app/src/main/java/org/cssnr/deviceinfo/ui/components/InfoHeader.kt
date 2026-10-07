package org.cssnr.deviceinfo.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.cssnr.deviceinfo.R
import org.cssnr.deviceinfo.ui.theme.DeviceInfoTheme

/**
 * The banner above the groups: which Android this is, as one line carrying the release name and the
 * API level, the second de-emphasized against the first.
 *
 * The two facts are already rows further down under Operating System. They are repeated here because
 * they are the two a reader looks up first and the banner is what they see without scrolling; the
 * rows keep them in the copied report, so nothing is lost either way.
 *
 * Read off `Build` here rather than handed in, the way [org.cssnr.deviceinfo.ui.theme.DeviceInfoTheme]
 * and [org.cssnr.deviceinfo.ui.screens.SettingsScreen] read `Build` for themselves: this is chrome,
 * not report content, so it produces no row and takes no part in a copy or a share. Both are
 * `static final`, so re-reading them on a recomposition costs nothing.
 *
 * Drawn in `primaryContainer` rather than the `surfaceContainer` [InfoGroup] uses, so the header
 * reads as chrome and the groups read as data. Corner radius and side margins match [InfoGroup] so
 * the two line up down the screen.
 *
 * Clickable: a tap opens the usage tips dialog, since a banner that carries no data of its own has a
 * better use as a discarded help affordance than as decoration. The ripple is clipped to the same
 * rounded shape as the container so the hit feedback stays inside the tile.
 */
@Composable
fun InfoHeader(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_MARGIN)
            .clip(RoundedCornerShape(HEADER_CORNER_RADIUS))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = HEADER_CONTENT_PADDING, vertical = HEADER_CONTENT_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.md_android_48px),
            contentDescription = null,
            // The drawable paints white, so the tint is what gives it the container's foreground
            // colour rather than leaving a white blob on a dark surface in dark mode.
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(HEADER_ICON_SIZE),
        )
        Spacer(modifier = Modifier.width(HEADER_ICON_GAP))
        Text(
            text = stringResource(R.string.info_header_android, Build.VERSION.RELEASE.withoutTrailingZeros()),
            // headlineSmall, 24sp, over the titleMedium 16sp the two-line version used. Material
            // documents headlineSmall as the smallest headline, reserved for short important text,
            // which is what a one-line version banner is; it also still reads below the app bar's
            // own titleLarge rather than competing with it.
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(modifier = Modifier.width(HEADER_TEXT_GAP))
        Text(
            text = stringResource(R.string.info_header_api, Build.VERSION.SDK_INT),
            // The same style as the release name, so the Row's CenterVertically is all that places
            // these. Nothing here opts into baseline alignment or carries a weight: the pair reads
            // as one centered run of text, which is what it looked like before either was added.
            style = MaterialTheme.typography.headlineSmall,
            // The muted half of the pair. Material's own banner guidance de-emphasizes with the
            // "on primary fixed variant" role, which AndroidX's ColorScheme does not carry, so
            // this is the container's own content color at reduced alpha instead. It stays the same
            // hue as the text beside it, which onSurfaceVariant would not. Material guarantees
            // contrast for any "on-" role at full alpha; dropping alpha spends that guarantee,
            // which is inherent to asking for muted text rather than a separate color role.
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = MUTED_TEXT_ALPHA),
        )
    }
}

private val HEADER_CORNER_RADIUS = 20.dp
private val HEADER_MARGIN = 16.dp
private val HEADER_CONTENT_PADDING = 16.dp
private val HEADER_ICON_SIZE = 48.dp
private val HEADER_ICON_GAP = 16.dp

/** The space between the release name and the API level it qualifies. */
private val HEADER_TEXT_GAP = 4.dp

/** How far back the muted API level's text sits from the release name beside it. */
private const val MUTED_TEXT_ALPHA = 0.7f

/**
 * The release name with trailing zero components dropped, so "8.0.0" reads as "8".
 *
 * Banner only, and deliberately not what the collector reports: the Version row under Operating
 * System, and every report copied or shared from this app, keep Google's own "8.0.0". A banner is a
 * summary and "Android 8" is how that release is marketed, so shortening it here reads better and
 * buys the width this line needs on a narrow screen. The two spellings are not interchangeable, so
 * this must not be applied to anything that gets copied.
 *
 * Only whole "0" components go, never digits inside one, so "8.1.0" shortens to "8.1" and "10"
 * stays "10" rather than becoming "1". At least one component always survives.
 */
private fun String.withoutTrailingZeros(): String {
    val components = split(".")
    var end = components.size
    while (end > 1 && components[end - 1] == "0") {
        end--
    }
    return components.take(end).joinToString(".")
}

@Preview(showBackground = true)
@Composable
fun InfoHeaderPreview() {
    DeviceInfoTheme {
        InfoHeader()
    }
}
