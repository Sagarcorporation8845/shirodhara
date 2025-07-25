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


data class ParameterRequest(
    val duration: Int,
    val temperature: Int
)


data class ActionRequest(
    val action: String
)


data class TreatmentResponse(
    val status: String
)


interface ShirodharaApi {
    @GET("/api/health")
    suspend fun getHealth(): Response<HealthResponse>

    
    @POST("/api/update")
    suspend fun updateDeviceState(@Body body: Any): Response<TreatmentResponse>
}