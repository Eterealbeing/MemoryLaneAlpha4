package me.blog.korn123.easydiary.activities

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.recyclerview.widget.GridLayoutManager
import me.blog.korn123.commons.utils.ColorUtils
import me.blog.korn123.commons.utils.EasyDiaryUtils
import me.blog.korn123.commons.utils.FontUtils
import me.blog.korn123.easydiary.R
import me.blog.korn123.easydiary.adapters.PostcardAdapter
import me.blog.korn123.easydiary.databinding.ActivityPostcardViewerBinding
import me.blog.korn123.easydiary.enums.GridSpanMode
import me.blog.korn123.easydiary.extensions.config
import me.blog.korn123.easydiary.extensions.isLandScape
import me.blog.korn123.easydiary.extensions.openGridSettingDialog
import me.blog.korn123.easydiary.extensions.showAlertDialog
import me.blog.korn123.easydiary.helper.DIARY_POSTCARD_DIRECTORY
import me.blog.korn123.easydiary.helper.GridItemDecorationPostcardViewer
import me.blog.korn123.easydiary.helper.POSTCARD_SEQUENCE
import me.blog.korn123.easydiary.helper.TransitionHelper
import org.apache.commons.io.FileUtils
import java.io.File


/**
 * Created by CHO HANJOONG on 2018-05-18.
 */

class PostcardViewerActivity : EasyDiaryActivity() {
    private lateinit var mBinding: ActivityPostcardViewerBinding
    private lateinit var mPostcardAdapter: PostcardAdapter
    private lateinit var mGridLayoutManager: GridLayoutManager
    private var mListPostcard: ArrayList<PostcardAdapter.PostCard> = arrayListOf()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityPostcardViewerBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        mBinding.toolbar.setNavigationOnClickListener { onBackPressed() }
        setSupportActionBar(mBinding.toolbar)
        FontUtils.getTypeface(this, config.settingFontName)?.let {
            mBinding.toolbarLayout.setCollapsedTitleTypeface(it)
            mBinding.toolbarLayout.setExpandedTitleTypeface(it)
        }

//        val flexboxLayoutManager = FlexboxLayoutManager(this).apply {
//            flexWrap = FlexWrap.WRAP
//            flexDirection = FlexDirection.ROW
////            alignItems = AlignItems.FLEX_START
//            justifyContent = JustifyContent.FLEX_START 
//        }
        
        val spacesItemDecoration = GridItemDecorationPostcardViewer(resources.getDimensionPixelSize(R.dimen.component_margin_small), this)
        mGridLayoutManager = GridLayoutManager(this, if (isLandScape()) config.postcardSpanCountLandscape else config.postcardSpanCountPortrait)

        EasyDiaryUtils.initWorkingDirectory(this@PostcardViewerActivity)
        mPostcardAdapter = PostcardAdapter(
                this@PostcardViewerActivity,
                mListPostcard,
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    val intent = Intent(this@PostcardViewerActivity, PostcardViewPagerActivity::class.java)
                    intent.putExtra(POSTCARD_SEQUENCE, position)
                    TransitionHelper.startActivityWithTransition(this@PostcardViewerActivity, intent)
                }
        )

        mBinding.contentPostCardViewer.root.apply {
            layoutManager = mGridLayoutManager
            addItemDecoration(spacesItemDecoration)
            adapter = mPostcardAdapter
//            setHasFixedSize(true)
//            clipToPadding = false
            setPopUpTypeface(FontUtils.getCommonTypeface(this@PostcardViewerActivity))
        }

        initPostCard()
        mBinding.toolbarImage.setColorFilter(ColorUtils.adjustAlpha(config.primaryColor, 0.5F))
        mBinding.deletePostCard.setOnClickListener {
            val selectedItems = arrayListOf<PostcardAdapter.PostCard>()
            mListPostcard.forEachIndexed { _, item ->
                if (item.isItemChecked) selectedItems.add(item)
            }
            
            when (selectedItems.size) {
                0 -> showAlertDialog("No post card selected.", null)
                else -> {
                    showAlertDialog(getString(R.string.delete_confirm),
                            DialogInterface.OnClickListener { _, _ ->
                                selectedItems.forEachIndexed { _, item ->
                                    FileUtils.forceDelete(item.file)
                                }
                                initPostCard()
                            }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.setBackgroundDrawable(null)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_postcard_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.layout -> openGridSettingDialog(mBinding.root, GridSpanMode.POSTCARD) {
                mGridLayoutManager.spanCount = it.toInt()
                mPostcardAdapter.notifyDataSetChanged()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initPostCard() {
        val listPostcard = File(EasyDiaryUtils.getApplicationDataDirectory(this) + DIARY_POSTCARD_DIRECTORY)
                .listFiles()
                .filter { it.extension.equals("jpg", true)}
                .sortedDescending()
                .map { file -> PostcardAdapter.PostCard(file, false) }

        mListPostcard.clear()
        mListPostcard.addAll(listPostcard)
        mPostcardAdapter.notifyDataSetChanged()
        if (mListPostcard.isEmpty()) {
            mBinding.infoMessage.visibility = View.VISIBLE
            mBinding.contentPostCardViewer.root.visibility = View.GONE
            mBinding.appBar.setExpanded(false)
        }
    }
}