package org.cutwire.drift.core.model

import java.util.UUID

enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT
}

enum class TextAnimation {
    NONE,
    FADE,
    POP,
    SLIDE_UP
}

data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "Your Text Here",
    val fontSizeSp: Float = 28f,
    val textColor: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0x00000000,
    val strokeColor: Long = 0xFF000000,
    val strokeWidthPx: Float = 2f,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val animation: TextAnimation = TextAnimation.NONE
)
