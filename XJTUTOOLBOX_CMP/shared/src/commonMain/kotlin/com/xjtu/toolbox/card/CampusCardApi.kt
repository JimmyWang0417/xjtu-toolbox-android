package com.xjtu.toolbox.card

import com.xjtu.toolbox.auth.CampusCardLogin
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.safeParseJsonObject
import com.xjtu.toolbox.util.safeString
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "CampusCardApi"

// ==================== 数据类 ====================

data class CardInfo(
    val account: String,
    val name: String,
    val studentNo: String,
    val balance: Double,
    val pendingAmount: Double,
    val lostFlag: Boolean,
    val frozenFlag: Boolean,
    val expireDate: String,
    val cardType: String,
    val department: String = ""
)

data class Transaction(
    val time: String,
    val merchant: String,
    val amount: Double,
    val balance: Double,
    val type: String,
    val description: String
)

/** 简单年月表示 */
data class YearMonth(val year: Int, val monthNumber: Int) : Comparable<YearMonth> {
    override fun compareTo(other: YearMonth): Int {
        val cmp = year.compareTo(other.year)
        return if (cmp != 0) cmp else monthNumber.compareTo(other.monthNumber)
    }

    fun lengthOfMonth(): Int = when (monthNumber) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        else -> 30
    }

    companion object {
        fun now(): YearMonth {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            return YearMonth(today.year, today.monthNumber)
        }

        fun of(year: Int, month: Int) = YearMonth(year, month)
    }
}

data class MonthlyStats(
    val month: YearMonth,
    val totalSpend: Double,
    val totalIncome: Double,
    val transactionCount: Int,
    val topMerchants: List<MerchantStat>,
    val avgDailySpend: Double = 0.0,
    val peakDay: String = "",
    val peakDayAmount: Double = 0.0
)

data class MerchantStat(
    val name: String,
    val totalAmount: Double,
    val count: Int
)

data class MealTimeStats(
    val count: Int,
    val totalAmount: Double,
    val avgAmount: Double
)

data class DayTypeStats(
    val label: String,
    val count: Int,
    val totalAmount: Double,
    val avgPerTransaction: Double,
    val avgPerDay: Double
) {
    companion object {
        fun from(label: String, dateAmountPairs: List<Pair<LocalDate, Double>>): DayTypeStats {
            val distinctDays = dateAmountPairs.map { it.first }.toSet().size.coerceAtLeast(1)
            val amounts = dateAmountPairs.map { it.second }
            return DayTypeStats(
                label = label,
                count = amounts.size,
                totalAmount = amounts.sum(),
                avgPerTransaction = if (amounts.isNotEmpty()) amounts.average() else 0.0,
                avgPerDay = amounts.sum() / distinctDays
            )
        }
    }
}

// ==================== API 类 ====================

class CampusCardApi(private val login: CampusCardLogin) {

    private val client get() = login.client
    private val baseUrl = CampusCardLogin.BASE_URL

    suspend fun getCardInfo(): CardInfo = getCardInfoInternal(allowRetry = true)

