package com.westly.oneuilauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File

/**
 * Remembers which wallpaper is chosen. A choice is either one of the built-in presets
 * (0, 1, 2, ...) or [CUSTOM], a photo copied into the app's own storage.
 */
object WallpaperStore {

    const val CUSTOM = -1

    private const val PREFS = "wallpaper"
    private const val KEY_CHOICE = "choice"
    private const val FILE_NAME = "wallpaper.jpg"
    private const val MAX_SIDE = 2200

    fun load(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val choice = prefs.getInt(KEY_CHOICE, 0)
        // A custom choice without its file falls back to the default wallpaper.
        return if (choice == CUSTOM && !customFile(context).exists()) 0 else choice
    }

    fun save(context: Context, choice: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_CHOICE, choice)
            .apply()
    }

    fun customFile(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Copies a picked photo into the app (shrunk and rotated). Blocking: run off the main thread. */
    fun importFromUri(context: Context, uri: Uri): Boolean {
        return try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return false

            val orientation = try {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val finalBitmap = if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } else {
                decoded
            }

            customFile(context).outputStream().use {
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
