package com.vigo.driverlog.domain

/** Loại sự kiện. Giá trị [name] được lưu vào cơ sở dữ liệu – KHÔNG đổi tên. */
enum class EventType(val label: String, val reportLabel: String) {
    START_TRIP("Bắt đầu đi", "BẮT ĐẦU ĐI"),
    ARRIVE("Đã đến nơi", "ĐẾN NƠI"),
    PARK_START("Bắt đầu đỗ xe", "BẮT ĐẦU ĐỖ XE"),
    PARK_END("Rời điểm đỗ", "RỜI ĐIỂM ĐỖ"),
    RESUME("Tiếp tục di chuyển", "TIẾP TỤC DI CHUYỂN"),
    END_TRIP("Kết thúc chuyến đi", "KẾT THÚC CHUYẾN ĐI"),
    QUICK_LOG("Ghi nhận nhanh", "GHI NHẬN NHANH");

    companion object {
        fun from(value: String): EventType = entries.firstOrNull { it.name == value } ?: QUICK_LOG
    }
}

enum class TripStatus { ACTIVE, COMPLETED, INCOMPLETE;
    companion object { fun from(v: String) = entries.firstOrNull { it.name == v } ?: ACTIVE }
}

/** Nguồn vị trí – phân biệt rõ GPS mới, vị trí cache và nhập tay. */
enum class LocationSource(val label: String) {
    GPS_FRESH("GPS mới"),
    GPS_CACHED("Vị trí gần nhất (cache)"),
    MANUAL("Nhập thủ công"),
    NONE("Không có vị trí");

    companion object { fun from(v: String) = entries.firstOrNull { it.name == v } ?: NONE }
}

enum class AddressStatus {
    RESOLVED,   // đã chuyển đổi từ tọa độ
    PENDING,    // chờ có mạng để chuyển đổi
    NOT_FOUND,  // dịch vụ không trả về địa chỉ
    MANUAL,     // người dùng nhập địa điểm (không có GPS)
    CORRECTED,  // người dùng sửa địa chỉ – có nhật ký chỉnh sửa
    NONE;       // không có tọa độ để chuyển đổi

    companion object { fun from(v: String) = entries.firstOrNull { it.name == v } ?: NONE }
}

/** Trạng thái hành trình suy ra từ sự kiện cuối cùng (không tính GHI NHẬN NHANH). */
enum class TripPhase(val label: String) {
    NOT_STARTED("Chưa bắt đầu"),
    MOVING("Đang di chuyển"),
    ARRIVED("Đã đến nơi"),
    PARKED("Đang đỗ xe"),
    LEFT_PARKING("Đã rời điểm đỗ");
}
