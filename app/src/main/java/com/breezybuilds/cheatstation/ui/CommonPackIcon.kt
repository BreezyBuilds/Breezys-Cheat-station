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

/** Shared app-action artwork for the same three icon packs as the console illustrations. */
class CommonPackIcon(context: Context, private val action: String, private val previewPack: String? = null) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val selectedPack = previewPack ?: Ui.Appearance.iconPack(context)
        val assetKey = action.lowercase().replace(" ", "")
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
        val pack = previewPack ?: Ui.Appearance.iconPack(context)
        val accent = when (action.lowercase()) {
            "cheats" -> 0xFFFFB341.toInt()
            "sources" -> 0xFF70B7F8.toInt()
            "backups" -> 0xFF63D8A1.toInt()
            else -> if (Ui.Appearance.theme(context) == "light") 0xFF5279B3.toInt() else 0xFFE1E9FF.toInt()
        }
        val light = ColorUtils.blendARGB(accent, Color.WHITE, 0.48f)
        val dark = ColorUtils.blendARGB(accent, Color.BLACK, 0.32f)
        canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(size / 100f, size / 100f)
        p.isAntiAlias = pack != "pixel"
        when (pack) {
            "outline" -> drawSymbol(canvas, accent, false, false, light, dark)
            "pixel" -> drawSymbol(canvas, accent, true, true, light, dark)
            else -> drawSymbol(canvas, accent, true, false, light, dark)
        }
        canvas.restore()
    }
    private fun box(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, fill: Boolean, pixel: Boolean, width: Float = 6f) {
        p.color = color
        p.style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
        p.strokeWidth = width
        c.drawRoundRect(RectF(l,t,r,b), if (pixel) 0f else 5f, if (pixel) 0f else 5f, p)
        p.style = Paint.Style.FILL
    }
    private fun line(c: Canvas, color: Int, width: Float, vararg xy: Float) {
        val path = Path().apply {
            moveTo(xy[0],xy[1]); var i=2
            while (i < xy.size) { lineTo(xy[i],xy[i+1]); i+=2 }
        }
        p.color=color; p.strokeWidth=width; p.strokeJoin=Paint.Join.ROUND
        p.strokeCap=Paint.Cap.SQUARE; p.style=Paint.Style.STROKE
        c.drawPath(path,p); p.style=Paint.Style.FILL
    }
    private fun circle(c: Canvas, x: Float, y: Float, radius: Float, color: Int, fill: Boolean, width: Float=5f) {
        p.color=color; p.style=if(fill) Paint.Style.FILL else Paint.Style.STROKE; p.strokeWidth=width
        c.drawCircle(x,y,radius,p); p.style=Paint.Style.FILL
    }
    private fun drawSymbol(c: Canvas, accent: Int, fill: Boolean, pixel: Boolean, light: Int, dark: Int) {
        val main = if (fill) accent else accent
        when(action.lowercase()) {
            "settings" -> {
                // Eight teeth around a central gear; square teeth in pixel mode.
                for (i in 0..7) {
                    c.save(); c.rotate(i*45f,50f,50f)
                    box(c,43f,10f,57f,28f, if(fill && i%2==0) light else main,fill,pixel,5f)
                    c.restore()
                }
                circle(c,50f,50f,30f,main,fill,5f)
                circle(c,50f,50f,13f,if(fill) Ui.Appearance.background(context) else main,!fill,5f)
            }
            "cheats" -> {
                val path=Path().apply { moveTo(55f,7f); lineTo(22f,55f); lineTo(46f,55f); lineTo(38f,93f); lineTo(81f,40f); lineTo(56f,40f); close() }
                p.color=main; p.style=if(fill) Paint.Style.FILL else Paint.Style.STROKE; p.strokeWidth=5f
                c.drawPath(path,p); p.style=Paint.Style.FILL
                if(fill && !pixel) line(c,light,5f,55f,17f,35f,48f)
            }
            "sources" -> {
                for (i in 0..2) {
                    val y=16f+i*26f
                    box(c,16f,y,84f,y+22f,if(fill && i==0) light else main,fill,pixel,4f)
                    if(fill) box(c,22f,y+6f,78f,y+11f,dark,true,pixel)
                }
            }
            else -> {
                box(c,13f,27f,87f,85f,main,fill,pixel,5f)
                box(c,20f,17f,80f,31f,if(fill) light else main,fill,pixel,5f)
                // Restore-arrow motif inside the backup archive.
                line(c,if(fill) dark else main,6f,70f,48f,60f,39f,48f,39f,37f,45f,32f,55f,37f,67f,50f,71f,62f,66f)
                line(c,if(fill) dark else main,6f,61f,48f,70f,48f,70f,38f)
            }
        }
    }
}
