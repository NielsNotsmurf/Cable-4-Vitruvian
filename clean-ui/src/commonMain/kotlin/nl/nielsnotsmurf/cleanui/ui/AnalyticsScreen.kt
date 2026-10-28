package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.sound.clickSounds
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.ui.components.LoadText
import nl.nielsnotsmurf.cleanui.ui.components.cables
import nl.nielsnotsmurf.cleanui.logic.BodyAnalytics
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.logic.Measure
import nl.nielsnotsmurf.cleanui.logic.TrainingStats
import nl.nielsnotsmurf.cleanui.logic.Window
import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.BodyMap
import nl.nielsnotsmurf.cleanui.ui.components.BodySide
import nl.nielsnotsmurf.cleanui.ui.components.CleanFilterChip
import nl.nielsnotsmurf.cleanui.ui.components.LifterChip
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel
import kotlin.math.abs
import kotlin.math.roundToInt
import nl.nielsnotsmurf.cleanui.platform.DateFormatter
import nl.nielsnotsmurf.cleanui.platform.nowMillis
import nl.nielsnotsmurf.cleanui.platform.formatDecimal
import nl.nielsnotsmurf.cleanui.platform.formatGrouped

/**
 * One lifter at a time. A measure (reps, volume, …) and a period (this week, …) at the top
 * drive the whole screen: the body figure, the numbers, the trend, insights and exercises.
 * Tapping a body region narrows everything below the figure to that region.
 */
