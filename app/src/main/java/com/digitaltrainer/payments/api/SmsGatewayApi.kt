package com.digitaltrainer.payments.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface SmsGatewayApi {

    @Headers("Content-Type: application/json")
    @POST
    suspend fun forwardSms(
        @Url url: String,
        @Query("key") key: String,
        @Query("device") device: String,
        @Header("X-Gateway-Key") gatewayKeyHeader: String,
        @Body body: SmsGatewayRequest
    ): Response<ResponseBody>

    @GET
    suspend fun pingHeartbeat(
        @Url url: String,
        @Query("key") key: String,
        @Query("device") device: String,
        @Header("X-Gateway-Key") gatewayKeyHeader: String
    ): Response<ResponseBody>
}
