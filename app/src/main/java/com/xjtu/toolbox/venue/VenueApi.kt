package com.xjtu.toolbox.venue

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xjtu.toolbox.auth.AuthExpiredException
import com.xjtu.toolbox.auth.SiteSession
import com.xjtu.toolbox.auth.VenueLogin
import com.xjtu.toolbox.auth.XJTULogin
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * 体育场馆预订 API
 *
 * 基于 http://202.117.17.144 的 HTML + JSON 混合接口（无独立 8071 端口——见下方说明）。
 *
 * 真实预订流程（按抓包 HAR 核实，2026-07-31）：
 * 1. `product/findtime.html?s_dates=日期&serviceid=场馆ID` → 该场馆当天全部时间段
 *    （TIME_NO），含库存 ID（stockid，即下方 stockId）、总量/已用/剩余。
 * 2. 若该时段库存 `surplus > 0`，再查 `seat/seat.html?id=场馆ID&stockid=库存ID`
 *    → 该库存下的具体场地（若场馆无需选场地，接口无场地明细，退化为整段一个单元）。
 *    场地明细取自返回 HTML 里 `#txt_seatid` 隐藏字段，格式
 *    `场地序号_场地明细ID_容量,场地序号_场地明细ID_容量,...`。
 * 3. 提交预订前先 POST `order/show.html?id=场馆ID`（body 只有 `param` 字段，
 *    JSON：`{"stock":{库存ID:数量},"address":"场馆ID","stockdetailids":"明细ID,...","extend":{}}`），
 *    服务端会返回一份自己校验/补全过的 `_param`（嵌在返回 HTML 的
 *    `<script>var _param=eval({...});var _booked=eval(0);...</script>` 里）。
 *    **提交订单必须原样带着这份服务端生成的 `_param`，不能自己重新拼**——自拼的
 *    参数字段和服务端期望的不完全一致，会被拒绝。
 * 4. 拿滑动验证码 `GET /gen`；用户滑动完成后拼接
 *    `yzm = <轨迹JSON>synjones<验证码ID>synjoneshttp://202.117.17.144:8071`
 *    ——这段 `:8071` 是服务器滑块页面 JS 里固定写死、用于服务端校验的文本，
 *    不是真实访问端口，必须原样保留在这个字符串里。
 * 5. `POST /order/book.html`（真实终点，通过页面 `cu()` 函数拼接 contextPath +
 *    url + ".html" 得出，客户端 JS 字面量写的是 `/order/book` 容易误判为无后缀），
 *    body：`param=<步骤3拿到的_param>&yzm=<步骤4拼接串>&json=true`。
 *
 * 历史教训：早期实现假设了两个从未在真实站点出现过的接口
 * （`findOkArea.html`/`findLockArea.html`），且把 `BASE_URL` 错误地设成
 * `http://202.117.17.144:8071`——抓包证实全站请求都走默认 80 端口，`:8071`
 * 只在上面第 4 步的 `yzm` 拼接串里作为固定文本出现。
 */
class VenueApi(private val site: SiteSession) {

    companion object {
        private const val TAG = "VenueApi"
        private const val BASE = "http://202.117.17.144"
        /**
         * 订单页由系统浏览器打开。支付页会继续使用当前站点的 CAS 会话，
         * 因而不能把 Android App 内的 Cookie 生硬地拼进 URL。
         */
        const val PAYMENT_BASE = BASE
        const val BROWSER_LOGIN_URL = VenueLogin.VENUE_OAUTH_URL
        private const val MAX_ORDER_PAGES = 100
        private val gson = Gson()

        // order/show.html 返回页面里嵌入的 `var _param=eval({...});var _booked=eval`
        // ——用非贪婪到下一条已知语句的方式截取，避免手动数花括号出错。
        private val PARAM_REGEX = Regex("""var _param=eval\((\{.*)\);var _booked=eval""")
    }

