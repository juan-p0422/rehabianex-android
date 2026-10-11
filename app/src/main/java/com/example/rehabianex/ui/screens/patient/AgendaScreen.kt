package com.example.rehabianex.ui.screens.patient

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.AgendaEvent
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.notifications.AgendaNotificationHelper
import com.example.rehabianex.ui.components.ChipSelector
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.OfflineStatusCard
import com.example.rehabianex.ui.components.PatientScreenScaffold
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexDangerButton
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.theme.RehabianexColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Composable
fun AgendaScreen(
    onNavigate: (String) -> Unit,
    uiState: AgendaEventsUiState,
    onRetry: () -> Unit,
    onCompleteEvent: (AgendaEvent) -> Unit,
    onCancelEvent: (AgendaEvent) -> Unit,
) {
    var visibleMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val visibleMonth = YearMonth.parse(visibleMonthText)

    PatientScreenScaffold(Routes.Agenda, onNavigate) {
        ScreenHeader("Mi Agenda", "Reuniones y recordatorios")
        Spacer(Modifier.height(12.dp))

        when (uiState) {
            AgendaEventsUiState.Loading -> AgendaLoading()
            is AgendaEventsUiState.Error -> AgendaError(
                message = uiState.message,
                onRetry = onRetry,
            )
            is AgendaEventsUiState.Content -> {
                OfflineStatusCard(uiState.offlineStatus)
                val monthEvents = uiState.events.filter { event ->
                    event.localDate()?.let { YearMonth.from(it) == visibleMonth } == true
                }

                MonthCalendarCard(
                    visibleMonth = visibleMonth,
                    events = uiState.events,
                    onPreviousMonth = { visibleMonthText = visibleMonth.minusMonths(1).toString() },
                    onNextMonth = { visibleMonthText = visibleMonth.plusMonths(1).toString() },
                )
                Spacer(Modifier.height(12.dp))
                RehabianexButton("+ Nuevo evento") {
                    onNavigate(Routes.NewEvent)
                }
                Spacer(Modifier.height(12.dp))
                AgendaNotificationCard(
                    allEvents = uiState.events,
                )
                Spacer(Modifier.height(12.dp))
                AgendaContent(
                    events = monthEvents,
                    visibleMonth = visibleMonth,
                    onCompleteEvent = onCompleteEvent,
                    onCancelEvent = onCancelEvent,
                )
            }
        }
    }
}

@Composable
private fun MonthCalendarCard(
    visibleMonth: YearMonth,
    events: List<AgendaEvent>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val today = LocalDate.now()
    val eventsByDate = events.groupBy { it.localDate() }
    val monthEventsCount = visibleMonth.lengthOfMonth()
        .let { days -> (1..days).sumOf { day -> eventsByDate[visibleMonth.atDay(day)].orEmpty().size } }

    RehabianexCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPreviousMonth) {
                Text("‹")
            }
            Text(
                text = "${visibleMonth.month.spanishName()} ${visibleMonth.year}",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onNextMonth) {
                Text("›")
            }
        }
        Text(
            "Hoy: ${today.dayOfMonth} · Eventos del mes: $monthEventsCount",
            color = RehabianexColors.GrayText,
        )
        Spacer(Modifier.height(8.dp))
        WeekHeader()
        Spacer(Modifier.height(4.dp))
        visibleMonth.calendarDays().chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { day ->
                    CalendarDayCell(
                        day = day,
                        today = today,
                        eventCount = day?.let { eventsByDate[it].orEmpty().size } ?: 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun WeekHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("L", "M", "X", "J", "V", "S", "D").forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = RehabianexColors.GrayText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: LocalDate?,
    today: LocalDate,
    eventCount: Int,
    modifier: Modifier = Modifier,
) {
    val isToday = day == today
    Surface(
        modifier = modifier
            .height(42.dp),
        shape = RoundedCornerShape(10.dp),
        color = when {
            day == null -> RehabianexColors.GrayCard
            isToday -> RehabianexColors.GreenSoft
            eventCount > 0 -> RehabianexColors.BlueLight
            else -> RehabianexColors.GrayCard
        },
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = day?.dayOfMonth?.toString().orEmpty(),
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) RehabianexColors.GreenDark else RehabianexColors.Black,
            )
            if (eventCount > 0) {
                Text(
                    text = "• $eventCount",
                    color = RehabianexColors.GreenDark,
                )
            }
        }
    }
}

