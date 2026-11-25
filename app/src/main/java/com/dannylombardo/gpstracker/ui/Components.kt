package com.dannylombardo.gpstracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dannylombardo.gpstracker.ui.theme.tabular

/** A screen's big title, with an optional line under it. */
@Composable
internal fun ScreenHeader(title: String, subtitle: String? = null) {
    LargeTitle(title, subtitle)
}

/** A small grey caps heading over a group of rows, like iOS's grouped lists. */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, top = 12.dp),
    )
}

/** An icon in a tinted circle. */
@Composable
internal fun IconBadge(
    icon: ImageVector,
    container: Color,
    content: Color,
    size: Dp = 40.dp,
) {
    Box(
        Modifier.size(size).background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.55f))
    }
}

/** A big number with its unit set smaller beside it, like "123.4 km". */
@Composable
internal fun BigNumber(
    value: String,
    unit: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.displayMedium,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = style.tabular(), color = color)
        Text(
            unit,
            style = MaterialTheme.typography.titleLarge,
            color = color.copy(alpha = 0.75f),
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
        )
    }
}

/** One figure in a grid of stats: icon, value, and what it is. */
@Composable
internal fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    GlassCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.size(30.dp).background(accent.copy(alpha = 0.14f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Text(value, style = MaterialTheme.typography.titleLarge.tabular())
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A row of equally wide stat tiles; give each one Modifier.weight(1f). */
@Composable
internal fun StatRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** A small value and label on a coloured hero card. */
@Composable
internal fun HeroStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .border(0.5.dp, color.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.8f), maxLines = 1)
    }
}

/** Kilometres in big type that count up to their value, like a fitness app's rings. */
@Composable
internal fun CountingKm(
    meters: Double,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    modifier: Modifier = Modifier,
) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(meters) {
        shown.animateTo((meters / 1000).toFloat(), tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    BigNumber(String.format(Locale.getDefault(), "%.1f", shown.value), "km", color = color, style = style, modifier = modifier)
}

/** A round frosted arrow button for the coloured headline cards. */
@Composable
internal fun HeroArrow(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.2f else 0.08f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(22.dp),
        )
    }
}
