package org.cutwire.drift.core.model

import java.util.UUID

enum class EffectType(val displayName: String, val category: String) {
    BRIGHTNESS("Brightness", "Color"),
    CONTRAST("Contrast", "Color"),
    SATURATION("Saturation", "Color"),
    EXPOSURE("Exposure", "Color"),
    TEMPERATURE("Temperature", "Color"),
    TINT("Tint", "Color"),
    GRAYSCALE("Grayscale", "Color"),
    SEPIA("Sepia", "Color"),
    VIGNETTE("Vignette", "Stylize"),
    BLUR("Gaussian Blur", "Blur"),
    SHARPEN("Sharpen", "Stylize"),
    FILM_GRAIN("Film Grain", "Stylize"),
    CHROMA_KEY("Chroma Key", "Keying")
}

data class EffectParameter(
    val name: String,
    val value: Float,
    val minValue: Float = -1.0f,
    val maxValue: Float = 1.0f,
    val defaultValue: Float = 0.0f
)

data class EffectInstance(
    val id: String = UUID.randomUUID().toString(),
    val type: EffectType,
    val isEnabled: Boolean = true,
    val parameters: Map<String, Float> = emptyMap()
)
