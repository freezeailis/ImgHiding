package com.example.tangramimghiding.ui.selfView

import android.app.ActionBar
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.tangramimghiding.R
import kotlin.math.min
import kotlin.random.Random

class MaskView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    View(context, attrs, defStyleAttr) {
    private val mPaint = Paint()

    // 分割密度
    private var splitDensity = 10

    // 浮在上方的图片
    private var maskBitmap: Bitmap? = null

    // 随机化块的显示
    private var ifShuffle = false

    // 显示块的百分比
    private var maskLevel = 100.0f

    // 推荐对 maskHeight & maskWidth 手动设置大小, 未设置则设置为 maskBitmap 的尺寸
    // 如果这两个参数和 maskBitmap 都没设置, throw Exception
    private var maskHeight = 0
    private var maskWidth = 0

    // 内部的每个元素表示分割出来的一个图像块
    private var splitIdx: Array<IntArray>
    private var fullSrcRec: Rect
    private var fullDesRes: Rect
    private var srcRectList: Array<Rect>? = null
    private var desRectList: Array<Rect>? = null

    init {
        attrs?.let {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MaskView)
            val resId = a.getResourceId(R.styleable.MaskView_maskSource, -1)
            if (resId != -1) {
                // 初始化 maskImg
                maskBitmap = BitmapFactory.decodeResource(resources, resId)
            }
            splitDensity = a.getInt(R.styleable.MaskView_splitDensity, splitDensity)
            ifShuffle = a.getBoolean(R.styleable.MaskView_ifShuffle, ifShuffle)
            maskLevel = a.getFloat(R.styleable.MaskView_initMaskLevel, maskLevel)
            maskHeight = (a.getDimension(R.styleable.MaskView_maskHeight, maskHeight.toFloat()) + 0.5).toInt()
            maskWidth = (a.getDimension(R.styleable.MaskView_maskWidth, maskWidth.toFloat()) + 0.5).toInt()
            a.recycle()
        }

