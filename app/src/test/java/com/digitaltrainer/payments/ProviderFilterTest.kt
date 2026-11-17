package com.digitaltrainer.payments

import com.digitaltrainer.payments.util.ProviderFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFilterTest {

    @Test
    fun testAllowedSenders_ExactMatch() {
        // Allowed senders (§4)
        assertTrue(ProviderFilter.isAllowed("bKash"))
        assertTrue(ProviderFilter.isAllowed("NAGAD"))
        assertTrue(ProviderFilter.isAllowed("16216"))
    }

    @Test
    fun testDisallowedSenders_StrictRejection() {
        // Casing differences must be strictly rejected (§11)
        assertFalse(ProviderFilter.isAllowed("Bkash"))
        assertFalse(ProviderFilter.isAllowed("BKASH"))
        assertFalse(ProviderFilter.isAllowed("bkash"))
        assertFalse(ProviderFilter.isAllowed("nagad"))
        assertFalse(ProviderFilter.isAllowed("Nagad"))
        assertFalse(ProviderFilter.isAllowed("rocket"))
        assertFalse(ProviderFilter.isAllowed("Rocket"))

        // Other senders & numbers
        assertFalse(ProviderFilter.isAllowed("GP"))
        assertFalse(ProviderFilter.isAllowed("BL"))
        assertFalse(ProviderFilter.isAllowed("ROBI"))
        assertFalse(ProviderFilter.isAllowed("AIRTEL"))
        assertFalse(ProviderFilter.isAllowed("12345"))
        assertFalse(ProviderFilter.isAllowed("01711000000"))

        // Null and empty
        assertFalse(ProviderFilter.isAllowed(null))
        assertFalse(ProviderFilter.isAllowed(""))
    }
}
