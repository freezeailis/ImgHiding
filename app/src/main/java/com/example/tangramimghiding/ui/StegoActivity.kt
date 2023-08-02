package com.example.tangramimghiding.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.get
import androidx.core.graphics.set
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.tangramimghiding.R
import com.example.tangramimghiding.databinding.ActivityStegoBinding
import com.example.tangramimghiding.logic.dao.BlocksDao
import com.example.tangramimghiding.logic.dao.TransResDao
import com.example.tangramimghiding.logic.dao.saveToAlbum
import com.example.tangramimghiding.logic.model.SettingParameters
import com.example.tangramimghiding.logic.utils.BitmapSplitTask
import com.example.tangramimghiding.logic.utils.EnDecodeUtils
import com.example.tangramimghiding.logic.utils.ImgHandleUtils
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.FutureTask
import kotlin.concurrent.thread

/**
 * 在这个页面, 将完成 载体图像 和 秘密图像 的
 * 1.选择, 2.分割(包含预处理), 3.秘密信息嵌入(包含编码 (EnDeCodeUtils), 这个环节将会生成含密图像), 4.保存含密图像. 四个功能
 * 其中, 2 和 3 之间有一个秘密信息的生成步骤(cost time most), 这将被委托给 SearchService 来完成, 并通过 cache 文件返回结果
 *@author aris
 *@time 2023/7/18 20:03
*/
class StegoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStegoBinding
    private val handler by lazy { StegoHandler(Looper.getMainLooper()) }
    private val viewModel by lazy { ViewModelProvider(this).get(StegoViewModel::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStegoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置沉浸式 & 状态栏字色
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsControllerCompat = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsControllerCompat.isAppearanceLightStatusBars = true

        // init 超参数
        SettingParameters.initNativePara()
        // init 默认秘密图像和默认载体图像
        val options = BitmapFactory.Options()
//        options.inSampleSize = 2
        options.inScaled = SettingParameters.ifScaled
        var defaultSecretImg =
            BitmapFactory.decodeResource(resources, R.drawable.default_secret_img, options)
        var defaultContainerImg =
            BitmapFactory.decodeResource(resources, R.drawable.default_container_img, options)
        // 对读取到的默认图像按照设置参数做一次缩放和裁剪
        defaultSecretImg = ImgHandleUtils.scaleAndTrim(defaultSecretImg, SettingParameters.ifScaled)
        defaultContainerImg =
            ImgHandleUtils.scaleAndTrim(defaultContainerImg, SettingParameters.ifScaled)
        viewModel.secretImg = defaultSecretImg
        viewModel.containerImg = defaultContainerImg
        viewModel.secretBlocks = IntArray(defaultSecretImg.height * defaultSecretImg.width)
        viewModel.containerBlocks = IntArray(defaultContainerImg.height * defaultContainerImg.width)
            // 对默认秘密图像和默认载体图像做分割,
        // 只有当默认分割任务做完了, 自定义图片的分割任务才会被允许执行(不过事实上耗时很短, 一般不至于出现这种情形)
        val defaultImgSplitThread =  thread {
            val pool = ForkJoinPool()
            viewModel.ifContainerHasSplit = pool.invoke(BitmapSplitTask(0, defaultContainerImg.height,
                0, defaultContainerImg.width, defaultContainerImg, viewModel.containerBlocks))
            viewModel.ifSecretHasSplit = pool.invoke(BitmapSplitTask(0, defaultSecretImg.height,
                0, defaultSecretImg.width, defaultSecretImg, viewModel.secretBlocks))
            pool.shutdown()
            handler.sendEmptyMessage(MsgType.RefreshExecutable.value)
        }

//        Log.d("bitMap", "containerImg height is %d, width is %d"
//            .format(viewModel.secretImg?.height, viewModel.secretImg?.width))

        // 载体图像的选择和加载
        val containerAlbumLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()){
            it?.let { uri ->
                Glide.with(this@StegoActivity).asBitmap().load(uri).into(binding.containerImgView)
                // 等待默认分割线程
                defaultImgSplitThread.join()
                viewModel.ifContainerHasSplit = false
                thread {
                    // 1. 根据 uri 读取并缩放裁剪选择的图像
                    var containerImg = Glide.with(this@StegoActivity).asBitmap().load(uri).submit().get()
                    containerImg.config = Bitmap.Config.ARGB_8888
                    containerImg = ImgHandleUtils.scaleAndTrim(containerImg, SettingParameters.ifScaled)
                    val cntOfBlocks =
                        containerImg.height * containerImg.width / SettingParameters.blockEleCnt
                    // 2. 对处理后的图像做预处理(分割)
                    viewModel.containerImg = containerImg
                    viewModel.containerBlocks =
                        IntArray(cntOfBlocks * SettingParameters.blockEleCnt)
                    viewModel.containerImg?.let { img ->
                        // 分割所选择的图像
                        val pool = ForkJoinPool()
                        viewModel.ifContainerHasSplit = pool.invoke(
                            BitmapSplitTask(
                                0, img.height,
                                0, img.width, img, viewModel.containerBlocks
                            )
                        )
                        pool.shutdown()
                    }
                    // 发送可以执行的通知(可能可以执行)
                    handler.sendEmptyMessage(MsgType.RefreshExecutable.value)
                }
            }
            if (it == null){
                // 没做任何选择就返回的情况
                binding.selectContainerImgBtn.isClickable = true
            }
        }
        binding.selectContainerImgBtn.setOnClickListener {
            it.isClickable = false
            containerAlbumLauncher.launch(arrayOf("image/*"))
        }

        // 秘密图像的选择和加载
        val secretAlbumLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()){
            it?.let { uri->
                Glide.with(this@StegoActivity).asBitmap().load(uri).into(binding.secretImgView)
                // 等待默认分割线程
                defaultImgSplitThread.join()
                viewModel.ifSecretHasSplit = false
                thread {
                    // 1. 根据 uri 读取并缩放裁剪所选择的图像
                    var secretImg = Glide.with(this@StegoActivity).asBitmap().load(uri).submit().get()
                    secretImg.config = Bitmap.Config.ARGB_8888
                    secretImg = ImgHandleUtils.scaleAndTrim(secretImg, SettingParameters.ifScaled)
                    val cntOfBlocks =
                        secretImg.height * secretImg.width / SettingParameters.blockEleCnt
                    // 2. 对处理后的图像做预处理(分割)
                    viewModel.secretImg = secretImg
                    viewModel.secretBlocks =
                        IntArray(cntOfBlocks * SettingParameters.blockEleCnt)
                    viewModel.secretImg?.let { img ->
                        // 分割所选择的图像
                        val pool = ForkJoinPool()
                        viewModel.ifSecretHasSplit = pool.invoke(
                            BitmapSplitTask(
                                0, img.height,
                                0, img.width, img, viewModel.secretBlocks
                            )
                        )
                        pool.shutdown()
                    }
                    // 发送可以执行的通知(可能可以执行)
                    handler.sendEmptyMessage(MsgType.RefreshExecutable.value)
                }
            }
            if (it == null) {
                // 没做任何选择就返回的情况
                binding.selectSecretImgBtn.isClickable = true
            }
        }
        binding.selectSecretImgBtn.setOnClickListener {
            it.isClickable = false
            secretAlbumLauncher.launch(arrayOf("image/*"))
        }

        // 控制搜索算法的执行
        binding.executeTangramBtn.setOnClickListener {
            // 避免重复点击
            it.isClickable = false
            // 设置 ui 显示上的改变
            binding.executeTangramTv.setTextColor(getColor(R.color.draker_gray))
            if (binding.processBar.visibility == View.GONE || binding.processHintTv.visibility == View.GONE){
                binding.processBar.visibility = View.VISIBLE
                binding.processHintTv.visibility = View.VISIBLE
            }
            // 在 carrierImg 上面覆盖一层 mask
            // mask 下面直接使用 containerImg (两者本身就很接近, 获得真实的 carrierImg 后换成真实的)
            binding.maskView.setMaskLevel(100.0f)
            binding.carrierImgView.setImageDrawable(binding.containerImgView.drawable)
            // 设置完 ui 的变化后把任务交给 SearchTransService 去做
            val intent = Intent(this, SearchTransService::class.java)
            // blocks 太大, 改用 cache 文件传输
            BlocksDao.putBlocks("containerBlocks", viewModel.containerBlocks)
            BlocksDao.putBlocks("secretBlocks", viewModel.secretBlocks)
//            intent.putExtra("containerBlocks", viewModel.containerBlocks)
//            intent.putExtra("secretBlocks", viewModel.secretBlocks)
            intent.putExtra("secretImgWidth", viewModel.secretImg?.width)
            startService(intent)
        }


        // 保存含密图像
        binding.saveCarrierBtn.setOnClickListener {
            thread {
                viewModel.carrierImg?.let { carrierImg->
                    val fileName = "carrierImg_" + System.currentTimeMillis() + ".png"
                    val uri = carrierImg.saveToAlbum(this, fileName, null, 100)
                    val msg = Message()
                    msg.apply {
                        what = MsgType.SaveEvent.value
                        // 0 失败 1 成功
                        arg1 = if (uri == null) 0 else 1
                    }
                    handler.sendMessage(msg)
                }
            }
        }

        // 注册对搜索进度消息的广播
        val intentFilter = IntentFilter()
        intentFilter.addAction("com.example.tangramimghiding.PROCESS_BROADCAST")
        val broadcastReceiver = SearchProcessReceiver()
        registerReceiver(broadcastReceiver, intentFilter)

        // 测试用的跳转按钮
        binding.toExtractBtn.setOnClickListener {
            val intent = Intent(this, ExtractActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null) // help gc
        super.onDestroy()
    }

    /**
     * 根据广播的内容对搜索进度条进行更新
     * 收到搜索完成的广播后, 启动嵌入任务的线程
     *@author aris
     *@time 2023/7/17 20:55
    */
    inner class SearchProcessReceiver: BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
                // 常规更新进度
            val process = intent.getDoubleExtra("process", 0.0)
            val s = "finish %.2f %%".format(process)
            binding.processHintTv.text = s
            binding.processBar.progress = process.toInt()
            binding.maskView.setMaskLevel((100.0 - process).toFloat())
                // 搜索完成
            // 完成 搜索 和 保存变换参数 时会传入200.0表示完成
            if (process > 150.0){
                val defaultS = "finish 0.0 %"
                binding.processHintTv.text = defaultS
                binding.processBar.progress = 0
//                binding.maskView.visibility = View.GONE
                binding.processBar.visibility = View.GONE
                binding.processHintTv.visibility = View.GONE
                this@StegoActivity.handler.sendEmptyMessage(MsgType.RefreshExecutable.value)
                // 开始嵌入任务
                val embeddingThread = Thread(EmbeddingAction())
                embeddingThread.start()
                // 嵌入完成后会自动发送允许保存图像的 msg
            }
        }
    }

    /**
     * 更新 /算法执行/ 按钮和 /保存含密图像/ 按钮的可点击属性和显示
     * 保存图像信息的提示
     * 参数可见 [MsgType]
     *@author aris
     *@time 2023/7/18 19:57
    */
    inner class StegoHandler(looper: Looper): Handler(looper){
        override fun handleMessage(msg: Message) {
            when(msg.what){
                // 控制 执行 tangram 算法 按钮是否可以点击的性质和展示
                MsgType.RefreshExecutable.value -> {
                    // 只有当 viewModel 中的 secretImg, containerImg都被初始化
                    // 且 对应的 blocks 都被分割好时 才会使得算法可以被执行
                    if (viewModel.secretImg != null && viewModel.containerImg != null &&
                        viewModel.ifContainerHasSplit && viewModel.ifSecretHasSplit){
                        Log.d("enable", "executeAllow")
                        binding.executeTangramBtn.isClickable = true
                        binding.executeTangramTv.setTextColor(getColor(R.color.gray))
                        // 搜索算法可以执行的时候, 说明也可以重新选择图像了
                        binding.selectSecretImgBtn.isClickable = true
                        binding.selectContainerImgBtn.isClickable = true
                        Log.d(
                            "enable", "containerImg height is %d, width is %d"
                                .format(viewModel.containerImg?.height, viewModel.containerImg?.width)
                        )
                        Log.d(
                            "enable", "secretImg height is %d, width is %d"
                                .format(viewModel.secretImg?.height, viewModel.secretImg?.width)
                        )
                    } else {
                        Log.d("enable", "executeNotAllow")
                        binding.executeTangramBtn.isClickable = false
                        binding.executeTangramTv.setTextColor(getColor(R.color.draker_gray))
                    }
                }
                // 控制 保存含密图像(carrierImg) 按钮是否可以点击的性质和展示
                MsgType.EnableSaveCarrier.value -> {
                    // 允许 save 按钮的执行, 同时更新 carrierImgView
                    binding.saveCarrierBtn.isClickable = true
                    binding.saveCarrierTv.setTextColor(getColor(R.color.gray))
                    binding.carrierImgView.setImageDrawable(BitmapDrawable(resources, viewModel.carrierImg))
                    Toast.makeText(this@StegoActivity, "图像加密完成", Toast.LENGTH_SHORT).show()
                }
                MsgType.SaveEvent.value -> {
                    if (msg.arg1 == 1){
                        // 保存成功
                        Toast.makeText(this@StegoActivity, "成功保存含密图像", Toast.LENGTH_SHORT).show()
                    } else {
                        // 保存失败
                        Toast.makeText(this@StegoActivity, "保存失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * 基于变换参数 和 载体图像, 完成编码和嵌入操作
     * 出于方便考虑, 这里的嵌入算法采用 *低两位* 的 LSB
     *@author aris
     *@time 2023/7/18 21:23
    */
    inner class EmbeddingAction(): Runnable{
        /**
         * 对单个通道对应的变换参数进行编码, 返回编码结果, 编码规则参见 [EnDecodeUtils.encode]
         *@param channelId rgb 通道的编号, 0-r, 1-g, 2-b
         *@author aris
         *@time 2023/7/18 23:04
         */
        inner class EnCodeTask(private val channelId: Int): Callable<String>{
            override fun call(): String {
                viewModel.secretImg?.let {secretImg ->
                    // 并发读 - safe
                    val transRes = TransResDao.getTrans()
                    return EnDecodeUtils.encode(transRes[channelId], secretImg.height, secretImg.width)
                }
                Log.e("encode", "secret img is null")
                return ""
            }
        }
        override fun run() {
                // 编码阶段
            // 开启 3 个线程分别进行 rgb 的编码操作
            val taskRGB = Array(3){ EnCodeTask(it) }
            val futureTaskRGB = Array(3){ FutureTask<String>(taskRGB[it]) }
            for (futureTask in futureTaskRGB){
                Thread(futureTask).start()
            }
            // 阻塞获得编码结果(包含 height, width 的编码)
            val transCodeRGB = Array(3){ futureTaskRGB[it].get() }

            val codeLen = transCodeRGB[0].length
                // 嵌入阶段, (按照自然顺序嵌入即可)
            if (viewModel.containerImg == null){
                Log.e("embedding", "viewModel.containerImg is still null")
            }
            viewModel.containerImg?.let {containerImg->
                val carrierImg = Bitmap.createBitmap(containerImg, 0, 0, containerImg.width, containerImg.height)
                var idx = 0
                for (y in 0 until carrierImg.height){
                    if (idx >= codeLen)
                        break
                    for (x in 0 until carrierImg.width){
                        // 每次取 2 位出来嵌入进一个像素中, (考虑到 rgb 三个通道其实是 6 位)
                        // 直到取完, codeLen 是偶数, 不用害怕
                        if (idx >= codeLen)
                            break
                        val bitRGB =
                            IntArray(3) { ((transCodeRGB[it][idx] - '0') shl 1) or (transCodeRGB[it][idx + 1] - '0') }
                        idx += 2
                        // 0b ff ff ff ff
                        val pixelValue = carrierImg[x, y]
                        // 删除 rgb 的低两位
                        var newPixelValue = pixelValue and (0xfffcfcfc.toInt())
                        // 将取出来的 6 位分别放入空出来的位置
                        newPixelValue =
                            newPixelValue or (bitRGB[0] shl 16) or (bitRGB[1] shl 8) or (bitRGB[2])
                        // 更新 carrierImg
                        carrierImg[x, y] = newPixelValue
                    }
                }
                viewModel.carrierImg = carrierImg
            }
            handler.sendEmptyMessage(MsgType.EnableSaveCarrier.value)
        }
    }


    enum class MsgType(val value: Int){
        // 更新算法执行按钮状态
        RefreshExecutable(1),
        // 更新保存图像按钮状态(如果可以保存, 这时候也可以更新预览图像了)
        EnableSaveCarrier(2),
        // 给出保存成功或者失败的提示
        SaveEvent(3)
    }

}

