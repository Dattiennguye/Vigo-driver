# Room, WorkManager, DataStore và Play Services đều đi kèm consumer rules.
# Giữ tên lớp entity để file sao lưu/log lỗi dễ đọc.
-keep class com.vigo.driverlog.data.db.** { *; }
-keepattributes SourceFile,LineNumberTable
