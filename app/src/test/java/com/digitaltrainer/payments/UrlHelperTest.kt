package com.digitaltrainer.payments

import com.digitaltrainer.payments.util.UrlHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlHelperTest {

    @Test
    fun testBuildGatewayUrl_WithoutQuestionMark() {
        val base = "https://digital-trainer-api.a2nnajmul.workers.dev/api/sms/gateway"
        val key = "mysecretkey"
        val device = "phone1"

        val result = UrlHelper.buildGatewayUrl(base, key, device)
        assertEquals(
            "https://digital-trainer-api.a2nnajmul.workers.dev/api/sms/gateway?key=mysecretkey&device=phone1",
            result
        )
    }

    @Test
    fun testBuildGatewayUrl_WithExistingQuestionMark() {
        val base = "https://digital-trainer-api.a2nnajmul.workers.dev/api/sms/gateway?mode=live"
        val key = "mysecretkey"
        val device = "shop-phone"

        val result = UrlHelper.buildGatewayUrl(base, key, device)
        assertEquals(
            "https://digital-trainer-api.a2nnajmul.workers.dev/api/sms/gateway?mode=live&key=mysecretkey&device=shop-phone",
            result
        )
    }

    @Test
    fun testBuildPingUrl_ReplacesGatewayPath() {
        val base = "https://digital-trainer-api.a2nnajmul.workers.dev/api/sms/gateway"
        val key = "mysecretkey"
        val device = "phone2"

        val result = UrlHelper.buildPingUrl(base, key, device)
        assertEquals(
            "https://digital-trainer-api.a2nnajmul.workers.dev/api/devices/ping?key=mysecretkey&device=phone2",
            result
        )
    }

    @Test
    fun testSanitizeAndValidateDeviceId_ValidFormats() {
        // Valid lowercase letters, numbers, dashes (§2)
        assertEquals("phone1", UrlHelper.sanitizeAndValidateDeviceId("phone1").getOrThrow())
        assertEquals("phone2", UrlHelper.sanitizeAndValidateDeviceId("  PHONE2  ").getOrThrow())
        assertEquals("shop-phone", UrlHelper.sanitizeAndValidateDeviceId("shop-phone").getOrThrow())
        assertEquals("bkash-01", UrlHelper.sanitizeAndValidateDeviceId("BKASH-01").getOrThrow())
        assertEquals("phone-a7b3", UrlHelper.sanitizeAndValidateDeviceId("phone-a7b3").getOrThrow())
    }

    @Test
    fun testSanitizeAndValidateDeviceId_RejectsEmptyAndInvalid() {
        // Empty rejected (§2)
        assertTrue(UrlHelper.sanitizeAndValidateDeviceId("").isFailure)
        assertTrue(UrlHelper.sanitizeAndValidateDeviceId("   ").isFailure)
        assertTrue(UrlHelper.sanitizeAndValidateDeviceId(null).isFailure)

        // Invalid characters (spaces, special symbols)
        assertTrue(UrlHelper.sanitizeAndValidateDeviceId("phone 1").isFailure)
        assertTrue(UrlHelper.sanitizeAndValidateDeviceId("phone_1").isFailure)
        assertTrue(UrlHelper.sanitizeAndValidateDeviceId("phone@1").isFailure)
    }
}