    private suspend fun getCardInfoInternal(allowRetry: Boolean): CardInfo {
        val responseBody = client.submitForm(
            url = "$baseUrl/User/GetCardInfoByAccountNoParm",
            formParameters = Parameters.build { append("json", "true") }
        ) {
            header("Accept", "application/json, text/javascript, */*; q=0.01")
            header("X-Requested-With", "XMLHttpRequest")
            header("Origin", baseUrl)
            header("Referer", "$baseUrl/Page/Page")
        }.bodyAsText()

        Logger.d(TAG, "getCardInfo: bodyLen=${responseBody.length}")

        val root = try {
            responseBody.safeParseJsonObject()
        } catch (e: Exception) {
            throw RuntimeException("校园卡返回了非JSON数据: ${responseBody.take(100)}")
        }

        val msgKey = root.keys.firstOrNull { it.equals("Msg", ignoreCase = true) }
        val msgElement = if (msgKey != null) root[msgKey] else null

        if (msgElement == null) {
            val isSucceedKey = root.keys.firstOrNull { it.equals("IsSucceed", ignoreCase = true) }
            val isSucceed = isSucceedKey?.let { root[it]?.jsonPrimitive?.boolean }
            if (isSucceed == false) throw RuntimeException("校园卡请求失败，请返回重新登录")
            throw RuntimeException("校园卡响应格式异常，请返回重新登录后重试")
        }

        val msg = msgElement.jsonPrimitive.content

        if (!msg.trimStart().startsWith("{")) {
            val errCode = msg.trim()
            if ((errCode == "-989" || errCode == "989") && allowRetry) {
                Logger.d(TAG, "getCardInfo: -989, attempting reAuthenticate...")
                if (login.reAuthenticate()) return getCardInfoInternal(allowRetry = false)
                throw RuntimeException("校园卡会话已过期，自动重新认证失败，请返回重新登录")
            }
            val errHint = when {
                errCode == "-989" || errCode == "989" -> "校园卡会话已过期，请返回重新登录"
                errCode.startsWith("-") || errCode.all { it.isDigit() || it == '-' } -> "校园卡系统错误（代码: $errCode），请稍后重试"
                else -> "校园卡响应异常: $errCode"
            }
            throw RuntimeException(errHint)
        }

        val cardData = msg.safeParseJsonObject()
        val queryCard = cardData["query_card"]!!.jsonObject
        val retcode = queryCard["retcode"]?.jsonPrimitive?.content
        if (retcode != "0") {
            throw RuntimeException("查询失败: ${queryCard["errmsg"]?.jsonPrimitive?.content ?: "未知错误"}")
        }

        val card = queryCard["card"]!!.jsonArray[0].jsonObject
        val elecAmt = card["elec_accamt"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val unsettled = card["unsettle_amount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0

        val accountFromApi = card["account"]?.jsonPrimitive?.content
        if (!accountFromApi.isNullOrEmpty() && login.cardAccount.isNullOrEmpty()) {
            login.cardAccount = accountFromApi
        }

        return CardInfo(
            account = card["account"]?.jsonPrimitive?.content ?: "",
            name = card["name"]?.jsonPrimitive?.content ?: "",
            studentNo = card["sno"]?.jsonPrimitive?.content ?: "",
            balance = elecAmt / 100.0,
            pendingAmount = unsettled / 100.0,
            lostFlag = card["lostflag"]?.jsonPrimitive?.content == "1",
            frozenFlag = card["freezeflag"]?.jsonPrimitive?.content == "1",
            expireDate = formatExpDate(card["expdate"]?.jsonPrimitive?.content ?: ""),
            cardType = card["cardname"]?.jsonPrimitive?.content?.trim() ?: ""
        )
    }

    suspend fun getTransactions(
        startDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(DatePeriod(months = 3)),
        endDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
        page: Int = 1,
        pageSize: Int = 30
    ): Pair<Int, List<Transaction>> {
        val account = login.cardAccount ?: ""
        val responseBody = client.submitForm(
            url = "$baseUrl/Report/GetPersonTrjn",
            formParameters = Parameters.build {
                append("sdate", startDate.toString())
                append("edate", endDate.toString())
                append("account", account)
                append("page", page.toString())
                append("rows", pageSize.toString())
            }
        ) {
            header("Accept", "application/json, text/javascript, */*; q=0.01")
            header("X-Requested-With", "XMLHttpRequest")
            header("Origin", baseUrl)
            header("Referer", "$baseUrl/Page/Page")
        }.bodyAsText()

        val root = try {
            responseBody.safeParseJsonObject()
        } catch (e: Exception) {
            throw RuntimeException("交易记录返回了非JSON数据: ${responseBody.take(100)}")
        }

        val total = root["total"]?.jsonPrimitive?.int ?: 0
        val rows = root["rows"]?.jsonArray ?: return total to emptyList()

        val transactions = rows.map { it.jsonObject }.map { row ->
            Transaction(
                time = row["OCCTIME"]?.jsonPrimitive?.content ?: "",
                merchant = row["MERCNAME"]?.jsonPrimitive?.content?.trim() ?: "",
                amount = row["TRANAMT"]?.jsonPrimitive?.double ?: 0.0,
                balance = row["CARDBAL"]?.jsonPrimitive?.double ?: 0.0,
                type = row["TRANNAME"]?.jsonPrimitive?.content?.trim() ?: "",
                description = row["JDESC"]?.jsonPrimitive?.content?.trim() ?: ""
            )
        }
        return total to transactions
    }

    suspend fun getAllTransactions(
        startDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(DatePeriod(months = 3)),
        endDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
        maxPages: Int = 20
    ): List<Transaction> {
        val (total, firstPage) = getTransactions(startDate, endDate, 1, 50)
        if (total <= 50 || firstPage.isEmpty()) return firstPage

        val totalPages = minOf((total + 49) / 50, maxPages)
        if (totalPages <= 1) return firstPage

        return coroutineScope {
            val deferredPages = (2..totalPages).map { page ->
                async {
                    try { getTransactions(startDate, endDate, page, 50).second }
                    catch (e: Exception) {
                        Logger.w(TAG, "page $page failed: ${e.message}")
                        emptyList()
                    }
                }
            }
            firstPage + deferredPages.awaitAll().flatten()
        }
    }

    fun calculateMonthlyStats(transactions: List<Transaction>): List<MonthlyStats> {
        val byMonth = transactions.groupBy { tx ->
            try {
                val datePart = tx.time.substringBefore(" ")
                val date = LocalDate.parse(datePart)
                YearMonth.of(date.year, date.monthNumber)
            } catch (_: Exception) {
                YearMonth.now()
            }
        }

        return byMonth.map { (month, txList) ->
            val spending = txList.filter { it.amount < 0 }
            val income = txList.filter { it.amount > 0 }

            val merchantStats = spending.groupBy { it.merchant }
                .map { (name, txs) -> MerchantStat(name, -txs.sumOf { it.amount }, txs.size) }
                .sortedByDescending { it.totalAmount }
                .take(10)

            val totalSpend = -spending.sumOf { it.amount }
            val daysInMonth = month.lengthOfMonth()
            val nowMonth = YearMonth.now()
            val daysPassed = if (month == nowMonth) {
                Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfMonth.coerceAtLeast(1)
            } else daysInMonth
            val avgDaily = if (daysPassed > 0) totalSpend / daysPassed else 0.0

            val dailySpend = spending.groupBy { it.time.substringBefore(" ") }
                .mapValues { (_, txs) -> -txs.sumOf { it.amount } }
            val peakEntry = dailySpend.maxByOrNull { it.value }

            MonthlyStats(
                month = month,
                totalSpend = totalSpend,
                totalIncome = income.sumOf { it.amount },
                transactionCount = txList.size,
                topMerchants = merchantStats,
                avgDailySpend = avgDaily,
                peakDay = peakEntry?.key ?: "",
                peakDayAmount = peakEntry?.value ?: 0.0
            )
        }.sortedByDescending { it.month }
    }

    fun categorizeSpending(transactions: List<Transaction>): Map<String, Double> {
        val categories = mutableMapOf<String, Double>()
        for (tx in transactions) {
            if (tx.amount >= 0) continue
            val category = classifyMerchant(tx.merchant, tx.description)
            categories[category] = (categories[category] ?: 0.0) + (-tx.amount)
        }
        return categories.toList().sortedByDescending { it.second }.toMap()
    }

    fun analyzeMealTimes(transactions: List<Transaction>): Pair<Map<String, MealTimeStats>, Int> {
        val rawMeals = mutableMapOf<String, MutableMap<String, MutableList<Double>>>()
        for (period in listOf("早餐", "午餐", "晚餐", "夜宵")) {
            rawMeals[period] = mutableMapOf()
        }

        for (tx in transactions) {
            if (tx.amount >= 0) continue
            val category = classifyMerchant(tx.merchant, tx.description)
            if (category != "餐饮") continue

            val hour = try {
                tx.time.substringAfter(" ").substringBefore(":").toInt()
            } catch (_: Exception) { continue }

            val period = when (hour) {
                in 5..10 -> "早餐"
                in 11..14 -> "午餐"
                in 17..21 -> "晚餐"
                in 22..23, in 0..4 -> "夜宵"
                else -> null
            }
            if (period == null) continue
            val date = tx.time.substringBefore(" ")
            rawMeals[period]?.getOrPut(date) { mutableListOf() }?.add(-tx.amount)
        }

        val activeDates = (rawMeals["早餐"]?.keys.orEmpty() +
                rawMeals["午餐"]?.keys.orEmpty() +
                rawMeals["晚餐"]?.keys.orEmpty()).toSet()

        val mealStats = rawMeals.filter { it.value.isNotEmpty() }.mapValues { (_, dateMap) ->
            val dayCount = dateMap.size
            val totalAmount = dateMap.values.sumOf { it.sum() }
            MealTimeStats(dayCount, totalAmount, if (dayCount > 0) totalAmount / dayCount else 0.0)
        }
        return mealStats to activeDates.size
    }

    fun analyzeWeekdayVsWeekend(transactions: List<Transaction>): Pair<DayTypeStats, DayTypeStats> {
        val weekday = mutableListOf<Pair<LocalDate, Double>>()
        val weekend = mutableListOf<Pair<LocalDate, Double>>()

        for (tx in transactions) {
            if (tx.amount >= 0) continue
            val date = try {
                LocalDate.parse(tx.time.substringBefore(" "))
            } catch (_: Exception) { continue }

            val amount = -tx.amount
            when (date.dayOfWeek.ordinal) {
                in 0..4 -> weekday.add(date to amount) // Mon-Fri
                else -> weekend.add(date to amount)     // Sat-Sun
            }
        }
        return DayTypeStats.from("工作日", weekday) to DayTypeStats.from("周末", weekend)
    }

    fun dailySpending(transactions: List<Transaction>): Map<LocalDate, Double> {
        return transactions.filter { it.amount < 0 }
            .groupBy { tx ->
                try { LocalDate.parse(tx.time.substringBefore(" ")) }
                catch (_: Exception) { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
            }
            .mapValues { (_, txs) -> -txs.sumOf { it.amount } }
            .toList().sortedBy { it.first }.toMap()
    }

    private fun classifyMerchant(merchant: String, description: String): String {
        val m = merchant.lowercase()
        val d = description.lowercase()
        return when {
            m.contains("浴室") || m.contains("澡堂") || m.contains("淋浴") || m.contains("浴池") -> "洗浴"
            m.contains("能源") || m.contains("电控") || d.contains("电费") || d.contains("水费")
                || m.contains("水控") || m.contains("电量") || d.contains("能源") -> "水电"
            m.contains("超市") || m.contains("便利") || m.contains("商店") || m.contains("售卖")
                || m.contains("小卖") || m.contains("便民") || m.contains("百货") -> "超市"
            m.contains("图书") || m.contains("打印") || m.contains("复印") || m.contains("文印")
                || m.contains("书店") || m.contains("文具") -> "学习"
            m.contains("洗衣") || m.contains("洗涤") || m.contains("干洗") || m.contains("洗鞋") -> "洗衣"
            m.contains("班车") || m.contains("通勤") || m.contains("校车") -> "交通"
            m.contains("食") || m.contains("餐") || m.contains("面") || m.contains("饭")
                || m.contains("粥") || m.contains("菜") || m.contains("吧台") || m.contains("咖啡")
                || m.contains("饮") || m.contains("小面") || m.contains("米线") || m.contains("饸络")
                || m.contains("凉皮") || m.contains("卤") || m.contains("削筋") || m.contains("称量")
                || m.contains("自助") || m.contains("档口") || m.contains("窗口") || m.contains("烧烤")
                || m.contains("奶茶") || m.contains("豆浆") || m.contains("包子") || m.contains("饺子")
                || m.contains("炒") || m.contains("烩") || m.contains("煮") || m.contains("蒸")
                || m.contains("时光") || m.contains("美食") || m.contains("小吃")
                || m.contains("麻辣") || m.contains("烤") || m.contains("煎")
                || m.contains("馒头") || m.contains("饼") || m.contains("糕")
                || m.contains("果汁") || m.contains("茶") || m.contains("鸡")
                || m.contains("鱼") || m.contains("肉") || m.contains("蛋") -> "餐饮"
            m.contains("医院") || m.contains("药") || m.contains("诊所") || m.contains("卫生") -> "医疗"
            d.contains("圈存") || d.contains("充值") || d.contains("转账") -> "充值"
            else -> "其他"
        }
    }

    private fun formatExpDate(raw: String): String {
        if (raw.length != 8) return raw
        return "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
    }
}
