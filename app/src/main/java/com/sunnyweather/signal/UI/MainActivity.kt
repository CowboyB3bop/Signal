package com.sunnyweather.signal.UI

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.postAtTime
import androidx.core.os.postDelayed
import com.sunnyweather.signal.R
import com.sunnyweather.signal.logic.BluetoothHelper
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /**权限检查，BLE蓝牙使用需要ACCESS_FINE_LOCATION权限，
         * 无该权限无法触发
         * BluetoothHelper.scanCallback的onScanResult回调
         */
        val locationPermission = ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)
        if (locationPermission != PackageManager.PERMISSION_GRANTED){
            /** 未理解部分 **/
/*            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION))
                Toast.makeText(this, "动态请求权限", Toast.LENGTH_LONG).show();*/
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1);
        }

    }
    //权限申请回调
    override fun onRequestPermissionsResult(
        requestCode: Int,     //申请号
        permissions: Array<out String>,   //申请权限数组，同时申请多个权限顺序储存再该数组中
        grantResults: IntArray      // 申请结果   与上述权限数组一一对应
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode){
            1 -> when(grantResults[0]) {
                PackageManager.PERMISSION_GRANTED -> Toast.makeText(this,"权限获取成功",Toast.LENGTH_SHORT).show()
                PackageManager.PERMISSION_DENIED -> {
                    Toast.makeText(this,"权限获取失败",Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
    override fun onResume() {

        Log.d("标记","onResume is onCall")
        super.onResume()
        //检查是否开启蓝牙

        val handle = Handler()
        handle.postDelayed(2000){
            thread {
                Log.d("标记","thread ing")
                if (BluetoothHelper.checkBTSwitch()) {
                    val result = BluetoothHelper.startLinkToDevice()
                    Log.d("标记","result = $result")
                } else {
                    requestBTSwitchOn()
                }
            }
        }

    }

    //程序退出，释放蓝牙连接
   override fun onDestroy() {
        super.onDestroy()
        BluetoothHelper.stopLinkToDevice()
    }

    //以AlertDialog的形式申请蓝牙权限
    private fun requestBTSwitchOn() {
        runOnUiThread{
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("需要使用蓝牙权限")
                .setCancelable(false)
                .setNegativeButton("取消") { _,_ ->
                    Toast.makeText(this,"您已拒绝",Toast.LENGTH_SHORT).show()
                }
                .setPositiveButton("确定") { _,_ ->
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(intent,1)
                }
                .show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            1 -> {
                when (resultCode) {
                    Activity.RESULT_OK -> Toast.makeText(this,"打开成功",Toast.LENGTH_SHORT).show()
                    Activity.RESULT_CANCELED -> Toast.makeText(this,"打开失败",Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

}