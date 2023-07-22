package com.example.tangramimghiding.ui

import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import androidx.lifecycle.ViewModel

class StegoViewModel : ViewModel() {

    // 载体图像和秘密图像
    var containerImg: Bitmap? = null
    var secretImg: Bitmap? = null
    // 存储嵌入了秘密信息的图像, 即含密图像
    var carrierImg: Bitmap? = null
    // 用于判断秘密图像和载体图像的分割任务是否已经完成
    @Volatile
    var ifContainerHasSplit = false
    @Volatile
    var ifSecretHasSplit = false

    // 存储秘密图像和载体图像分割后的结果
    // 按照自然顺序存储块
    @Volatile
    lateinit var containerBlocks : IntArray
    @Volatile
    lateinit var secretBlocks : IntArray


}