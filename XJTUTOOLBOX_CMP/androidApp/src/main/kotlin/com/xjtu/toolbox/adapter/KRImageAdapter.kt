package com.xjtu.toolbox.adapter

import android.graphics.drawable.Drawable
import com.tencent.kuikly.core.render.android.adapter.IKRImageAdapter
import com.tencent.kuikly.core.render.android.adapter.HRImageLoadOption

object KRImageAdapter : IKRImageAdapter {
    override fun fetchDrawable(
        imageLoadOption: HRImageLoadOption,
        callback: (drawable: Drawable?) -> Unit
    ) {
        // Basic implementation: return null for now
        // TODO: integrate with Coil or other image loading library
        callback(null)
    }
}
