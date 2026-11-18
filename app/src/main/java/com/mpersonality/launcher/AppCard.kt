package com.mpersonality.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Card que agrupa apps lado a lado. Se tiver mais apps do que cabem, rola para o lado.
 * [interactive] = false para a prévia no editor (não aceita toques nem arrastes).
 */
@Composable
fun AppCard(
    launcher: Launcher,
    card: CardItem,
    modifier: Modifier,
    iconSize: Dp,
    interactive: Boolean = true,
) {
    val s = card.style
    val shape = RoundedCornerShape(s.cornerRadius.dp)
    val tint = Color(s.backgroundColor).copy(alpha = s.backgroundAlpha)
    val drag = launcher.dragSource
    val faded = drag is DragSource.Card && drag.cardId == card.id

    val base = if (interactive) modifier.hitTarget(launcher.registry, HitTarget.Card(card.id)) else modifier
    val background = when (s.background) {
        CardBackground.TRANSPARENT -> Modifier
        CardBackground.SOLID -> Modifier.background(tint)
        CardBackground.BLUR -> {
            val hasBlur = launcher.wallpaper.blurred != null
            Modifier
                .wallpaperBehind(launcher.wallpaper, blurred = true)
                .background(if (hasBlur) tint else tint.copy(alpha = max(tint.alpha, 0.35f)))
        }
    }
    val border = if (s.showBorder) Modifier.border(s.borderWidth.dp, Color(s.borderColor), shape) else Modifier

    Box(
        base
            .alpha(if (faded) 0.3f else 1f)
            .clip(shape)
            .then(background)
            .then(border),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (s.showTitle && s.title.isNotBlank()) {
                Text(
                    s.title,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(s.spacing.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (card.apps.isEmpty()) {
                    Text(
                        "Arraste apps para cá",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                    )
                }
                for (appKey in card.apps) {
                    key(appKey) {
                        val app = launcher.apps.byKey[appKey]
                        if (app != null) {
                            val beingDragged = drag is DragSource.InCard && drag.cardId == card.id && drag.app == appKey
                            val iconModifier = if (interactive) {
                                Modifier
                                    .hitTarget(launcher.registry, HitTarget.CardApp(card.id, appKey))
                                    .clickable { launcher.launch(app) }
                            } else {
                                Modifier
                            }
                            AppIcon(app, iconSize, iconModifier.alpha(if (beingDragged) 0.3f else 1f))
                        }
                    }
                }
            }
        }
    }
}
