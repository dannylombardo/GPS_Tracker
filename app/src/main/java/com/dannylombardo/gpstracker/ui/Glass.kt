package com.dannylombardo.gpstracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.dannylombardo.gpstracker.ui.theme.LocalDarkTheme
import com.dannylombardo.gpstracker.ui.theme.apple
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/*
 * The "liquid glass" kit. Bars and the tab bar are real frosted glass: they blur whatever
 * scrolls under them (Android 12 and up; older phones get a near-solid tint instead).
 * Cards are translucent with a bright top edge, over a softly tinted page background, so
 * they read as glass panes without the cost of blurring every row of a list.
 */

/** Height of a page's top bar, below the status bar. */
internal val TopBarHeight = 56.dp

/** Space the floating tab bar takes at the bottom, above the navigation bar. */
internal val TabBarSpace = 96.dp

/** The frosted material behind bars: a strong blur with a light wash of the card colour. */
@Composable
internal fun glassBarStyle(): HazeStyle {
    val apple = MaterialTheme.apple
    val dark = LocalDarkTheme.current
    return HazeStyle(
        backgroundColor = apple.groupedBackground,
        tints = listOf(HazeTint(apple.card.copy(alpha = if (dark) 0.55f else 0.62f))),
        blurRadius = 28.dp,
        noiseFactor = 0f,
        fallbackTint = HazeTint(apple.card.copy(alpha = 0.95f)),
    )
}

/** The fill of a translucent glass card. */
@Composable
internal fun glassFill(): Color {
    val dark = LocalDarkTheme.current
    return MaterialTheme.apple.card.copy(alpha = if (dark) 0.78f else 0.82f)
}

/** The light catching a pane of glass: bright along the top edge, fading out down the sides. */
@Composable
internal fun glassEdge(strong: Boolean = false): BorderStroke {
    val dark = LocalDarkTheme.current
    val top = when {
        dark -> if (strong) 0.30f else 0.16f
        else -> if (strong) 1f else 0.9f
    }
    val bottom = if (dark) 0.04f else 0.35f
    return BorderStroke(
        0.8.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = top), Color.White.copy(alpha = bottom))),
    )
}

/** A translucent glass card. Clickable when [onClick] is given, with a gentle press-in. */
@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(stiffness = 600f), label = "press")
    Column(
        modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(glassFill())
            .border(glassEdge(), shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}

/** A bold coloured card with a glassy sheen, for each screen's headline numbers. */
@Composable
internal fun HeroCard(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    watermark: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = colors.last(), spotColor = colors.last())
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .drawBehind {
                // A soft highlight in the top corner, like light on curved glass.
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * 0.15f, 0f),
                        radius = size.width * 0.8f,
                    ),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.15f, 0f),
                )
            }
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f))),
                ),
                shape,
            ),
    ) {
        if (watermark != null) {
            // A big faint symbol tucked into the corner, for a bit of character.
            Icon(
                watermark,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.09f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 36.dp, y = 36.dp)
                    .size(190.dp),
            )
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

/** The page background: iOS grouped grey (black in dark mode) with a faint wash of colour at the top for the glass to pick up. */
@Composable
internal fun AppBackground(modifier: Modifier = Modifier) {
    val apple = MaterialTheme.apple
    val dark = LocalDarkTheme.current
    val blue = apple.blue.copy(alpha = if (dark) 0.22f else 0.14f)
    val teal = apple.teal.copy(alpha = if (dark) 0.14f else 0.12f)
    val indigo = apple.indigo.copy(alpha = if (dark) 0.16f else 0.08f)
    Box(
        modifier
            .fillMaxSize()
            .background(apple.groupedBackground)
            .drawBehind {
                val r = size.width * 0.9f
                drawCircle(Brush.radialGradient(listOf(blue, Color.Transparent), Offset(0f, 0f), r), r, Offset(0f, 0f))
                drawCircle(
                    Brush.radialGradient(listOf(teal, Color.Transparent), Offset(size.width, size.height * 0.12f), r),
                    r,
                    Offset(size.width, size.height * 0.12f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(indigo, Color.Transparent), Offset(size.width * 0.3f, size.height), r),
                    r,
                    Offset(size.width * 0.3f, size.height),
                )
            },
    )
}

/** True once a list has scrolled past its first few pixels. */
@Composable
internal fun LazyListState.scrolledState(thresholdPx: Int = 8): State<Boolean> =
    remember(this) { derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > thresholdPx } }

