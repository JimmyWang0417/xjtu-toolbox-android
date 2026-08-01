package com.xjtu.toolbox

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.tencent.kuikly.core.render.android.adapter.KuiklyRenderAdapterManager
import com.tencent.kuikly.core.render.android.expand.KuiklyBaseView
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate
import com.xjtu.toolbox.adapter.*

class MainActivity : AppCompatActivity() {

    private var kuiklyView: KuiklyBaseView? = null

    companion object {
        const val EXTRA_LAUNCH_ROUTE = "launch_route"

        init {
            initKuiklyAdapters()
        }

        private fun initKuiklyAdapters() {
            with(KuiklyRenderAdapterManager) {
                krImageAdapter = KRImageAdapter
                krLogAdapter = KRLogAdapter
                krRouterAdapter = KRRouterAdapter
                krThreadAdapter = KRThreadAdapter()
                krUncaughtExceptionHandlerAdapter = KRExceptionAdapter
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupImmersiveMode()

        val container = findViewById<ViewGroup>(R.id.kuikly_container)
        val delegate = object : KuiklyRenderViewBaseDelegatorDelegate {}

        kuiklyView = KuiklyBaseView(this, delegate)
        kuiklyView?.onAttach("", "main", mapOf())
        container.addView(kuiklyView)
    }

    override fun onResume() {
        super.onResume()
        kuiklyView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        kuiklyView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        kuiklyView?.onDetach()
    }

    private fun setupImmersiveMode() {
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }
}
