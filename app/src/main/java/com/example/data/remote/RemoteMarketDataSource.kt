package com.example.data.remote

import android.util.Log
import com.example.data.model.IndexQuote
import com.example.data.model.InstitutionalChips
import com.example.data.model.KLinePoint
import com.example.data.model.StockQuote
import com.example.domain.calculator.TechnicalCalculator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class RemoteMarketDataSource {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    private val twseApi: TwseApiService = Retrofit.Builder()
        .baseUrl("https://openapi.twse.com.tw/v1/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TwseApiService::class.java)

    private val tpexApi: TpexApiService = Retrofit.Builder()
        .baseUrl("https://www.tpex.org.tw/openapi/v1/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TpexApiService::class.java)

    private val yahooApi: YahooFinanceApiService = Retrofit.Builder()
        .baseUrl("https://query1.finance.yahoo.com/v8/finance/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(YahooFinanceApiService::class.java)

    /**
     * Fetch real-time price, historical candlestick K-Line OHLCV data from Yahoo Finance API
     */
    suspend fun fetchYahooChartData(
        symbol: String,
        name: String,
        category: String,
        existingQuote: StockQuote?
    ): StockQuote? = withContext(Dispatchers.IO) {
        try {
            val yahooTicker = toYahooTicker(symbol)
            val response = yahooApi.getChart(symbol = yahooTicker, range = "3mo", interval = "1d")
            val result = response.chart?.result?.firstOrNull() ?: return@withContext null
            val meta = result.meta ?: return@withContext null
            val timestamps = result.timestamp ?: emptyList()
            val quotes = result.indicators?.quote?.firstOrNull()

            val currentPrice = meta.regularMarketPrice ?: (existingQuote?.currentPrice ?: 100.0)
            val prevClose = meta.previousClose ?: meta.chartPreviousClose ?: (existingQuote?.previousClose ?: currentPrice)
            val change = currentPrice - prevClose
            val changePercent = if (prevClose > 0) (change / prevClose) * 100.0 else 0.0
            val volume = meta.regularMarketVolume?.let { it / 1000 } ?: (existingQuote?.volume ?: 15000L) // lots (張)

            val opens = quotes?.open ?: emptyList()
            val highs = quotes?.high ?: emptyList()
            val lows = quotes?.low ?: emptyList()
            val closes = quotes?.close ?: emptyList()
            val volumes = quotes?.volume ?: emptyList()

            val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN)
            val klines = mutableListOf<KLinePoint>()

            for (i in timestamps.indices) {
                val o = opens.getOrNull(i) ?: continue
                val h = highs.getOrNull(i) ?: continue
                val l = lows.getOrNull(i) ?: continue
                val c = closes.getOrNull(i) ?: continue
                val v = volumes.getOrNull(i) ?: 0L
                val time = timestamps[i] * 1000L
                klines.add(
                    KLinePoint(
                        timestamp = time,
                        dateStr = sdf.format(Date(time)),
                        open = o,
                        high = h,
                        low = l,
                        close = c,
                        volume = v / 1000L
                    )
                )
            }

            val calculatedKlines = if (klines.isNotEmpty()) {
                TechnicalCalculator.enrichIndicators(klines)
            } else {
                existingQuote?.kLineHistory ?: emptyList()
            }

            val latestOpen = opens.lastOrNull() ?: (existingQuote?.openPrice ?: currentPrice)
            val latestHigh = highs.lastOrNull() ?: meta.regularMarketDayHigh ?: currentPrice
            val latestLow = lows.lastOrNull() ?: meta.regularMarketDayLow ?: currentPrice

            return@withContext (existingQuote ?: StockQuote(
                symbol = symbol,
                name = name,
                category = category,
                currentPrice = currentPrice,
                openPrice = latestOpen,
                highPrice = latestHigh,
                lowPrice = latestLow,
                previousClose = prevClose,
                change = change,
                changePercent = changePercent,
                volume = volume,
                totalAmount = (currentPrice * volume) / 10000.0
            )).copy(
                currentPrice = currentPrice,
                openPrice = latestOpen,
                highPrice = latestHigh,
                lowPrice = latestLow,
                previousClose = prevClose,
                change = change,
                changePercent = changePercent,
                volume = volume,
                totalAmount = (currentPrice * volume) / 10000.0,
                kLineHistory = calculatedKlines
            )
        } catch (e: Exception) {
            Log.w("RemoteMarketDataSource", "Failed to fetch Yahoo chart for $symbol: ${e.message}")
            return@withContext null
        }
    }

    /**
     * Fetch Real-time Index data from Yahoo Finance (e.g. ^TWII 加權指數, ^TWOII 櫃買指數)
     */
    suspend fun fetchYahooIndex(symbol: String, defaultName: String, fallback: IndexQuote): IndexQuote =
        withContext(Dispatchers.IO) {
            try {
                val response = yahooApi.getChart(symbol = symbol, range = "1d", interval = "1d")
                val result = response.chart?.result?.firstOrNull() ?: return@withContext fallback
                val meta = result.meta ?: return@withContext fallback
                val current = meta.regularMarketPrice ?: fallback.current
                val prev = meta.previousClose ?: meta.chartPreviousClose ?: fallback.current
                val change = current - prev
                val changePercent = if (prev > 0) (change / prev) * 100.0 else 0.0

                fallback.copy(
                    current = current,
                    change = change,
                    changePercent = changePercent,
                    high = meta.regularMarketDayHigh ?: fallback.high,
                    low = meta.regularMarketDayLow ?: fallback.low
                )
            } catch (e: Exception) {
                Log.w("RemoteMarketDataSource", "Failed to fetch Yahoo Index for $symbol: ${e.message}")
                fallback
            }
        }

    /**
     * Fetch TWSE Fundamental PE / Yield / PB data (BWIBBU_ALL)
     */
    suspend fun fetchTwseFundamentals(): Map<String, TwseBwibbuDto> = withContext(Dispatchers.IO) {
        try {
            val list = twseApi.getBwibbuAll()
            list.filter { !it.code.isNullOrBlank() }.associateBy { it.code!! }
        } catch (e: Exception) {
            Log.w("RemoteMarketDataSource", "Failed to fetch TWSE BWIBBU: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Fetch TWSE Daily Trading Info (STOCK_DAY_ALL)
     */
    suspend fun fetchTwseStockDay(): Map<String, TwseStockDayDto> = withContext(Dispatchers.IO) {
        try {
            val list = twseApi.getStockDayAll()
            list.filter { !it.code.isNullOrBlank() }.associateBy { it.code!! }
        } catch (e: Exception) {
            Log.w("RemoteMarketDataSource", "Failed to fetch TWSE STOCK_DAY_ALL: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Fetch TPEx Daily Quotes
     */
    suspend fun fetchTpexQuotes(): Map<String, TpexDailyQuoteDto> = withContext(Dispatchers.IO) {
        try {
            val list = tpexApi.getMainboardDailyCloseQuotes()
            list.filter { !it.code.isNullOrBlank() }.associateBy { it.code!! }
        } catch (e: Exception) {
            Log.w("RemoteMarketDataSource", "Failed to fetch TPEx Quotes: ${e.message}")
            emptyMap()
        }
    }

    private fun toYahooTicker(symbol: String): String {
        return when {
            symbol.startsWith("^") -> symbol
            symbol == "6770" || symbol == "8069" || symbol == "6488" || symbol == "3293" || symbol == "5483" -> "$symbol.TWO" // TPEx (櫃買)
            else -> "$symbol.TW" // TWSE (上市)
        }
    }
}
