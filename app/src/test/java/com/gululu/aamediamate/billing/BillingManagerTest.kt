package com.gululu.aamediamate.billing

import android.os.Looper
import com.android.billingclient.api.*
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class BillingManagerTest {
    private val client = mockk<BillingClient>(relaxed = true)
    private val updates = slot<PurchasesUpdatedListener>()
    private val query = slot<PurchasesResponseListener>()
    private val acknowledgements = mutableListOf<AcknowledgePurchaseResponseListener>()
    private lateinit var manager: BillingManager
    private val ok = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()

    @Before fun setup() {
        val builder = mockk<BillingClient.Builder>()
        mockkStatic(BillingClient::class)
        every { BillingClient.newBuilder(any()) } returns builder
        every { builder.setListener(capture(updates)) } returns builder
        every { builder.enablePendingPurchases(any()) } returns builder
        every { builder.enableAutoServiceReconnection() } returns builder
        every { builder.build() } returns client
        every { client.isReady } returns true
        every { client.queryPurchasesAsync(any<QueryPurchasesParams>(), capture(query)) } just Runs
        every { client.acknowledgePurchase(any(), capture(acknowledgements)) } just Runs
        manager = BillingManager(RuntimeEnvironment.getApplication())
    }

    @After fun teardown() {
        manager.close()
        unmockkStatic(BillingClient::class)
    }

    private fun purchase(state: Int, acknowledged: Boolean = false) = mockk<Purchase> {
        every { purchaseToken } returns "token"
        every { purchaseState } returns state
        every { isAcknowledged } returns acknowledged
    }

    @Test fun pendingPurchaseIsReplacedByItsCompletedUpdate() {
        val pending = purchase(Purchase.PurchaseState.PENDING)
        val completed = purchase(Purchase.PurchaseState.PURCHASED)
        updates.captured.onPurchasesUpdated(ok, listOf(pending))
        verify(exactly = 0) { client.acknowledgePurchase(any(), any()) }
        updates.captured.onPurchasesUpdated(ok, listOf(completed))
        assertEquals(listOf(completed), manager.purchases.value)
        verify(exactly = 1) { client.acknowledgePurchase(any(), any()) }
    }

    @Test fun restoredPurchaseIsAcknowledgedWithoutANewPurchaseEvent() {
        manager.refreshPurchases()
        query.captured.onQueryPurchasesResponse(ok, listOf(purchase(Purchase.PurchaseState.PURCHASED)))
        verify(exactly = 1) { client.acknowledgePurchase(any(), any()) }
        assertEquals(1, manager.purchases.value.size)
    }

    @Test fun duplicateEventsDoNotStartConcurrentAcknowledgements() {
        val completed = purchase(Purchase.PurchaseState.PURCHASED)
        repeat(2) { updates.captured.onPurchasesUpdated(ok, listOf(completed)) }
        verify(exactly = 1) { client.acknowledgePurchase(any(), any()) }
    }

    @Test fun failedAcknowledgementRetriesAndCloseCancelsFurtherRetries() {
        val failure = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.NETWORK_ERROR).build()
        updates.captured.onPurchasesUpdated(ok, listOf(purchase(Purchase.PurchaseState.PURCHASED)))
        acknowledgements[0].onAcknowledgePurchaseResponse(failure)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        verify(exactly = 2) { client.acknowledgePurchase(any(), any()) }
        acknowledgements[1].onAcknowledgePurchaseResponse(failure)
        manager.close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
        verify(exactly = 2) { client.acknowledgePurchase(any(), any()) }
    }
}
