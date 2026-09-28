package com.wrh.keshiguanjia.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wrh.keshiguanjia.data.Graph
import com.wrh.keshiguanjia.logic.LessonSlot
import com.wrh.keshiguanjia.logic.TimeUtils
import com.wrh.keshiguanjia.logic.WeekSchedule
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel : ViewModel() {

    val week: StateFlow<Map<Int, List<LessonSlot>>> = Graph.classRepository.observeAllWithTimes()
        .map { classes ->
            WeekSchedule.groupByDay(
                classes.flatMap { cw ->
                    cw.times.map { t ->
                        LessonSlot(
                            dayOfWeek = t.dayOfWeek,
                            startMinute = t.startMinute,
                            endMinute = t.endMinute,
                            className = cw.clazz.name,
                            subject = cw.clazz.subject,
                            room = t.room,
                        )
                    }
                }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel() as T
    }
}

/** 首页：本周课程表（周视图），今天自动滚到可见位置。 */
@Composable
fun HomeScreen() {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val week by vm.week.collectAsStateWithLifecycle()
    val today = remember { TimeUtils.todayDayOfWeek() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        listState.animateScrollToItem((today - TimeUtils.MONDAY).coerceIn(0, 6))
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "本周课程表",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(TimeUtils.WEEK_RANGE.toList()) { day ->
                DayColumn(day, week[day].orEmpty(), isToday = day == today)
            }
        }
    }
}

@Composable
private fun DayColumn(day: Int, slots: List<LessonSlot>, isToday: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxHeight()
            .width(150.dp),
        colors = if (isToday) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    TimeUtils.dayLabel(day) + if (isToday) " · 今天" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (slots.isEmpty()) {
                Text(
                    "无课",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            slots.forEachIndexed { index, slot ->
                if (index > 0) HorizontalDivider(Modifier.padding(top = 8.dp))
                Column(Modifier.padding(top = 8.dp)) {
                    Text(
                        TimeUtils.minutesToText(slot.startMinute) + " - " + TimeUtils.minutesToText(slot.endMinute),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        slot.className,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (slot.subject.isNotBlank()) {
                        Text(slot.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (slot.room.isNotBlank()) {
                        Text(slot.room, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