        // 没有传入 maskBitmap 就默认使用纯白图像
        if (maskBitmap == null) {
            if (maskWidth != 0 && maskHeight != 0) {
                maskBitmap = Bitmap.createBitmap(
                    IntArray(maskWidth * maskHeight) { (0xffffffff).toInt() },
                    maskWidth,
                    maskHeight,
                    Bitmap.Config.ARGB_8888
                )
            } else {
                throw java.lang.Exception("attrs not enough, must specific maskSource or maskHeight & maskWidth")
            }
        } else if (maskWidth == 0 || maskHeight == 0){
            // 没有传入 maskHeight | maskWidth 就用 maskBitmap 的height | width
            maskHeight = if (maskHeight == 0) maskBitmap!!.height else maskHeight
            maskWidth = if (maskWidth == 0) maskBitmap!!.width else maskWidth
        }
        // 二维的索引集合, 里面的索引 [i, j] 满足 i ∈ [0, splitDensity), j ∈ [0, splitDensity)
        // 完全随机的消除 mask
//        splitIdx =
//            Array<IntArray>(splitDensity * splitDensity) { intArrayOf(it / splitDensity, it % splitDensity) }
//        if (ifShuffle) {
//            splitIdx.shuffle()
//        }
        // 从外圈逐渐往内圈消除 mask
        splitIdx = getCircleSplitIdx(splitDensity)
        // 初始化用来绘制的 Rect
            // notNullActually
        maskBitmap?.let { maskBitmap ->
            val srcSingleWidth = maskBitmap.width*1.0 / splitDensity
            val srcSingleHeight = maskBitmap.height*1.0 / splitDensity
            // 从 bitMap 裁剪下来的 rect
            srcRectList = Array(splitIdx.size) {
                Rect(
                    (splitIdx[it][0] * srcSingleWidth).toInt(),
                    (splitIdx[it][1] * srcSingleHeight).toInt(),
                    ((splitIdx[it][0] + 1) * srcSingleWidth).toInt(),
                    ((splitIdx[it][1] + 1) * srcSingleHeight).toInt()
                )
            }
            // 这里直接使用 Int 可能会造成最后的结果不能完全覆盖要覆盖的区域
            val desSingleWidth = maskWidth*1.0 / splitDensity
            val desSingleHeight = maskHeight*1.0 / splitDensity
            // 将放置的位置 rect
            desRectList = Array(splitIdx.size) {
                Rect((splitIdx[it][0] * desSingleWidth).toInt(),
                    (splitIdx[it][1] * desSingleHeight).toInt(),
                    ((splitIdx[it][0] + 1) * desSingleWidth).toInt(),
                    ((splitIdx[it][1] + 1) * desSingleHeight).toInt()
                )
            }
        }
        fullSrcRec = Rect(0, 0, maskBitmap?.width ?: maskWidth, maskBitmap?.height ?: maskHeight)
        fullDesRes = Rect(0, 0, maskWidth, maskHeight)

    }

    /**
     * 通过这个方法更新 maskLevel 并重绘
     *@author aris
     *@time 2023/7/23 16:40
     */
    public fun setMaskLevel(newMaskLevel: Float) {
        maskLevel = newMaskLevel
        postInvalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        // MaskSize (maskHeight & maskWidth) 即为该 view 的实际尺寸
        if (layoutParams.width == ActionBar.LayoutParams.WRAP_CONTENT && layoutParams.height == ActionBar.LayoutParams.WRAP_CONTENT) {
            setMeasuredDimension(maskWidth, maskHeight)
        } else if (layoutParams.width == ActionBar.LayoutParams.WRAP_CONTENT) {
            setMeasuredDimension(maskWidth, heightSize)
        } else if (layoutParams.height == ActionBar.LayoutParams.WRAP_CONTENT) {
            setMeasuredDimension(widthSize, maskHeight)
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        // draw nothing
        if (maskLevel <= 1e-5) {
            return
        }
        // draw all bitMap
        if (maskLevel >= 100.0 - 1e-5) {
            canvas?.apply {
                maskBitmap?.let { maskBitmap ->
                    drawBitmap(maskBitmap, fullSrcRec, fullDesRes, mPaint)
                }
            }
            return
        }
        // draw some part of the bitMap
        maskBitmap?.let {maskBitmap->
            val lastIdx = (maskLevel / 100.0 * splitDensity * splitDensity).toInt()
            for (i in 0 until lastIdx) {
                srcRectList?.get(i)?.let {secRec->
                    desRectList?.get(i)?.let {desRec->
                        canvas?.drawBitmap(maskBitmap, secRec,  desRec, mPaint)
                    }
                }
            }
        }
    }


    /**
     * 将 mask 的消失逻辑改为从外圈往内圈逐渐消失
     * 设置 ifShuffle 为 true 会增加消失的随机性但不会改变这个趋势
     *@author aris
     *@time 2023/7/23 21:53
    */
    private fun getCircleSplitIdx(edgeSize: Int, ifShuffle: Boolean=true): Array<IntArray>{
        // 将 edgeSize 转换成奇数 (方便后续的计算)
        val realEdgeSize = if (edgeSize.and(1) == 1) edgeSize else edgeSize+1
        val splitIdx =
            Array(realEdgeSize * realEdgeSize) { IntArray(0) }
        var curSplitIdx = 0
        val curIdx = mutableListOf<IntArray>()
        val random = Random(System.currentTimeMillis())
        // 每次循环往里面增加一圈的索引(从内往外加)
        for (circleCnt in (realEdgeSize shr 1) downTo 0) {
            // 最内圈且分割边(splitDensity)为奇数(最内圈只有一个元素, 我们限制了 splitDensity 一定为奇数)
            if(circleCnt == (realEdgeSize shr 1)){
                curIdx.add(intArrayOf(realEdgeSize shr 1, realEdgeSize shr 1))
                continue
            }
            // 每圈的最上方(包含两侧)
            for (i in circleCnt until realEdgeSize-circleCnt)
                curIdx.add(intArrayOf(i, circleCnt))
            // 每圈的最右侧(不包含上下)
            for (i in circleCnt+1 until realEdgeSize-circleCnt-1)
                curIdx.add(intArrayOf(realEdgeSize-circleCnt-1, i))
            // 每圈的最下方(包含两侧)
            for (i in realEdgeSize-circleCnt-1 downTo circleCnt)
                curIdx.add(intArrayOf(i, realEdgeSize-circleCnt-1))
            // 每圈的最左侧(不包含上下)
            for (i in realEdgeSize-circleCnt-2 downTo circleCnt+1)
                curIdx.add(intArrayOf(circleCnt, i))
            // 每过一定阶段, 将 curIdx 里面的坐标打乱后(ifShuffle)加入到 splitIdx 里面
            if (circleCnt % 3 == 0 && circleCnt != 0){
                if (ifShuffle) {
                    curIdx.shuffle()
                    // 为了增加边界状态的随机性, 如果 splitIdx 已经持有一定的元素,
                    // 将 splitIdx 末尾持有的元素和 curIdx 里面的元素进行一定程度的随机交换
                    if (curSplitIdx > 10){
                        // 根据边长正比增加随机交换的程度
                        val degree = 4 * ((realEdgeSize shr 1) - circleCnt)
                        for (i in 0 until degree){
                            val i1 = random.nextInt(min(curSplitIdx-1, curIdx.size))
                            val i2 = random.nextInt(curIdx.size)
                            val temp = splitIdx[curSplitIdx - i1 - 1]
                            splitIdx[curSplitIdx - i1] = curIdx[i2]
                            curIdx[i2] = temp
                        }
                    }
                }
                for (item in curIdx){
                    splitIdx[curSplitIdx++] = item
                }
                curIdx.clear()
            }

        }
        if (curIdx.isNotEmpty()) {
            if (ifShuffle){
                curIdx.shuffle()
                if (curSplitIdx > 10){
                    // 根据边长正比增加随机交换的程度
                    val degree = 4 * (realEdgeSize shr 1)
                    for (i in 0 until degree){
                        val i1 = random.nextInt(min(curSplitIdx-1, curIdx.size))
                        val i2 = random.nextInt(curIdx.size)
                        val temp = splitIdx[curSplitIdx - i1 - 1]
                        splitIdx[curSplitIdx - i1] = curIdx[i2]
                        curIdx[i2] = temp
                    }
                }
            }
            for (item in curIdx){
                splitIdx[curSplitIdx++] = item
            }
            curIdx.clear()
        }
        return splitIdx
    }
}
