package com.digitaltrainer.payments

import com.digitaltrainer.payments.util.SmsParserHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsParserHelperTest {

    @Test
    fun testBkashFixture() {
        val bkashText = "You have received Tk 100.00 from 01580471306. Fee Tk 0.00. Balance Tk 532.20. TrxID DI445VCEIE at 04/09/2026 18:06"
        val notificationText = SmsParserHelper.buildNotificationText("bKash", bkashText)
        assertEquals("Payment SMS forwarded — bKash ৳100.00", notificationText)
    }

    @Test
    fun testNagadFixture() {
        val nagadText = "Money Received. Amount: Tk 49.00 / Sender: 01755340446 / TxnID: 75GIMCJS"
        val notificationText = SmsParserHelper.buildNotificationText("NAGAD", nagadText)
        assertEquals("Payment SMS forwarded — Nagad ৳49.00", notificationText)
    }

    @Test
    fun testRocketFixture() {
        val rocketText = "Tk99.00 received from A/C:***127. TxnId:6587766794. Balance Tk 1,250.00."
        val notificationText = SmsParserHelper.buildNotificationText("16216", rocketText)
        assertEquals("Payment SMS forwarded — Rocket ৳99.00", notificationText)
    }
}
