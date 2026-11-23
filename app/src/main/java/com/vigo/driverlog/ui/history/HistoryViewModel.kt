package com.vigo.driverlog.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.vigo.driverlog.appContainer
import com.vigo.driverlog.data.db.EditLogEntity
import com.vigo.driverlog.data.db.EventEntity
import com.vigo.driverlog.data.db.TripEntity
import com.vigo.driverlog.data.geo.GeocodeWorker
import com.vigo.driverlog.data.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class HistoryState(
    val day: LocalDate = LocalDate.now(),
    val events: List<EventEntity> = emptyList(),
    val trips: Map<String, TripEntity> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val pendingAddresses: Int = 0,
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.appContainer
    val day = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HistoryState> = day.flatMapLatest { d ->
        val zone = ZoneId.systemDefault()
        val start = d.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        combine(c.trips.eventsBetween(start, end), c.trips.tripsOverlapping(start, end), c.settings.settings, c.trips.pendingAddressCount) { e, t, s, p ->
            HistoryState(d, e, t.associateBy { it.tripId }, s, p)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun setDay(d: LocalDate) { day.value = d }
    fun shift(days: Long) { day.value = day.value.plusDays(days) }
    fun retryAddresses() = GeocodeWorker.enqueue(getApplication())
}

class EventDetailViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val c = app.appContainer
    val eventId: String = checkNotNull(handle.get<String>("eventId"))

    val event: StateFlow<EventEntity?> = c.trips.observeEvent(eventId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val logs: StateFlow<List<EditLogEntity>> = c.trips.observeEditLogs(eventId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    @OptIn(ExperimentalCoroutinesApi::class)
    val trip: StateFlow<TripEntity?> = event.flatMapLatest { e ->
        flowOf(e?.tripId?.let { id -> runCatching { c.db.tripDao().getById(id) }.getOrNull() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    fun saveNote(note: String) = viewModelScope.launch {
        c.trips.updateNote(eventId, note); _messages.tryEmit("Đã lưu ghi chú. Thay đổi được ghi vào nhật ký chỉnh sửa.")
    }

    fun correctAddress(address: String, reason: String) = viewModelScope.launch {
        if (address.isBlank()) { _messages.tryEmit("Địa chỉ không được để trống."); return@launch }
        c.trips.correctAddress(eventId, address, reason); _messages.tryEmit("Đã sửa địa chỉ. Địa chỉ cũ được lưu trong nhật ký chỉnh sửa.")
    }

    fun retryAddress() = viewModelScope.launch {
        c.trips.requeueAddress(eventId)
        GeocodeWorker.enqueue(getApplication())
        _messages.tryEmit("Đang thử xác định địa chỉ (cần kết nối mạng).")
    }
}
