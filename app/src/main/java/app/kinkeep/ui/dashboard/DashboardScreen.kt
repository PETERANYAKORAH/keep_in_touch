package app.kinkeep.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kinkeep.R
import app.kinkeep.domain.ContactHealth
import app.kinkeep.domain.HealthCategory
import app.kinkeep.domain.ScoringEngine
import app.kinkeep.ui.common.ContactRow
import app.kinkeep.ui.home.UiModel
import app.kinkeep.ui.theme.color

@Composable
fun DashboardScreen(
    ui: UiModel,
    onOpen: (Long) -> Unit,
    onCall: (Long) -> Unit,
    onMessage: (Long) -> Unit,
) {
    val d = ui.dashboard
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(stringResource(R.string.dash_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }

        if (d.trackedCount == 0) {
            item { Text(stringResource(R.string.dash_empty), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp)) }
            return@LazyColumn
        }

        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HealthRing(d.overallScore, d.trackedCount) } }
        item { CategoryCounts(d.categoryCounts) }
        item { ThisWeek(d.peopleThisWeek, d.streakWeeks, d.weeklyGoal) }

        item { SectionTitle(R.string.section_reach_out) }
        if (d.reachOutToday.isEmpty()) {
            item { Text(stringResource(R.string.all_caught_up), style = MaterialTheme.typography.bodyLarge) }
        } else {
            contactItems(d.reachOutToday, onOpen, onCall, onMessage)
        }

        if (d.missedCallsToReturn.isNotEmpty()) {
            item { SectionTitle(R.string.section_missed) }
            contactItems(d.missedCallsToReturn, onOpen, onCall, onMessage)
        }

        item { SectionTitle(R.string.section_weekly) }
        item { WeeklyBars(d.weeklyActivity) }
        item { Text(stringResource(R.string.weekly_hint), style = MaterialTheme.typography.bodySmall) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.contactItems(
    list: List<ContactHealth>,
    onOpen: (Long) -> Unit,
    onCall: (Long) -> Unit,
    onMessage: (Long) -> Unit,
) {
    items(list.size, key = { list[it].id }) { index ->
        val item = list[index]
        Column(Modifier.fillMaxWidth().clickable { onOpen(item.id) }) {
            ContactRow(item)
            Row {
                TextButton(onClick = { onCall(item.id) }) { Text(stringResource(R.string.action_call)) }
                TextButton(onClick = { onMessage(item.id) }) { Text(stringResource(R.string.action_message)) }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun HealthRing(score: Int, tracked: Int) {
    val color = ScoringEngine.categoryFor(score).color()
    val track = MaterialTheme.colorScheme.primaryContainer
    Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke))
            drawArc(color, startAngle = -90f, sweepAngle = 360f * score / 100f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), style = MaterialTheme.typography.displayMedium, color = color)
            Text(pluralStringResource(R.plurals.tracked_people, tracked, tracked), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CategoryCounts(counts: Map<HealthCategory, Int>) {
    Row(Modifier.fillMaxWidth()) {
        HealthCategory.entries.forEach { cat ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text((counts[cat] ?: 0).toString(), style = MaterialTheme.typography.titleLarge, color = cat.color())
                Text(cat.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun WeeklyBars(values: List<Int>) {
    val barColor = MaterialTheme.colorScheme.primary
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val description = stringResource(R.string.weekly_desc, values.joinToString(", "))
    Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = description }) {
        val n = values.size.coerceAtLeast(1)
        val gap = 8.dp.toPx()
        val barW = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val h = if (v == 0) 3.dp.toPx() else size.height * v / max
            drawRoundRect(barColor, topLeft = Offset(i * (barW + gap), size.height - h), size = Size(barW, h), cornerRadius = CornerRadius(4.dp.toPx()))
        }
    }
}

@Composable
private fun ThisWeek(people: Int, streak: Int, goal: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            if (people == 0) stringResource(R.string.summary_quiet) else pluralStringResource(R.plurals.summary_people, people, people),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (streak > 0) pluralStringResource(R.plurals.summary_streak, streak, streak) else stringResource(R.string.dash_goal, goal),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
        )
    }
}
