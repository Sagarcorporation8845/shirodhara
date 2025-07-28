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

                        // --- REFINED STATE LOGIC ---
                        // The health status from the device is the single source of truth.
                        health?.let { newStatus ->
                            val currentState = _treatmentState.value

                            if (newStatus.treatment_active) {
                                // If device says treatment is active, the state is InProgress.
                                _treatmentState.value = TreatmentState.InProgress
                            } else if (newStatus.heating_active) {
                                // If not treating, but heating is active...
                                if (newStatus.temperature_reached) {
                                    // ...and temp is reached, state is Ready.
                                    _treatmentState.value = TreatmentState.Ready
                                } else {
                                    // ...otherwise, state is Heating.
                                    _treatmentState.value = TreatmentState.Heating
                                }
                            } else {
                                // Device is not treating and not heating.
                                if (currentState is TreatmentState.InProgress) {
                                    // If the app thought it was treating, it must be complete now.
                                    _treatmentState.value = TreatmentState.Completed
                                } else if (currentState !is TreatmentState.Completed && currentState !is TreatmentState.Idle) {
                                    // If we were heating or ready, but now the device is idle, reset to idle.
                                    _treatmentState.value = TreatmentState.Idle
                                }
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

    // This function now just sends the command and lets the health monitor update the state.
    fun setTreatmentParameters(duration: Int, temperature: Int) {
        viewModelScope.launch {
            _treatmentState.value = TreatmentState.Heating // Set initial state for user feedback
            try {
                repository.setParameters(duration, temperature)
            } catch (e: IOException) {
                _treatmentState.value = TreatmentState.Error("Network error: ${e.message}")
            }
        }
    }

    // This function now just sends the command.
    fun startTreatment() {
        viewModelScope.launch {
            try {
                repository.startTreatment()
            } catch (e: IOException) {
                _treatmentState.value = TreatmentState.Error("Network error: ${e.message}")
            }
        }
    }

    // This function now just sends the command.
    fun stopTreatment() {
        viewModelScope.launch {
            try {
                repository.stopTreatment()
            } catch (e: IOException) {
                _treatmentState.value = TreatmentState.Error("Network error: ${e.message}")
            }
        }
    }

    // This is used by the UI to go back to the dashboard after completion.
    fun resetToIdle() {
        _treatmentState.value = TreatmentState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        healthMonitoringJob?.cancel()
    }
}
