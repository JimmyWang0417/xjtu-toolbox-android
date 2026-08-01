package com.xjtu.toolbox.venue

import com.fleeksoft.ksoup.Ksoup
import com.xjtu.toolbox.auth.VenueLogin
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.currentTimeMillis
import com.xjtu.toolbox.util.safeParseJsonObject
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val TAG = "VenueApi"

class VenueApi(private val login: VenueLogin) {

    private val base = VenueLogin.BASE_URL

    // ─── 数据模型 ─────────────────────────────────────────

    data class Venue(
        val id: Int, val name: String,
        val address: String? = null, val iconType: String? = null
    )

    data class AreaSlot(
        val areaId: Long, val areaName: String, val stockId: Long,
        val timeSlot: String, val price: Double, val date: String,
        val status: Int, val allCount: Int, val usingNum: Int, val serviceid: String
    ) {
        val isAvailable: Boolean get() = status == 1
    }

    data class CaptchaData(
        val id: String, val backgroundImage: String, val sliderImage: String,
        val bgWidth: Int, val bgHeight: Int, val sliderWidth: Int, val sliderHeight: Int
    )

    data class BookingResult(
        val success: Boolean, val orderId: String? = null,
        val price: Double = 0.0, val message: String = ""
    )

    // ─── API 方法 ─────────────────────────────────────────

    suspend fun fetchVenueList(): List<Venue> {
        val html = login.executeWithReAuth {
            val resp = login.client.get("$base/product/index.html")
            resp.status.value to resp.bodyAsText()
        }

        val doc = Ksoup.parse(html, base)
        val venues = mutableListOf<Venue>()

        doc.select("li:has(a[href*=show.html?id=])").forEach { li ->
            val a = li.selectFirst("a[href*=show.html?id=]") ?: return@forEach
            val href = a.attr("href")
            val idMatch = Regex("""id=(\d+)""").find(href) ?: return@forEach
            val id = idMatch.groupValues[1].toIntOrNull() ?: return@forEach

            val name = li.selectFirst("h5")?.text()?.trim()
            if (name.isNullOrBlank()) return@forEach

            val address = li.selectFirst(".address")?.text()?.trim()
                ?.removePrefix("地址:")?.trim()
            val iconClass = li.selectFirst("i.icon")?.className() ?: ""
            val iconType = Regex("""icon-(\w+)""").find(iconClass)?.groupValues?.get(1)

            venues.add(Venue(id, name, address, iconType))
        }

        Logger.d(TAG, "fetchVenueList: ${venues.size} venues found")
        return venues
    }

    suspend fun fetchAvailableSlots(serviceid: Int, date: String): List<AreaSlot> {
        val url = "$base/product/findOkArea.html?s_date=$date&serviceid=$serviceid&_=${currentTimeMillis()}"
        val body = login.executeWithReAuth {
            val resp = login.client.get(url)
            resp.status.value to resp.bodyAsText()
        }
        return parseAreaSlots(body, date, serviceid.toString())
    }

    suspend fun fetchLockedSlots(serviceid: Int, date: String): List<AreaSlot> {
        val url = "$base/product/findLockArea.html?s_date=$date&serviceid=$serviceid&_=${currentTimeMillis()}"
        val body = login.executeWithReAuth {
            val resp = login.client.get(url)
            resp.status.value to resp.bodyAsText()
        }
        return parseAreaSlots(body, date, serviceid.toString())
    }

    suspend fun generateCaptcha(): CaptchaData {
        val body = login.executeWithReAuth {
            val resp = login.client.get("$base/gen")
            resp.status.value to resp.bodyAsText()
        }
        val json = body.safeParseJsonObject()
        val captcha = json["captcha"]!!.jsonObject
        return CaptchaData(
            id = json["id"]!!.jsonPrimitive.content,
            backgroundImage = captcha["backgroundImage"]!!.jsonPrimitive.content,
            sliderImage = captcha["sliderImage"]!!.jsonPrimitive.content,
            bgWidth = captcha["backgroundImageWidth"]!!.jsonPrimitive.int,
            bgHeight = captcha["backgroundImageHeight"]!!.jsonPrimitive.int,
            sliderWidth = captcha["sliderImageWidth"]!!.jsonPrimitive.int,
            sliderHeight = captcha["sliderImageHeight"]!!.jsonPrimitive.int
        )
    }

