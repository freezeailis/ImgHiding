package com.example.tangramimghiding.logic.model

import android.util.Log
import kotlin.math.ceil
import kotlin.math.log


/**
 * 用于存放算法运行过程中所需要的超参数, 并给出一个默认值
 *@author aris
 *@time 2023/7/13 21:11
 */
object SettingParameters {
    // 每组变换参数有四个项目: loc, rm, k, b
    const val PARA_CNT = 4

        // 读取用到的超参数
    // 理想的图像尺寸, 我们把选择的图像往这个尺寸缩放
    var IdealImgArea = 256*256
    var ifScaled = true

        // 分割用到的超参数
    // 分割块的尺寸和对应的块内元素个数
    var blockEdgeSize: Int = 4
    val blockEleCnt: Int
        get() { return blockEdgeSize * blockEdgeSize }

    // 控制最小任务粒度
    val taskEndRange = 1024 / blockEdgeSize

    lateinit var rmChoose: Array<IntArray>

        // 搜索用到的超参数
    // k 参数的选择, 放大了十倍以避免浮点数
    var kChoose: IntArray = intArrayOf(10, 20, 5, 1)
    // 对于每个目标块的搜索范围
    var searchRange: Int = 256
    // 认为符合搜索结果的阈值
    var searchThreshold = 16.0

        // 编码用到的超参数 (loc, rm, k, b)
    var kChoose2idx = HashMap<Int, Int>()
    init {
        for (i in kChoose.indices){
            kChoose2idx[kChoose[i]] = i
        }
    }
    // 完全保存 b ∈ [-255, 255] 需要 9 位
    // 我们只提供了 B_LEN.value 位来存储
    // 所以必要的时候会舍弃 b_offset 位的精度
    val b_offset = 9 - CodeLen.B_LEN.value
    // 用于控制编码长度
    enum class CodeLen(val value: Int) {
        SHAPE_LEN(10),
        RM_LEN(2),
        K_LEN(2),
        B_LEN(8),
        LOC_LEN(ceil(log(searchRange.toDouble(), 2.0)).toInt())
    }
    val SingleParaLen: Int
    get() {return CodeLen.LOC_LEN.value + CodeLen.RM_LEN.value + CodeLen.K_LEN.value + CodeLen.B_LEN.value}

    /**
     * 对于给的的数组, 将其看作尺寸为 blockEdgeSize * blockEdgeSize 的块
     * 对该块做上下翻转, 随后按照数组的形式返回
     *@author aris
     *@time 2023/7/17 14:56
    */
    private fun flip(ori: IntArray): IntArray{
        return if (ori.size != blockEleCnt) {
            Log.e("init", "flip error, wrong size of block: %d".format(ori.size))
            IntArray(0)
        } else {
            val res = IntArray(ori.size){ori[it]}
            for (y in 0 until blockEdgeSize/2){
                for (x in 0 until blockEdgeSize){
                    val ry = blockEdgeSize - y - 1
                    val temp = res[y * blockEdgeSize + x]
                    res[y * blockEdgeSize + x] = res[ry * blockEdgeSize + x]
                    res[ry * blockEdgeSize + x] = temp
                }
            }
            res
        }
    }


    /**
     * 对于给的的数组, 将其看作尺寸为 blockEdgeSize * blockEdgeSize 的块
     * 对该块做沿着主对角线的镜像/翻转, 随后按照数组的形式返回
     *@author aris
     *@time 2023/7/17 15:03
    */
    private fun mirror(ori: IntArray): IntArray{
        return if (ori.size != blockEleCnt) {
            Log.e("init", "flip error, wrong size of block: %d".format(ori.size))
            IntArray(0)
        } else {
            val res = IntArray(ori.size){ori[it]}
            for (y in 0 until blockEdgeSize){
                for (x in y+1 until blockEdgeSize){
                    val ry = x
                    val rx = y
                    val temp = res[y * blockEdgeSize + x]
                    res[y * blockEdgeSize + x] = res[ry * blockEdgeSize + rx]
                    res[ry * blockEdgeSize + rx] = temp
                }
            }
            res
        }
    }

    /**
     * 1.加载本地参数数据(if exist)
     * 2.基于此做一些预计算
     *@author aris
     *@time 2023/7/17 15:12
    */
    fun initNativePara(){
        // load para

        // compute rm
        rmChoose = Array<IntArray>(4){ IntArray(0) }
        var oriSeq = IntArray(blockEleCnt){it}
        var idx = 0
        for (i in 0 until 4){
            val r1 = mirror(flip(oriSeq))
            val r2 = flip(r1)
            oriSeq = r1
            rmChoose[idx++] = r1
//            rmChoose[idx++] = r2
        }
    }

}