    private fun request(url: String, referer: String = "$BASE/product/index.html"): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Referer", referer)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36")

    private fun ajaxRequest(url: String, referer: String): Request.Builder =
        request(url, referer).header("X-Requested-With", "XMLHttpRequest")

    private fun execute(builder: Request.Builder) =
        runBlocking { site.executeWithReAuth(builder.build()) }

    // ─── 数据模型 ─────────────────────────────────────────

    /** 场馆（从 product/index.html 解析） */
    data class Venue(
        val id: Int,
        val name: String,
        val address: String? = null,
        val iconType: String? = null    // icon-badminton, icon-tennis, ...
    )

    /** 一个时段下的一个可选场地单元（从 findtime.html + seat/seat.html 合并得出） */
    data class AreaSlot(
        val areaDetailId: Long,   // 场地明细ID，提交订单 stockdetailids 用；无细分场地时退化为 stockId
        val areaName: String,     // "场地1"/"场地2"/...；无细分场地时为 "预订"；已满时为 "已满"
        val stockId: Long,        // 库存ID，提交订单 stock map 的 key，同一时段下所有场地共享
        val timeSlot: String,     // 18:00-19:00
        val price: Double,
        val date: String,         // 2026-03-03
        val allCount: Int,        // 该时段总容量（时段级，非逐场地）
        val usingNum: Int,        // 已用（时段级）
        val surplus: Int,         // 剩余（时段级）——服务端只在这个粒度给出占用数据
        val serviceid: String
    ) {
        val isAvailable: Boolean get() = surplus > 0
    }

    /** 验证码数据 */
    data class CaptchaData(
        val id: String,
        val backgroundImage: String,  // data:image/jpeg;base64,...
        val sliderImage: String,      // data:image/png;base64,...
        val bgWidth: Int,
        val bgHeight: Int,
        val sliderWidth: Int,
        val sliderHeight: Int
    )

    /** 服务端在 order/show.html 步骤生成的待提交订单参数（必须原样带回，不能自拼） */
    data class PendingOrder(private val paramJson: String) {
        internal fun rawParamJson(): String = paramJson
    }

    /** 预订结果 */
    data class BookingResult(
        val success: Boolean,
        val orderId: String? = null,
        val price: Double = 0.0,
        val message: String = ""
    )

    /** 一个订单明细（一个日期/时段/场地）。 */
    data class OrderDetail(
        val date: String,
        val timeSlot: String,
        val areaName: String,
        val price: Double,
        val serviceId: String,
        val serviceName: String
    )

    /** 订单信息。状态值与场馆服务端保持一致：0 预订中、1 预订成功、2 预订取消。 */
    data class OrderInfo(
        val orderId: String,
        val status: Int,
        val createdAt: String,
        val price: Double,
        val details: List<OrderDetail>
    ) {
        val statusText: String
            get() = when (status) {
                0 -> "预订中"
                1 -> "预订成功"
                2 -> "预订取消"
                else -> "未知状态($status)"
            }

        val venueName: String
            get() = details.firstOrNull { it.serviceName.isNotBlank() }?.serviceName.orEmpty()

        val firstDate: String
            get() = details.firstOrNull()?.date.orEmpty()

        /** 待支付订单可直接唤起支付引导。 */
        val canPay: Boolean get() = status == 0

        /** 服务端允许对预订中/预订成功订单发起取消。 */
        val canCancel: Boolean get() = status == 0 || status == 1
    }

    /** 订单分页响应。服务端不同部署可能返回数组或带 rows/object 的对象，统一成此模型。 */
    data class OrderPage(
        val orders: List<OrderInfo>,
        val page: Int,
        val pageSize: Int,
        val total: Int? = null,
        val hasMore: Boolean = false
    )

    /** 取消订单/其它订单操作的统一结果。 */
    data class OrderActionResult(
        val success: Boolean,
        val message: String
    )

    private data class TimeSlotInfo(
        val timeNo: String,
        val stockId: Long,
        val price: Double,
        val allCount: Int,
        val usingNum: Int,
        val surplus: Int
    )

    // ─── API 方法 ─────────────────────────────────────────

    /**
     * 获取场馆列表（从 index.html 解析）
     */
    fun fetchVenueList(): List<Venue> {
        val response = execute(request("$BASE/product/index.html"))
        val html = response.body?.string() ?: throw RuntimeException("获取场馆列表失败")
        response.close()
        if (XJTULogin.isAuthFailureResponse(html)) {
            throw AuthExpiredException("体育场馆")
        }

        val doc = Jsoup.parse(html, BASE)
        val venues = mutableListOf<Venue>()

        // HTML 结构: <li><a href="show.html?id=55"></a><dl><dt><i class="icon icon-tennis ..."></i></dt><dd><h5>场馆名</h5><div class="address">地址:...</div>...</dd></dl></li>
        // <a> 标签里没有文字，场馆名在同级 <dl>→<dd>→<h5> 中
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

        Log.d(TAG, "fetchVenueList: ${venues.size} venues found")
        if (venues.isEmpty()) {
            Log.w(TAG, "fetchVenueList: empty, title=${doc.title()}, body=${doc.body().text().take(300)}")
            throw RuntimeException("场馆列表为空或页面结构已变化，请稍后重试")
        }
        return venues
    }

    /**
     * 获取指定场馆某日的可预约场地/时段。
     *
     * 两级查询：先 findtime.html 拿时段+库存量，再对每个「有剩余」的时段查
     * seat/seat.html 拿具体场地明细。已满的时段不再查场地明细（服务端也查不出
     * 有意义的数据），直接标记为不可选。
     */
    fun fetchAvailableSlots(serviceid: Int, date: String): List<AreaSlot> {
        val showReferer = "$BASE/product/show.html?id=$serviceid"
        val timeSlots = fetchTimeSlots(serviceid, date, showReferer)
        val result = mutableListOf<AreaSlot>()
        for (ts in timeSlots) {
            if (ts.surplus <= 0) {
                result.add(
                    AreaSlot(
                        areaDetailId = ts.stockId,
                        areaName = "已满",
                        stockId = ts.stockId,
                        timeSlot = ts.timeNo,
                        price = ts.price,
                        date = date,
                        allCount = ts.allCount,
                        usingNum = ts.usingNum,
                        surplus = ts.surplus,
                        serviceid = serviceid.toString()
                    )
                )
                continue
            }
            val areas = fetchSeatAreas(serviceid, ts.stockId, showReferer)
            if (areas.isEmpty()) {
                // 场馆本身不需要选具体场地：整段作为单一预订单元
                result.add(
                    AreaSlot(
                        areaDetailId = ts.stockId,
                        areaName = "预订",
                        stockId = ts.stockId,
                        timeSlot = ts.timeNo,
                        price = ts.price,
                        date = date,
                        allCount = ts.allCount,
                        usingNum = ts.usingNum,
                        surplus = ts.surplus,
                        serviceid = serviceid.toString()
                    )
                )
            } else {
                areas.forEach { (detailId, name) ->
                    result.add(
                        AreaSlot(
                            areaDetailId = detailId,
                            areaName = name,
                            stockId = ts.stockId,
                            timeSlot = ts.timeNo,
                            price = ts.price,
                            date = date,
                            allCount = ts.allCount,
                            usingNum = ts.usingNum,
                            surplus = ts.surplus,
                            serviceid = serviceid.toString()
                        )
                    )
                }
            }
        }
        return result
    }

    private fun fetchTimeSlots(serviceid: Int, date: String, referer: String): List<TimeSlotInfo> {
        val url = "$BASE/product/findtime.html?type=day&s_dates=$date&serviceid=$serviceid&_=${System.currentTimeMillis()}"
        val response = execute(ajaxRequest(url, referer))
        val body = response.body?.string() ?: return emptyList()
        response.close()
        if (XJTULogin.isAuthFailureResponse(body)) throw AuthExpiredException("体育场馆")

        return try {
            val json = gson.fromJson(body, JsonObject::class.java)
            // 该场馆当天无任何时段时，"object" 字段值是 JSON null（不是字段缺失），
            // getAsJsonArray 会直接把 JsonNull 强转 JsonArray 抛 ClassCastException，
            // 必须先判断 isJsonNull 再取 JsonArray，不能只靠 get() 返回 Kotlin null 兜底。
            val objectEl = json.get("object")
            if (objectEl == null || objectEl.isJsonNull) return emptyList()
            val arr = objectEl.asJsonArray
            arr.mapNotNull { el ->
                val obj = el.asJsonObject
                val timeNo = obj.get("TIME_NO")?.asString ?: return@mapNotNull null
                val stockId = obj.get("ID")?.asLong ?: return@mapNotNull null
                val price = obj.get("PRICE")?.asString?.toDoubleOrNull() ?: 0.0
                val allCount = obj.get("ALL_COUNT")?.asInt ?: 0
                val usingNum = obj.get("USING_NUM")?.asInt ?: 0
                val surplus = obj.get("SURPLUS")?.asInt ?: (allCount - usingNum).coerceAtLeast(0)
                TimeSlotInfo(timeNo, stockId, price, allCount, usingNum, surplus)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchTimeSlots parse error", e)
            emptyList()
        }
    }

    /**
     * 查询某个库存（时段实例）下的具体场地明细。
     * 返回 (场地明细ID, 场地名) 列表；场馆不需要选场地时返回空列表。
     */
    private fun fetchSeatAreas(serviceid: Int, stockId: Long, referer: String): List<Pair<Long, String>> {
        val url = "$BASE/seat/seat.html?id=$serviceid&type=2&stockid=$stockId&json=html&_=${System.currentTimeMillis()}"
        val response = execute(ajaxRequest(url, referer))
        val html = response.body?.string() ?: return emptyList()
        response.close()

        return try {
            val doc = Jsoup.parse(html, BASE)
            // <input type="hidden" value="1_4286121_1,2_4286122_2," id="txt_seatid" />
            // 格式：场地序号_场地明细ID_容量，逐个逗号分隔
            val seatIdRaw = doc.selectFirst("#txt_seatid")?.attr("value").orEmpty()
            if (seatIdRaw.isBlank()) return emptyList()

            // <span class="cell football" title="场地1" data="1" id="seat_1" rel="2">
            val nameByIndex = doc.select("span.cell").mapNotNull { span ->
                val idx = span.attr("data").toIntOrNull() ?: return@mapNotNull null
                val title = span.attr("title").ifBlank { "场地$idx" }
                idx to title
            }.toMap()

            seatIdRaw.split(",")
                .mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
                .mapNotNull { entry ->
                    val parts = entry.split("_")
                    if (parts.size < 2) return@mapNotNull null
                    val idx = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val detailId = parts[1].toLongOrNull() ?: return@mapNotNull null
                    detailId to (nameByIndex[idx] ?: "场地$idx")
                }
        } catch (e: Exception) {
            Log.e(TAG, "fetchSeatAreas parse error", e)
            emptyList()
        }
    }

    /**
     * 生成滑动验证码
     */
    fun generateCaptcha(serviceid: Int): CaptchaData {
        val referer = "$BASE/order/show.html?id=$serviceid"
        val response = execute(ajaxRequest("$BASE/gen", referer))
        val body = response.body?.string() ?: throw RuntimeException("获取验证码失败")
        response.close()

        val json = gson.fromJson(body, JsonObject::class.java)
        val captcha = json.getAsJsonObject("captcha")
        return CaptchaData(
            id = json.get("id").asString,
            backgroundImage = captcha.get("backgroundImage").asString,
            sliderImage = captcha.get("sliderImage").asString,
            bgWidth = captcha.get("backgroundImageWidth").asInt,
            bgHeight = captcha.get("backgroundImageHeight").asInt,
            sliderWidth = captcha.get("sliderImageWidth").asInt,
            sliderHeight = captcha.get("sliderImageHeight").asInt
        )
    }

    /**
     * 预订第一步：把选中的场地+时段提交给服务端，换回一份服务端校验/补全过的
     * `_param`。这份 `_param` 必须原样带到 [submitBooking]，不能自己重新构造
     * ——自拼字段和服务端期望的结构不完全一致，会被服务端拒绝。
     *
     * @param serviceid 场馆 ID
     * @param selections 选中的 AreaSlot 列表（可跨多个时段/库存）
     */
    fun prepareOrder(serviceid: Int, selections: List<AreaSlot>): PendingOrder {
        require(selections.isNotEmpty()) { "请先选择时段" }

        // 同一库存(stockId)下选中的场地数量即为该库存的预订份数
        val stockCounts = LinkedHashMap<Long, Int>()
        selections.forEach { stockCounts[it.stockId] = (stockCounts[it.stockId] ?: 0) + 1 }
        val stockDetailIds = selections.map { it.areaDetailId.toString() }.distinct()

        val param = JsonObject().apply {
            add(
                "stock",
                JsonObject().apply { stockCounts.forEach { (id, count) -> addProperty(id.toString(), count.toString()) } }
            )
            addProperty("address", serviceid.toString())
            addProperty("stockdetailids", stockDetailIds.joinToString(","))
            add("extend", JsonObject())
        }

        val formBody = FormBody.Builder()
            .add("param", gson.toJson(param))
            .build()

        val referer = "$BASE/product/show.html?id=$serviceid"
        val response = execute(request("$BASE/order/show.html?id=$serviceid", referer).post(formBody))
        val html = response.body?.string() ?: throw RuntimeException("获取订单确认信息失败")
        response.close()

        if (XJTULogin.isAuthFailureResponse(html)) throw AuthExpiredException("体育场馆")

        val match = PARAM_REGEX.find(html)
            ?: run {
                Log.w(TAG, "prepareOrder: _param not found, body preview=${html.take(300)}")
                throw RuntimeException("该时段可能已被预订或下架，请重新选择")
            }
        return PendingOrder(match.groupValues[1])
    }

    /**
     * 预订第二步：带着 [prepareOrder] 拿到的服务端 `_param` + 滑块验证码结果提交订单。
     *
     * @param serviceid 场馆 ID（用于拼接 Referer，不参与提交参数本身）
     * @param pendingOrder [prepareOrder] 返回的服务端参数
     * @param captchaId 验证码 ID（from [generateCaptcha]）
     * @param sliderTrackJson 滑动轨迹 JSON 字符串
     */
    fun submitBooking(
        serviceid: Int,
        pendingOrder: PendingOrder,
        captchaId: String,
        sliderTrackJson: String
    ): BookingResult {
        // 服务端滑块页面固定拼接格式：{轨迹JSON}synjones{验证码ID}synjones{固定文本}
        // 这段固定文本本身写的是 8071 端口，是服务端用来做签名校验的常量，
        // 不是真实访问端口，必须原样带上，不能因为「端口是错的」而删掉。
        val yzm = "${sliderTrackJson}synjones${captchaId}synjoneshttp://202.117.17.144:8071"

        val formBody = FormBody.Builder()
            .add("param", pendingOrder.rawParamJson())
            .add("yzm", yzm)
            .add("json", "true")
            .build()

        val referer = "$BASE/order/show.html?id=$serviceid"
        // 真实终点是 order/book.html——页面 JS 用 cu(url) 给 "/order/book" 拼上
        // contextPath + ".html" 后缀，字面量容易被误读成没有后缀。
        val response = execute(ajaxRequest("$BASE/order/book.html", referer).post(formBody))
        val responseBody = response.body?.string() ?: "{}"
        response.close()

        Log.d(TAG, "submitBooking: response=$responseBody")

        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val result = json.get("result")?.asString
            val message = json.get("message")?.asString ?: ""
            val objElem = json.get("object")
            val obj = if (objElem != null && objElem.isJsonObject) objElem.asJsonObject else null
            val orderId = obj?.get("orderid")?.takeIf { !it.isJsonNull }?.asString

            // shopping.js 里 result=="1" 直接完成（免支付/已扣款），result=="2"
            // 需要跳转支付页——两种都算「预订成功」，只是后续动作不同。
            if ((result == "1" || result == "2") && !orderId.isNullOrBlank()) {
                val price = obj.get("price")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0
                val needsPay = result == "2"
                BookingResult(
                    success = true,
                    orderId = orderId,
                    price = price,
                    message = message.ifEmpty {
                        if (needsPay) "预订成功，请尽快前往「移动交通大学」App 完成支付"
                        else "预订成功"
                    }
                )
            } else {
                BookingResult(false, message = message.ifEmpty { "预订失败：${responseBody.take(200)}" })
            }
        } catch (e: Exception) {
            Log.e(TAG, "submitBooking: parse error", e)
            BookingResult(false, message = "预订失败: ${e.message}")
        }
    }

    // ─── 订单 ─────────────────────────────────────────────────────────

    /**
     * 分页查询「我的订单」。
     *
     * PR #54 使用过带 `/web` 前缀的旧部署地址；当前移动端场馆站点的实际
     * contextPath 是根路径，因此这里按现有抓包使用 `/yyuser/...`。响应在
     * 不同版本服务端上既可能是 JSON 数组，也可能包在 `rows`/`object` 中，
     * 解析器会统一兼容。
     */
    fun fetchOrders(page: Int = 1, pageSize: Int = 20): OrderPage {
        require(page >= 1) { "订单页码必须从 1 开始" }
        require(pageSize in 1..100) { "订单分页大小无效" }

        val url = "$BASE/yyuser/searchorder.html" +
            "?page=$page&rows=$pageSize&status=&iscomment=" +
            "&stockSDate=&stockEDate=&_=${System.currentTimeMillis()}"
        val response = execute(ajaxRequest(url, "$BASE/yyuser/searchorder.html"))
        val body = response.body?.string().orEmpty()
        val code = response.code
        response.close()

        if (code !in 200..299) {
            throw RuntimeException("加载订单失败（HTTP $code）")
        }
        if (XJTULogin.isAuthFailureResponse(body)) {
            throw AuthExpiredException("体育场馆")
        }
        // 没有订单时服务端会返回空数组；空 body 也按空页处理，避免把「暂无订单」
        // 错误地显示成网络故障。
        if (body.isBlank()) return OrderPage(emptyList(), page, pageSize, total = 0, hasMore = false)

        return parseOrderPage(body, page, pageSize)
    }

    /** 与旧客户端命名保持兼容，供其它入口按需读取单页订单。 */
    fun getOrders(page: Int = 1, pageSize: Int = 20): OrderPage =
        fetchOrders(page, pageSize)

    /** 拉取全部订单；保留分页 API 供页面按需加载。 */
    fun fetchAllOrders(pageSize: Int = 20): List<OrderInfo> {
        val result = mutableListOf<OrderInfo>()
        var page = 1
        while (page <= MAX_ORDER_PAGES) {
            val current = fetchOrders(page, pageSize)
            result += current.orders
            if (!current.hasMore || current.orders.isEmpty()) break
            page++
        }
        return result.sortedWith(
            compareByDescending<OrderInfo> { it.createdAt.ifBlank { "0000-00-00 00:00:00" } }
                .thenByDescending { it.orderId }
        )
    }

    /** 取消订单。服务端成功码通常是 `1`，同时兼容旧部署的布尔/文本返回值。 */
    fun cancelOrder(orderId: String): OrderActionResult {
        require(orderId.isNotBlank()) { "订单号不能为空" }
        val form = FormBody.Builder()
            .add("orderid", orderId)
            .add("json", "true")
            .build()
        val response = execute(
            ajaxRequest("$BASE/order/delorder.html", "$BASE/yyuser/searchorder.html")
                .post(form)
        )
        val body = response.body?.string().orEmpty()
        val code = response.code
        response.close()

        if (code !in 200..299) {
            return OrderActionResult(false, "取消订单失败（HTTP $code）")
        }
        if (XJTULogin.isAuthFailureResponse(body)) {
            throw AuthExpiredException("体育场馆")
        }

        val root = runCatching { JsonParser.parseString(body) }.getOrNull()
        val obj = root?.takeIf { it.isJsonObject }?.asJsonObject
        val result = readString(obj, "result", "code", "success").orEmpty().lowercase()
        val message = readString(obj, "message", "msg", "notice")
            ?.takeIf { it.isNotBlank() }
            ?: if (result in setOf("1", "true", "success", "ok")) "取消成功" else "取消失败"
        val success = result in setOf("1", "true", "success", "ok") ||
            (result == "100" && message.contains("成功"))
        return OrderActionResult(success, message)
    }

    /** 支付页面 URL（订单支付需要在系统浏览器中完成 CAS 会话接力）。 */
    fun paymentUrl(orderId: String): String =
        "$PAYMENT_BASE/pay/show.html?id=${java.net.URLEncoder.encode(orderId, Charsets.UTF_8.name())}"

    /** PR #54 中使用的命名别名。 */
    fun payUrl(orderId: String): String = paymentUrl(orderId)

    private fun parseOrderPage(body: String, page: Int, pageSize: Int): OrderPage {
        val root = try {
            JsonParser.parseString(body)
        } catch (e: Exception) {
            val text = Jsoup.parse(body).text().trim()
            throw RuntimeException(text.takeIf { it.isNotBlank() } ?: "订单接口返回格式异常", e)
        }

        val array = findOrderArray(root)
        if (array == null) {
            // 某些部署在没有订单时返回 `{object:null}`，与空数组等价。
            if (root.isJsonObject && root.asJsonObject.entrySet().all { it.value.isJsonNull }) {
                return OrderPage(emptyList(), page, pageSize, total = 0, hasMore = false)
            }
            throw RuntimeException("订单接口返回格式异常")
        }

        val orders = array.mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject?.let(::parseOrder)
        }.filter { it.orderId.isNotBlank() }
        val total = findTotal(root)
        val hasMore = total?.let { page * pageSize < it } ?: (orders.size >= pageSize)
        return OrderPage(orders, page, pageSize, total, hasMore)
    }

    private fun parseOrder(item: JsonObject): OrderInfo {
        val details = mutableListOf<OrderDetail>()
        val detailsElement = firstElement(item, "orderdetail", "orderDetail", "details", "items")
        val detailElements = when {
            detailsElement?.isJsonArray == true -> detailsElement.asJsonArray.toList()
            detailsElement?.isJsonObject == true -> listOf(detailsElement)
            else -> emptyList()
        }
        detailElements.forEach { element ->
            if (!element.isJsonObject) return@forEach
            val detail = element.asJsonObject
            val stock = firstElement(detail, "stock")?.asObjectOrNull()
            val stockDetail = firstElement(detail, "stockdetail", "stockDetail")?.asObjectOrNull()
            val service = firstElement(detail, "service", "venue", "product")?.asObjectOrNull()
            details += OrderDetail(
                date = readString(stock, "s_date", "sDate", "date", "stockSDate").orEmpty(),
                timeSlot = readString(stock, "time_no", "timeNo", "time", "timeSlot").orEmpty(),
                areaName = readString(stockDetail, "sname", "name", "areaName", "area").orEmpty(),
                price = readDouble(detail, "price", "amount", "money"),
                serviceId = readString(detail, "serviceid", "serviceId", "id").orEmpty(),
                serviceName = readString(service, "name", "serviceName", "servicename").orEmpty()
            )
        }
        return OrderInfo(
            orderId = readString(item, "orderid", "orderId", "id").orEmpty(),
            status = readInt(item, "status", "orderStatus", "state"),
            createdAt = readString(item, "createdate", "createDate", "created_at", "orderDate").orEmpty(),
            price = readDouble(item, "price", "amount", "money"),
            details = details
        )
    }

    /** 在数组/rows/object/data/list 等常见包装中寻找订单数组。 */
    private fun findOrderArray(element: JsonElement?, depth: Int = 0): JsonArray? {
        if (element == null || element.isJsonNull || depth > 4) return null
        if (element.isJsonArray) {
            val array = element.asJsonArray
            // 空数组本身就是合法的「暂无订单」响应；非空数组则避免误把
            // orderdetail/其它业务数组当成订单列表。
            if (array.size() == 0 || array.any { candidate ->
                    candidate.isJsonObject && firstElement(
                        candidate.asJsonObject,
                        "orderid", "orderId", "orderStatus", "createdate", "createDate"
                    ) != null
                }) return array
            return array.asSequence()
                .mapNotNull { child -> findOrderArray(child, depth + 1) }
                .firstOrNull()
        }
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        val preferred = listOf("object", "rows", "data", "list", "orders", "orderList")
        preferred.forEach { key ->
            val child = obj.get(key)
            val found = findOrderArray(child, depth + 1)
            if (found != null) return found
        }
        obj.entrySet().forEach { (_, child) ->
            val found = findOrderArray(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun findTotal(element: JsonElement?, depth: Int = 0): Int? {
        if (element == null || element.isJsonNull || depth > 3) return null
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        listOf("total", "totalCount", "records", "count").forEach { key ->
            val value = obj.get(key)
            if (value != null && !value.isJsonNull) {
                readInt(value)?.let { return it }
            }
        }
        listOf("object", "data", "result").forEach { key ->
            findTotal(obj.get(key), depth + 1)?.let { return it }
        }
        return null
    }

    private fun firstElement(obj: JsonObject?, vararg keys: String): JsonElement? {
        if (obj == null) return null
        keys.forEach { key ->
            val direct = obj.get(key)
            if (direct != null && !direct.isJsonNull) return direct
        }
        obj.entrySet().forEach { (key, value) ->
            if (!value.isJsonNull && keys.any { it.equals(key, ignoreCase = true) }) return value
        }
        return null
    }

    private fun readString(obj: JsonObject?, vararg keys: String): String? =
        readString(firstElement(obj, *keys))

    private fun readString(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null
        return runCatching { element.asString }.getOrNull()?.trim()
    }

    private fun readInt(obj: JsonObject?, vararg keys: String): Int =
        readInt(firstElement(obj, *keys)) ?: 0

    private fun readInt(element: JsonElement?): Int? {
        val raw = readString(element) ?: return null
        return raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt()
    }

    private fun readDouble(obj: JsonObject?, vararg keys: String): Double =
        readDouble(firstElement(obj, *keys))

    private fun readDouble(element: JsonElement?): Double {
        val raw = readString(element).orEmpty()
            .replace(",", "")
            .replace("¥", "")
            .replace("￥", "")
        return raw.toDoubleOrNull() ?: 0.0
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        takeIf { it.isJsonObject }?.asJsonObject

}
