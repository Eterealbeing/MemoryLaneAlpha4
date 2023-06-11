package me.blog.korn123.easydiary.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.xw.repo.BubbleSeekBar
import me.blog.korn123.commons.utils.EasyDiaryUtils
import me.blog.korn123.commons.utils.FontUtils
import me.blog.korn123.easydiary.R
import me.blog.korn123.easydiary.activities.BaseSettingsActivity
import me.blog.korn123.easydiary.activities.SettingsActivity
import me.blog.korn123.easydiary.adapters.FontItemAdapter
import me.blog.korn123.easydiary.adapters.OptionItemAdapter
import me.blog.korn123.easydiary.databinding.FragmentSettingsFontBinding
import me.blog.korn123.easydiary.enums.DialogMode
import me.blog.korn123.easydiary.extensions.*
import me.blog.korn123.easydiary.helper.DEFAULT_CALENDAR_FONT_SCALE
import me.blog.korn123.easydiary.helper.EXTERNAL_STORAGE_PERMISSIONS
import me.blog.korn123.easydiary.helper.USER_CUSTOM_FONTS_DIRECTORY
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import java.io.File

class SettingsFontFragment : androidx.fragment.app.Fragment() {


    /***************************************************************************************************
     *   global properties
     *
     ***************************************************************************************************/
    private lateinit var mBinding: FragmentSettingsFontBinding
    private lateinit var progressContainer: ConstraintLayout
    private lateinit var  mRequestFontPick: ActivityResultLauncher<Intent>
    private lateinit var  mRequestExternalStoragePermissionLauncher: ActivityResultLauncher<Array<String>>


