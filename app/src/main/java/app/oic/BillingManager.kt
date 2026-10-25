package app.oic

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager(context: Context) : PurchasesUpdatedListener {
    private val prefs: SharedPreferences = context.getSharedPreferences("oic_billing", Context.MODE_PRIVATE)
    private val _hasDonated = MutableStateFlow(prefs.getBoolean("has_donated", false))
    val hasDonated = _hasDonated.asStateFlow()

    private val billingClient: BillingClient? = runCatching {
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
    }.getOrNull()

    init {
        startConnection()
    }

    private fun startConnection() {
        runCatching {
            billingClient?.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryPurchases()
                    }
                }
                override fun onBillingServiceDisconnected() {
                    // ignore
                }
            })
        }
    }

    private fun queryPurchases() {
        runCatching {
            val client = billingClient ?: return
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            client.queryPurchasesAsync(params, PurchasesResponseListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    val donated = purchases.any { purchase ->
                        purchase.products.contains("donate_2_gbp") && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    if (donated) {
                        setDonated(true)
                    }
                }
            })
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.products.contains("donate_2_gbp") && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    setDonated(true)
                    if (!purchase.isAcknowledged) {
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient?.acknowledgePurchase(ackParams, AcknowledgePurchaseResponseListener { _ -> })
                    }
                }
            }
        }
    }

    private fun setDonated(donated: Boolean) {
        prefs.edit().putBoolean("has_donated", donated).apply()
        _hasDonated.value = donated
    }

    fun launchBilling(activity: Activity) {
        runCatching {
            val client = billingClient ?: return
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId("donate_2_gbp")
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
            client.queryProductDetailsAsync(params, ProductDetailsResponseListener { result, productDetailsList ->
                val list = productDetailsList as? List<ProductDetails>
                if (result.responseCode == BillingClient.BillingResponseCode.OK && !list.isNullOrEmpty()) {
                    val productDetails = list[0]
                    val productDetailsParamsList = listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build()
                    )
                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(productDetailsParamsList)
                        .build()
                    client.launchBillingFlow(activity, billingFlowParams)
                }
            })
        }
    }
}
