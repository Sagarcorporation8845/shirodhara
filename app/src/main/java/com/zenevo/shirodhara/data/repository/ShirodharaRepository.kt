package com.zenevo.shirodhara.data.repository

import com.zenevo.shirodhara.data.api.ApiClient
import com.zenevo.shirodhara.data.api.HealthResponse
import com.zenevo.shirodhara.data.api.TreatmentRequest
import com.zenevo.shirodhara.data.api.TreatmentResponse
import retrofit2.Response

class ShirodharaRepository {
    private val api = ApiClient.shirodharaApi
    
    suspend fun getHealth(): Response<HealthResponse> {
        return api.getHealth()
    }
    
    suspend fun startTreatment(
        duration: Int,
        temperature: Int,
        startTreatmentNow: Boolean = false
    ): Response<TreatmentResponse> {
        return api.startTreatment(
            TreatmentRequest(
                duration = duration,
                temperature = temperature,
                start_treatment = startTreatmentNow
            )
        )
    }
} 