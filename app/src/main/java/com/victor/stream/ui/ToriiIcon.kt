package com.victor.stream.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Original torii-gate icon for the Anime section.
 * Drawn from scratch — intentionally not any franchise's logo.
 */
private var toriiCache: ImageVector? = null

val ToriiIcon: ImageVector
    get() {
        val cached = toriiCache
        if (cached != null) return cached
        val built = buildTorii()
        toriiCache = built
        return built
    }

private fun buildTorii(): ImageVector =
    ImageVector.Builder(
        name = "Torii",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Curved top lintel with upswept ends
        path(fill = SolidColor(Color.White)) {
            moveTo(2f, 6.6f)
            quadTo(12f, 3.6f, 22f, 6.6f)
            lineTo(21.3f, 8.8f)
            quadTo(12f, 6.2f, 2.7f, 8.8f)
            close()
        }
        // Second cross beam
        path(fill = SolidColor(Color.White)) {
            moveTo(5f, 10.8f)
            lineTo(19f, 10.8f)
            lineTo(19f, 12.6f)
            lineTo(5f, 12.6f)
            close()
        }
        // Left pillar (slight outward flare at the base)
        path(fill = SolidColor(Color.White)) {
            moveTo(5.7f, 8.4f)
            lineTo(7.7f, 8.4f)
            lineTo(8.3f, 21f)
            lineTo(5.1f, 21f)
            close()
        }
        // Right pillar
        path(fill = SolidColor(Color.White)) {
            moveTo(16.3f, 8.4f)
            lineTo(18.3f, 8.4f)
            lineTo(18.9f, 21f)
            lineTo(15.7f, 21f)
            close()
        }
    }.build()
