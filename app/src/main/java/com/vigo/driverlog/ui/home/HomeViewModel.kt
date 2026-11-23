package com.vigo.driverlog.ui.home

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vigo.driverlog.appContainer
import com.vigo.driverlog.data.CapturedLocation
import com.vigo.driverlog.data.RecordRejected
import com.vigo.driverlog.data.TapMoment
import com.vigo.driverlog.data.TripProfile
import com.vigo.driverlog.data.db.EventEntity
import com.vigo.driverlog.data.db.TripEntity
import com.vigo.driverlog.data.geo.GeocodeWorker
import com.vigo.driverlog.data.location.FailReason
import com.vigo.driverlog.data.location.Fix
import com.vigo.driverlog.data.location.FixResult
import com.vigo.driverlog.data.location.GpsStatus
import com.vigo.driverlog.data.location.LocationService
import com.vigo.driverlog.data.settings.AppSettings
import com.vigo.driverlog.domain.EventType
import com.vigo.driverlog.domain.LocationSource
import com.vigo.driverlog.domain.TripPhase
import com.vigo.driverlog.domain.TripStateMachine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class PendingCapture(val type: EventType, val tap: TapMoment)

sealed interface CaptureDialog {
    data class LowAccuracy(val pending: PendingCapture, val fix: Fix) : CaptureDialog
    data class Failed(val pending: PendingCapture, val reason: FailReason, val cached: Fix?) : CaptureDialog
    data class Manual(val pending: PendingCapture) : CaptureDialog
    data class ConfirmEnd(val pending: PendingCapture) : CaptureDialog
    /** Sau khi lưu: cho phép bổ sung ghi chú (bắt buộc hiện với GHI NHẬN NHANH). */
    data class AddNote(val event: EventEntity) : CaptureDialog
}

