package me.blog.korn123.easydiary.fragments

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.*
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.formatter.IValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.utils.ViewPortHandler
import kotlinx.coroutines.*
import me.blog.korn123.commons.utils.DateUtils
import me.blog.korn123.commons.utils.EasyDiaryUtils
import me.blog.korn123.commons.utils.FlavorUtils
import me.blog.korn123.commons.utils.FontUtils
import me.blog.korn123.easydiary.R
import me.blog.korn123.easydiary.activities.StatisticsActivity
import me.blog.korn123.easydiary.databinding.FragmentWeightLineChartBinding
import me.blog.korn123.easydiary.extensions.config
import me.blog.korn123.easydiary.extensions.darkenColor
import me.blog.korn123.easydiary.extensions.updateDrawableColorInnerCardView
import me.blog.korn123.easydiary.helper.AAF_TEST
import me.blog.korn123.easydiary.helper.DAILY_SCALE
import me.blog.korn123.easydiary.helper.EasyDiaryDbHelper
import me.blog.korn123.easydiary.helper.TransitionHelper
import me.blog.korn123.easydiary.models.Diary
import java.text.SimpleDateFormat
import kotlin.random.Random

class WeightLineChartFragment : androidx.fragment.app.Fragment() {
    private lateinit var mBinding: FragmentWeightLineChartBinding
    private lateinit var mLineChart: LineChart
    private val mTimeMillisMap = hashMapOf<Int, Long>()
    private var mCoroutineJob: Job? = null
    private var mChartMode = "A"
    private val mDataSets = ArrayList<ILineDataSet>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mBinding = FragmentWeightLineChartBinding.inflate(layoutInflater)
        return mBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // FIXME: When ViewBinding is used, the MATCH_PARENT option declared in the layout does not work, so it is temporarily declared here.
        mBinding.root.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        mBinding.root.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT

        mLineChart = mBinding.lineChart
        mLineChart.description.isEnabled = false

        // if more than 60 entries are displayed in the chart, no values will be
        // drawn
        mLineChart.setMaxVisibleValueCount(60)

        // scaling can now only be done on x- and y-axis separately
        mLineChart.setPinchZoom(false)

//        barChart.setDrawGridBackground(true)
        // mChart.setDrawYLabels(false);
//        barChart.zoom(1.5F, 0F, 0F, 0F)

        val xAxisFormatter = WeightXAxisValueFormatter(context)
        mLineChart.xAxis.run {
            position = XAxis.XAxisPosition.BOTTOM
            typeface = FontUtils.getCommonTypeface(requireContext())
            textColor = requireContext().config.textColor
            labelRotationAngle = -45F
            setDrawGridLines(false)
            granularity = 1f // only intervals of 1 day
            labelCount = 7
            valueFormatter = xAxisFormatter
        }

        val yAxisFormatter = WeightYAxisValueFormatter(context)
        mLineChart.axisLeft.run {
            typeface = FontUtils.getCommonTypeface(requireContext())
            textColor = requireContext().config.textColor
            setLabelCount(8, false)
            valueFormatter = yAxisFormatter
            setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
            spaceTop = 0f
            axisMinimum = 0f // this replaces setStartAtZero(true)
            labelCount = 8
        }

        mLineChart.axisRight.run {
            setDrawGridLines(false)
            typeface = FontUtils.getCommonTypeface(requireContext())
            textColor = requireContext().config.textColor
            setLabelCount(8, false)
            valueFormatter = yAxisFormatter
            spaceTop = 0f
            axisMinimum = 0f // this replaces setStartAtZero(true)
            labelCount = 8
        }

        mLineChart.legend.run {
            typeface = FontUtils.getCommonTypeface(requireContext())
            textColor = requireContext().config.textColor
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
            form = Legend.LegendForm.SQUARE
            formSize = 9f
            textSize = 11f
            xEntrySpace = 4f
        }

        val mv = WeightMarkerView(requireContext(), xAxisFormatter)
        mv.chartView = mLineChart // For bounds control
        mLineChart.marker = mv // Set the marker to the chart

