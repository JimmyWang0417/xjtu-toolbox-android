package com.xjtu.toolbox.venue

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 滑动轨迹中的单个点
 */
data class TrackPoint(
    val x: Int,
    val y: Int,
    val type: String,  // "down", "move", "up"
    val t: Long        // 相对时间戳（ms）
)

/**
 * 滑动验证码结果
 */
data class SliderResult(
    val bgImageWidth: Int,
    val bgImageHeight: Int,
    val sliderImageWidth: Int,
    val sliderImageHeight: Int,
    val startSlidingTime: String,   // ISO 8601
    val entSlidingTime: String,     // ISO 8601
    val trackList: List<TrackPoint>
) {
    fun toJson(): String {
        val obj = buildJsonObject {
            put("bgImageWidth", bgImageWidth)
            put("bgImageHeight", bgImageHeight)
            put("sliderImageWidth", sliderImageWidth)
            put("sliderImageHeight", sliderImageHeight)
            put("startSlidingTime", startSlidingTime)
            put("entSlidingTime", entSlidingTime)
            put("trackList", buildJsonArray {
                trackList.forEach { tp ->
                    add(buildJsonObject {
                        put("x", tp.x)
                        put("y", tp.y)
                        put("type", tp.type)
                        put("t", tp.t)
                    })
                }
            })
        }
        return obj.toString()
    }
}

/**
 * 生成模拟人类滑动轨迹（自动解题时用）
 * @param targetX 目标 x 坐标（显示坐标系）
 * @param duration 总滑动时间 (ms)
 */
fun generateHumanLikeTrack(targetX: Int, duration: Long = 1200L): List<TrackPoint> {
    val baseT = (800L..1500L).random()
    val points = mutableListOf<TrackPoint>()
    points.add(TrackPoint(0, 0, "down", baseT))

    val steps = (duration / 16).toInt()  // ~60fps
    var t = baseT + (100L..200L).random()
    for (i in 1..steps) {
        val progress = i.toFloat() / steps
        // 缓动函数：先快后慢 (easeOutCubic)
        val eased = 1 - (1 - progress) * (1 - progress) * (1 - progress)
        val x = (targetX * eased).toInt()
        val y = (-2..2).random()  // 微小 y 轴抖动
        points.add(TrackPoint(x, y, "move", t))
        t += (12L..20L).random()
    }

    points.add(TrackPoint(targetX, (-3..0).random(), "up", t + (200L..500L).random()))
    return points
}
