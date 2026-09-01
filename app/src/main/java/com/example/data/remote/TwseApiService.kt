package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * TWSE (臺灣證券交易所) OpenAPI Service
 * Base URL: https://openapi.twse.com.tw/v1/
 * Specs: https://openapi.twse.com.tw/#/
 */
interface TwseApiService {

    /**
     * 上市個股日收盤價及成交量資訊 (STOCK_DAY_ALL)
     * https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL
     */
    @GET("exchangeReport/STOCK_DAY_ALL")
    suspend fun getStockDayAll(): List<TwseStockDayDto>

    /**
     * 上市個股本益比、殖利率及股價淨值比 (BWIBBU_ALL)
     * https://openapi.twse.com.tw/v1/exchangeReport/BWIBBU_ALL
     */
    @GET("exchangeReport/BWIBBU_ALL")
    suspend fun getBwibbuAll(): List<TwseBwibbuDto>

    /**
     * 大盤統計資訊 (FMTQIK)
     * https://openapi.twse.com.tw/v1/exchangeReport/FMTQIK
     */
    @GET("exchangeReport/FMTQIK")
    suspend fun getMarketDailySummary(): List<TwseMarketSummaryDto>
}

@JsonClass(generateAdapter = true)
data class TwseStockDayDto(
    @Json(name = "Code") val code: String? = "",
    @Json(name = "Name") val name: String? = "",
    @Json(name = "TradeVolume") val tradeVolume: String? = "0",
    @Json(name = "TradeValue") val tradeValue: String? = "0",
    @Json(name = "OpeningPrice") val openingPrice: String? = "0",
    @Json(name = "HighestPrice") val highestPrice: String? = "0",
    @Json(name = "LowestPrice") val lowestPrice: String? = "0",
    @Json(name = "ClosingPrice") val closingPrice: String? = "0",
    @Json(name = "Change") val change: String? = "0",
    @Json(name = "Transaction") val transaction: String? = "0"
)

@JsonClass(generateAdapter = true)
data class TwseBwibbuDto(
    @Json(name = "Code") val code: String? = "",
    @Json(name = "Name") val name: String? = "",
    @Json(name = "PEratio") val peRatio: String? = "0",
    @Json(name = "DividendYield") val dividendYield: String? = "0",
    @Json(name = "PBratio") val pbRatio: String? = "0"
)

@JsonClass(generateAdapter = true)
data class TwseMarketSummaryDto(
    @Json(name = "Date") val date: String? = "",
    @Json(name = "TradeVolume") val tradeVolume: String? = "0",
    @Json(name = "TradeValue") val tradeValue: String? = "0",
    @Json(name = "Transaction") val transaction: String? = "0",
    @Json(name = "TAIEX") val taiex: String? = "0",
    @Json(name = "Change") val change: String? = "0"
)