    /***************************************************************************************************
     *   override functions
     *
     ***************************************************************************************************/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mRequestFontPick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            requireActivity().run {
                pauseLock()
                if (activityResult.resultCode == Activity.RESULT_OK && activityResult.data != null) {
                    activityResult.data!!.data?.let { uri ->
                        val fileName = EasyDiaryUtils.queryName(contentResolver, uri)
                        if (FilenameUtils.getExtension(fileName).equals("ttf", true)) {
                            Thread(Runnable {
                                val inputStream = contentResolver.openInputStream(uri)
                                val fontDestDir = File(EasyDiaryUtils.getApplicationDataDirectory(this) + USER_CUSTOM_FONTS_DIRECTORY)
                                FileUtils.copyToFile(inputStream, File(fontDestDir, fileName))
                                runOnUiThread{
                                    progressContainer.visibility = View.GONE
                                    showAlertDialog("${FilenameUtils.getBaseName(fileName)} font file is registered.", null)
                                }
                            }).start()
                            progressContainer.visibility = View.VISIBLE
                        } else {
                            showAlertDialog(
                                "$fileName is not ttf file.",
                                null,
                                null,
                                DialogMode.INFO,
                                false,
                                getString(R.string.add_ttf_fonts_title),
                            )
                        }
                    }
                }
            }
        }

        mRequestExternalStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            requireActivity().run {
                pauseLock()
                when (checkPermission(EXTERNAL_STORAGE_PERMISSIONS)) {
                    true -> openFontSettingDialog()
                    false -> makeSnackBar(mBinding.root, getString(R.string.guide_message_3))
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mBinding = FragmentSettingsFontBinding.inflate(layoutInflater)
        return mBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressContainer = (requireActivity() as BaseSettingsActivity).getProgressContainer()
        requireActivity().run {
            changeDrawableIconColor(config.textColor, R.drawable.ic_minus_6)
            changeDrawableIconColor(config.textColor, R.drawable.ic_plus_6)
        }
        bindEvent()
        updateFragmentUI(mBinding.root)
        initPreference()
    }

    override fun onResume() {
        super.onResume()
        updateFragmentUI(mBinding.root)
        initPreference()
    }


    /***************************************************************************************************
     *   etc functions
     *
     ***************************************************************************************************/
    private val mOnClickListener = View.OnClickListener { view ->
        requireActivity().run {
            when (view.id) {
                R.id.fontSetting -> if (checkPermission(EXTERNAL_STORAGE_PERMISSIONS)) {
                    openFontSettingDialog()
                } else {
                    confirmExternalStoragePermission(EXTERNAL_STORAGE_PERMISSIONS, mRequestExternalStoragePermissionLauncher)
                }
                R.id.decreaseFont -> {
                    config.settingFontSize = config.settingFontSize - 5
                    initTextSize(mBinding.root)
                }
                R.id.increaseFont -> {
                    config.settingFontSize = config.settingFontSize + 5
                    initTextSize(mBinding.root)
                }
                R.id.calendarFontScale -> {
                    openCalendarFontScaleDialog()
                }
                R.id.addTtfFontSetting -> {
//                openGuideView(getString(R.string.add_ttf_fonts_title))
                    performFileSearch()
                }
                R.id.boldStyleOption -> {
                    mBinding.boldStyleOptionSwitcher.toggle()
                    config.boldStyleEnable = mBinding.boldStyleOptionSwitcher.isChecked
                }
            }
        }
    }

    private fun bindEvent() {
        mBinding.run {
            fontSetting.setOnClickListener(mOnClickListener)
            fontLineSpacing.configBuilder
                    .min(0.2F)
                    .max(1.8F)
                    .progress(requireActivity().config.lineSpacingScaleFactor)
                    .floatType()
                    .secondTrackColor(config.textColor)
                    .trackColor(config.textColor)
                    .sectionCount(16)
                    .sectionTextInterval(2)
                    .showSectionText()
                    .sectionTextPosition(BubbleSeekBar.TextPosition.BELOW_SECTION_MARK)
                    .autoAdjustSectionMark()
                    .build()
            val bubbleSeekBarListener = object : BubbleSeekBar.OnProgressChangedListener {
                override fun onProgressChanged(bubbleSeekBar: BubbleSeekBar?, progress: Int, progressFloat: Float, fromUser: Boolean) {
                    Log.i("progress", "$progress $progressFloat")
                    requireActivity().config.lineSpacingScaleFactor = progressFloat
                    setFontsStyle()
                    Log.i("progress", "${requireActivity().config.lineSpacingScaleFactor}")
                }
                override fun getProgressOnActionUp(bubbleSeekBar: BubbleSeekBar?, progress: Int, progressFloat: Float) {}
                override fun getProgressOnFinally(bubbleSeekBar: BubbleSeekBar?, progress: Int, progressFloat: Float, fromUser: Boolean) {}
            }
            fontLineSpacing.setOnProgressChangedListener(bubbleSeekBarListener)
            decreaseFont.setOnClickListener(mOnClickListener)
            increaseFont.setOnClickListener(mOnClickListener)
            calendarFontScale.setOnClickListener(mOnClickListener)
            addTtfFontSetting.setOnClickListener(mOnClickListener)
            boldStyleOption.setOnClickListener(mOnClickListener)
        }
    }

    /**
     * Fires an intent to spin up the "file chooser" UI and select an image.
     */
    private fun performFileSearch() {

        // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
        // browser.
        // ACTION_OPEN_DOCUMENT
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            // Filter to only show results that can be "opened", such as a
            // file (as opposed to a list of contacts or timezones)
//             addCategory(Intent.CATEGORY_OPENABLE)

            // Filter to show only images, using the image MIME data type.
            // If one wanted to search for ogg vorbis files, the type would be "audio/ogg".
            // To search for all documents available via installed storage providers,
            // it would be "*/*".
            type = "*/*"
        }

        mRequestFontPick.launch(intent)
    }

    private fun initPreference() {
        mBinding.run {
            fontSettingSummary.text = FontUtils.fontFileNameToDisplayName(requireActivity(), requireActivity().config.settingFontName)
            calendarFontScaleDescription.text = when (requireActivity().config.settingCalendarFontScale) {
                DEFAULT_CALENDAR_FONT_SCALE -> getString(R.string.calendar_font_scale_disable)
                else -> getString(R.string.calendar_font_scale_factor, requireActivity().config.settingCalendarFontScale)
            }
            boldStyleOptionSwitcher.isChecked = requireActivity().config.boldStyleEnable
        }
    }

    /**
     *  Float Formatting depends on Locale
     *  https://stackoverflow.com/questions/44541638/java-lang-numberformatexception-while-executing-in-france-machine
     */
    private fun openCalendarFontScaleDialog() {
        var alertDialog: AlertDialog? = null
        val builder = AlertDialog.Builder(requireActivity())
        builder.setNegativeButton(getString(android.R.string.cancel), null)
        val inflater = requireActivity().getSystemService(AppCompatActivity.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val containerView = inflater.inflate(R.layout.dialog_option_item, null)
        val listView = containerView.findViewById<ListView>(R.id.listView)

        var selectedIndex = 0
        val listFontScale = ArrayList<Map<String, String>>()
        listFontScale.add(mapOf("optionTitle" to getString(R.string.calendar_font_scale_disable), "optionValue" to "-1"))
        for (i in 1..20 step 1) {
            listFontScale.add(mapOf("optionTitle" to getString(R.string.calendar_font_scale_factor, i * 0.1), "optionValue" to "${i * 0.1F}"))
        }

        listFontScale.mapIndexed { index, map ->
            val fontScale = map["optionValue"] ?: "0"
            if (requireActivity().config.settingCalendarFontScale == fontScale.toFloat()) selectedIndex = index
        }

        val arrayAdapter = OptionItemAdapter(requireActivity(), R.layout.item_check_label, listFontScale, requireActivity().config.settingCalendarFontScale)
        listView.adapter = arrayAdapter
        listView.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            val fontInfo = parent.adapter.getItem(position) as HashMap<String, String>
            fontInfo["optionValue"]?.let {
                requireActivity().config.settingCalendarFontScale = it.toFloat()
            }
            alertDialog?.cancel()
            initPreference()
        }

        alertDialog = builder.create().apply { requireActivity().updateAlertDialog(this, null, containerView, getString(R.string.calendar_font_scale_title)) }
        listView.setSelection(selectedIndex)
    }

    private fun openFontSettingDialog() {
        var alertDialog: AlertDialog? = null
        EasyDiaryUtils.initWorkingDirectory(requireActivity())
        val builder = AlertDialog.Builder(requireActivity())
        builder.setNegativeButton(getString(android.R.string.cancel), null)
//        builder.setTitle(getString(R.string.font_setting))
        val inflater = requireActivity().getSystemService(AppCompatActivity.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val fontView = inflater.inflate(R.layout.dialog_fonts, null)
        val listView = fontView.findViewById<ListView>(R.id.listFont)

        val fontNameArray = resources.getStringArray(R.array.pref_list_fonts_title)
        val fontPathArray = resources.getStringArray(R.array.pref_list_fonts_values)
        val listFont = ArrayList<Map<String, String>>()
        var selectedIndex = 0
        for (i in fontNameArray.indices) {
            val map = HashMap<String, String>()
            map.put("disPlayFontName", fontNameArray[i])
            map.put("fontName", fontPathArray[i])
            listFont.add(map)
        }

        val fontDir = File(EasyDiaryUtils.getApplicationDataDirectory(requireActivity()) + USER_CUSTOM_FONTS_DIRECTORY)
        fontDir.list()?.let {
            for (fontName in it) {
                if (FilenameUtils.getExtension(fontName).equals("ttf", ignoreCase = true)) {
                    val map = HashMap<String, String>()
                    map.put("disPlayFontName", FilenameUtils.getBaseName(fontName))
                    map.put("fontName", fontName)
                    listFont.add(map)
                }
            }
        }

        listFont.mapIndexed { index, map ->
            if (requireActivity().config.settingFontName == map["fontName"]) selectedIndex = index
        }

        val arrayAdapter = FontItemAdapter(requireActivity(), R.layout.item_check_label, listFont)
        listView.adapter = arrayAdapter
        listView.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            val fontInfo = parent.adapter.getItem(position) as HashMap<String, String>
            fontInfo["fontName"]?.let {
                requireActivity().config.settingFontName = it
                FontUtils.setCommonTypeface(requireActivity())
                initPreference()
                setFontsStyle()

                (requireActivity() as SettingsActivity).run {
                    pauseLock()
                    updateUI()
                }
            }
            alertDialog?.cancel()
        }

        alertDialog = builder.create().apply { requireActivity().updateAlertDialog(this, null, fontView, getString(R.string.font_setting)) }
        listView.setSelection(selectedIndex)
    }

    private fun setFontsStyle() {
        FontUtils.setFontsTypeface(requireActivity(), null, mBinding.root)
    }
}