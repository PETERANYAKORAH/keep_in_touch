package app.kinkeep.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.kinkeep.R
import app.kinkeep.domain.ContactHealth
import app.kinkeep.notify.NotificationHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun provider(): WidgetDataProvider
}

/** Home-screen widget showing the three people most due for a call. Tap a name to open them. */
class OverdueWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .provider().topOverdue()
        provideContent { WidgetContent(context, items) }
    }
}

class OverdueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverdueWidget()
}

suspend fun refreshWidgets(context: Context) {
    runCatching { OverdueWidget().updateAll(context.applicationContext) }
}

@Composable
private fun WidgetContent(context: Context, items: List<ContactHealth>) {
    val background = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF10291F))
    val strong = ColorProvider(day = Color(0xFF0B3D2E), night = Color(0xFFA8D5BE))
    val soft = ColorProvider(day = Color(0xFF5E7268), night = Color(0xFFD0E4D8))

    Column(modifier = GlanceModifier.fillMaxSize().background(background).padding(12.dp)) {
        Text(
            text = context.getString(R.string.widget_title),
            style = TextStyle(color = strong, fontWeight = FontWeight.Bold, fontSize = 14.sp),
        )
        if (items.isEmpty()) {
            Text(
                text = context.getString(R.string.all_caught_up),
                modifier = GlanceModifier.padding(top = 8.dp),
                style = TextStyle(color = soft, fontSize = 13.sp),
            )
        } else {
            items.forEach { c ->
                val days = c.result.daysSinceLastContact
                val subtitle = if (days == null) context.getString(R.string.no_contact_yet)
                else context.resources.getQuantityString(R.plurals.last_spoke_days, days, days)
                Column(
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp)
                        .clickable(actionStartActivity(NotificationHelper.openIntent(context, c.id))),
                ) {
                    Text(text = c.name, style = TextStyle(color = strong, fontWeight = FontWeight.Medium, fontSize = 14.sp))
                    Text(text = subtitle, style = TextStyle(color = soft, fontSize = 12.sp))
                }
            }
        }
    }
}
