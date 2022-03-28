package me.blog.korn123.easydiary.chart

import android.annotation.SuppressLint
import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import me.blog.korn123.commons.utils.FontUtils
import me.blog.korn123.easydiary.R

@SuppressLint("ViewConstructor")
/**
 * Custom implementation of the MarkerView.
 *
 * @author Philipp Jahoda
 */
class XYMarkerView(context: Context, private val xAxisValueFormatter: IAxisValueFormatter) : MarkerView(context, R.layout.partial_custom_marker_view) {
    private val tvContent: TextView = findViewById(R.id.tvContent)
    private val imageDiarySymbol: ImageView = findViewById(R.id.image_diary_symbol)

    // callbacks everytime the MarkerView is redrawn, can be used to update the
    // content (user-interface)
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let { entry ->
            tvContent.run {
                text = String.format("%s: %d", context.getString(R.string.write_count), entry.y.toInt())
                typeface = FontUtils.getCommonTypeface(context)
            }
            entry.icon?.let {
                imageDiarySymbol.visibility = VISIBLE
                imageDiarySymbol.setImageDrawable(it)
            }
            super.refreshContent(entry, highlight)
        }
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }
}
