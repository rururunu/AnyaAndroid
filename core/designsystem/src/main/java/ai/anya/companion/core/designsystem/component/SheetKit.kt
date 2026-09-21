package ai.anya.companion.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.anya.companion.core.designsystem.haptic.rememberAnyaHaptics
import ai.anya.companion.core.designsystem.theme.AnyaSpace

/**
 * Shared building blocks for Apple-HIG-style bottom sheets: a centered title block, opaque
 * grouped cards, hairline separators, and text action/info rows. Every sheet in the app
 * composes these instead of rolling its own card/row styling, so the action-sheet look
 * stays identical everywhere.
 */

/**
 * Centered sheet header: a compact semibold title with an optional secondary line under it,
 * the way an iOS sheet leads with its title block.
 */
@Composable
public fun AnyaSheetHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AnyaSpace.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Opaque rounded group that the rows of a sheet sit in (iOS grouped-list card). */
@Composable
public fun AnyaSheetCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AnyaSpace.CardRadius))
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

/** 0.5dp separator, the hairline weight iOS uses between grouped rows. */
@Composable
public fun AnyaSheetHairline(
    modifier: Modifier = Modifier,
    insetStart: Dp = 0.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = insetStart)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * One row of a sheet's action group: a centered label with iOS row height. Full-width
 * hairline underneath when [showDivider] is set (action-sheet separators are not inset).
 * Destructive actions pass [AnyaColors.Danger][ai.anya.companion.core.designsystem.theme.AnyaColors.Danger]
 * as [color]; cancel/close rows pass [emphasized] = true.
 */
@Composable
public fun AnyaSheetActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    emphasized: Boolean = false,
    showDivider: Boolean = false,
) {
    val haptics = rememberAnyaHaptics()
    val resolvedColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable {
                    haptics.buttonPress()
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = resolvedColor,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
        if (showDivider) {
            AnyaSheetHairline()
        }
    }
}

/**
 * One row of a sheet's grouped list: label on the leading edge, value (or a trailing slot,
 * for status chips) on the trailing edge. The separator starts where the label does, the
 * way iOS insets its list separators. Pass a [subtitle] for two-line rows (a title over a
 * secondary line, as in device lists), [emphasized] to semibold the title, and [onClick]
 * to make the row selectable (a selection row plays the light linear switch haptic).
 */
@Composable
public fun AnyaSheetInfoRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String = "",
    subtitle: String? = null,
    showDivider: Boolean = true,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberAnyaHaptics()
    val rowModifier = if (subtitle != null) {
        Modifier
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = AnyaSpace.Lg, vertical = AnyaSpace.Sm)
    } else {
        Modifier
            .height(44.dp)
            .padding(horizontal = AnyaSpace.Lg)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable {
                            haptics.linearTick()
                            onClick()
                        }
                    } else {
                        Modifier
                    },
                )
                .then(rowModifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (subtitle != null) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            when {
                trailing != null -> trailing()
                value.isNotEmpty() -> Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (showDivider) {
            AnyaSheetHairline(insetStart = AnyaSpace.Lg)
        }
    }
}
