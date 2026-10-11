package com.breezybuilds.cheatstation.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import androidx.core.graphics.ColorUtils

/** Three independently drawn, resolution-independent console illustration styles.
 * Precision = fine line art; Soft = layered hardware silhouettes; Pixel = authored 16x16 tiles.
 * All four supported systems remain recognizable in every palette and every pack.
 */
class ConsolePackIcon(
    context: Context,
    private val console: String,
    private val previewPack: String? = null
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        contentDescription = "$console console illustration"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val selectedPack = previewPack ?: Ui.Appearance.iconPack(context)
        val assetKey = console.lowercase().replace(" ", "")
        val assetName = "retro_${selectedPack}_${assetKey}"
        val resourceId = resources.getIdentifier(assetName, "drawable", context.packageName)
        if (resourceId != 0) {
            val bitmap = BitmapFactory.decodeResource(resources, resourceId)
            if (bitmap != null) {
                val side = minOf(width, height).toFloat()
                val dst = RectF((width-side)/2f, (height-side)/2f, (width+side)/2f, (height+side)/2f)
                val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                imagePaint.isFilterBitmap = selectedPack != "pixel"
                canvas.drawBitmap(bitmap, null, dst, imagePaint)
                bitmap.recycle()
                return
            }
        }
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(size / 100f, size / 100f)
        val pack = previewPack ?: Ui.Appearance.iconPack(context)
        paint.isAntiAlias = pack != "pixel"
        paint.isFilterBitmap = pack != "pixel"
        val accent = if (Ui.Appearance.systemMode(context) == "theme")
            Ui.Appearance.action(context) else Ui.Appearance.accent(context, console)
        val ink = Ui.Appearance.foreground(context)
        val surface = Ui.Appearance.surface(context)
        val edge = Ui.Appearance.divider(context)
        val shade = ColorUtils.blendARGB(accent, surface, 0.56f)
        when (pack) {
            "outline" -> drawOutline(canvas, accent, ink, surface, edge)
            "pixel" -> drawPixel(canvas, accent, ink, surface, shade)
            else -> drawDuotone(canvas, accent, ink, surface, shade)
        }
        canvas.restore()
    }

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 0f, stroke: Float = 0f) {
        paint.color = color
        paint.style = if (stroke > 0f) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = stroke
        paint.strokeJoin = Paint.Join.ROUND
        c.drawRoundRect(RectF(l, t, r, b), radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun segment(c: Canvas, x: Float, y: Float, toX: Float, toY: Float, color: Int, thickness: Float = 2.5f) {
        paint.color = color
        paint.strokeWidth = thickness
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        c.drawLine(x, y, toX, toY, paint)
        paint.style = Paint.Style.FILL
    }

    private fun dot(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.color = color; paint.style = Paint.Style.FILL
        c.drawCircle(x, y, radius, paint)
    }

    private fun poly(c: Canvas, color: Int, stroke: Float = 0f, vararg points: Float) {
        val path = Path().apply {
            moveTo(points[0], points[1])
            var i = 2
            while (i < points.size) { lineTo(points[i], points[i + 1]); i += 2 }
            close()
        }
        paint.color = color
        paint.style = if (stroke > 0f) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = stroke
        paint.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawOutline(c: Canvas, accent: Int, ink: Int, surface: Int, edge: Int) {
        // Transparent background: illustrations sit directly on themed surfaces.
        when (console.uppercase()) {
            "3DS" -> {
                rect(c, 16f, 12f, 84f, 49f, ink, 6f, 2.6f)
                rect(c, 22f, 18f, 78f, 43f, accent, 2f, 2f)
                rect(c, 18f, 53f, 82f, 88f, ink, 6f, 2.6f)
                rect(c, 33f, 60f, 65f, 80f, accent, 2f, 2f)
                segment(c, 25f, 69f, 35f, 69f, ink, 2.5f)
                segment(c, 30f, 64f, 30f, 74f, ink, 2.5f)
                dot(c, 71f, 63f, 2f, ink); dot(c, 76f, 69f, 2f, ink)
                dot(c, 17f, 51f, 1.8f, accent); dot(c, 83f, 51f, 1.8f, accent)
            }
            "PS2" -> {
                poly(c, ink, 2.6f, 25f, 18f, 62f, 12f, 74f, 25f, 74f, 84f, 25f, 89f)
                segment(c, 61f, 14f, 61f, 86f, accent, 2f)
                segment(c, 33f, 30f, 54f, 27f, ink, 2f)
                segment(c, 33f, 40f, 54f, 37f, ink, 2f)
                rect(c, 31f, 77f, 57f, 80f, accent, 1f)
                dot(c, 66f, 79f, 2f, accent)
            }
            "WII" -> {
                poly(c, ink, 2.6f, 24f, 17f, 56f, 13f, 62f, 83f, 29f, 87f)
                segment(c, 30f, 26f, 53f, 23f, accent, 2f)
                segment(c, 34f, 33f, 50f, 31f, ink, 1.8f)
                dot(c, 51f, 76f, 2.1f, accent)
                rect(c, 69f, 25f, 82f, 78f, ink, 5f, 2.6f)
                dot(c, 75f, 35f, 3f, accent)
                segment(c, 72f, 53f, 79f, 53f, ink, 2f)
            }
            else -> {
                poly(c, ink, 2.6f, 19f, 31f, 53f, 19f, 83f, 32f, 82f, 77f, 45f, 88f, 19f, 76f)
                poly(c, accent, 2f, 19f, 31f, 45f, 44f, 83f, 32f)
                segment(c, 45f, 44f, 45f, 88f, accent, 2.4f)
                rect(c, 25f, 52f, 39f, 68f, ink, 2f, 2f)
                dot(c, 56f, 62f, 3f, accent); dot(c, 70f, 57f, 3f, accent)
                segment(c, 34f, 21f, 34f, 16f, ink, 3f)
                segment(c, 34f, 16f, 64f, 16f, ink, 3f)
            }
        }
    }

    private fun drawDuotone(c: Canvas, accent: Int, ink: Int, surface: Int, shade: Int) {
        val pale = ColorUtils.blendARGB(accent, Color.WHITE, if (Ui.Appearance.theme(context) == "light") 0.24f else 0.46f)
        // No enclosing sticker or white tile.
        // Silhouettes have layers, hardware highlights and distinct control shapes.
        when (console.uppercase()) {
            "3DS" -> {
                rect(c, 13f, 12f, 87f, 49f, shade, 7f)
                rect(c, 18f, 17f, 82f, 44f, accent, 4f)
                rect(c, 24f, 20f, 76f, 40f, surface, 2f)
                rect(c, 16f, 50f, 84f, 90f, pale, 7f)
                rect(c, 32f, 56f, 68f, 83f, surface, 4f)
                rect(c, 22f, 64f, 37f, 68f, shade, 1f)
                rect(c, 27f, 59f, 32f, 73f, shade, 1f)
                dot(c, 72f, 62f, 3.5f, shade); dot(c, 77f, 69f, 3.5f, accent)
            }
            "PS2" -> {
                poly(c, shade, 0f, 23f, 18f, 60f, 12f, 79f, 23f, 79f, 85f, 34f, 91f, 23f, 84f)
                poly(c, accent, 0f, 60f, 12f, 79f, 23f, 79f, 85f, 63f, 80f)
                segment(c, 31f, 30f, 56f, 27f, pale, 2.8f)
                segment(c, 31f, 39f, 56f, 36f, pale, 2.8f)
                rect(c, 32f, 76f, 55f, 81f, surface, 1f)
                dot(c, 71f, 78f, 2.5f, pale)
            }
            "WII" -> {
                poly(c, shade, 0f, 19f, 15f, 54f, 12f, 62f, 86f, 29f, 90f)
                poly(c, pale, 0f, 19f, 15f, 54f, 12f, 58f, 21f, 25f, 25f)
                segment(c, 28f, 29f, 51f, 26f, accent, 2.8f)
                dot(c, 52f, 78f, 3f, accent)
                rect(c, 68f, 21f, 85f, 81f, pale, 7f)
                rect(c, 73f, 29f, 80f, 37f, accent, 3f)
                dot(c, 76.5f, 49f, 3f, shade)
                rect(c, 73f, 60f, 80f, 64f, shade, 1f)
            }
            else -> {
                poly(c, shade, 0f, 17f, 31f, 53f, 17f, 85f, 30f, 83f, 79f, 45f, 91f, 17f, 77f)
                poly(c, pale, 0f, 17f, 31f, 45f, 44f, 45f, 91f, 17f, 77f)
                poly(c, accent, 0f, 45f, 44f, 85f, 30f, 83f, 79f, 45f, 91f)
                rect(c, 23f, 51f, 37f, 69f, surface, 3f)
                dot(c, 57f, 59f, 4f, pale); dot(c, 72f, 56f, 4f, pale)
                segment(c, 31f, 22f, 31f, 17f, pale, 3f)
                segment(c, 31f, 17f, 66f, 17f, pale, 3f)
            }
        }
    }

    private fun drawPixel(c: Canvas, accent: Int, ink: Int, surface: Int, shade: Int) {
        // Hand-authored 16x16 pixel tiles. No strokes, curves, smoothing or scaled bitmap.
        val rows = when (console.uppercase()) {
            "3DS" -> arrayOf(
                "................", "..111111111111..", "..122222222221..", "..123333333321..",
                "..123333333321..", "..122222222221..", "..111111111111..", "...4444444444...",
                "..111111111111..", "..122233332221..", "..124233332421..", "..144233332441..",
                "..122233332221..", "..122222222221..", "..111111111111..", "................"
            )
            "PS2" -> arrayOf(
                "................", "....111111......", "....1222211.....", "....12333211....",
                "....12333221....", "....12444221....", "....12333221....", "....12444221....",
                "....12333221....", "....12444221....", "....12333221....", "....12444221....",
                "....12222221....", "....11111111....", ".....444444.....", "................"
            )
            "WII" -> arrayOf(
                "................", "..111111....11..", "..122221....121.", "..123321....121.",
                "..123321....131.", "..123321....121.", "..123321....141.", "..123321....121.",
                "..123321....131.", "..123321....121.", "..123321....141.", "..123321....121.",
                "..123321....121.", "..122221....121.", "..111111....11..", "................"
            )
            else -> arrayOf(
                "................", ".....111111.....", "...1112222111...", "..112222222211..",
                "..123333333321..", "..123333333321..", "..123333333321..", "..123344443321..",
                "..123344443321..", "..123333333321..", "..123333333321..", "..123333333321..",
                "..122222222221..", "...1112222111...", ".....111111.....", "................"
            )
        }
        // Pixel artwork remains transparent around its silhouette.
        val colors = intArrayOf(0, ink, accent, surface, shade)
        rows.forEachIndexed { y, line ->
            line.forEachIndexed { x, ch ->
                val index = ch - '0'
                if (index in 1..4) rect(c, 10f + x * 5f, 10f + y * 5f, 15f + x * 5f, 15f + y * 5f, colors[index])
            }
        }
    }
}
