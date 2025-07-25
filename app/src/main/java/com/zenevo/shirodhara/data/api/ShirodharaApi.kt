package com.zenevo.shirodhara.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class HealthResponse(
    val temperature: Float,
    val heater_state: Boolean,
    val treatment_active: Boolean,
    val heating_active: Boolean,
    val temperature_reached: Boolean,
    val target_temperature: Int,
    val remaining_time: Long? = null
)

data class TreatmentRequest(
    val duration: Int,
    val temperature: Int,
    val start_treatment: Boolean = false
)

data class TreatmentResponse(
    val status: String
)

data class ErrorResponse(
    val error: String
)

interface ShirodharaApi {
    @GET("/api/health")
    suspend fun getHealth(): Response<HealthResponse>
    
    @POST("/api/update")
    suspend fun startTreatment(@Body request: TreatmentRequest): Response<TreatmentResponse>
} 