    suspend fun submitBooking(
        serviceid: Int, selections: List<AreaSlot>,
        captchaId: String, sliderTrackJson: String
    ): BookingResult {
        val stockMap = buildJsonObject {
            for (slot in selections) put(slot.stockId.toString(), "1")
        }
        val stockDetailMap = buildJsonObject {
            for (slot in selections) put(slot.stockId.toString(), slot.areaId.toString())
        }
        val stockDetailIds = selections.joinToString(",") { it.areaId.toString() }

        val param = buildJsonObject {
            put("activityPrice", 0)
            put("activityStr", null as String?)
            put("address", serviceid.toString())
            put("dates", null as String?)
            put("extend", null as String?)
            put("flag", "0")
            put("isBulkBooking", null as String?)
            put("isbookall", "0")
            put("isfreeman", "0")
            put("istimes", "1")
            put("mercacc", null as String?)
            put("merccode", null as String?)
            put("order", null as String?)
            put("orderfrom", null as String?)
            put("remark", null as String?)
            put("serviceid", null as String?)
            put("shoppingcart", "0")
            put("sno", null as String?)
            putJsonObject("stock") { for (slot in selections) put(slot.stockId.toString(), "1") }
            putJsonObject("stockdetail") { for (slot in selections) put(slot.stockId.toString(), slot.areaId.toString()) }
            put("stockdetailids", stockDetailIds)
            put("stockid", null as String?)
            put("subscriber", "0")
            put("time_detailnames", null as String?)
            put("userBean", null as String?)
            put("venueReason", null as String?)
        }

        val yzm = "${sliderTrackJson}synjones${captchaId}synjoneshttp://202.117.17.144:8071"

        val responseBody = login.executeWithReAuth {
            val resp = login.client.submitForm(
                url = "$base/order/book.html",
                formParameters = Parameters.build {
                    append("param", param.toString())
                    append("yzm", yzm)
                    append("json", "true")
                }
            )
            resp.status.value to resp.bodyAsText()
        }

        Logger.d(TAG, "submitBooking: response=$responseBody")

        return try {
            val json = responseBody.safeParseJsonObject()
            val result = json["result"]?.jsonPrimitive?.content
            val message = json["message"]?.jsonPrimitive?.content ?: ""
            val obj = json["object"]?.jsonObject

            if (result == "2" || obj?.containsKey("orderid") == true) {
                val orderId = obj?.get("orderid")?.jsonPrimitive?.content ?: ""
                val price = obj?.get("price")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                BookingResult(true, orderId, price, message.ifEmpty { "预订成功，请尽快前往「移动交通大学」App 完成支付" })
            } else {
                BookingResult(false, message = message.ifEmpty { "预订失败：${responseBody.take(200)}" })
            }
        } catch (e: Exception) {
            Logger.e(TAG, "submitBooking: parse error", e)
            BookingResult(false, message = "预订失败: ${e.message}")
        }
    }

    // ─── 内部辅助 ─────────────────────────────────────────

    private fun parseAreaSlots(jsonStr: String, date: String, serviceid: String): List<AreaSlot> {
        val result = mutableListOf<AreaSlot>()
        try {
            val json = jsonStr.safeParseJsonObject()
            val arr = json["object"]?.jsonArray ?: return emptyList()

            for (element in arr) {
                val obj = element.jsonObject
                val areaId = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue
                val areaName = obj["sname"]?.jsonPrimitive?.content ?: "场地"
                val stockId = obj["stockid"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue
                val stock = obj["stock"]?.jsonObject ?: continue

                val timeNo = stock["time_no"]?.jsonPrimitive?.content ?: continue
                val price = stock["price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                val status = stock["status"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val allCount = stock["all_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val usingNum = stock["using_num"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                result.add(AreaSlot(
                    areaId = areaId, areaName = areaName, stockId = stockId,
                    timeSlot = timeNo, price = price, date = date,
                    status = status, allCount = allCount, usingNum = usingNum,
                    serviceid = serviceid
                ))
            }
        } catch (e: Exception) {
            Logger.e(TAG, "parseAreaSlots error", e)
        }
        return result
    }
}
