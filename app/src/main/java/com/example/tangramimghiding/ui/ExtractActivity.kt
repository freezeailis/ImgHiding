package com.example.tangramimghiding.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.telecom.Call
import android.util.Log
import android.util.TypedValue
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.get
import androidx.core.graphics.set
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.tangramimghiding.R
import com.example.tangramimghiding.databinding.ActivityExtractBinding
import com.example.tangramimghiding.logic.dao.BlocksDao
import com.example.tangramimghiding.logic.dao.CodeStringDao
import com.example.tangramimghiding.logic.dao.TransResDao
import com.example.tangramimghiding.logic.model.SettingParameters
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RecursiveTask
import kotlin.concurrent.thread
import com.example.tangramimghiding.logic.model.SettingParameters.PARA_CNT
import com.example.tangramimghiding.logic.utils.BitmapSplitTask
import com.example.tangramimghiding.logic.utils.EnDecodeUtils
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask


/**
 * 在这个页面, 将完成对 含密图像 的解密操作
 * 具体步骤可以划为
 * 1.选择含密图像, 2.提取秘密信息并解码, 3.根据解密结果和含密图像本身, 恢复秘密图像
 * 如果是刚刚完成了加密, 可以直接从 cache 文件中加载步骤 1 和步骤 2 所需要的信息, 直接进行第三步
 *@author aris
 *@time 2023/7/18 20:09
 */
class ExtractActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExtractBinding
    private val viewModel by lazy { ViewModelProvider(this).get(ExtractViewModel::class.java) }
    private val handler by lazy { ExtractHandler(Looper.getMainLooper()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExtractBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置沉浸式 & 状态栏字色
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsControllerCompat = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsControllerCompat.isAppearanceLightStatusBars = true

        // init 默认含密图像
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        options.inTargetDensity = TypedValue().density
        options.inScaled = false
//        val defaultCarrierImg =
//            BitmapFactory.decodeResource(resources, R.drawable.default_carrier_img, options)

        val r = resources
        val resId = R.drawable.default_carrier_img
        val uri = Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                    + r.getResourcePackageName(resId) + "/"
                    + r.getResourceTypeName(resId) + "/"
                    + r.getResourceEntryName(resId))
        val defaultCarrierImg =
            BitmapFactory.decodeStream(
                this@ExtractActivity.contentResolver.openInputStream(uri))

        val defaultCarrierBlocks = IntArray(defaultCarrierImg.width * defaultCarrierImg.height)
        viewModel.carrierImg = defaultCarrierImg
        viewModel.carrierBlocks = defaultCarrierBlocks

        // 对默认载体图像做分割
        val defaultImgSplitThread = thread {
            val pool = ForkJoinPool()
            viewModel.ifCarrierHasSplit = pool.invoke(
                BitmapSplitTask(
                    0, defaultCarrierImg.height,
                    0, defaultCarrierImg.width, defaultCarrierImg, defaultCarrierBlocks
                )
            )
            pool.shutdown()
            handler.sendEmptyMessage(MsgType.EnableExecutable.value)
        }

        // 载体图像的选择和加载
        val secretAlbumLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) {
                it?.let { uri ->
                    thread {
                        // 短暂的不能执行
                        viewModel.ifCarrierHasSplit = false
                        viewModel.carrierImg =
//                            Glide.with(this@ExtractActivity).asBitmap().load(it).submit().get()
                            BitmapFactory.decodeStream(
                                this@ExtractActivity.contentResolver.openInputStream(uri))
                        viewModel.carrierImg?.let { img ->
                            // 等待默认分割线程
                            defaultImgSplitThread.join()
                            // 不需要缩放和裁剪
                            viewModel.carrierImg = img
                            val cntOfBlocks =
                                img.height * img.width / SettingParameters.blockEleCnt
                            val carrierBlocks =
                                IntArray(cntOfBlocks * SettingParameters.blockEleCnt)
                            viewModel.carrierBlocks = carrierBlocks
                            // 分割所选择的图像
                            val pool = ForkJoinPool()
                            viewModel.ifCarrierHasSplit = pool.invoke(
                                BitmapSplitTask(
                                    0, img.height,
                                    0, img.width, img, carrierBlocks
                                )
                            )
                            pool.shutdown()
                        }
                        // 更新可执行状态
                        handler.sendEmptyMessage(MsgType.EnableExecutable.value)
                        Log.d(
                            "read", "carrierImg height is %d, width is %d"
                                .format(viewModel.carrierImg?.height, viewModel.carrierImg?.width)
                        )
                    }
                    Glide.with(this).load(it).into(binding.carrierImgView)
                }
            }   // 图像选择按钮
        binding.selectCarrierImgBtn.setOnClickListener {
            secretAlbumLauncher.launch(arrayOf("image/*"))
        }

        // 提取算法的执行
        binding.executeExtractBtn.setOnClickListener {
            thread {
                // 先提取秘密信息, 后用提取的秘密信息执行秘密图像恢复(恢复得到的称为重建图像)
                // 1. 提取秘密信息
                val hidingInfo = FutureTask<Array<IntArray>>(ExtractTask())
                Thread(hidingInfo).start()
//                val comp = TransResDao.getTrans()

                val transRes = hidingInfo.get()
                // 2. 根据秘密信息和含密图像本身做变换得到重建图像(reconImg)
                viewModel.reconImg = Bitmap.createBitmap(
                    viewModel.reconWidth,
                    viewModel.reconHeight,
                    Bitmap.Config.ARGB_8888
                )
                val pool = ForkJoinPool()
                viewModel.carrierBlocks?.let { carrierBlocks ->
                    pool.invoke(UseTransTask(0, transRes[0].size, transRes, carrierBlocks))
                }
                pool.shutdown()
                handler.sendEmptyMessage(MsgType.EnableSaveRecon.value)
            }
        }

    }

    /**
     * 更新 /算法执行/ 按钮和 /保存重建图像/ 按钮的可点击属性和显示状态
     * 保存图像信息的提示
     * 参数可见 [MsgType]
     *@author aris
     *@time 2023/7/19 16:28
     */
    inner class ExtractHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MsgType.EnableExecutable.value -> {
                    binding.executeExtractBtn.isClickable = true
                    binding.executeExtractTv.setTextColor(getColor(R.color.gray))
                }
                MsgType.EnableSaveRecon.value -> {
                    binding.saveTargetImgBtn.isClickable = true
                    binding.saveTargetImgTv.setTextColor(getColor(R.color.gray))
                    // 更新预览图像
                    binding.reconImgView.setImageDrawable(
                        BitmapDrawable(
                            resources,
                            viewModel.reconImg
                        )
                    )
                    Log.d(
                        "enable",
                        "reconImg height is %d, width is %d".format(
                            viewModel.reconImg?.height,
                            viewModel.reconImg?.width
                        )
                    )
                }
                MsgType.SaveEvent.value -> {
                    if (msg.arg1 == 1) {
                        // 保存成功
                        Toast.makeText(this@ExtractActivity, "成功保存重建图像", Toast.LENGTH_SHORT).show()
                    } else {
                        // 保存失败
                        Toast.makeText(this@ExtractActivity, "保存失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * 首先按照自然顺序从含密图像提取 01 seq
     * 随后开启 rgb 三个线程分别解码获取结果
     *@author aris
     *@time 2023/7/19 15:36
     */
    inner class ExtractTask() : Callable<Array<IntArray>> {
        lateinit var extractRawRes: Array<String>
        private lateinit var carrierImg: Bitmap

        init {
            if (viewModel.carrierImg == null) {
                Log.e("extract", "viewModel.carrierImg is still null")
            }
            viewModel.carrierImg?.let {
                carrierImg = it
                carrierImg.isPremultiplied = false
            }
        }

        inner class DecodeTask(private val channelId: Int) : Callable<IntArray> {
            override fun call(): IntArray {
                return EnDecodeUtils.decode(extractRawRes[channelId])
            }
        }

        override fun call(): Array<IntArray> {
            val rawStringBuilderRGB = Array(3) { StringBuilder() }
            // 先提取目标图像的 height 和 width, 随后用其计算出所需要的 tarLen
            var tarLen = -1
            val firstTarLen = SettingParameters.CodeLen.SHAPE_LEN.value * 2
            var extractAll = false
            for (y in 0 until carrierImg.height) {
                if (extractAll)
                    break
                for (x in 0 until carrierImg.width) {
                    if (extractAll)
                        break
                    val curPixelValue = carrierImg[x, y]
                    val pixValueRGB = intArrayOf(
                        Color.red(curPixelValue),
                        Color.green(curPixelValue),
                        Color.blue(curPixelValue)
                    )
                    // 分别存到 rgb 三个通道
                    for (i in rawStringBuilderRGB.indices) {
                        val sb = rawStringBuilderRGB[i]
                        // 每次取后 2 位加入到 stringBuilder 里面
                        val extStr = (pixValueRGB[i] and 0x03).toString(2)
                        sb.append("0".repeat(2 - extStr.length) + extStr)
                        if (tarLen == -1 && sb.length == firstTarLen) {
                            val heightWidth = sb.toString()
                            val height = heightWidth.substring(0, firstTarLen / 2).toInt(2)
                            val width = heightWidth.substring(firstTarLen / 2, firstTarLen).toInt(2)
                            viewModel.reconHeight = height
                            viewModel.reconWidth = width
                            tarLen =
                                firstTarLen + (height * width) / SettingParameters.blockEleCnt * SettingParameters.SingleParaLen
                        } else {
                            // 三个通道都提取完成
                            if ((sb.length == tarLen) && (i == rawStringBuilderRGB.size - 1)) {
                                extractAll = true
                            }
                        }
                    }
                }
            }
            // 更新提取结果(不包含 height, width 的编码)
//            var comp = CodeStringDao.getCodes()
//            comp = Array(3) { comp[it].substring(firstTarLen) }
            extractRawRes = Array(3) { rawStringBuilderRGB[it].toString() }
            extractRawRes = Array(3) { rawStringBuilderRGB[it].substring(firstTarLen) }
            // 直接使用 cache 里面的编码字符串
//            extractRawRes = Array(3) { comp[it] }
            // 开启三个线程分别对提取的结果解码
            val decodeTasks = Array(3) { FutureTask(DecodeTask(it)) }
            for (task in decodeTasks) {
                Thread(task).start()
            }
            return Array(3) { decodeTasks[it].get() }
        }
    }

    /**
     * 根据变换参数和含密图像本身, 恢复秘密图像, 即类前描述的步骤 3
     *@author aris
     *@time 2023/7/18 20:13
     */
    inner class UseTransTask(
        private val startIdx: Int, private val endIdx: Int,
        private val transRes: Array<IntArray>,
        private val carrierBlocks: IntArray
    ) : RecursiveTask<Boolean>() {
        override fun compute(): Boolean {
            // 中止条件
            // 每次取 taskEndRange 个变换参数出来做变换
            if (endIdx - startIdx <= PARA_CNT * SearchTransService.taskEndRange) {
                for (start in startIdx until endIdx step PARA_CNT) {
                    // 单个变换
                    val reconRGB = Array(3) { IntArray(0) }
                    var baseR: IntArray = IntArray(0)
                    var baseG: IntArray = IntArray(0)
                    var baseB: IntArray = IntArray(0)
                    var baseRGB: Array<IntArray> = Array(0) { IntArray(0) }
                    // rgb 三个通道分别处理
                    for (i in transRes.indices) {
                        val trans = transRes[i]
                        // 使用真实的位置(测试用)
//                        val loc = trans[start]
                        // 通过相对位置计算真实位置f
                        val loc =
                            ((trans[start] + start / PARA_CNT) * SettingParameters.blockEleCnt) % carrierBlocks.size
                        val rm = SettingParameters.rmChoose[trans[start + 1]]
                        val k = SettingParameters.kChoose[trans[start + 2]] * 1.0 / 10
                        val b = trans[start + 3] * (1 shl SettingParameters.b_offset)
                        val basePixes =
                            IntArray(SettingParameters.blockEleCnt) { carrierBlocks[loc + it] }
                        baseR = IntArray(basePixes.size) { Color.red(basePixes[it]) }
                        baseG = IntArray(basePixes.size) { Color.green(basePixes[it]) }
                        baseB = IntArray(basePixes.size) { Color.blue(basePixes[it]) }
                        baseRGB = arrayOf(baseR, baseG, baseB)
                        val subMinValues = baseRGB[i].min()
                        // 获得变换结果
                        reconRGB[i] =
                            IntArray(baseR.size) { (k * (baseRGB[i][rm[it]] - subMinValues) + b).toInt() }
                    }
                    for (u in reconRGB.indices) {
                        for (v in reconRGB[0].indices) {
//                            if (reconRGB[u][v] > 255 || reconRGB[u][v] < 0)
//                                Log.d(
//                                    "useInteresting",
//                                    "change Res is %d, recon id is %d".format(
//                                        reconRGB[u][v],
//                                        start / PARA_CNT
//                                    )
//                                )
                            reconRGB[u][v] =
                                if (reconRGB[u][v] > 255) 255 else if (reconRGB[u][v] < 0) 0 else reconRGB[u][v]
                        }
                    }
                    // 写入 bitMap
                    var idx = 0
                    val yStart =
                        (start / PARA_CNT) / (viewModel.reconWidth / SettingParameters.blockEdgeSize) * PARA_CNT
                    val xStart =
                        (start / PARA_CNT) % (viewModel.reconWidth / SettingParameters.blockEdgeSize) * PARA_CNT
                    for (u in 0 until SettingParameters.blockEdgeSize) {
                        for (v in 0 until SettingParameters.blockEdgeSize) {
                            val red = reconRGB[0][idx]
                            val green = reconRGB[1][idx]
                            val blue = reconRGB[2][idx]
                            ++idx
                            val color = (0xff shl 24) + (red shl 16) + (green shl 8) + blue
                            viewModel.reconImg?.let {
                                it[v + xStart, u + yStart] = color
                            }
                        }
                    }
                }
                return true
            } else {
                val midIdx = startIdx + PARA_CNT * SearchTransService.taskEndRange
                val subTask1 = UseTransTask(startIdx, midIdx, transRes, carrierBlocks)
                val subTask2 = UseTransTask(midIdx, endIdx, transRes, carrierBlocks)
                val r2 = subTask2.fork()
                val r1 = subTask1.compute()
                return r1 && r2.get()
            }
        }
    }

    enum class MsgType(val value: Int) {
        // 更新恢复算法执行按钮的状态
        EnableExecutable(1),

        // 更新保存重建图像按钮的状态(如果可以保存, 这时候也可以更新预览图像了)
        EnableSaveRecon(2),

        // 给出保存成功或者失败的提示
        SaveEvent(3)
    }
}




