package me.blog.korn123.easydiary.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import me.blog.korn123.commons.utils.FontUtils
import me.blog.korn123.easydiary.R
import me.blog.korn123.easydiary.extensions.initTextSize
import me.blog.korn123.easydiary.extensions.updateDrawableColorInnerCardView
import me.blog.korn123.easydiary.extensions.updateTextColors

/**
 * Refactored code on 2019-12-25.
 *
 */
class RealmFileItemAdapter(
        val activity: Activity,
        private val layoutResourceId: Int,
        private val list: List<Map<String, String>>
) : ArrayAdapter<Map<String, String>>(activity , layoutResourceId, list) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val itemView: View = convertView ?: LayoutInflater.from(parent.context).inflate(this.layoutResourceId, parent, false)
        if (itemView is ViewGroup) {
            activity.run {
                initTextSize(itemView)
                updateTextColors(itemView)
            }
            FontUtils.setFontsTypeface(this.context, null, itemView)
        }

        when (itemView.tag is ViewHolder) {
            true -> itemView.tag as ViewHolder
            false -> {
                val holder = ViewHolder(itemView.findViewById(R.id.fileName), itemView.findViewById(R.id.createdTime))
                itemView.tag = holder
                holder
            }
        }.run {
            activity.updateDrawableColorInnerCardView(R.drawable.ic_text_file_5)
            name.text = list[position]["name"]
            createdTime.text = list[position]["createdTime"]
        }

        return itemView
    }

    class ViewHolder(val name: TextView, val createdTime: TextView)
}