        // determine title parameter
        arguments?.let { bundle ->
            val title = bundle.getString(CHART_TITLE)
            if (title != null) {
                mBinding.chartTitle.text = title
                mBinding.chartTitle.visibility = View.VISIBLE
                getView()?.findViewById<ImageView>(R.id.image_weight_symbol)?.let {
                    it.visibility = View.VISIBLE
                    FlavorUtils.initWeatherView(requireActivity(), it, DAILY_SCALE)
                }
                getView()?.findViewById<ImageView>(R.id.image_expend_chart)?.let {
                    it.visibility = View.VISIBLE
                    requireActivity().updateDrawableColorInnerCardView(it, config.textColor)
                    it.setOnClickListener { view ->
                        view.postDelayed( {
                            TransitionHelper.startActivityWithTransition(
                                requireActivity(),
                                Intent(
                                    requireActivity(),
                                    StatisticsActivity::class.java
                                ).putExtra(StatisticsActivity.CHART_MODE, StatisticsActivity.MODE_SINGLE_LINE_CHART_WEIGHT)
                            )
                        }, 300)
                    }
                }
            }
        }

        drawChart()

        mBinding.run {
            radioGroupChartOption.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.radio_button_option_a -> {
                        mChartMode = "A"
                        drawChart()
                    }
                    R.id.radio_button_option_b -> {
                        mChartMode = "B"
                        drawChart()
                    }
                }
            }
            checkOptionsFill.setTextColor(requireContext().config.textColor)
            checkOptionsFill.setOnCheckedChangeListener { _, isChecked ->
                mDataSets.forEach {
                    it.setDrawFilled(isChecked)
                }
//                mLineChart.notifyDataSetChanged()
                mLineChart.invalidate()
            }
        }
    }

    private fun drawChart() {
        mCoroutineJob = CoroutineScope(Dispatchers.IO).launch {
            mDataSets.clear()
            if (mChartMode == "A") {
                val barEntries = setData()
                if (barEntries.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val lineDataSet = LineDataSet(barEntries, "Weight")
                        val iValueFormatter = WeightIValueFormatter(context)
                        lineDataSet.valueFormatter = iValueFormatter
                        lineDataSet.setDrawIcons(false)
                        lineDataSet.setDrawValues(true)
                        lineDataSet.setDrawFilled(true)
                        mDataSets.add(lineDataSet)
                        val lineData = LineData(mDataSets)
                        lineData.setValueTextSize(10f)
                        lineData.setValueTypeface(FontUtils.getCommonTypeface(requireContext()))
//                        Color.rgb(
//                            Random.nextInt(0, 255),
//                            Random.nextInt(0, 255),
//                            Random.nextInt(0, 255)
//                        ).also {
//                            lineDataSet.circleColors = arrayListOf(it)
//                            lineDataSet.color = it
//                            lineDataSet.fillColor = it
//                        }
                        requireContext().config.primaryColor.also { color ->
                            lineDataSet.circleColors = arrayListOf(color)
                            lineDataSet.color = color
                            lineDataSet.fillColor = color
                        }
                        mLineChart.data = lineData
                        mLineChart.animateY(600)
                        mBinding.barChartProgressBar.visibility = View.GONE
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        mBinding.barChartProgressBar.visibility = View.GONE
                    }
                }
            } else {
                EasyDiaryDbHelper.getTemporaryInstance().let { realmInstance ->
                    val listDiary = EasyDiaryDbHelper.findDiary(
                        null,
                        false,
                        0,
                        0,
                        DAILY_SCALE,
                        realmInstance = realmInstance
                    )

                    var sumWeight = 0F
                    val filteredItems = arrayListOf<Diary>()
                    listDiary.reversed().forEach { diary ->
                        diary.title?.let {
                            if (EasyDiaryUtils.isContainNumber(it)) {
                                val weight = EasyDiaryUtils.findNumber(it)
                                sumWeight += weight
                                filteredItems.add(diary)
                            }
                        }
                    }

                    val yearlyMap = filteredItems.groupBy { item -> item.dateString!!.substring(0, 4) }
                    val iterator = yearlyMap.iterator()
//                    val color = requireContext().config.primaryColor
                    var itemIndex = yearlyMap.count()
                    while (iterator.hasNext()) {
                        val element = iterator.next()
                        val barEntries = ArrayList<Entry>()

                        val monthMap = element.value.groupBy { it.dateString!!.substring(5, 7) }
                        fun monthlyWeight(key: String): Float {
                            return monthMap[key]?.let { monthlyItems ->
                                var average = 0F
                                var sum = 0F
                                monthlyItems.map { sum += EasyDiaryUtils.findNumber(it.title) }
                                average = sum.div(monthlyItems.size)
                                average
                            } ?: 0F
                        }

                        val averageInfo = arrayListOf<Float>().apply {
                            for (i in 1..12) {
                                add(monthlyWeight("$i".padStart(2, '0')))
                            }
                        }
                        for (i in 1..12) {
                            if (averageInfo[i.minus(1)] > 0f) barEntries.add(Entry(i.toFloat(), averageInfo[i.minus(1)]))
                        }
                        val lineDataSet = LineDataSet(barEntries, element.key)
                        val iValueFormatter = WeightIValueFormatter(context)
                        lineDataSet.valueFormatter = iValueFormatter
                        lineDataSet.setDrawIcons(false)
                        lineDataSet.setDrawValues(true)
                        lineDataSet.setDrawFilled(true)
                        Color.argb(
                            50,
                            Random.nextInt(0, 255),
                            Random.nextInt(0, 255),
                            Random.nextInt(0, 255)
                        ).also {
                            var color = it
                            if (itemIndex == 1) {
                                color = requireContext().config.primaryColor
                                lineDataSet.setCircleColorHole(color)
                            }
                            lineDataSet.circleColors = arrayListOf(color)
                            lineDataSet.color = color
                            lineDataSet.fillColor = color
                        }
//                        val darkenColor = color.darkenColor(itemIndex.times(-5))
//                        lineDataSet.circleColors = arrayListOf(color)
//                        lineDataSet.color = darkenColor
//                        lineDataSet.fillColor = darkenColor
                        mDataSets.add(lineDataSet)
                        itemIndex--
                    }
                    withContext(Dispatchers.Main) {
                        if (sumWeight > 0) {
                            val average = sumWeight.div(filteredItems.size)
                            mLineChart.axisLeft.axisMinimum = average.minus(10)
                            mLineChart.axisLeft.axisMaximum = average.plus(10)
                            mLineChart.axisRight.axisMinimum = average.minus(10)
                            mLineChart.axisRight.axisMaximum = average.plus(10)
                        }

                        val lineData = LineData(mDataSets)
                        lineData.setValueTextSize(10f)
                        lineData.setValueTypeface(FontUtils.getCommonTypeface(requireContext()))
                        mLineChart.data = lineData
                        mLineChart.animateY(600)
                        mBinding.barChartProgressBar.visibility = View.GONE
                    }
                    realmInstance.close()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mCoroutineJob?.run { if (isActive) cancel() }
    }

    private fun setData(): ArrayList<Entry> {
        val barEntries = ArrayList<Entry>()
        EasyDiaryDbHelper.getTemporaryInstance().let { realmInstance ->
            val listDiary = EasyDiaryDbHelper.findDiary(null, false, 0, 0, DAILY_SCALE, realmInstance = realmInstance)
            var index = 0
            var sumWeight = 0F
            listDiary.reversed().forEach { diaryDto ->
                diaryDto.title?.let {
                    if (EasyDiaryUtils.isContainNumber(it)) {
                        val weight = EasyDiaryUtils.findNumber(it)
                        sumWeight += weight
                        barEntries.add(Entry(index.toFloat(), weight))
                        mTimeMillisMap[index] = diaryDto.currentTimeMillis
                        index++
                    }
                }
            }
            if (index > 0) {
                val average = sumWeight.div(index)
                mLineChart.axisLeft.axisMinimum = average.minus(10)
                mLineChart.axisLeft.axisMaximum = average.plus(10)
                mLineChart.axisRight.axisMinimum = average.minus(10)
                mLineChart.axisRight.axisMaximum = average.plus(10)
            }
            realmInstance.close()
        }
        return barEntries
    }

    private fun xAxisTimeMillisToDate(timeMillis: Long): String =
        if (timeMillis > 0) DateUtils.getDateStringFromTimeMillis(timeMillis, SimpleDateFormat.MEDIUM) else "N/A"

    private fun fillValueForward(averageInfo: ArrayList<Float>) {
        Log.i(AAF_TEST, "원본 ${averageInfo.joinToString(",")}")
        averageInfo.forEachIndexed { index, fl ->
            if (fl == 0f) {
                up@ for (seq in index..averageInfo.size.minus(1)) {
                    if (averageInfo[seq] > 0F) {
                        averageInfo[index] = averageInfo[seq]
                        break@up
                    }
                }
            }
        }
        Log.i(AAF_TEST, "앞 ${averageInfo.joinToString(",")}")
    }

    private fun fillValueBackward(averageInfo: ArrayList<Float>) {
        averageInfo.forEachIndexed { index, fl ->
            if (fl == 0f) {
                down@ for (seq in index.minus(1) downTo 0) {
                    if (averageInfo[seq] > 0F) {
                        averageInfo[index] = averageInfo[seq]
                        break@down
                    }
                }
            }
        }
        Log.i(AAF_TEST, "뒤 ${averageInfo.joinToString(",")}")
    }

    companion object {
        const val CHART_TITLE = "chartTitle"
    }

    inner class WeightXAxisValueFormatter(private var context: Context?) : IAxisValueFormatter {
        override fun getFormattedValue(value: Float, axis: AxisBase): String {
            val label = when (mChartMode == "A") {
                true -> {
                    val timeMillis: Long = mTimeMillisMap[value.toInt()] ?: 0
                    xAxisTimeMillisToDate(timeMillis)
                }
                false -> {
                    value.toInt().toString().padStart(2, '0')
                }

            }
            return label
        }
    }

    inner class WeightYAxisValueFormatter(private var context: Context?) : IAxisValueFormatter {
        override fun getFormattedValue(value: Float, axis: AxisBase): String {
            return "${value}kg"
        }
    }

    inner class WeightIValueFormatter(private var context: Context?) : IValueFormatter {

        /**
         * Called when a value (from labels inside the chart) is formatted
         * before being drawn. For performance reasons, avoid excessive calculations
         * and memory allocations inside this method.
         *
         * @param value           the value to be formatted
         * @param entry           the entry the value belongs to - in e.g. BarChart, this is of class BarEntry
         * @param dataSetIndex    the index of the DataSet the entry in focus belongs to
         * @param viewPortHandler provides information about the current chart state (scale, translation, ...)
         * @return the formatted label ready for being drawn
         */
        override fun getFormattedValue(value: Float, entry: Entry, dataSetIndex: Int, viewPortHandler: ViewPortHandler): String {
            return "${value}kg"
        }
    }

    inner class WeightMarkerView(context: Context, private val xAxisValueFormatter: IAxisValueFormatter) : MarkerView(context, R.layout.partial_custom_marker_view) {
        private val tvContent: TextView = findViewById(R.id.tvContent)

        // callbacks everytime the MarkerView is redrawn, can be used to update the
        // content (user-interface)
        override fun refreshContent(e: Entry?, highlight: Highlight?) {
            e?.let { entry ->

                tvContent.run {
                    text = "${xAxisValueFormatter.getFormattedValue(entry.x, mLineChart.xAxis)}: ${entry.y}kg"
                    typeface = FontUtils.getCommonTypeface(context)
                }
                super.refreshContent(entry, highlight)
            }
        }

        override fun getOffset(): MPPointF {
            return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
        }
    }
}