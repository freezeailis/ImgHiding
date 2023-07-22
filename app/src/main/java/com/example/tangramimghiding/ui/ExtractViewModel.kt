package com.example.tangramimghiding.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel

class ExtractViewModel: ViewModel() {
    // 重构图像(根据恢复算法恢复出来的秘密图像)
    var reconImg: Bitmap? = null
    // 含密图像
    var carrierImg: Bitmap? = null
    // 含密图像分割后的块
    @Volatile
    lateinit var carrierBlocks: IntArray
    @Volatile
    var ifCarrierHasSplit = false

    var reconHeight = 256
    var reconWidth = 256
}