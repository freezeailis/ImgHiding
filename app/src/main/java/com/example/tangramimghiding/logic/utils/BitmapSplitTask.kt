package com.example.tangramimghiding.logic.utils

import android.graphics.Bitmap
import androidx.core.graphics.get
import com.example.tangramimghiding.logic.model.SettingParameters
import java.util.concurrent.RecursiveTask

/**
 * 图像分割任务的 task
 * 将 [splitTarget] 按照自然顺序, 划分成长和宽均为 [SettingParameters.blockEdgeSize] 的子块
 * 并将结果放到 [blockContainer] 里面
 * 由于两个页面都需要这个任务, 提到 utils 里面
 *@author aris
 *@time 2023/7/19 14:40
*/
class BitmapSplitTask(private val rowStart: Int, private val rowEnd: Int,
                      private val colStart: Int, private val colEnd: Int,
                      private val splitTarget: Bitmap, private val blockContainer: IntArray)
    :RecursiveTask<Boolean>(){
    override fun compute(): Boolean {
        if ((colEnd - colStart) % SettingParameters.blockEdgeSize != 0
            || (rowEnd - rowStart) % SettingParameters.blockEdgeSize != 0){
            return false
        }
        // 中止条件
        if (rowEnd - rowStart == SettingParameters.blockEdgeSize){
//                val blockCntPerCol = splitTarget.height / SettingParameters.blockEdgeSize
//                val blockCntPerRow = splitTarget.width / SettingParameters.blockEdgeSize
            for (cs in colStart until colEnd step SettingParameters.blockEdgeSize){
                val curIdx = rowStart * splitTarget.width + cs * SettingParameters.blockEdgeSize
                val res = IntArray(SettingParameters.blockEleCnt)
                var idx = 0
                for (y in rowStart until rowEnd){
                    for (x in cs until cs + SettingParameters.blockEdgeSize){
                        res[idx++] = (splitTarget[x, y])
                    }
                }
                System.arraycopy(res, 0, blockContainer, curIdx, SettingParameters.blockEleCnt)
            }
            return true
        }
        // 优先从高度方向划分任务
        return if (rowEnd - rowStart > SettingParameters.blockEdgeSize){
            val rowMid = rowStart + SettingParameters.blockEdgeSize
            val subTask1 = BitmapSplitTask(rowStart, rowMid, colStart, colEnd, splitTarget, blockContainer)
            val subTask2 = BitmapSplitTask(rowMid, rowEnd, colStart, colEnd, splitTarget, blockContainer)
            val r1 = subTask2.fork()
            val r2 = subTask1.compute()
            r1.get() && r2
        } else {
            false
        }
    }
}