package com.example.fileexplorerjc

import android.content.Context
import android.content.SharedPreferences
import java.io.File

object FavoritesManager {
    private const val PREFS_NAME = "favorites_prefs"
    private const val KEY_FAVORITES = "favorite_paths"
    private const val SEPARATOR = "|||"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun addFavorite(path: String): Boolean {
        val favorites = getFavorites().toMutableSet()
        val added = favorites.add(path)
        if (added) saveFavorites(favorites)
        return added
    }

    fun removeFavorite(path: String): Boolean {
        val favorites = getFavorites().toMutableSet()
        val removed = favorites.remove(path)
        if (removed) saveFavorites(favorites)
        return removed
    }

    fun isFavorite(path: String): Boolean = getFavorites().contains(path)

    fun getFavorites(): Set<String> {
        val favoritesString = prefs.getString(KEY_FAVORITES, "") ?: ""
        return if (favoritesString.isEmpty()) emptySet()
        else favoritesString.split(SEPARATOR).toSet()
    }

    fun getValidFavorites(): List<File> {
        val favorites = getFavorites()
        val validFavorites = favorites
            .map { File(it) }
            .filter { it.exists() && it.isDirectory }

        val validPaths = validFavorites.map { it.absolutePath }.toSet()
        if (validPaths.size != favorites.size) {
            saveFavorites(validPaths)
        }
        return validFavorites.sortedBy { it.name.lowercase() }
    }

    fun toggleFavorite(path: String): Boolean {
        return if (isFavorite(path)) {
            removeFavorite(path)
            false
        } else {
            addFavorite(path)
            true
        }
    }

    private fun saveFavorites(favorites: Set<String>) {
        prefs.edit()
            .putString(KEY_FAVORITES, favorites.joinToString(SEPARATOR))
            .apply()
    }
}
