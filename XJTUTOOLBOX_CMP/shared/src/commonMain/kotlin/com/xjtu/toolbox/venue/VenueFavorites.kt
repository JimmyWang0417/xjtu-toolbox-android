package com.xjtu.toolbox.venue

import com.xjtu.toolbox.util.FileSystem
import com.xjtu.toolbox.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 场馆收藏管理器（CMP 版本）
 *
 * 使用 FileSystem 持久化收藏数据，通过 StateFlow 提供响应式状态更新
 */
class VenueFavorites(private val storagePath: String) {

    companion object {
        private const val TAG = "VenueFavorites"
        private const val FILE_NAME = "venue_favorites.txt"
    }

    private val filePath = "$storagePath/$FILE_NAME"

    private val _favoriteIds = MutableStateFlow<Set<Int>>(loadFavorites())
    val favoriteIds: StateFlow<Set<Int>> = _favoriteIds.asStateFlow()

    /**
     * 判断场馆是否已收藏
     */
    fun isFavorite(venueId: Int): Boolean = _favoriteIds.value.contains(venueId)

    /**
     * 切换收藏状态
     * @return 切换后的收藏状态
     */
    fun toggleFavorite(venueId: Int): Boolean {
        val current = _favoriteIds.value.toMutableSet()
        val newState = if (current.contains(venueId)) {
            current.remove(venueId)
            false
        } else {
            current.add(venueId)
            true
        }
        _favoriteIds.value = current
        saveFavorites(current)
        return newState
    }

    /**
     * 添加收藏
     */
    fun addFavorite(venueId: Int) {
        if (!_favoriteIds.value.contains(venueId)) {
            val current = _favoriteIds.value.toMutableSet()
            current.add(venueId)
            _favoriteIds.value = current
            saveFavorites(current)
        }
    }

    /**
     * 移除收藏
     */
    fun removeFavorite(venueId: Int) {
        if (_favoriteIds.value.contains(venueId)) {
            val current = _favoriteIds.value.toMutableSet()
            current.remove(venueId)
            _favoriteIds.value = current
            saveFavorites(current)
        }
    }

    private fun loadFavorites(): Set<Int> {
        return try {
            val content = FileSystem.readText(filePath) ?: return emptySet()
            content.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        } catch (e: Exception) {
            Logger.w(TAG, "loadFavorites failed: ${e.message}")
            emptySet()
        }
    }

    private fun saveFavorites(ids: Set<Int>) {
        try {
            FileSystem.writeText(filePath, ids.joinToString(","))
        } catch (e: Exception) {
            Logger.w(TAG, "saveFavorites failed: ${e.message}")
        }
    }
}