@Composable
fun AnalyticsScreen(
    state: CleanUiState,
    history: List<HistoryEntry>,
    onMoreAnalytics: () -> Unit,
    /** Sets a 1RM by hand (kg per cable); null removes it. */
    onSetOneRepMax: (lifterId: String, exerciseId: String, kg: Float?) -> Unit = { _, _, _ -> }
) {
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var measure by rememberSaveable { mutableStateOf(Measure.REPS) }
    var window by rememberSaveable { mutableStateOf(Window.WEEK) }
    var region by rememberSaveable { mutableStateOf<String?>(null) }
    var side by rememberSaveable { mutableStateOf(BodySide.FRONT) }

    val lifter = state.lifters.firstOrNull { it.id == picked }
        ?: state.activeLifters.firstOrNull() ?: state.lifters.firstOrNull()
    val now = remember(history) { nowMillis() }
    val mine = remember(history, lifter?.id) { history.filter { it.lifterId == lifter?.id } }
    val regionOf = remember(state.exercises) { BodyAnalytics.regionLookup(state.exercises) }
    val range = remember(window, now, mine) { BodyAnalytics.range(window, now, first = mine.minOfOrNull { it.timestamp }) }
    val inWindow = remember(mine, range) { BodyAnalytics.inRange(mine, range) }
    val regionStats = remember(inWindow, measure, regionOf) { BodyAnalytics.regions(inWindow, regionOf, measure, BODY_REGIONS) }
    // Everything under the figure follows the picked region.
    val focus = remember(mine, region, regionOf) { if (region == null) mine else mine.filter { regionOf(it) == region } }
    val focusWindow = remember(focus, range) { BodyAnalytics.inRange(focus, range) }
    val focusPrevious = remember(focus, range) { BodyAnalytics.previous(focus, range) }
    val trend = remember(focus, measure, window, range) { BodyAnalytics.trend(focus, measure, window, range) }
    val exercises = remember(focusWindow, measure, regionOf) { BodyAnalytics.exercises(focusWindow, regionOf, measure) }
    val records = remember(focus, range) { BodyAnalytics.records(focus, range) }
    val unit = state.unit
    val cablesByName = remember(state.exercises) { state.exercises.associate { it.name to it.cables } }
    val insights = remember(mine, measure, window, range, unit, regionOf) {
        BodyAnalytics.insights(
            mine, regionOf, measure, window, range, BODY_REGIONS, now,
            format = { formatMeasure(it, measure, unit) },
            kg = { LoadCalculator.format(it, unit) }
        )
    }
    val accent = CleanColors.Accent
    val dateFormat = remember { DateFormatter("d MMM") }

    fun pickRegion(r: String?) {
        region = if (r == region) null else r
        when (r) {
            "Back" -> side = BodySide.BACK
            "Chest" -> side = BodySide.FRONT
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Analytics", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 16.dp))
        if (state.lifters.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.lifters, key = { it.id }) { l -> LifterChip(l, selected = l.id == lifter?.id, onClick = { picked = l.id }) }
            }
        }

        // The two filters for the whole screen.
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Measure.entries.toList()) { m -> CleanFilterChip(m.label, selected = m == measure, onClick = { measure = m }) }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(window.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (window == Window.ALL) "since ${dateFormat.format(range.start)}"
                    else "${dateFormat.format(range.start)} – ${dateFormat.format(range.end - 1)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Slider(
                value = window.ordinal.toFloat(),
                onValueChange = { window = Window.entries[it.roundToInt().coerceIn(0, Window.entries.lastIndex)] },
                valueRange = 0f..Window.entries.lastIndex.toFloat(),
                steps = Window.entries.size - 2,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = CleanColors.Line,
                    activeTickColor = Color.White,
                    inactiveTickColor = CleanColors.Muted
                )
            )
            Row {
                Window.entries.forEach { w ->
                    Text(
                        w.shortLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (w == window) CleanColors.Ink else CleanColors.Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clickable { window = w }
                    )
                }
            }
        }

        BodyCard(
            stats = regionStats,
            measure = measure,
            window = window,
            unit = unit,
            region = region,
            prs = remember(state.oneRepMaxes, state.exercises, lifter?.id) { regionPrs(state, lifter) },
            side = side,
            onSide = { side = it },
            onRegion = ::pickRegion
        )

        // Headline numbers for the window, against the period before.
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val tiles = buildList {
                val current = BodyAnalytics.value(focusWindow, measure)
                val change = if (BodyAnalytics.isAdditive(measure)) {
                    BodyAnalytics.change(current, focusPrevious?.let { BodyAnalytics.value(it, measure) })
                } else null
                add(Kpi(formatMeasure(current, measure, unit, compact = true), kpiLabel(measure, unit), change))
                Measure.entries.filter { it != measure && it != Measure.HEAVIEST }.forEach { m ->
                    val v = BodyAnalytics.value(focusWindow, m)
                    add(Kpi(formatMeasure(v, m, unit, compact = true), kpiLabel(m, unit), BodyAnalytics.change(v, focusPrevious?.let { BodyAnalytics.value(it, m) })))
                }
                add(Kpi(records.size.toString(), if (records.size == 1) "new record" else "new records", null))
                val streak = TrainingStats.weekStreak(mine, now)
                add(Kpi(streak.toString(), if (streak == 1) "week streak" else "weeks streak", null))
            }
            items(tiles) { KpiTile(it, accent) }
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("${measure.label} · ${window.label}${region?.let { " · $it" } ?: ""}", Modifier.weight(1f))
            }
            TrendChart(trend, accent) { formatMeasure(it, measure, unit) }
        }

        if (insights.isNotEmpty()) {
            SectionLabel("Insights", Modifier.padding(horizontal = 20.dp))
            val pager = rememberPagerState { insights.size }
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 10.dp
            ) { page ->
                val insight = insights[page]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CleanColors.Surface)
                        .padding(16.dp)
                        .heightIn(min = 76.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(insight.title, style = MaterialTheme.typography.titleMedium)
                    Text(insight.body, style = MaterialTheme.typography.bodyMedium, color = CleanColors.Muted)
                }
            }
            if (insights.size > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(insights.size) { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == pager.currentPage) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (i == pager.currentPage) accent else CleanColors.Line)
                        )
                    }
                }
            }
        }

        Card {
            SectionLabel("Balance · ${measure.label.lowercase()} per region")
            val max = regionStats.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
            regionStats.sortedByDescending { it.value }.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { pickRegion(s.region) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        s.region,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (s.region == region) FontWeight.Bold else null,
                        modifier = Modifier.width(84.dp)
                    )
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(CleanColors.Line)) {
                        Box(
                            Modifier
                                .fillMaxWidth(s.value / max)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (region == null || s.region == region) accent else accent.copy(alpha = 0.35f))
                        )
                    }
                    Text(
                        formatMeasure(s.value, measure, unit, compact = true),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }
        }

        SectionLabel("Exercises${region?.let { " · $it" } ?: ""}", Modifier.padding(horizontal = 20.dp))
        if (exercises.isEmpty()) {
            Text(
                "Nothing ${if (region != null) "for ${region!!.lowercase()} " else ""}in this period yet.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(exercises, key = { it.name }) { e ->
                    Column(
                        Modifier
                            .width(168.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.5.dp, CleanColors.Line, RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e.region.ifEmpty { "Other" }, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        val cables = cablesByName[e.name] ?: 2
                        if (measure == Measure.HEAVIEST) {
                            LoadText(formatMeasure(e.value, measure, unit), cables, style = MaterialTheme.typography.headlineMedium, color = accent)
                        } else {
                            Text(formatMeasure(e.value, measure, unit), style = MaterialTheme.typography.headlineMedium, color = accent, maxLines = 1)
                        }
                        val best = e.bestKg
                        if (best != null) LoadText("Heaviest measured ${LoadCalculator.format(best, unit)}", cables, style = MaterialTheme.typography.bodySmall)
                        else Text("No load", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Sparkline(e.points, accent, Modifier.fillMaxWidth().height(32.dp))
                    }
                }
            }
        }

        OneRepMaxCard(state, lifter, region, accent, onSetOneRepMax)

        if (records.isNotEmpty()) {
            Card {
                SectionLabel("Records · measured · ${window.label}")
                records.take(6).forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(r.exerciseName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                        LoadText(LoadCalculator.format(r.kg, unit), cablesByName[r.exerciseName] ?: 2, style = MaterialTheme.typography.bodyMedium)
                        Text(" · ${dateFormat.format(r.timestamp)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.5.dp, CleanColors.Line, RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onMoreAnalytics)
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text("More charts and CSV export", style = MaterialTheme.typography.bodyLarge)
                Text("PR charts, consistency, safety events and exports", style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CleanColors.Muted)
        }
    }
}

@Composable
private fun BodyCard(
    stats: List<BodyAnalytics.RegionStat>,
    measure: Measure,
    window: Window,
    unit: WeightUnit,
    region: String?,
    prs: Map<String, Float>,
    side: BodySide,
    onSide: (BodySide) -> Unit,
    onRegion: (String?) -> Unit
) {
    val accent = CleanColors.Accent
    val max = stats.maxOfOrNull { it.value }?.takeIf { it > 0f }
    val fills = stats.associate { s ->
        s.region to if (max == null || s.value <= 0f) CleanColors.Line else lerp(CleanColors.Line, accent, 0.2f + 0.8f * s.value / max)
    }
    val labels = prs.mapValues { LoadCalculator.format(it.value, unit, withUnit = false) }
    val unitName = if (unit == WeightUnit.LB) "lb" else "kg"

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(measure.label, Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).border(1.dp, CleanColors.Line, RoundedCornerShape(50))
            ) {
                BodySide.entries.forEach { s ->
                    Text(
                        if (s == BodySide.FRONT) "Front" else "Back",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (s == side) Color.White else CleanColors.Ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (s == side) CleanColors.Ink else Color.Transparent)
                            .clickable { onSide(s) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BodyMap(
                side = side,
                fills = fills,
                labels = labels,
                selected = region,
                onRegion = onRegion,
                modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
                description = stats.joinToString { "${it.region} ${formatMeasure(it.value, measure, unit)}" }
            )
        }
        // Colour scale.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("0", style = MaterialTheme.typography.bodySmall)
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(lerp(CleanColors.Line, accent, 0.2f), accent)))
            )
            Text(max?.let { formatMeasure(it, measure, unit, compact = true) } ?: "–", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Numbers are your best 1RM per region in $unitName: chest and back as total load (both cables on one bar), the rest per cable.",
            style = MaterialTheme.typography.bodySmall
        )
        if (region != null) {
            Row(
                Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CleanColors.Ink)
                    .clickable(role = Role.Button) { onRegion(null) }
                    .heightIn(min = 36.dp)
                    .padding(start = 14.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Only $region", style = MaterialTheme.typography.labelMedium, color = Color.White)
                Icon(Icons.Filled.Close, contentDescription = "Show all regions", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        } else {
            Text("Tap a region to see only that region below.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Label under a compact number: "reps", "kg volume", … */
private fun kpiLabel(measure: Measure, unit: WeightUnit): String {
    val u = if (unit == WeightUnit.LB) "lb" else "kg"
    return when (measure) {
        Measure.VOLUME -> "$u volume"
        Measure.HEAVIEST -> "$u heaviest"
        Measure.TIME -> "training time"
        else -> measure.label.lowercase()
    }
}

private data class Kpi(val value: String, val label: String, val change: Int?)

@Composable
private fun KpiTile(kpi: Kpi, accent: Color) {
    Column(
        Modifier
            .widthIn(min = 120.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, CleanColors.Line, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(kpi.value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        Text(kpi.label, style = MaterialTheme.typography.bodySmall)
        kpi.change?.let { c ->
            Text(
                when {
                    c > 0 -> "▲ $c%"
                    c < 0 -> "▼ ${abs(c)}%"
                    else -> "= same"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (c >= 0) accent else CleanColors.Muted
            )
        }
    }
}

@Composable
private fun OneRepMaxCard(
    state: CleanUiState,
    lifter: UiLifter?,
    region: String?,
    accent: Color,
    onSet: (lifterId: String, exerciseId: String, kg: Float?) -> Unit
) {
    val exercises = state.exercises.filter { region == null || it.region == region }.associateBy { it.id }
    // With a region tapped, its estimated 1RMs show too, so any of them can be set by hand.
    val maxes = lifter?.let { state.oneRepMaxes[it.id] }.orEmpty()
        .filterValues { region != null || !it.isEstimate }
        .mapNotNull { (id, orm) -> exercises[id]?.let { it to orm } }
        .sortedWith(compareBy<Pair<ExerciseCard, OneRepMax>> { it.second.isEstimate }.thenByDescending { it.second.kg })
    var editing by remember { mutableStateOf<Pair<ExerciseCard, OneRepMax>?>(null) }
    var showAll by remember(region) { mutableStateOf(false) }
    Card {
        SectionLabel("1RMs${region?.let { " · $it" } ?: ""}")
        Text(
            if (maxes.isEmpty()) "No 1RMs yet. Run the 1RM test from Train. Tap a region on the body to set one by hand."
            else "Only the 1RM test or you change these. Tap one to correct it.",
            style = MaterialTheme.typography.bodySmall
        )
        maxes.let { if (showAll) it else it.take(8) }.forEach { (ex, orm) ->
            val cables = ex.cables
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = lifter != null) { editing = ex to orm }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ex.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(
                        when (orm.source) {
                            OneRepMaxSource.BENCHMARK -> "Tested"
                            OneRepMaxSource.MANUAL -> "Entered by hand"
                            OneRepMaxSource.RELATED -> "Estimated · tap to set"
                            else -> "Worked out from your sets"
                        } + if (cables == 2) " · per cable, ${LoadCalculator.format(orm.kg * 2, state.unit)} total" else " · one cable",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                LoadText(
                    (if (orm.isEstimate) "~" else "") + LoadCalculator.format(orm.kg, state.unit),
                    cables,
                    color = if (orm.isEstimate) CleanColors.Muted else accent
                )
            }
        }
        if (maxes.size > 8) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "Show less" else "Show all ${maxes.size}", color = CleanColors.Ink)
            }
        }
    }
    val e = editing
    if (e != null && lifter != null) {
        val (ex, orm) = e
        var text by remember(e) {
            mutableStateOf(if (orm.isEstimate) "" else LoadCalculator.format(orm.kg, state.unit, withUnit = false))
        }
        val unitName = if (state.unit == WeightUnit.LB) "lb" else "kg"
        val entered = text.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }
        AlertDialog(
            onDismissRequest = { editing = null },
            modifier = Modifier.clickSounds(),
            containerColor = CleanColors.Background,
            title = { Text(ex.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${lifter.name}'s 1RM in $unitName" + if (ex.cables == 2) " per cable." else ".", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                        singleLine = true,
                        suffix = { Text(unitName) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = entered != null,
                    onClick = {
                        val kg = entered!!.let { if (state.unit == WeightUnit.LB) it / 2.20462f else it }
                        onSet(lifter.id, ex.id, kg)
                        editing = null
                    }
                ) { Text("Save", color = CleanColors.Ink) }
            },
            dismissButton = {
                Row {
                    if (!orm.isEstimate) {
                        TextButton(onClick = { onSet(lifter.id, ex.id, null); editing = null }) { Text("Remove", color = CleanColors.Danger) }
                    }
                    TextButton(onClick = { editing = null }) { Text("Cancel", color = CleanColors.Ink) }
                }
            }
        )
    }
}


/**
 * Best real 1RM per body region for [lifter]. Chest and back lifts move one bar with both
 * cables, so they show total load (per cable × cables); the other regions stay per cable.
 * Estimates scaled from other lifts are left out, so a number is always something lifted or tested.
 */
private fun regionPrs(state: CleanUiState, lifter: UiLifter?): Map<String, Float> {
    val maxes = lifter?.let { state.oneRepMaxes[it.id] }.orEmpty()
    return state.exercises
        .mapNotNull { ex ->
            maxes[ex.id]?.takeIf { !it.isEstimate && ex.region.isNotEmpty() }?.let {
                ex.region to if (ex.region in TOTAL_LOAD_REGIONS) it.kg * ex.cables else it.kg
            }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, totals) -> totals.max() }
}

/** Regions whose lifts aren't split per arm, shown as total load on the body map. */
private val TOTAL_LOAD_REGIONS = setOf("Chest", "Back")

/** Bars per day, week or month; tap a bar to read its value. */
@Composable
private fun TrendChart(buckets: List<BodyAnalytics.Bucket>, accent: Color, format: (Float) -> String) {
    var picked by remember(buckets) { mutableIntStateOf(buckets.indexOfLast { it.value > 0f }) }
    val max = buckets.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    val shown = buckets.getOrNull(picked)
    Text(
        shown?.let { "${format(it.value)}  ·  ${it.label}" } ?: "Nothing in this period",
        style = MaterialTheme.typography.titleMedium
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(top = 8.dp)
            .pointerInput(buckets) {
                detectTapGestures { tap ->
                    if (buckets.isNotEmpty()) picked = (tap.x / (size.width.toFloat() / buckets.size)).toInt().coerceIn(0, buckets.lastIndex)
                }
            }
    ) {
        if (buckets.isEmpty()) return@Canvas
        val slot = size.width / buckets.size
        val bar = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        buckets.forEachIndexed { i, b ->
            val h = (b.value / max) * size.height
            val x = i * slot + (slot - bar) / 2
            drawRoundRect(
                CleanColors.Line,
                topLeft = Offset(x, 0f),
                size = Size(bar, size.height),
                cornerRadius = CornerRadius(bar / 3)
            )
            if (h > 0f) {
                drawRoundRect(
                    if (i == picked) accent else accent.copy(alpha = 0.55f),
                    topLeft = Offset(x, size.height - h),
                    size = Size(bar, h),
                    cornerRadius = CornerRadius(bar / 3)
                )
            }
        }
    }
    val every = ((buckets.size + 11) / 12).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth()) {
        buckets.forEachIndexed { i, b ->
            Text(
                if (i % every == 0) b.label else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (i == picked) CleanColors.Ink else CleanColors.Muted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, CleanColors.Line, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) { content() }
}

@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        if (values.size < 2) {
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(size.width - 4.dp.toPx(), size.height / 2))
            return@Canvas
        }
        val min = values.min()
        val range = (values.max() - min).takeIf { it > 0f } ?: 1f
        val pad = 4.dp.toPx()
        fun point(i: Int) = Offset(
            pad + i * (size.width - 2 * pad) / (values.size - 1),
            size.height - pad - (values[i] - min) / range * (size.height - 2 * pad)
        )
        val path = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1 until values.size) lineTo(point(i).x, point(i).y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, radius = 3.5.dp.toPx(), center = point(values.lastIndex))
    }
}

