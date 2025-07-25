package com.zenevo.shirodhara.data.repository

import com.zenevo.shirodhara.data.api.ActionRequest
import com.zenevo.shirodhara.data.api.ApiClient
import com.zenevo.shirodhara.data.api.HealthResponse
import com.zenevo.shirodhara.data.api.ParameterRequest
import com.zenevo.shirodhara.data.api.TreatmentResponse
import retrofit2.Response

class ShirodharaRepository {
    private val api = ApiClient.shirodharaApi

    suspend fun getHealth(): Response<HealthResponse> {
        return api.getHealth()
    }

    suspend fun setParameters(duration: Int, temperature: Int): Response<TreatmentResponse> {
        val request = ParameterRequest(duration = duration, temperature = temperature)
        return api.updateDeviceState(request)
    }

    suspend fun startTreatment(): Response<TreatmentResponse> {
        val request = ActionRequest(action = "start")
        return api.updateDeviceState(request)
    }

    suspend fun stopTreatment(): Response<TreatmentResponse> {
        val request = ActionRequest(action = "stop")
        return api.updateDeviceState(request)
    }
}