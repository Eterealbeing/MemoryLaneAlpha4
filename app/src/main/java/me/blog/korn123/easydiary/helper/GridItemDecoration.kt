package me.blog.korn123.easydiary.helper

import android.graphics.Rect
import android.view.View

class GridItemDecoration(private val space: Int, val callback: () -> Int) : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        val isTopColumn = position < callback.invoke()
        val isStartColumn = position % callback.invoke() == 0
        outRect.top = if (isTopColumn) 0 else space
        outRect.left = if (isStartColumn) 0 else space
    }
}