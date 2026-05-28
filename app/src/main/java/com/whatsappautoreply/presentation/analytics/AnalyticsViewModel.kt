package com.whatsappautoreply.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsappautoreply.data.database.dao.DirectionStat
import com.whatsappautoreply.data.database.dao.ToneStat
import com.whatsappautoreply.data.repository.AnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    analyticsRepository: AnalyticsRepository
) : ViewModel() {

    val totalMessages: StateFlow<Int> = analyticsRepository.getTotalMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalAutoReplies: StateFlow<Int> = analyticsRepository.getTotalAutoReplies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val messageDirectionStats: StateFlow<List<DirectionStat>> = analyticsRepository.getMessageDirectionStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val toneDistribution: StateFlow<List<ToneStat>> = analyticsRepository.getToneDistribution()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
