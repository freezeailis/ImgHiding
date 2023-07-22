package com.example.tangramimghiding.logic.dao

import com.example.tangramimghiding.HidingApplication
import com.google.gson.Gson
import java.io.*

/**
 * 直接传输编码后的字符串
 * 仅作为测试 编解码, 嵌入提取 时获得真值使用
 *@author aris
 *@time 2023/7/19 23:35
*/
object CodeStringDao {
    private val gson = Gson()
    private const val name = "transCodeCache"

    fun putCodes(codes: Array<String>){
        val path = HidingApplication.context.cacheDir.path + name
        val s = gson.toJson(codes)
        try {
            val fos = FileOutputStream(path)
            BufferedWriter(OutputStreamWriter(fos)).use {
                it.write(s)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getCodes(): Array<String>{
        val path = HidingApplication.context.cacheDir.path + name
        val content = StringBuilder()
        try {
            val fis = FileInputStream(path)
            BufferedReader(InputStreamReader(fis)).use { reader ->
                reader.forEachLine {
                    content.append(it)
                }
            }
        } catch (e: IOException){
            e.printStackTrace()
        }
        return gson.fromJson(content.toString(), Array<String>::class.java)
    }


}