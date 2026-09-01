package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Yahoo Finance (yfinance) API Service
 * Base URL: https://query1.finance.yahoo.com/v8/finance/
 */
interface YahooFinanceApiService {

    /**
     * Fetch stock chart, real-time quote and historical candlestick K-Line OHLCV data
     * e.g. /chart/2330.TW?range=3mo&interval=1d
     */
    @GET("chart/{symbol}")
    suspend fun getChart(
        @Path("symbol") symbol: String,
        @Query("range") range: String = "3mo",
        @Query("interval") interval: String = "1d",
        @Query("includePrePost") includePrePost: Boolean = false
    ): YahooChartResponse
}

@JsonClass(generateAdapter = true)
data class YahooChartResponse(
    @Json(name = "chart") val chart: YahooChartBody? = null
)

@JsonClass(generateAdapter = true)
data class YahooChartBody(
    @Json(name = "result") val result: List<YahooChartResult>? = null,
    @Json(name = "error") val error: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class YahooChartResult(
    @Json(name = "meta") val meta: YahooChartMeta? = null,
    @Json(name = "timestamp") val timestamp: List<Long>? = null,
    @Json(name = "indicators") val indicators: YahooChartIndicators? = null
)

@JsonClass(generateAdapter = true)
data class YahooChartMeta(
    @Json(name = "currency") val currency: String? = "TWD",
    @Json(name = "symbol") val symbol: String? = "",
    @Json(name = "regularMarketPrice") val regularMarketPrice: Double? = 0.0,
    @Json(name = "chartPreviousClose") val chartPreviousClose: Double? = 0.0,
    @Json(name = "previousClose") val previousClose: Double? = 0.0,
    @Json(name = "regularMarketDayHigh") val regularMarketDayHigh: Double? = 0.0,
    @Json(name = "regularMarketDayLow") val regularMarketDayLow: Double? = 0.0,
    @Json(name = "regularMarketVolume") val regularMarketVolume: Long? = 0L
)

@JsonClass(generateAdapter = true)
data class YahooChartIndicators(
    @Json(name = "quote") val quote: List<YahooQuoteValues>? = null
)

@JsonClass(generateAdapter = true)
data class YahooQuoteValues(
    @Json(name = "open") val open: List<Double?>? = null,
    @Json(name = "high") val high: List<Double?>? = null,
    @Json(name = "low") val low: List<Double?>? = null,
    @Json(name = "close") val close: List<Double?>? = null,
    @Json(name = "volume") val volume: List<Long?>? = null
)
