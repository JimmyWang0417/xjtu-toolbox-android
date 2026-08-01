package com.xjtu.toolbox.ui

// ── 通用课格接口 ─────────────────────────

interface ScheduleSlot {
    val slotName: String
    val slotLocation: String
    val slotDayOfWeek: Int
    val slotStartSection: Int
    val slotEndSection: Int
}
