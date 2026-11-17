package com.digitaltrainer.payments

import com.digitaltrainer.payments.api.SmsGatewayRequest
import com.google.gson.Gson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsGatewayRequestTest {

    private val gson = Gson()

    @Test
    fun testJsonPayloadStructure_MatchesServerContract() {
        // §3 contract: {"from":"bKash","text":"<full SMS text>","sentStamp":1763400000000,"receivedStamp":1763400001000,"sim":"sim1"}
        val sampleText = "You have received Tk 100.00 from 01580471306. Fee Tk 0.00. Balance Tk 532.20. TrxID DI445VCEIE at 04/09/2026 18:06"
        val request = SmsGatewayRequest(
            from = "bKash",
            text = sampleText,
            sentStamp = 1763400000000L,
            receivedStamp = 1763400001000L,
            sim = "sim1"
        )

        val jsonString = gson.toJson(request)
        val json = JSONObject(jsonString)

        assertEquals("bKash", json.getString("from"))
        assertEquals(sampleText, json.getString("text"))
        assertEquals(1763400000000L, json.getLong("sentStamp"))
        assertEquals(1763400001000L, json.getLong("receivedStamp"))
        assertEquals("sim1", json.getString("sim"))

        // Exact keys present
        assertTrue(json.has("from"))
        assertTrue(json.has("text"))
        assertTrue(json.has("sentStamp"))
        assertTrue(json.has("receivedStamp"))
        assertTrue(json.has("sim"))
        assertEquals(5, json.length())
    }
}
