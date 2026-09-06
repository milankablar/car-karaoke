package com.gululu.aamediamate.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager(context: Context) {
    private var closed = false
    private val pendingAcknowledgements = mutableSetOf<String>()
    private val retryHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val retryAttempts = mutableMapOf<String, Int>()

    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases = _purchases.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            if (closed) return@PurchasesUpdatedListener
            _purchases.value = (_purchases.value + purchases).associateBy { it.purchaseToken }.values.toList()
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d("BillingManager", "User cancelled the purchase flow.")
        } else {
            // Handle any other error codes.
        }
    }

    private var billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    private val _billingState = MutableStateFlow<BillingUiState>(BillingUiState.Loading)
    val billingState = _billingState.asStateFlow()

    fun startConnection() {
        if (closed) return
        _billingState.value = BillingUiState.Loading
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (closed) return
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingManager", "Billing client connected.")
                    queryProducts()
                    queryPurchases()
                } else {
                    _billingState.value = BillingUiState.Error("Setup failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                if (closed) return
                Log.d("BillingManager", "Billing client disconnected.")
                _billingState.value = BillingUiState.Error("Service disconnected")
            }
        })
    }

    fun retryConnection() {
        startConnection()
    }

    fun refreshPurchases() {
        if (!closed && billingClient.isReady) queryPurchases()
    }

    /** Disconnects billing and cancels retries owned by the activity. */
    fun close() {
        closed = true
        retryHandler.removeCallbacksAndMessages(null)
        billingClient.endConnection()
        pendingAcknowledgements.clear()
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (closed) return@queryPurchasesAsync
                _purchases.value = purchasesList
                purchasesList.forEach(::handlePurchase)
                Log.d("BillingManager", "Query purchases success: ${purchasesList.size} items")
            } else {
                Log.e("BillingManager", "Query purchases failed: ${billingResult.debugMessage}")
            }
        }
    }

    private fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("donate_tier_1")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("donate_tier_2")
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (closed) return@queryProductDetailsAsync
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val products = productDetailsResult.productDetailsList
                _billingState.value = BillingUiState.Success(products)
                Log.d("BillingManager", "Product query successful. Found ${products.size} products.")
                if (productDetailsResult.unfetchedProductList.isNotEmpty()) {
                    Log.w(
                        "BillingManager",
                        "Unfetched products: ${productDetailsResult.unfetchedProductList}"
                    )
                }
            } else {
                _billingState.value = BillingUiState.Error("Query failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                Log.e("BillingManager", "Product query failed with response code: ${billingResult.responseCode} and message: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        if (closed) return
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) queryPurchases()
        else if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _billingState.value = BillingUiState.Error(result.debugMessage)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (closed) return
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged && pendingAcknowledgements.add(purchase.purchaseToken)) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    pendingAcknowledgements.remove(purchase.purchaseToken)
                    if (closed) return@acknowledgePurchase
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        retryAttempts.remove(purchase.purchaseToken)
                        Log.d("BillingManager", "Purchase acknowledged.")
                    } else {
                        val attempt = (retryAttempts[purchase.purchaseToken] ?: 0) + 1
                        retryAttempts[purchase.purchaseToken] = attempt
                        if (attempt <= 3) retryHandler.postDelayed({ handlePurchase(purchase) }, attempt * 5_000L)
                    }
                }
            }
        }
    }
}

sealed interface BillingUiState {
    data object Loading : BillingUiState
    data class Success(val products: List<ProductDetails>) : BillingUiState
    data class Error(val message: String) : BillingUiState
}
