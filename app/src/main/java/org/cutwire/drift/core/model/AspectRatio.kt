package org.cutwire.drift.core.model

enum class AspectRatio(
    val label: String,
    val widthRatio: Int,
    val heightRatio: Int,
    val defaultWidth: Int,
    val defaultHeight: Int
) {
    RATIO_16_9("16:9 Landscape", 16, 9, 1920, 1080),
    RATIO_9_16("9:16 Portrait (Reels/Shorts)", 9, 16, 1080, 1920),
    RATIO_1_1("1:1 Square", 1, 1, 1080, 1080),
    RATIO_4_5("4:5 Social Post", 4, 5, 1080, 1350),
    RATIO_21_9("21:9 Ultrawide Cinema", 21, 9, 2560, 1080);

    val floatRatio: Float
        get() = widthRatio.toFloat() / heightRatio.toFloat()
}
