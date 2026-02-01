package com.example.fileexplorerjc

import android.content.Context
import android.content.SharedPreferences

/**
 * 管理文件类型的默认打开应用
 * 通过文件扩展名来保存和查询默认应用的包名
 */
object DefaultAppManager {
    private const val PREFS_NAME = "default_app_prefs"
    private const val KEY_PREFIX = "default_app_"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 设置某个扩展名的默认打开应用
     * @param extension 文件扩展名（不含点，如 "pdf"）
     * @param packageName 应用包名
     */
    fun setDefaultApp(extension: String, packageName: String) {
        prefs.edit()
            .putString("$KEY_PREFIX${extension.lowercase()}", packageName)
            .apply()
    }

    /**
     * 获取某个扩展名的默认打开应用
     * @param extension 文件扩展名
     * @return 应用包名，如果未设置则返回 null
     */
    fun getDefaultApp(extension: String): String? {
        return prefs.getString("$KEY_PREFIX${extension.lowercase()}", null)
    }

    /**
     * 清除某个扩展名的默认打开应用
     * @param extension 文件扩展名
     */
    fun clearDefaultApp(extension: String) {
        prefs.edit()
            .remove("$KEY_PREFIX${extension.lowercase()}")
            .apply()
    }

    /**
     * 检查某个扩展名是否设置了默认应用
     */
    fun hasDefaultApp(extension: String): Boolean {
        return getDefaultApp(extension) != null
    }

    /**
     * 检查指定包名是否是某扩展名的默认应用
     */
    fun isDefaultApp(extension: String, packageName: String): Boolean {
        return getDefaultApp(extension) == packageName
    }
}
