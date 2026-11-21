package app.kinkeep.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.kinkeep.R
import app.kinkeep.domain.ContactHealth
import app.kinkeep.ui.theme.color

@Composable
fun ContactRow(item: ContactHealth, modifier: Modifier = Modifier) {
    val category = item.result.category
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text("${item.circle.label} · ${category.label}", style = MaterialTheme.typography.bodyMedium, color = category.color())
            val days = item.result.daysSinceLastContact
            Text(
                if (days == null) stringResource(R.string.no_contact_yet) else pluralStringResource(R.plurals.last_spoke_days, days, days),
                style = MaterialTheme.typography.bodySmall,
            )
            val missed = item.result.unreturnedMissedCalls
            if (missed > 0) {
                Text(pluralStringResource(R.plurals.missed_to_return, missed, missed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        val scoreLabel = stringResource(R.string.score_label, item.result.score)
        Text(
            item.result.score.toString(),
            modifier = Modifier.semantics { contentDescription = scoreLabel },
            style = MaterialTheme.typography.headlineSmall, color = category.color(),
        )
    }
}
