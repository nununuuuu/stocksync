package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET

/**
 * TPEx (證券櫃檯買賣中心) OpenAPI Service
 * Base URL: https://www.tpex.org.tw/openapi/v1/
 * Specs: https://www.tpex.org.tw/openapi/#/
 */
interface TpexApiService {

    /**
     * 上櫃個股收盤行情 (tpex_mainboard_daily_close_quotes)
     * https://www.tpex.org.tw/openapi/v1/tpex_mainboard_daily_close_quotes
     */
    @GET("tpex_mainboard_daily_close_quotes")
    suspend fun getMainboardDailyCloseQuotes(): List<TpexDailyQuoteDto>

    /**
     * 上櫃個股本益比、殖利率及淨值比 (tpex_pe_ratio)
     */
    @GET("tpex_pe_ratio")
    suspend fun getPeRatioData(): List<TpexPeDto>
}

@JsonClass(generateAdapter = true)
data class TpexDailyQuoteDto(
    @Json(name = "SecuritiesCompanyCode") val code: String? = "",
    @Json(name = "CompanyName") val name: String? = "",
    @Json(name = "Close") val close: String? = "0",
    @Json(name = "Change") val change: String? = "0",
    @Json(name = "Open") val open: String? = "0",
    @Json(name = "High") val high: String? = "0",
    @Json(name = "Low") val low: String? = "0",
    @Json(name = "TradingShares") val tradingShares: String? = "0",
    @Json(name = "TransactionAmount") val transactionAmount: String? = "0",
    @Json(name = "TransactionNumber") val transactionNumber: String? = "0"
)

@JsonClass(generateAdapter = true)
data class TpexPeDto(
    @Json(name = "SecuritiesCompanyCode") val code: String? = "",
    @Json(name = "CompanyName") val name: String? = "",
    @Json(name = "PriceEarningRatio") val peRatio: String? = "0",
    @Json(name = "YieldRatio") val yieldRatio: String? = "0",
    @Json(name = "PriceBookRatio") val pbRatio: String? = "0"
)
