package com.vx7.marketing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** A simple report model rendered identically to PDF (A4, paged) and PNG (one tall image). */
class Table(val headers: List<String>, val weights: List<Float>, val rows: List<List<String>>, val rightAlign: Set<Int> = emptySet())
class Section(val heading: String, val lines: List<String> = emptyList(), val table: Table? = null)
class ReportDoc(
    val title: String, val subtitle: String, val sections: List<Section>,
    /** true = contains private (Pvt) rates; a banner is drawn on every page. */
    val isPrivate: Boolean = false
)

object Exporter {
    private val NAVY = 0xFF0B1228.toInt()
    private val NAVY2 = 0xFF14204A.toInt()
    private val BLUE = 0xFF2677E8.toInt()
    private val GOLD = 0xFFD5B05D.toInt()
    private val INK = 0xFF121A33.toInt()
    private val ZEBRA = 0xFFEAF0FB.toInt()
    private val RED = 0xFFB3261E.toInt()

    private fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun lay(text: String, p: TextPaint, w: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, w.toInt().coerceAtLeast(1)).setAlignment(align).setIncludePad(false).build()

    private fun logo(ctx: Context, size: Int): Bitmap =
        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(ctx.resources, R.drawable.vx7_logo), size, size, true)

    private fun outFile(ctx: Context, name: String): File {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 24L * 3600 * 1000
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        return File(dir, name)
    }

    fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "report" }

    private fun colX(t: Table, left: Float, width: Float, gap: Float): FloatArray {
        val total = t.weights.sum()
        val usable = width - gap * (t.weights.size - 1)
        val xs = FloatArray(t.weights.size + 1)
        var x = left
        for (i in t.weights.indices) { xs[i] = x; x += usable * t.weights[i] / total + gap }
        xs[t.weights.size] = left + width + gap
        return xs
    }

    // ---------------------------------------------------------------- PDF (A4, multi-page)
    fun pdf(ctx: Context, d: ReportDoc, baseName: String): File {
        val W = 595; val H = 842; val M = 40f; val CW = W - 2 * M
        val limit = H - 56f
        val doc = PdfDocument()
        val title = paint(20f, NAVY, true); val tag = paint(9f, GOLD, true); val head = paint(14f, BLUE, true)
        val sub = paint(11f, 0xFF4B566E.toInt()); val body = paint(10f, INK); val bodyB = paint(10f, INK, true)
        val small = paint(8.5f, 0xFF6B7280.toInt()); val white = paint(9.5f, Color.WHITE, true); val warn = paint(9.5f, Color.WHITE, true)
        val fill = Paint(); val logoBmp = logo(ctx, 128)
        var pageNo = 0; var y = 0f
        lateinit var page: PdfDocument.Page
        lateinit var cv: Canvas

        fun footer() {
            cv.drawText("VX7 Marketing  \u2022  ${d.title}  \u2022  Page $pageNo", M, H - 26f, small)
        }
        fun newPage() {
            if (pageNo > 0) { footer(); doc.finishPage(page) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            cv = page.canvas
            y = M
            if (d.isPrivate) {
                fill.color = RED; cv.drawRect(0f, 0f, W.toFloat(), 22f, fill)
                cv.drawText("PRIVATE \u2014 contains Pvt Rates. Do not share.", M, 15f, warn)
                y = M + 6f
            }
        }
        fun para(text: String, p: TextPaint, gap: Float = 3f) {
            val l = lay(text, p, CW)
            for (i in 0 until l.lineCount) {
                val h = (l.getLineBottom(i) - l.getLineTop(i)).toFloat()
                if (y + h > limit) newPage()
                cv.drawText(text.substring(l.getLineStart(i), l.getLineEnd(i)).trimEnd('\n'), M, y + (l.getLineBaseline(i) - l.getLineTop(i)), p)
                y += h
            }
            y += gap
        }
        fun tableHead(t: Table, xs: FloatArray) {
            fill.color = NAVY; cv.drawRect(M, y, M + CW, y + 20f, fill)
            t.headers.forEachIndexed { i, h ->
                val w = xs[i + 1] - xs[i] - 8f
                val l = lay(h, white, w, if (i in t.rightAlign) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
                cv.save(); cv.translate(xs[i] + 4f, y + 4f); l.draw(cv); cv.restore()
            }
            y += 20f
        }

        newPage()
        cv.drawBitmap(logoBmp, null, RectF(M, y, M + 56f, y + 56f), null)
        cv.drawText("VX7 MARKETING", M + 68f, y + 26f, title)
        cv.drawText("PRODUCT \u2022 RATE \u2022 QUANTITY MANAGEMENT", M + 68f, y + 44f, tag)
        y += 66f
        fill.color = GOLD; cv.drawRect(M, y, M + CW, y + 2f, fill); y += 16f
        para(d.title.uppercase(), head, 2f)
        if (d.subtitle.isNotBlank()) para(d.subtitle, sub, 8f)

        for (s in d.sections) {
            if (y + 40f > limit) newPage()
            para(s.heading, bodyB.also { it.textSize = 12f }, 4f)
            bodyB.textSize = 10f
            s.lines.forEach { para(it, body, 2f) }
            val t = s.table
            if (t != null) {
                if (t.rows.isEmpty()) para("No data.", small, 6f) else {
                    val xs = colX(t, M, CW, 0f)
                    if (y + 60f > limit) newPage()
                    tableHead(t, xs)
                    t.rows.forEachIndexed { i, row ->
                        val ls = row.mapIndexed { c, cell ->
                            lay(cell, body, xs[c + 1] - xs[c] - 8f, if (c in t.rightAlign) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
                        }
                        val rowH = (ls.maxOfOrNull { it.height } ?: 12) + 8f
                        if (y + rowH > limit) { newPage(); tableHead(t, xs) }
                        if (i % 2 == 0) { fill.color = ZEBRA; cv.drawRect(M, y, M + CW, y + rowH, fill) }
                        ls.forEachIndexed { c, l -> cv.save(); cv.translate(xs[c] + 4f, y + 4f); l.draw(cv); cv.restore() }
                        y += rowH
                    }
                    fill.color = 0xFFC7D2E8.toInt(); cv.drawRect(M, y, M + CW, y + 1f, fill)
                    y += 12f
                }
            }
            y += 6f
        }
        footer(); doc.finishPage(page)
        val f = outFile(ctx, safeName(baseName) + ".pdf")
        try { FileOutputStream(f).use { doc.writeTo(it) } } finally { doc.close() }
        return f
    }

    // ---------------------------------------------------------------- PNG (one image, height computed from content)
    fun png(ctx: Context, d: ReportDoc, baseName: String): File {
        val W = 1080; val pad = 48f; val inner = W - 2 * pad
        val brand = paint(52f, Color.WHITE, true); val tag = paint(24f, GOLD, true); val head = paint(40f, GOLD, true)
        val sub = paint(28f, 0xFFB8C2DD.toInt()); val sec = paint(34f, GOLD, true); val line = paint(28f, Color.WHITE)
        val cell = paint(27f, INK); val th = paint(25f, Color.WHITE, true); val small = paint(22f, 0xFFB8C2DD.toInt())
        val warn = paint(26f, Color.WHITE, true)
        val logoBmp = logo(ctx, 140); val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        fun run(cv: Canvas?): Float {
            fun draw(l: StaticLayout, x: Float, yy: Float) { cv?.let { it.save(); it.translate(x, yy); l.draw(it); it.restore() } }
            cv?.drawColor(NAVY)
            var y = 0f
            if (d.isPrivate) {
                fill.color = RED; cv?.drawRect(0f, 0f, W.toFloat(), 52f, fill)
                cv?.drawText("PRIVATE \u2014 contains Pvt Rates. Do not share.", pad, 36f, warn)
                y = 52f
            }
            y += pad
            cv?.drawBitmap(logoBmp, null, RectF(pad, y, pad + 140f, y + 140f), null)
            cv?.drawText("VX7 MARKETING", pad + 168f, y + 66f, brand)
            cv?.drawText("PRODUCT \u2022 RATE \u2022 QUANTITY", pad + 168f, y + 112f, tag)
            y += 140f + 28f
            fill.color = GOLD; cv?.drawRect(pad, y, W - pad, y + 4f, fill); y += 30f
            val h = lay(d.title.uppercase(), head, inner); draw(h, pad, y); y += h.height + 8f
            if (d.subtitle.isNotBlank()) { val s = lay(d.subtitle, sub, inner); draw(s, pad, y); y += s.height + 6f }
            y += 20f
            for (s in d.sections) {
                val sh = lay(s.heading, sec, inner); draw(sh, pad, y); y += sh.height + 10f
                for (ln in s.lines) { val l = lay(ln, line, inner); draw(l, pad, y); y += l.height + 6f }
                val t = s.table
                if (t != null) {
                    if (t.rows.isEmpty()) { val l = lay("No data.", small, inner); draw(l, pad, y); y += l.height + 10f } else {
                        val xs = colX(t, pad, inner, 0f)
                        val laid = t.rows.map { row -> row.mapIndexed { c, txt ->
                            lay(txt, cell, xs[c + 1] - xs[c] - 24f, if (c in t.rightAlign) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL) } }
                        val heads = t.headers.mapIndexed { c, txt ->
                            lay(txt, th, xs[c + 1] - xs[c] - 24f, if (c in t.rightAlign) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL) }
                        val headH = (heads.maxOfOrNull { it.height } ?: 28) + 24f
                        val rowHs = laid.map { r -> (r.maxOfOrNull { it.height } ?: 28) + 24f }
                        val total = headH + rowHs.sum()
                        fill.color = Color.WHITE; cv?.drawRoundRect(RectF(pad, y, W - pad, y + total), 20f, 20f, fill)
                        fill.color = BLUE; cv?.drawRoundRect(RectF(pad, y, W - pad, y + headH + 14f), 20f, 20f, fill)
                        fill.color = if (rowHs.isNotEmpty()) Color.WHITE else BLUE; cv?.drawRect(pad, y + headH, W - pad, y + headH + 14f, fill)
                        heads.forEachIndexed { c, l -> draw(l, xs[c] + 12f, y + 12f) }
                        var ry = y + headH
                        laid.forEachIndexed { i, r ->
                            if (i % 2 == 1) { fill.color = ZEBRA; cv?.drawRect(pad, ry, W - pad, ry + rowHs[i], fill) }
                            r.forEachIndexed { c, l -> draw(l, xs[c] + 12f, ry + 12f) }
                            ry += rowHs[i]
                        }
                        y += total + 24f
                    }
                }
                y += 14f
            }
            val ft = lay("Created with VX7 Marketing", small, inner, Layout.Alignment.ALIGN_CENTER)
            draw(ft, pad, y); y += ft.height + pad
            return y
        }

        val height = run(null).toInt()
        val bmp = try {
            Bitmap.createBitmap(W, height, if (height > 16000) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            throw IllegalStateException("This report is too large to render as one image. Export it as a PDF instead.")
        }
        try {
            run(Canvas(bmp))
            val f = outFile(ctx, safeName(baseName) + ".png")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return f
        } finally { bmp.recycle() }
    }

    // ---------------------------------------------------------------- share / open
    fun uriFor(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

    fun share(ctx: Context, f: File, mime: String, title: String) {
        val uri = uriFor(ctx, f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(f.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, title))
    }

    /** Returns false when no installed app can open the file. */
    fun open(ctx: Context, f: File, mime: String): Boolean {
        val uri = uriFor(ctx, f)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            clipData = ClipData.newRawUri(f.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { ctx.startActivity(view); true } catch (e: Exception) { false }
    }

    fun copyTo(ctx: Context, f: File, dest: Uri) {
        ctx.contentResolver.openOutputStream(dest, "wt")?.use { out -> f.inputStream().use { it.copyTo(out) } }
            ?: throw IllegalStateException("Could not open the chosen location.")
    }
}
