package org.cssnr.deviceinfo.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.cssnr.deviceinfo.data.DeviceInfoCollector
import org.cssnr.deviceinfo.data.InfoCategory
import org.cssnr.deviceinfo.data.InfoItem
import org.cssnr.deviceinfo.data.SettingsRepository

data class HomeState(
    val categories: List<InfoCategory> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
) {
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()

    /** Every row on the screen, in display order. */
    val allItems: List<InfoItem>
        get() = categories.flatMap { it.items }

    /**
     * Selected rows in screen order, dropping any whose id no longer exists in [categories] so a
     * selection cannot outlive the row it refers to.
     */
    val selectedItems: List<InfoItem>
        get() = allItems.filter { it.id in selectedIds }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * The collected snapshot, held as state rather than cached in a repository. There is nothing
     * to persist: the values come from `android.os.Build` fields that the platform fixes for the
     * life of the process, so re-reading them would return the same strings, and there is no
     * database worth keeping a table of immutable constants in. See [DeviceInfoCollector] for why
     * collecting them costs nothing to repeat.
     *
     * Seeded eagerly with a non-null value so the first frame has rows to draw and the screen never
     * has to render an empty state it will immediately replace.
     */
    private val _state = MutableStateFlow(HomeState(categories = collect()))
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /**
     * The application context, not whatever screen happened to ask. The collector reads device
     * state, which does not vary per screen, and holding an Activity here would outlive it.
     */
    private fun collect(): List<InfoCategory> = DeviceInfoCollector.collect(getApplication())

    /**
     * Whether a row tap copies `Label: Value` or the value alone.
     *
     * Held as its own flow rather than folded into [state], because it is a preference rather than
     * part of the snapshot: nothing here rebuilds the categories when it changes. The initial value
     * is the same `true` the repository defaults to, so the first frame cannot disagree with the
     * stored setting the way a nullable initial value would.
     */
    val copyKeyAndValue: StateFlow<Boolean> = SettingsRepository(application).copyKeyAndValue
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    fun toggleSelection(id: String) {
        _state.update { current ->
            val selected = current.selectedIds.toMutableSet()
            if (!selected.remove(id)) selected.add(id)
            current.copy(selectedIds = selected)
        }
    }

    fun clearSelection() {
        _state.update { current -> current.copy(selectedIds = emptySet()) }
    }
}