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
    data object Idle : TreatmentState()
    data object Heating : TreatmentState()
    data object Ready : TreatmentState()
    data object InProgress : TreatmentState()
    data object Completed : TreatmentState()
    data class Error(val message: String) : TreatmentState()
}

class ShirodharaViewModel : ViewModel() {
    private val repository = ShirodharaRepository()

    private val _healthData = MutableStateFlow<HealthResponse?>(null)
    val healthData: StateFlow<HealthResponse?> = _healthData.asStateFlow()

    private val _treatmentState = MutableStateFlow<TreatmentState>(TreatmentState.Idle)
    val treatmentState: StateFlow<TreatmentState> = _treatmentState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var healthMonitoringJob: Job? = null

    init {
        startHealthMonitoring()
    }

    fun startHealthMonitoring() {
        healthMonitoringJob?.cancel()
        healthMonitoringJob = viewModelScope.launch {
            while (true) {
                try {
                    val response = repository.getHealth()
                    if (response.isSuccessful) {
                        val health = response.body()
                        _healthData.value = health
                        _isConnected.value = true

                        // Update treatment state based on health data
                        health?.let {
                            if (it.treatment_active) {
                                _treatmentState.value = TreatmentState.InProgress
                            } else if (_treatmentState.value == TreatmentState.InProgress && !it.treatment_active) {
                                // If treatment was in progress but now is not, it's completed
                                _treatmentState.value = TreatmentState.Completed
                            } else if (it.temperature_reached && _treatmentState.value == TreatmentState.Heating) {
                                _treatmentState.value = TreatmentState.Ready
                            }
                        }
                    } else {
                        _isConnected.value = false
                    }
                } catch (e: IOException) {
                    _isConnected.value = false
                }
                delay(1000) // Poll every second
            }
        }
    }

    fun setTreatmentParameters(duration: Int, temperature: Int) {
        viewModelScope.launch {
            _treatmentState.value = TreatmentState.Heating
            try {
                val response = repository.setParameters(duration, temperature)
                if (!response.isSuccessful) {
                    _treatmentState.value = TreatmentState.Error("Failed to set parameters")
                }
            } catch (e: IOException) {
                _treatmentState.value = TreatmentState.Error("Network error: ${e.message}")
            }
        }
    }

    fun startTreatment() {
        viewModelScope.launch {
            try {
                val response = repository.startTreatment()
                if (response.isSuccessful) {
                    _treatmentState.value = TreatmentState.InProgress
                } else {
                    _treatmentState.value = TreatmentState.Error("Failed to start treatment")
                }
            } catch (e: IOException) {
                _treatmentState.value = TreatmentState.Error("Network error: ${e.message}")
            }
        }
    }
    
    fun stopTreatment() {
        viewModelScope.launch {
            try {
                repository.stopTreatment()
                _treatmentState.value = TreatmentState.Completed
            } catch (e: IOException) {
                _treatmentState.value = TreatmentState.Error("Network error: ${e.message}")
            }
        }
    }

    fun resetToIdle() {
        _treatmentState.value = TreatmentState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        healthMonitoringJob?.cancel()
    }
}
