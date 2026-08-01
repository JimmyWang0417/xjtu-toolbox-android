package com.xjtu.toolbox

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.setContent

@Page("main")
class MainPage : ComposeContainer() {
    override fun willInit() {
        super.willInit()
        setContent {
            App()
        }
    }
}
