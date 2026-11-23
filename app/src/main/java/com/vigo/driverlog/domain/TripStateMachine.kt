package com.vigo.driverlog.domain

/**
 * Quy tắc chuyển trạng thái hành trình. Dùng chung cho giao diện (hiện nút nào)
 * và cho repository (chặn chuỗi sự kiện vô lý ngay trong transaction).
 */
object TripStateMachine {

    fun phaseAfter(lastTripEvent: EventType?, hasActiveTrip: Boolean): TripPhase {
        if (!hasActiveTrip) return TripPhase.NOT_STARTED
        return when (lastTripEvent) {
            null, EventType.START_TRIP, EventType.RESUME -> TripPhase.MOVING
            EventType.ARRIVE -> TripPhase.ARRIVED
            EventType.PARK_START -> TripPhase.PARKED
            EventType.PARK_END -> TripPhase.LEFT_PARKING
            EventType.END_TRIP -> TripPhase.NOT_STARTED
            EventType.QUICK_LOG -> TripPhase.MOVING // không xảy ra: QUICK_LOG bị loại trước
        }
    }

    /** Các thao tác chính được phép ở mỗi trạng thái (GHI NHẬN NHANH luôn được phép). */
    fun allowedActions(phase: TripPhase): List<EventType> = when (phase) {
        TripPhase.NOT_STARTED -> listOf(EventType.START_TRIP)
        TripPhase.MOVING -> listOf(EventType.ARRIVE, EventType.END_TRIP)
        TripPhase.ARRIVED -> listOf(EventType.PARK_START, EventType.RESUME, EventType.END_TRIP)
        TripPhase.PARKED -> listOf(EventType.PARK_END)
        TripPhase.LEFT_PARKING -> listOf(EventType.RESUME, EventType.PARK_START, EventType.END_TRIP)
    }

    fun isAllowed(phase: TripPhase, type: EventType): Boolean =
        type == EventType.QUICK_LOG || type in allowedActions(phase)

    /** Thông báo tiếng Việt khi người dùng bấm sai thứ tự. */
    fun rejectionMessage(phase: TripPhase, type: EventType): String = when {
        type == EventType.START_TRIP -> "Đang có chuyến đi chưa kết thúc. Hãy kết thúc chuyến hiện tại trước khi bắt đầu chuyến mới."
        phase == TripPhase.NOT_STARTED -> "Chưa có chuyến đi nào đang diễn ra. Hãy nhấn BẮT ĐẦU ĐI trước."
        phase == TripPhase.PARKED -> "Xe đang đỗ. Hãy nhấn RỜI ĐIỂM ĐỖ trước."
        else -> "Không thể ghi nhận \"${type.label}\" khi đang ở trạng thái \"${phase.label}\"."
    }
}