data class HomeUiState(
    val settings: AppSettings = AppSettings(),
    val activeTrip: TripEntity? = null,
    val tripEvents: List<EventEntity> = emptyList(),
    val phase: TripPhase = TripPhase.NOT_STARTED,
    val lastFinished: TripEntity? = null,
    val loaded: Boolean = false,
) {
    val actions get() = TripStateMachine.allowedActions(phase)
    val isStaleTrip: Boolean get() {
        val t = activeTrip ?: return false
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return t.startTime < todayStart
    }
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.appContainer
    private val location = c.location

    val state: StateFlow<HomeUiState> = combine(
        c.settings.settings, c.trips.activeTrip, c.trips.activeTripEvents, c.trips.lastFinishedTrip,
    ) { s, trip, events, last ->
        val lastType = events.lastOrNull { it.eventType != EventType.QUICK_LOG.name }?.let { EventType.from(it.eventType) }
        HomeUiState(s, trip, events, TripStateMachine.phaseAfter(lastType, trip != null), last, true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val permissionTick = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val gps = permissionTick.flatMapLatest { location.statusUpdates() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), GpsStatus(location.hasPermission(), location.isLocationEnabled(), null))

    private val _capturing = MutableStateFlow<EventType?>(null)
    val capturing = _capturing.asStateFlow()

    private val _dialog = MutableStateFlow<CaptureDialog?>(null)
    val dialog = _dialog.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun onPermissionChanged() = permissionTick.update { it + 1 }

    fun hasPermission() = location.hasPermission()

    /** Người dùng nhấn một nút ghi nhận. Thời gian được chụp NGAY tại đây. */
    fun onAction(type: EventType) {
        if (_capturing.value != null || _dialog.value != null) return // chống bấm nhiều lần
        val tap = TapMoment(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        val st = state.value
        if (type != EventType.QUICK_LOG && !TripStateMachine.isAllowed(st.phase, type)) {
            _messages.tryEmit(TripStateMachine.rejectionMessage(st.phase, type)); return
        }
        if (type == EventType.START_TRIP && !st.settings.isProfileComplete) {
            _messages.tryEmit("Hãy nhập tên lái xe và biển số xe trong Cài đặt trước khi bắt đầu."); return
        }
        val pending = PendingCapture(type, tap)
        if (type == EventType.END_TRIP) { _dialog.value = CaptureDialog.ConfirmEnd(pending); return }
        acquire(pending)
    }

    fun confirmEnd(pending: PendingCapture) { _dialog.value = null; acquire(pending) }

    /** Lấy GPS mới cho một sự kiện đang chờ; thời gian sự kiện vẫn là lúc nhấn nút. */
    fun acquire(pending: PendingCapture) {
        _dialog.value = null
        _capturing.value = pending.type
        viewModelScope.launch {
            try {
                when (val r = location.getFreshFix()) {
                    is FixResult.Success -> {
                        if (r.fix.accuracy > LocationService.GOOD_ACCURACY_M) _dialog.value = CaptureDialog.LowAccuracy(pending, r.fix)
                        else save(pending, gpsLocation(r.fix, LocationSource.GPS_FRESH, pending))
                    }
                    is FixResult.Failure -> _dialog.value = CaptureDialog.Failed(pending, r.reason, r.lastKnown)
                }
            } finally {
                _capturing.value = null
            }
        }
    }

    private fun gpsLocation(fix: Fix, source: LocationSource, pending: PendingCapture) =
        CapturedLocation.Gps(fix, source, (pending.tap.elapsedRealtimeMs - fix.elapsedRealtimeMs).coerceAtLeast(0))

    fun saveLowAccuracy(d: CaptureDialog.LowAccuracy) { _dialog.value = null; launchSave(d.pending, gpsLocation(d.fix, LocationSource.GPS_FRESH, d.pending)) }

    fun saveCached(d: CaptureDialog.Failed) {
        val fix = d.cached ?: return
        _dialog.value = null
        launchSave(d.pending, gpsLocation(fix, LocationSource.GPS_CACHED, d.pending))
    }

    fun openManual(pending: PendingCapture) { _dialog.value = CaptureDialog.Manual(pending) }

    fun saveManual(pending: PendingCapture, place: String) {
        if (place.isBlank()) { _messages.tryEmit("Hãy nhập tên địa điểm."); return }
        _dialog.value = null
        launchSave(pending, CapturedLocation.Manual(place))
    }

    fun dismissDialog() { _dialog.value = null }

    private fun launchSave(p: PendingCapture, loc: CapturedLocation) = viewModelScope.launch { save(p, loc) }

    private suspend fun save(p: PendingCapture, loc: CapturedLocation) {
        val s = state.value.settings
        try {
            val e = c.trips.record(p.type, p.tap, loc, TripProfile(s.driverName, s.vehiclePlate, s.companyName.ifBlank { null }))
            if (loc is CapturedLocation.Gps) GeocodeWorker.enqueue(getApplication())
            if (p.type == EventType.QUICK_LOG) _dialog.value = CaptureDialog.AddNote(e)
            else _messages.tryEmit("Đã ghi nhận: ${p.type.label}")
        } catch (e: RecordRejected) {
            _messages.tryEmit(e.message ?: "Không thể ghi nhận.")
        } catch (e: Exception) {
            _messages.tryEmit("Lỗi khi lưu dữ liệu: ${e.message ?: "không xác định"}. Dữ liệu cũ không bị ảnh hưởng.")
        }
    }

    fun requestNote(event: EventEntity) { _dialog.value = CaptureDialog.AddNote(event) }

    fun saveNote(eventId: String, note: String) {
        _dialog.value = null
        if (note.isBlank()) return
        viewModelScope.launch {
            runCatching { c.trips.updateNote(eventId, note) }
                .onSuccess { _messages.tryEmit("Đã lưu ghi chú.") }
                .onFailure { _messages.tryEmit("Không lưu được ghi chú.") }
        }
    }

    fun markStaleIncomplete() {
        val t = state.value.activeTrip ?: return
        viewModelScope.launch {
            c.trips.markIncomplete(t.tripId)
            _messages.tryEmit("Đã đánh dấu chuyến đi cũ là chưa hoàn thành.")
        }
    }
}
