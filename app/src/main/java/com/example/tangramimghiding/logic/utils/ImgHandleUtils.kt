package com.example.tangramimghiding.logic.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import com.example.tangramimghiding.logic.model.SettingParameters
import kotlin.math.sqrt

object ImgHandleUtils {
    /**
     * 将图像按照 IdealImgArea 缩放(可以由 ifScale 控制)
     * 随后裁剪成 blockEdgeSize 的倍数
     * 目的是使得图像处理成可以被算法处理的对象
     *@author aris
     *@time 2023/7/14 20:51
    */
    fun scaleAndTrim(img: Bitmap, ifScale: Boolean): Bitmap {
        // 先按照一定的比例缩放(基于理想图像尺寸)
        var res = img
        if (ifScale) {
            val matrix = Matrix()
            val ratio = sqrt(SettingParameters.IdealImgArea.toFloat() / (img.width * img.height))
            if (ratio < 1){
                matrix.postScale(ratio, ratio)
                res = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
            }

        }
        // 裁剪使得宽度和高度是 minEdge 的整数倍
        return Bitmap.createBitmap(
            res, 0, 0,
            res.width - res.width % SettingParameters.blockEdgeSize,
            res.height - res.height % SettingParameters.blockEdgeSize
        )
    }
}