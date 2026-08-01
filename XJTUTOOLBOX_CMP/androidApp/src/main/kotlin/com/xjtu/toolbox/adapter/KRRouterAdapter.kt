package com.xjtu.toolbox.adapter

import android.app.Activity
import android.content.Context
import com.tencent.kuikly.core.render.android.adapter.IKRRouterAdapter
import org.json.JSONObject

object KRRouterAdapter : IKRRouterAdapter {
    override fun openPage(
        context: Context,
        pageName: String,
        pageData: JSONObject,
    ) {
        // Single-page app: all navigation handled internally by NavigationState
        // For multi-page scenarios, start KuiklyRenderActivity here
    }

    override fun closePage(context: Context) {
        (context as? Activity)?.finish()
    }
}
