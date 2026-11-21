package app.kinkeep.ui.theme

import androidx.compose.ui.graphics.Color
import app.kinkeep.domain.HealthCategory

/** Always shown together with the category's text label, never colour alone. */
fun HealthCategory.color(): Color = when (this) {
    HealthCategory.THRIVING -> KinColors.Green700
    HealthCategory.HEALTHY -> KinColors.Green500
    HealthCategory.NEEDS_ATTENTION -> KinColors.AccentAmber
    HealthCategory.FADING -> KinColors.AccentRust
    HealthCategory.DORMANT -> KinColors.Slate
}