/** True once a list has scrolled its large title up under the bar. */
@Composable
internal fun LazyListState.largeTitleGoneState(): State<Boolean> {
    val threshold = with(LocalDensity.current) { 44.dp.roundToPx() }
    return remember(this) { derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > threshold } }
}

/**
 * A page with a frosted top bar over its content. The bar is clear until the content
 * scrolls under it ([scrolled]), then frosts over. [showTitle] puts the title in the bar
 * (tab pages hide it while their large title is still on screen). [content] gets the
 * padding that keeps it clear of the bars at rest. [bottomBar] floats over the bottom,
 * frosted the same way.
 */
@Composable
internal fun PageScaffold(
    title: String,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomSpace: Dp = 0.dp,
    bottomBar: (@Composable BoxScope.(HazeState) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val hazeState = remember { HazeState() }
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val padding = PaddingValues(top = status + TopBarHeight, bottom = nav + bottomSpace)
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().haze(hazeState)) {
            AppBackground()
            content(padding)
        }
        GlassTopBar(
            title = title,
            showTitle = showTitle,
            frosted = scrolled,
            hazeState = hazeState,
            onBack = onBack,
            actions = actions,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        bottomBar?.invoke(this, hazeState)
    }
}

@Composable
private fun GlassTopBar(
    title: String,
    showTitle: Boolean,
    frosted: Boolean,
    hazeState: HazeState,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass by animateFloatAsState(if (frosted) 1f else 0f, label = "bar")
    val titleAlpha by animateFloatAsState(if (showTitle) 1f else 0f, label = "title")
    val style = glassBarStyle()
    val separator = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .fillMaxWidth()
            .hazeChild(hazeState, style) { alpha = glass }
            .drawBehind {
                drawLine(
                    separator.copy(alpha = 0.6f * glass),
                    Offset(0f, size.height),
                    Offset(size.width, size.height),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
            .statusBarsPadding()
            .height(TopBarHeight)
            .padding(horizontal = 12.dp),
    ) {
        if (onBack != null) {
            GlassIconButton(
                Icons.Rounded.ChevronLeft,
                contentDescription = "Back",
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
                iconSize = 28.dp,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 104.dp)
                .alpha(titleAlpha),
        )
        Row(
            Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** A round glass button with an icon, for bars and over maps. */
@Composable
internal fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
    badge: Int = 0,
) {
    Box(modifier) {
        Box(
            Modifier
                .size(size)
                .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(CircleShape)
                .background(glassFill())
                .border(glassEdge(strong = true), CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else tint.copy(alpha = 0.3f),
                modifier = Modifier.size(iconSize),
            )
        }
        if (badge > 0) {
            Text(
                badge.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .widthIn(min = 18.dp)
                    .background(MaterialTheme.apple.red, CircleShape)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

/** A glass capsule with a word in it, like "Restore" or "Empty", for bars. */
@Composable
internal fun GlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier
            .height(40.dp)
            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(glassFill())
            .border(glassEdge(strong = true), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

/** A screen's big title, iOS style, with an optional line under it and buttons beside it. */
@Composable
internal fun LargeTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
        if (subtitle != null) {
            Text(
                subtitle.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** One of the floating tab bar's tabs. */
internal class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** The floating glass tab bar: a frosted capsule above the bottom edge, with a lens over the current tab. */
@Composable
internal fun GlassTabBar(
    items: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val itemWidth = 104.dp
    val lensOffset by animateDpAsState(itemWidth * selected, spring(dampingRatio = 0.75f, stiffness = 500f), label = "lens")
    val shape = CircleShape
    val dark = LocalDarkTheme.current
    Box(
        modifier
            .padding(bottom = 14.dp)
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(shape)
            .hazeChild(hazeState, glassBarStyle())
            .border(glassEdge(strong = true), shape)
            .padding(6.dp),
    ) {
        // The lens: a brighter pane of glass sliding under the selected tab.
        Box(
            Modifier
                .offset(x = lensOffset)
                .width(itemWidth)
                .height(56.dp)
                .clip(shape)
                .background(if (dark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                .border(glassEdge(), shape),
        )
        Row {
            items.forEachIndexed { index, item ->
                val isSelected = index == selected
                val tint by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    label = "tab",
                )
                Column(
                    Modifier
                        .width(itemWidth)
                        .height(56.dp)
                        .clip(shape)
                        .clickable(role = Role.Tab, onClickLabel = item.label) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (isSelected) item.selectedIcon else item.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
        }
    }
}

/** A big filled capsule button, the main action on a card or page. */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = Color.White,
) {
    CapsuleButton(text, onClick, modifier, icon, enabled, container, content)
}

/** A capsule tinted with the accent colour, for second-tier actions. */
@Composable
internal fun TintedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    CapsuleButton(text, onClick, modifier, icon, enabled, color.copy(alpha = 0.14f), color)
}

@Composable
private fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: ImageVector?,
    enabled: Boolean,
    container: Color,
    content: Color,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = 700f), label = "press")
    Row(
        modifier
            .height(50.dp)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = content),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

/** An iOS-style switch: green when on, white knob, no outline. */
@Composable
internal fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val apple = MaterialTheme.apple
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        thumbContent = null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = apple.green,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = apple.fill,
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedTrackColor = apple.green.copy(alpha = 0.4f),
            disabledCheckedThumbColor = Color.White,
            disabledUncheckedTrackColor = apple.fill.copy(alpha = 0.5f),
            disabledUncheckedThumbColor = Color.White.copy(alpha = 0.8f),
            disabledUncheckedBorderColor = Color.Transparent,
            disabledCheckedBorderColor = Color.Transparent,
        ),
    )
}

/** An iOS segmented control: a grey track with a white knob that slides to the picked option. */
@Composable
internal fun SegmentedControl(
    options: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
) {
    val apple = MaterialTheme.apple
    val dark = LocalDarkTheme.current
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val segment = with(density) { (widthPx / options.size.coerceAtLeast(1)).toDp() }
    val knobOffset by animateDpAsState(segment * (selected ?: 0), spring(dampingRatio = 0.8f, stiffness = 600f), label = "knob")
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(apple.fill)
            .onSizeChanged { widthPx = it.width - with(density) { 6.dp.roundToPx() } }
            .padding(3.dp),
    ) {
        if (selected != null && widthPx > 0) {
            Box(
                Modifier
                    .offset(x = knobOffset)
                    .width(segment)
                    .fillMaxHeight()
                    .shadow(3.dp, RoundedCornerShape(9.dp))
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (dark) Color(0xFF636366) else Color.White),
            )
        }
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(role = Role.RadioButton) { onSelect(index) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icons?.getOrNull(index)?.let {
                        Icon(it, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        label,
                        style = if (index == selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A section title in a list, with an optional "See All" style link on the right. */
@Composable
internal fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 14.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        if (action != null && onAction != null) {
            Row(
                Modifier
                    .padding(start = 8.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** A grouped list section, iOS Settings style: a small caps header, rounded rows, a footnote. */
@Composable
internal fun GroupedSection(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                header.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        GlassCard(contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(18.dp), content = content)
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

/** One row of a grouped section: a coloured icon tile, a title and detail, and a switch, value or chevron on the right. */
@Composable
internal fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(30.dp).background(iconColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.apple.tertiaryLabel,
                modifier = Modifier.padding(start = 4.dp).size(24.dp),
            )
        }
    }
}

/** The hairline between grouped rows, inset past the icon like iOS. */
@Composable
internal fun RowDivider(inset: Dp = 60.dp) {
    Box(
        Modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
