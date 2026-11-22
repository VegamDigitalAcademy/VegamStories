package com.vegamstories.app.presentation.settings

import androidx.lifecycle.ViewModel
import com.vegamstories.app.core.logging.ApiCallCount
import com.vegamstories.app.core.logging.ApiCallCounter
import com.vegamstories.app.core.logging.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

private const val TAG = "ApiCallsViewModel"

/** Settings → API calls (debug builds only). */
@HiltViewModel
class ApiCallsViewModel @Inject constructor(
    private val counter: ApiCallCounter,
) : ViewModel() {

    private val log = AppLogger(TAG)

    /** Every API called so far, the latest call first. Updates live as calls are made. */
    val counts: StateFlow<List<ApiCallCount>> = counter.counts

    fun onReset() {
        log.i("$TAG: User: reset the API call counts")
        counter.reset()
    }
}