/** A measure value with its unit: "1,240 reps", "3.4 t", "1 h 20 min". [compact] drops the unit word. */
internal fun formatMeasure(value: Float, measure: Measure, unit: WeightUnit, compact: Boolean = false): String {
    fun count(word: String): String {
        val n = value.roundToInt()
        val text = formatGrouped((n).toLong())
        return if (compact) text else "$text $word${if (n == 1) "" else "s"}"
    }
    return when (measure) {
        Measure.REPS -> count("rep")
        Measure.SETS -> count("set")
        Measure.WORKOUTS -> count("workout")
        Measure.HEAVIEST -> LoadCalculator.format(value, unit, withUnit = !compact)
        Measure.VOLUME -> {
            val v = LoadCalculator.toDisplay(value, unit)
            val u = if (unit == WeightUnit.LB) "lb" else "kg"
            val text = if (v >= 10_000f) "${formatDecimal((v / 1000f).toDouble(), 1)}k" else formatGrouped((v.roundToInt()).toLong())
            if (compact) text else "$text $u"
        }
        Measure.TIME -> {
            val min = value.roundToInt()
            when {
                compact && min >= 60 -> "${min / 60}h${(min % 60).toString().padStart(2, '0')}"
                compact -> "${min}m"
                min >= 60 -> "${min / 60} h ${min % 60} min"
                else -> "$min min"
            }
        }
    }
}
