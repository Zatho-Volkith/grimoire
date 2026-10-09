package dev.grimoire.ui.design

import androidx.compose.ui.unit.dp

/** Spacing scale. Generous between cards, tight inside them. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner radii: fields and rows vs. cards and dialogs. Pills use a 50% shape. */
object Radius {
    val field = 8.dp
    val card = 12.dp
}

/** Fixed layout widths so content never stretches across a wide window. */
object Widths {
    val editor = 960.dp
    val listPane = 280.dp
    val sidebar = 200.dp
    val picker = 640.dp
}