@Composable
private fun AgendaNotificationCard(
    allEvents: List<AgendaEvent>,
) {
    val context = LocalContext.current
    var period by rememberSaveable { mutableStateOf(AgendaPeriod.Today.label) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pendingAction?.invoke()
        } else {
            Toast.makeText(
                context,
                "Activa el permiso de notificaciones para recibir avisos.",
                Toast.LENGTH_SHORT,
            ).show()
        }
        pendingAction = null
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        if (AgendaNotificationHelper.canPostNotifications(context)) {
            action()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingAction = action
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val selectedPeriod = AgendaPeriod.fromLabel(period)
    val periodEvents = allEvents.eventsForPeriod(selectedPeriod)

    SoftCard {
        Text("Notificaciones de agenda", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        ChipSelector(
            options = AgendaPeriod.entries.map { it.label },
            selected = period,
            onSelected = { period = it },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${periodEvents.size} evento(s) para ${selectedPeriod.readableLabel}.",
            color = RehabianexColors.GrayText,
        )
        Spacer(Modifier.height(8.dp))
        RehabianexOutlineButton("Activar recordatorios") {
            if (periodEvents.isEmpty()) {
                Toast.makeText(context, "No hay eventos para programar.", Toast.LENGTH_SHORT).show()
            } else {
                runWithNotificationPermission {
                    AgendaNotificationHelper.showSummaryNotification(
                        context = context,
                        periodLabel = selectedPeriod.readableLabel,
                        eventCount = periodEvents.size,
                    )
                    val summaries = scheduleRollingSummaries(
                        context = context,
                        events = allEvents,
                        period = selectedPeriod,
                    )
                    val scheduled = periodEvents.count { event ->
                        AgendaNotificationHelper.scheduleEventReminder(
                            context = context,
                            eventId = event.id,
                            startsAtMillis = event.startsAtMillis() ?: 0L,
                            durationMinutes = event.durationMinutes,
                        )
                    }
                    Toast.makeText(
                        context,
                        if (scheduled > 0) {
                            "Se activaron $scheduled recordatorio(s) y $summaries resumen(es)."
                        } else {
                            "No hay eventos futuros para programar."
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }
}

@Composable
private fun AgendaContent(
    events: List<AgendaEvent>,
    visibleMonth: YearMonth,
    onCompleteEvent: (AgendaEvent) -> Unit,
    onCancelEvent: (AgendaEvent) -> Unit,
) {
    if (events.isEmpty()) {
        RehabianexCard {
            Text("No tienes eventos en ${visibleMonth.month.spanishName()} ${visibleMonth.year}.")
            Spacer(Modifier.height(4.dp))
            Text(
                "Cuando agregues reuniones, citas o recordatorios aparecerán aquí.",
                color = RehabianexColors.GrayText,
            )
        }
        return
    }

    events.forEachIndexed { index, event ->
        AgendaEventCard(
            event = event,
            onCompleteEvent = onCompleteEvent,
            onCancelEvent = onCancelEvent,
        )
        if (index != events.lastIndex) {
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AgendaLoading() {
    RehabianexCard {
        CircularProgressIndicator(color = RehabianexColors.GreenMain)
        Spacer(Modifier.height(10.dp))
        Text("Cargando agenda...")
    }
}

@Composable
private fun AgendaError(
    message: String,
    onRetry: () -> Unit,
) {
    DangerCard {
        Text(message)
    }
    Spacer(Modifier.height(10.dp))
    RehabianexOutlineButton("Reintentar", onClick = onRetry)
}

@Composable
private fun AgendaEventCard(
    event: AgendaEvent,
    onCompleteEvent: (AgendaEvent) -> Unit,
    onCancelEvent: (AgendaEvent) -> Unit,
) {
    val context = LocalContext.current

    RehabianexCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    event.dayNumber.takeIf { it > 0 }?.toString() ?: "--",
                    fontWeight = FontWeight.Bold,
                    color = RehabianexColors.GreenDark,
                )
                Text(event.dateLabel, color = RehabianexColors.GrayText)
            }
            Column(Modifier.weight(1f)) {
                Text(event.title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${event.typeLabel} · ${event.timeText}", color = RehabianexColors.GrayText)
                Text(event.detail, color = RehabianexColors.GrayText)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RehabianexOutlineButton(
                text = "Completado",
                modifier = Modifier.weight(1f),
            ) {
                AgendaNotificationHelper.cancelEventReminder(context, event.id)
                AgendaNotificationHelper.scheduleManualEventCleanup(context, event.id)
                onCompleteEvent(event)
                Toast.makeText(
                    context,
                    "Evento marcado como completado. El aviso fue retirado.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            RehabianexDangerButton(
                text = "Cancelar",
                modifier = Modifier.weight(1f),
            ) {
                AgendaNotificationHelper.cancelEventReminder(context, event.id)
                AgendaNotificationHelper.scheduleManualEventCleanup(context, event.id)
                onCancelEvent(event)
                Toast.makeText(
                    context,
                    "Evento cancelado. El aviso fue retirado.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}

private enum class AgendaPeriod(
    val label: String,
    val readableLabel: String,
) {
    Today("Hoy", "hoy"),
    Week("Semana", "esta semana");

    companion object {
        fun fromLabel(label: String): AgendaPeriod {
            return entries.firstOrNull { it.label == label } ?: Today
        }
    }
}

private fun List<AgendaEvent>.eventsForPeriod(
    period: AgendaPeriod,
): List<AgendaEvent> {
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    return filter { event ->
        val date = event.localDate() ?: return@filter false
        when (period) {
            AgendaPeriod.Today -> date == today
            AgendaPeriod.Week -> !date.isBefore(weekStart) && !date.isAfter(weekEnd)
        }
    }
}

private fun scheduleRollingSummaries(
    context: Context,
    events: List<AgendaEvent>,
    period: AgendaPeriod,
): Int {
    val today = LocalDate.now()
    val upcomingDates = events
        .mapNotNull { it.localDate() }
        .filter { !it.isBefore(today) }
    val lastDate = upcomingDates.maxOrNull()
        ?.takeIf { !it.isAfter(today.plusDays(30)) }
        ?: today

    return generateSequence(today.plusDays(1)) { date ->
        date.plusDays(1).takeIf { !it.isAfter(lastDate) }
    }.count { date ->
        val triggerAtMillis = date.atTime(8, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val count = when (period) {
            AgendaPeriod.Today -> events.count { event -> event.localDate() == date }
            AgendaPeriod.Week -> {
                val weekEnd = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                events.count { event ->
                    val eventDate = event.localDate() ?: return@count false
                    !eventDate.isBefore(date) && !eventDate.isAfter(weekEnd)
                }
            }
        }

        AgendaNotificationHelper.scheduleSummaryNotification(
            context = context,
            periodLabel = period.readableLabel,
            eventCount = count,
            triggerAtMillis = triggerAtMillis,
        )
    }
}

private fun YearMonth.calendarDays(): List<LocalDate?> {
    val firstDay = atDay(1)
    val leadingEmptyDays = firstDay.dayOfWeek.value - 1
    val days = buildList {
        repeat(leadingEmptyDays) { add(null) }
        repeat(lengthOfMonth()) { dayIndex -> add(atDay(dayIndex + 1)) }
    }

    val trailingEmptyDays = (7 - (days.size % 7)).takeIf { it < 7 } ?: 0
    return days + List(trailingEmptyDays) { null }
}

private fun AgendaEvent.localDate(): LocalDate? {
    return startsAt.toZonedDateTimeOrNull()?.toLocalDate()
}

private fun AgendaEvent.startsAtMillis(): Long? {
    return startsAt.toZonedDateTimeOrNull()
        ?.toInstant()
        ?.toEpochMilli()
}

private fun String.toZonedDateTimeOrNull() = runCatching {
    OffsetDateTime.parse(this).atZoneSameInstant(ZoneId.systemDefault())
}.getOrNull()
    ?: runCatching {
        LocalDateTime.parse(this).atZone(ZoneId.systemDefault())
    }.getOrNull()
    ?: runCatching {
        LocalDate.parse(this).atStartOfDay(ZoneId.systemDefault())
    }.getOrNull()

private fun Month.spanishName(): String {
    return when (this) {
        Month.JANUARY -> "Enero"
        Month.FEBRUARY -> "Febrero"
        Month.MARCH -> "Marzo"
        Month.APRIL -> "Abril"
        Month.MAY -> "Mayo"
        Month.JUNE -> "Junio"
        Month.JULY -> "Julio"
        Month.AUGUST -> "Agosto"
        Month.SEPTEMBER -> "Septiembre"
        Month.OCTOBER -> "Octubre"
        Month.NOVEMBER -> "Noviembre"
        Month.DECEMBER -> "Diciembre"
    }
}
