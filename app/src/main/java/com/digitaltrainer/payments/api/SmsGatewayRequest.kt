package com.digitaltrainer.payments.api

import com.google.gson.annotations.SerializedName

data class SmsGatewayRequest(
    @SerializedName("from")
    val from: String,

    @SerializedName("text")
    val text: String,

    @SerializedName("sentStamp")
    val sentStamp: Long,

    @SerializedName("receivedStamp")
    val receivedStamp: Long,

    @SerializedName("sim")
    val sim: String,

    @SerializedName("device")
    val device: String
)
