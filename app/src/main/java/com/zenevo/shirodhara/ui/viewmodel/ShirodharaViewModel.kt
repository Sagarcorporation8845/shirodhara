package com.zenevo.shirodhara.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenevo.shirodhara.data.api.HealthResponse
import com.zenevo.shirodhara.data.repository.ShirodharaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.io.IOException

sealed class TreatmentState {
    data object Loading : TreatmentState()
    data class Success(val message: String) : TreatmentState()
    data class Error(val message: String) : TreatmentState()
}

class ShirodharaViewModel : ViewModel() {
    private val repository = ShirodharaRepository()
    
    private val _healthData = MutableStateFlow<HealthResponse?>(null)
    val healthData: StateFlow<HealthResponse?> = _healthData.asStateFlow()
    
    private val _treatmentState = MutableStateFlow<TreatmentState?>(null)
    val treatmentState: StateFlow<TreatmentState?> = _treatmentState.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private var healthMonitoringJob: Job? = null
    
    init {
        startHealthMonitoring()
    }
    
    fun startHealthMonitoring() {
        healthMonitoringJob?.cancel()
        healthMonitoringJob = viewModelScope.launch {
            while(true) {
                try {
                    val response = repository.getHealth()
                    if (response.isSuccessful) {
                        val health = response.body()
                        _healthData.value = health
                        _isConnected.value = true
                    } else {
                        _isConnected.value = false
                    }
                } catch (e: IOException) {
                    _isConnected.value = false
                }
                delay(1000) // Poll every second for more responsive updates
            }
        }
    }
    
    fun startTreatment(duration: Int, temperature: Int, startTreatmentNow: Boolean = false) {
        viewModelScope.launch {
            _treatmentState.value = TreatmentState.Loading
            try {
                val response = repository.startTreatment(
                    duration = duration,
                    temperature = temperature,
                    startTreatmentNow = startTreatmentNow
                )
                
                if (response.isSuccessful) {
                    _treatmentState.value = TreatmentState.Success(response.body()?.status ?: "Treatment started")
                } else {
                    _treatmentState.value = TreatmentState.Error("Failed to start treatment")
                }
            } catch (e: IOException) {
                _treatmentState.value = TreatmentState.Error("Network error: ${e.message}")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        healthMonitoringJob?.cancel()
    }
} 