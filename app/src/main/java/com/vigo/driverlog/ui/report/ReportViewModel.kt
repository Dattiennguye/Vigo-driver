package com.vigo.driverlog.ui.report

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vigo.driverlog.appContainer
import com.vigo.driverlog.domain.ReportModel
import com.vigo.driverlog.report.ExportedReport
import com.vigo.driverlog.report.ReportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class ReportUiState(
    val day: LocalDate = LocalDate.now(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val model: ReportModel? = null,
    val previews: List<Bitmap> = emptyList(),
    val error: String? = null,
    val exported: ExportedReport? = null,
)

class ReportViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.appContainer
    private val _state = MutableStateFlow(ReportUiState())
    val state = _state.asStateFlow()

    private var pages: List<Bitmap> = emptyList()
    private var job: Job? = null

    fun setDay(d: LocalDate) { _state.update { it.copy(day = d, exported = null) }; refresh() }
    fun shift(days: Long) = setDay(_state.value.day.plusDays(days))

    /** Dựng lại báo cáo từ cơ sở dữ liệu (dùng cho XEM TRƯỚC và XUẤT LẠI). */
    fun refresh() {
        job?.cancel()
        val day = _state.value.day
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val settings = c.settings.settings.first()
                val model = c.reports.buildModel(day, settings)
                if (model.isEmpty) {
                    clearPages()
                    _state.update { it.copy(loading = false, model = model, previews = emptyList()) }
                    return@launch
                }
                val rendered = c.reports.render(model, allowNetwork = settings.onlineMapInReport)
                val previews = withContext(Dispatchers.Default) {
                    rendered.map { Bitmap.createScaledBitmap(it, it.width / 2, it.height / 2, true) }
                }
                clearPages()
                pages = rendered
                _state.update { it.copy(loading = false, model = model, previews = previews) }
            } catch (e: ReportException) {
                _state.update { it.copy(loading = false, error = e.message) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = "Lỗi khi tạo báo cáo: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    fun export(onDone: (ExportedReport) -> Unit = {}) {
        if (pages.isEmpty() || _state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val r = c.reports.save(_state.value.day, pages)
                _state.update { it.copy(saving = false, exported = r) }
                onDone(r)
            } catch (e: ReportException) {
                _state.update { it.copy(saving = false, error = e.message) }
            } catch (e: Throwable) {
                _state.update { it.copy(saving = false, error = "Lỗi khi xuất ảnh: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    /** XUẤT LẠI: dựng lại từ dữ liệu mới nhất rồi lưu đè bản cũ cùng ngày. */
    fun reExport() {
        _state.update { it.copy(exported = null) }
        job?.cancel()
        val day = _state.value.day
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val settings = c.settings.settings.first()
                val model = c.reports.buildModel(day, settings)
                val rendered = c.reports.render(model, settings.onlineMapInReport)
                val previews = withContext(Dispatchers.Default) { rendered.map { Bitmap.createScaledBitmap(it, it.width / 2, it.height / 2, true) } }
                clearPages(); pages = rendered
                _state.update { it.copy(loading = false, model = model, previews = previews) }
                export()
            } catch (e: ReportException) {
                _state.update { it.copy(loading = false, error = e.message) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = "Lỗi khi tạo báo cáo: ${e.message}") }
            }
        }
    }

    fun shareIntent() = _state.value.exported?.let { c.reports.shareIntent(it.uris, _state.value.day) }
    fun viewIntent() = _state.value.exported?.uris?.firstOrNull()?.let { c.reports.viewIntent(it) }

    private fun clearPages() {
        // Không recycle ngay: ảnh xem trước cũ có thể vẫn đang được vẽ. GC sẽ thu hồi.
        pages = emptyList()
    }

    override fun onCleared() { pages = emptyList() }
}
