package com.xjtu.toolbox.cmps.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

object CampusCardToolkit {
    fun summarize(info: CardInfo, updatedAt: String): CampusCardSummary =
        CampusCardSummary(
            holder = info.name.ifBlank { "校园卡" },
            balance = money(info.balance),
            subsidy = money(info.pendingAmount),
            updatedAt = updatedAt,
            account = info.account,
            studentNo = info.studentNo,
            pendingAmount = money(info.pendingAmount),
            status = info.statusLabel,
        )

    fun presentTransactions(transactions: List<RawCardTransaction>): List<CardTransaction> =
        transactions.map { tx ->
            CardTransaction(
                title = tx.type.ifBlank { if (tx.amount < 0) "消费" else "入账" },
                location = tx.merchant.ifBlank { tx.description.ifBlank { "校园卡交易" } },
                amount = signedMoney(tx.amount),
                time = tx.time,
                balance = money(tx.balance),
                type = tx.type,
                category = classifyMerchant(tx.merchant, tx.description),
            )
        }

    fun insight(transactions: List<RawCardTransaction>): CardInsight =
        CardInsight(
            monthlyStats = calculateMonthlyStats(transactions),
            categorySpend = categorizeSpending(transactions),
            mealStats = analyzeMealTimes(transactions),
        )

    fun calculateMonthlyStats(transactions: List<RawCardTransaction>): List<MonthlyCardStats> {
        val byMonth = transactions.groupBy { tx ->
            runCatching {
                val date = LocalDate.parse(tx.time.substringBefore(" "))
                YearMonth(date.year, date.monthNumber)
            }.getOrElse {
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                YearMonth(today.year, today.monthNumber)
            }
        }

        return byMonth.map { (month, txList) ->
            val spending = txList.filter { it.amount < 0 }
            val income = txList.filter { it.amount > 0 }
            val merchantStats = spending.groupBy { it.merchant.ifBlank { "未知商户" } }
                .map { (name, txs) -> MerchantStat(name, txs.sumOf { it.spending }, txs.size) }
                .sortedByDescending { it.totalAmount }
                .take(8)
            val totalSpend = spending.sumOf { it.spending }
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val daysPassed = if (month.year == today.year && month.monthNumber == today.monthNumber) {
                today.dayOfMonth.coerceAtLeast(1)
            } else {
                month.lengthOfMonth()
            }
            val dailySpend = spending.groupBy { it.time.substringBefore(" ") }
                .mapValues { (_, txs) -> txs.sumOf { it.spending } }
            val peakEntry = dailySpend.maxByOrNull { it.value }

            MonthlyCardStats(
                month = month,
                totalSpend = totalSpend,
                totalIncome = income.sumOf { it.amount },
                transactionCount = txList.size,
                topMerchants = merchantStats,
                avgDailySpend = if (daysPassed > 0) totalSpend / daysPassed else 0.0,
                peakDay = peakEntry?.key ?: "",
                peakDayAmount = peakEntry?.value ?: 0.0,
            )
        }.sortedByDescending { it.month }
    }

    fun categorizeSpending(transactions: List<RawCardTransaction>): Map<String, Double> {
        val categories = mutableMapOf<String, Double>()
        for (tx in transactions) {
            if (tx.amount >= 0) continue
            val category = classifyMerchant(tx.merchant, tx.description)
            categories[category] = (categories[category] ?: 0.0) + tx.spending
        }
        return categories.toList().sortedByDescending { it.second }.toMap()
    }

    fun analyzeMealTimes(transactions: List<RawCardTransaction>): Map<String, Double> {
        val meals = linkedMapOf("早餐" to 0.0, "午餐" to 0.0, "晚餐" to 0.0, "夜宵" to 0.0)
        for (tx in transactions) {
            if (tx.amount >= 0) continue
            if (classifyMerchant(tx.merchant, tx.description) != "餐饮") continue
            val hour = tx.time.substringAfter(" ", "").substringBefore(":").toIntOrNull() ?: continue
            val period = when (hour) {
                in 5..10 -> "早餐"
                in 11..14 -> "午餐"
                in 17..21 -> "晚餐"
                in 22..23, in 0..4 -> "夜宵"
                else -> null
            } ?: continue
            meals[period] = (meals[period] ?: 0.0) + tx.spending
        }
        return meals.filterValues { it > 0.0 }
    }

    fun classifyMerchant(merchant: String, description: String): String {
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
                || m.contains("粥") || m.contains("菜") || m.contains("咖啡") || m.contains("饮")
                || m.contains("小吃") || m.contains("麻辣") || m.contains("烤") || m.contains("奶茶") -> "餐饮"
            m.contains("医院") || m.contains("药") || m.contains("诊所") || m.contains("卫生") -> "医疗"
            d.contains("圈存") || d.contains("充值") || d.contains("转账") -> "充值"
            else -> "其他"
        }
    }

    fun money(value: Double): String = "¥ ${format2(value)}"

    fun signedMoney(value: Double): String =
        if (value >= 0) "+${money(value)}" else "-${money(-value)}"

    private fun format2(value: Double): String {
        val cents = (value * 100).toInt()
        val yuan = cents / 100
        val cent = kotlin.math.abs(cents % 100)
        return "$yuan.${cent.toString().padStart(2, '0')}"
    }
}
