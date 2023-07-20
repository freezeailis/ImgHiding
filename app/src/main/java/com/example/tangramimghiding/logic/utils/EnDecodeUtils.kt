package com.example.tangramimghiding.logic.utils
import android.util.Log
import com.example.tangramimghiding.logic.model.SettingParameters
import com.example.tangramimghiding.logic.model.SettingParameters.CodeLen as CodeLen
import com.example.tangramimghiding.logic.model.SettingParameters.PARA_CNT
import com.example.tangramimghiding.logic.model.SettingParameters.SingleParaLen
import kotlin.math.abs

object EnDecodeUtils {
    /**
     * 将变换参数编码成 01Seq
     * 变换参数到索引的转变由外部完成
     * 一次完成一组(rgb为单位的一组, 比如整个 r 层的编码)
     * Note: 前 [CodeLen.SHAPE_LEN.value]*2 位分别原来表示目标图像的 height 和 width
     *@return height+width+[loc+rm+k+b]*n
     *@author aris
     *@time 2023/7/14 22:56
    */
    fun encode(paras: IntArray, height: Int, width: Int): String{
        val res = StringBuilder()
        res.apply {
            val heightS = height.toString(2)
            append("0".repeat(CodeLen.SHAPE_LEN.value - heightS.length) + heightS)
            val widthS = width.toString(2)
            append("0".repeat(CodeLen.SHAPE_LEN.value - widthS.length) + widthS)
            if (heightS.length > CodeLen.SHAPE_LEN.value || widthS.length > CodeLen.SHAPE_LEN.value) {
                Log.e("encode", "too large height[%d] or width[%d]".format(height, width))
            }

            for (idx in paras.indices step PARA_CNT){
                // sequence by [loc, rm, k, b]
                val s1 = paras[idx].toString(2)
                val s2 = paras[idx+1].toString(2)
                val s3 = paras[idx+2].toString(2)
                // b 包含正负号, 我们在最前面单独用一位来表示符号
                val sign = if (paras[idx+3] > 0) 0 else 1
                val s4 = abs(paras[idx+3]).toString(2)
                append("0".repeat(CodeLen.LOC_LEN.value - s1.length))
                append(s1)
                append("0".repeat(CodeLen.RM_LEN.value - s2.length))
                append(s2)
                append("0".repeat(CodeLen.K_LEN.value - s3.length))
                append(s3)
                append(sign)
                append("0".repeat(CodeLen.B_LEN.value - s4.length - 1))
                append(s4)
            }
        }
        return res.toString()
    }

    /**
     * 将编码结果转换成实际参数
     * 一次性完成全部的转换
     * Note: 不包含宽度或者高度的信息
     *@param [loc+rm+k+b]*n
     *@return IntArray :[loc, rm, k, b] * n
     *@author aris
     *@time 2023/7/14 22:57
    */
    fun decode(bitSeq: String): IntArray{
        val res = mutableListOf<Int>()
        val sb = StringBuilder(bitSeq)

        res.apply {
            val singleParaLen = SingleParaLen
            for (start in sb.indices step singleParaLen){
                val curSubStr = sb.substring(start, start + singleParaLen)
                var curStart = 0
                val loc = curSubStr.substring(curStart, CodeLen.LOC_LEN.value).toInt(2)
                curStart += CodeLen.LOC_LEN.value
                val rm = curSubStr.substring(curStart, curStart + CodeLen.RM_LEN.value).toInt(2)
                curStart += CodeLen.RM_LEN.value
                val k = curSubStr.substring(curStart, curStart + CodeLen.K_LEN.value).toInt(2)
                curStart += CodeLen.K_LEN.value
                val sign = if (curSubStr[curStart] == '0') 1 else -1
                curStart += 1
                val b = sign * curSubStr.substring(curStart, curStart + CodeLen.B_LEN.value-1).toInt(2)
                add(loc)
                add(rm)
                add(k)
                add(b)
            }
        }
        return res.toIntArray()
    }
}