package com.example.tangramimghiding.ui

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.util.Log
import android.widget.Toast
import com.example.tangramimghiding.logic.dao.BlocksDao
import com.example.tangramimghiding.logic.dao.TransResDao
import com.example.tangramimghiding.logic.model.SettingParameters
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RecursiveTask
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random

class SearchTransService : Service() {
    private val binder = SearchBinder()
    // 并发读-safe
    lateinit var containerBlocks: IntArray
    lateinit var secretBlocks: IntArray

    companion object{
        @Volatile
        lateinit var searchResRGB: Array<IntArray>
        val curFinishCnt = AtomicInteger(0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        thread {
            curFinishCnt.getAndSet(0)
            containerBlocks = BlocksDao.getBlocks("containerBlocks")
            secretBlocks = BlocksDao.getBlocks("secretBlocks")
            intent?.let {
                searchResRGB = Array(3) { IntArray(secretBlocks.size / SettingParameters.blockEleCnt * 4) }
                // 不满足条件
                if (containerBlocks.isEmpty() || secretBlocks.isEmpty()){
                    Toast.makeText(this@SearchTransService, "Empty Array Got", Toast.LENGTH_SHORT).show()
                    Log.e("search", "containerBlocks size is %d, secretBlocks size is %d".format(containerBlocks.size, secretBlocks.size))
                    stopSelf()
                } else { // 满足条件
                    val pool = ForkJoinPool()
                    Log.d("poolSize", pool.parallelism.toString())
                    val startTime = System.currentTimeMillis()
                    pool.invoke(TrangramSearchTask(0, secretBlocks.size))
                    val endTime = System.currentTimeMillis()
                    Log.d("timeCost", (endTime - startTime).toString())
                    pool.shutdown()
                    // save search res
                    TransResDao.putTrans(searchResRGB)
                    // 表示 搜索 和 保存 已经完成
                    val process = 200.0
                    val finishIntent = Intent("com.example.tangramimghiding.PROCESS_BROADCAST")
                    finishIntent.putExtra("process", process)
                    finishIntent.setPackage(packageName)
                    sendBroadcast(finishIntent)
                }
            }
            stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }


    /**
     * 定义搜索任务的 Task
     * 从 viewModel 中读 blocks 数据
     * 搜索得到变换结果, 并利用 EnDeCodeUtils 将搜索结果编码 (搜到一个编码一个)
     *@author aris
     *@time 2023/7/14 21:48
     */
    inner class TrangramSearchTask(private val startIdx: Int, private val endIdx: Int)
        : RecursiveTask<Boolean>() {
        override fun compute(): Boolean {
            // 中止条件
            if (endIdx - startIdx <= SettingParameters.blockEleCnt * SettingParameters.taskEndRange){
                for (secIdx in startIdx until endIdx step SettingParameters.blockEleCnt){
                    // 最优变化判断和存储
                    val findRGB = BooleanArray(3){ false }
                    val minLossRGB = DoubleArray(3){Int.MAX_VALUE.toDouble()}
                    val resRGB = Array(3){IntArray(0) }
                    // 秘密块初始化
                    val secPixes = IntArray(SettingParameters.blockEleCnt){ secretBlocks[it + secIdx] }
                    val secR = IntArray(secPixes.size){ Color.red(secPixes[it]) }
                    val secG = IntArray(secPixes.size){ Color.green(secPixes[it]) }
                    val secB = IntArray(secPixes.size){ Color.blue(secPixes[it]) }
                    val secRGB = arrayOf(secR, secG, secB)
                    // 搜索最优变化并存入到 resRGB 里面
                    // loc
                    for (rowConIdx in secIdx until secIdx + SettingParameters.searchRange * SettingParameters.blockEleCnt
                            step SettingParameters.blockEleCnt){
                        // ∈ [0, searchRange)
                        val loc = (rowConIdx - secIdx) / SettingParameters.blockEleCnt
                        if (findRGB[0] && findRGB[1] && findRGB[2])
                            break
                        // 载体块初始化
                        val conIdx = rowConIdx % containerBlocks.size
                        val conPixes = IntArray(SettingParameters.blockEleCnt){ containerBlocks[it + conIdx] }
                        val conR = IntArray(conPixes.size){ Color.red(conPixes[it]) }
                        val conG = IntArray(conPixes.size){ Color.green(conPixes[it]) }
                        val conB = IntArray(conPixes.size){ Color.blue(conPixes[it]) }
                        val conRGB = arrayOf(conR, conG, conB)
                        // 分别搜索 RGB 三个通道的变化
                        for (i in findRGB.indices){
                            if (findRGB[i])
                                continue
                            val minValue = conRGB[i].min()
                            val con = IntArray(conRGB[i].size){conRGB[i][it] - minValue}
                            val conSum = con.sum()
                            val conMax = con.max()
                            val sec = secRGB[i]
                            val secSum = sec.sum()
                            // rm
                            for (rm_idx in 0 until SettingParameters.rmChoose.size){
                                if (findRGB[i]) break
                                val rm = SettingParameters.rmChoose[rm_idx]
                                // k
                                for (k_idx in SettingParameters.kChoose.indices){
                                    if (findRGB[i]) break
                                    val k = SettingParameters.kChoose[k_idx] * 1.0 / 10
                                    // b
                                    var b = ((secSum - k * conSum) / SettingParameters.blockEleCnt).toInt()
//                                    var b = ((sec.max() - k * con.max())).toInt()
                                    b = min(b, (floor(255 - k * conMax)).toInt())
                                    if (b < -255)
                                        continue
                                    if (b > 255 || b < -255){
                                        Log.d("searchInteresting", "unexpected b value: [%d]".format(b))
                                    }
                                    // 限制 b 的取值在 -255 到 255 之间
                                    b = if (b > 255) 255 else if (b < -255) -255 else b
                                    val transCon = IntArray(con.size){(con[rm[it]] * k + b).toInt()}
                                    var isAllowed = true
                                    for (idx in transCon.indices){
                                        if (transCon[idx] > 255 || transCon[idx] < 0){
                                            isAllowed = false
                                            break
                                        }
                                        transCon[idx] = if (transCon[idx] > 255) 255 else if (transCon[idx] < 0) 0 else transCon[idx]
                                    }
                                    if (!isAllowed)
                                        continue
                                    // 统计变换参数
                                        // 直接存储真实的位置 (测试用)
//                                    val curRes = intArrayOf(conIdx, rm_idx, k_idx, b / (1 shl SettingParameters.b_offset))
                                        // 存储相对位置
                                    val curRes = intArrayOf(loc, rm_idx, k_idx, b / (1 shl SettingParameters.b_offset))
                                    // MSE loss
                                    val loss = 1.0*IntArray(transCon.size){(transCon[it] - sec[it]) * (transCon[it] - sec[it])}.sum() / transCon.size
                                    if (loss <= SettingParameters.searchThreshold){
                                        findRGB[i] = true
                                        minLossRGB[i] = loss
                                        resRGB[i] = curRes
                                    } else {
                                        if (loss < minLossRGB[i]){
                                            minLossRGB[i] = loss
                                            resRGB[i] = curRes
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // 把结果存放到 searchResRGB 里面
                    for (i in resRGB.indices){
                        val res = resRGB[i]
                        System.arraycopy(res, 0, searchResRGB[i], secIdx / SettingParameters.blockEleCnt*4, 4)
                    }
                    if(secIdx % 500 == 0)
                        Log.d("search", "task id [%d] finish with [%f] [%f] [%f] loss"
                            .format(secIdx / SettingParameters.blockEleCnt, minLossRGB[0], minLossRGB[1], minLossRGB[2]))
                }
                curFinishCnt.addAndGet((endIdx - startIdx) / SettingParameters.blockEleCnt)
                // 刷新阈值, 搜索的块的数量超过这个阈值就刷新一次
                // 发送广播
                val curFinishCntValue = curFinishCnt.get()
                if(curFinishCntValue == secretBlocks.size / SettingParameters.blockEleCnt || true){
                    val process = 100.0 * curFinishCntValue / (secretBlocks.size / SettingParameters.blockEleCnt)
                    // 通过广播通知搜索进度
                    val intent = Intent("com.example.tangramimghiding.PROCESS_BROADCAST")
                    intent.putExtra("process", process)
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                }
                return true
            } else if ((endIdx - startIdx) % SettingParameters.blockEleCnt == 0){
                // 任务分割
                val midIdx = startIdx + SettingParameters.blockEleCnt * SettingParameters.taskEndRange
                val subTask1 = TrangramSearchTask(startIdx, midIdx)
                val subTask2 = TrangramSearchTask(midIdx, endIdx)
                val r1 = subTask1.fork()
                val r2 = subTask2.compute()
                return r1.get() && r2
            }
            return false
        }
    }

    inner class SearchBinder: Binder(){
        fun getProcess() = 1.0 * curFinishCnt.get() / (secretBlocks.size / SettingParameters.blockEleCnt)
    }

}