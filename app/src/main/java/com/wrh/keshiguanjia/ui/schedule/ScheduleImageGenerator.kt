package com.wrh.keshiguanjia.ui.schedule

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import com.wrh.keshiguanjia.logic.DayLesson
import com.wrh.keshiguanjia.logic.TimeUtils
import java.time.LocalDate

/** 班级专属色：按 classId 稳定取色，让不同班级在所有课表里颜色一致、易于区分。 */
private val CLASS_PALETTE = listOf(
    0xFF0E7490, 0xFF2563EB, 0xFF7C3AED, 0xFFDB2777,
    0xFFEA580C, 0xFF16A34A, 0xFFB45309, 0xFF0F766E,
)

fun classColor(classId: Long): Color = Color(CLASS_PALETTE[(classId % CLASS_PALETTE.size).toInt()])

/**
 * 程序化生成完整周课表图片（不依赖 UI 截图，高度随内容自适应，保证整周全部包含）。
 */
object WeeklyScheduleImage {

    private const val W = 1080f
    private const val PAD = 44f
    private const val BG = 0xFFFCFAFF.toInt()
    private const val BRAND = 0xFF0E7490.toInt()
    private const val HEADER = 0xFF5F5F6B.toInt()
    private const val TIME = 0xFF64646D.toInt()
    private const val EXTRA = 0xFF8A8A93.toInt()
    private const val CANCELLED = 0xFF9C9CA5.toInt()
    private const val BLOCK = 0xFFF1F0F5.toInt()
    private const val BLOCK_TODAY = 0xFFECE4F8.toInt()

    private fun textPaint(sizePx: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = sizePx
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    fun render(
        weekDates: List<LocalDate>,
        lessonsByDate: Map<LocalDate, List<DayLesson>>,
        today: LocalDate = LocalDate.now(),
        title: String = "本周课程表",
    ): Bitmap {
        val blockPadV = 30f
        val blockPadH = 34f
        val dayHeaderH = 58f

        // 预计算每天内容行（时间/班名/附加信息三行一组）
        data class LessonLines(
            val color: Int,
            val time: String,
            val name: String,
            val extras: String,
            val cancelled: Boolean,
        )

        data class Block(val date: LocalDate, val isToday: Boolean, val lines: List<LessonLines>, val height: Float)

        val blocks = weekDates.map { date ->
            val lessons = lessonsByDate[date].orEmpty()
            val rows = lessons.map { l ->
                LessonLines(
                    color = if (l.isCancelled) CANCELLED else classColor(l.classId).toInt(),
                    time = TimeUtils.minutesToText(l.startMinute) + " - " + TimeUtils.minutesToText(l.endMinute) +
                        (if (l.isCancelled) " · 已停课" else "") +
                        (if (l.isExtra) " · 加课" else ""),
                    name = l.className,
                    extras = listOf(l.subject, l.room).filter { it.isNotBlank() }.joinToString(" · "),
                    cancelled = l.isCancelled,
                )
            }
            val contentH = if (rows.isEmpty()) 52f else rows.sumOf { r ->
                (48 + 54 + (if (r.extras.isNotBlank()) 38 else 0) + 18).toDouble()
            }.toFloat()
            Block(
                date = date,
                isToday = date == today,
                lines = rows,
                height = blockPadV * 2 + dayHeaderH + contentH + 18f,
            )
        }

        val headerH = 216f
        val totalH = (headerH + blocks.sumOf { it.height.toDouble() }).toInt() + PAD.toInt()
        val bmp = Bitmap.createBitmap(W.toInt(), totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(BG)

        canvas.drawText(title, PAD, 108f, textPaint(62f, BRAND, bold = true))
        val range = if (weekDates.isNotEmpty()) {
            val a = weekDates.first()
            val b = weekDates.last()
            "${a.monthValue}/${a.dayOfMonth} - ${b.monthValue}/${b.dayOfMonth}"
        } else {
            ""
        }
        canvas.drawText(range, PAD, 172f, textPaint(34f, EXTRA))

        val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        var y = headerH
        for (block in blocks) {
            // 日块背景
            blockPaint.color = if (block.isToday) BLOCK_TODAY else BLOCK
            canvas.drawRoundRect(RectF(PAD, y, W - PAD, y + block.height), 30f, 30f, blockPaint)

            // 日标题
            val dayText = TimeUtils.dayLabel(block.date.dayOfWeek.value) +
                " ${block.date.monthValue}/${block.date.dayOfMonth}" +
                (if (block.isToday) " · 今天" else "")
            canvas.drawText(
                dayText, PAD + blockPadH, y + blockPadV + 42f,
                textPaint(40f, if (block.isToday) BRAND else HEADER, bold = true),
            )
            var ly = y + blockPadV + dayHeaderH

            if (block.lines.isEmpty()) {
                canvas.drawText("无课", PAD + blockPadH, ly + 34f, textPaint(34f, CANCELLED))
            }
            for (lesson in block.lines) {
                // 班级色条
                barPaint.color = lesson.color
                canvas.drawRoundRect(
                    RectF(PAD + blockPadH, ly + 6f, PAD + blockPadH + 10f, ly + 6f + 96f), 5f, 5f, barPaint,
                )
                val textX = PAD + blockPadH + 38f
                canvas.drawText(lesson.time, textX, ly + 38f, textPaint(34f, lesson.color))
                canvas.drawText(lesson.name, textX, ly + 38f + 54f, textPaint(42f, lesson.color, bold = true))
                ly += 92f
                if (lesson.extras.isNotBlank()) {
                    canvas.drawText(lesson.extras, textX, ly + 4f + 34f, textPaint(30f, EXTRA))
                    ly += 38f
                }
                ly += 18f
            }
            y += block.height
        }
        return bmp
    }

    private fun Color.toInt(): Int = android.graphics.Color.argb(
        (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
    )
}
