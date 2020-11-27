 package com.sunnyweather.signal.logic

import android.bluetooth.*
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import com.sunnyweather.signal.MyApplication
import java.util.*

object BluetoothHelper {

    private const val TAG = "标记"

    private val verificationOfServerUUID = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")              //获取设备认证的server的UUID
    private val verificationOfCharacteristicUUID = UUID.fromString("00000009-0000-3512-2118-0009af100700")      //获取设备认证的characteristic的UUID
    private val verificationOfDescriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")          //获取设备认证的descriptor
    private val IMMEDIATE_ALERT_CHAR_UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb")             //手环震动serviceUUID
    private const val TARGET_DEVICE = "HUAWEI WATCH GT 2-C1C" //目标设备 就决定是你了  Mi Smart Band 4
    const val STATE_SCAN_FAIL = 1    //扫描失败
    const val STATE_CONNECT_OK = 2        //蓝牙连接成功
    const val STATE_CONNECT_FAIL = -1    //认证失败
    private lateinit var mGatt: BluetoothGatt  //用于与目标设备通信蓝牙通信的Gatt
    private var isScanning = false //是否正在扫描标志，实际上蓝牙扫描时会发出系统广播 ACTION_DISCOVERY_FINISHED  可以尝试使用BroadcastReceiver修改其值
    private val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()     //获取系统默认蓝牙适配器
    private var mState = -1    //连接结果
    //创建callback实例
    private val mBluetoothGattCallback = object: BluetoothGattCallback() {
        /**第一回调
         * 连接状态发生改变调用该回调函数
         * 回调完成通过调用gatt.discoverServices（）
         * 回调onServicesDiscovered（）
         * */
        override fun onConnectionStateChange(
            gatt: BluetoothGatt, //用于通讯的Gatt对象
            status: Int,          //连接结果
            newState: Int         //新的连接状态
        ) {

            super.onConnectionStateChange(gatt, status, newState)
            Log.i(TAG,"onConnectionStateChange    status : $status, newState: $newState")
            when (status) {

                0 ->  {
                    gatt.discoverServices()
                }  //搜索目标设备服务列表

            }

        }
        /**第二回调
         * 搜索到目标设备Server列表回调此函数
         * 该回调中可完成对目标characteristic的查找
         * 回调完成通过调用gatt.readCharacteristic()
         * 回调onCharacteristicRead()
         * */
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            //目标设备所有的服务存储再serverList中
            val serverList = gatt.services

        }


        /**
         * 当读取到目标characteristic，回调该函数
         * */
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            when (characteristic.uuid) {
                verificationOfCharacteristicUUID ->{
                    Log.d(TAG,"设备认证中")
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (gatt != null && characteristic != null) {
                Log.d("标记","status: $status,value:${Arrays.toString(characteristic.value)}")
            }
        }


        /**
         *  调用gatt.writeDescriptor回调至此函数
         *  可查看是否成功写入
         */
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)

            if (descriptor.uuid == verificationOfDescriptorUUID   //如果是设备认证descriptor
                && status == 0            //且成功写入
            ) {
                Log.d(TAG,"descriptor write ok")
            }

        }


        /**
         * 当调用setCharacteristicNotification()
         * 收到响应，回调此函数
         * **/
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic.uuid == verificationOfCharacteristicUUID){            //如果该响应为设备认证发出
                /** 这里将手环发回数据先打印出来康康 **/
                Log.i(TAG,Arrays.toString(characteristic.value))
                if (characteristic.value.equals(byteArrayOf(0x01,0x02))){
                    mState = STATE_CONNECT_OK
                }
            }
        }

    }

    //检查系统是否开启蓝牙
    fun checkBTSwitch():Boolean{
        return mBluetoothAdapter.isEnabled
    }

    //开始连接设备
    fun startLinkToDevice():Int{

        Log.d(TAG,"第一步")
        //第一步，扫描周围可供链接的BLE设备
        val scanResult = startLeScanTargetDevice()
        if (scanResult == null) {
            mState = STATE_SCAN_FAIL
            return mState
        }
        Log.d("标记","第二步")
        //第二步,开始与目标设备进行Gatt连接
        //mGatt = startConnectGattToTargetDevice(scanResult)

        //偷跑荣耀手环
        mGatt = startHonorBandConnect(scanResult)
        return mState
    }

    private fun startHonorBandConnect(scanResult: BluetoothDevice): BluetoothGatt{
        val gatt = scanResult.connectGatt(MyApplication.mContext,false, mBluetoothGattCallback)    //向目标设备发起连接，并传入回调函数
        if (gatt.device.bondState == BOND_BONDED) {           //如果目标设备连接成功
            for (service in gatt.services) {
                Log.d(TAG,"service: ${service.type},uuid: ${service.uuid}")
            }
        }
        return gatt
    }

    //让手环开始震动
    fun vibrateSmartBand(){
        //网上直接搜索全部服务列表从中找震动uuid挺笨的，搜索一次所有服务，从中找到包含震动的服务
        for (service in mGatt.services) {
            for (characteristic in service.characteristics){
                if (characteristic.uuid == IMMEDIATE_ALERT_CHAR_UUID) {
                    Log.d(TAG,"包含震动服务的uuid为：${service.uuid}")
                    return
                }
            }
        }
    }

    //断开连接设备
    fun stopLinkToDevice(){

        /**
         * 手机连接蓝牙设备数量有上限，所以BLE设备断开连接时
         * 应当立即解除与手机匹配
         */

        mGatt.close()
        Log.d("标记", "设备连接状态${mGatt.getConnectionState(mGatt.device)}")

    }

    //连接目标设备调用方法
    private fun startConnectGattToTargetDevice(targetDevice: BluetoothDevice): BluetoothGatt{

        /**
         * BLE蓝牙连接采用Gatt协议进行连接通信
         * 因此需要调用
         * BluetoothDevice.connectGatt(Context context, boolean autoConnect,
         *      BluetoothGattCallback callback) : BluetoothGatt
         * 方法
         * 其中参数callback为BluetoothGattCallback的一个实例
         *
         */

        val gatt = targetDevice.connectGatt(MyApplication.mContext,false, mBluetoothGattCallback)    //向目标设备发起连接，并传入回调函数
        if (gatt.device.bondState == BOND_BONDED) {           //如果目标设备连接成功
            val service = gatt.getService(
                verificationOfServerUUID)                    //获取认证服务service
            val characteristic = service.getCharacteristic(
                verificationOfCharacteristicUUID)            //获取认证服务characteristic
            val descriptor = characteristic.getDescriptor(
                verificationOfDescriptorUUID)                //获取认证服务descriptor
            /**开始认证服务**/
            /** 第一步，以要求response的方式向设备认证descriptor中写入 0x01,0x00 **/
            descriptor.value = byteArrayOf(0x01,0x00)
            val result = gatt.writeDescriptor(descriptor)     //调用该函数向descriptor中写入数据（/命令），会回调到gattCallback的onDescriptorWrite()中
            if (result){
                /** 第二步，向手环中写入16位随机数绑定设备
                 * 注：如果已经绑定过，可直接通过加密算法认证，需加一层判定，该部分未实现 **/
                val command = byteArrayOf(
                    0x01,0x00,0x11,0x12,0x13,0x04,0x05,0x16,0x17,
                    0x08,0x11,0x12,0x13,0x04,0x05,0x16,0x17,0x08)      //该18位byte数组  以0x0x,0x00开头，表示手环绑定新设备
                characteristic.writeType = WRITE_TYPE_NO_RESPONSE      //不要求回复写入
                characteristic.value = command
                gatt.writeCharacteristic(characteristic)               //向手环发送数据
                gatt.setCharacteristicNotification(characteristic,true)   //需要设置一个监听，为手环绑定结果，若回复
            }
        }
        return gatt
    }

    //扫描目标设备调用方法
    private fun startLeScanTargetDevice(): BluetoothDevice? {
        /**
         * 蓝牙5.0以下采用
         *         mBluetoothAdapter.startLeScan(callback: Callback)
         *         之前采用该方法会报过时警告
         *
         * 5.0以上
         *         mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
         *         mBluetoothLeScanner.startScan(ScanCallback mScanCallback);
         */
        //扫描服务需要一个特定的ScanCallback实例作为参数
        var scanResult: BluetoothDevice? = null
        val mBluetoothLeScanner = mBluetoothAdapter.bluetoothLeScanner
        val mScanCallback = object: ScanCallback(){

            //重写此函数，执行发现目标设备的逻辑
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result != null && result.device.name != null)  Log.d("标记","onScanResult : ${result.device.name}")
                if (result?.device?.name == TARGET_DEVICE) { //已经找到目标设备，此时应该停止扫描

                    Log.d("标记","成功找到目标设备:$TARGET_DEVICE")
                    isScanning = false
                    mBluetoothLeScanner.stopScan(this)

                    /**
                     * 业务逻辑
                     */
                    scanResult = result.device
                }
            }


            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.d("标记","扫描失败ec:$errorCode")
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                Log.d("标记","onBatchScanResults")
            }

        }
        /**
        Looper.prepare()
        val mHandler = Handler()    //线程通信Handler
        mHandler.postDelayed(thread {
        if (isScanning) mBluetoothLeScanner.stopScan(mScanCallback)
        },5000)
         */
        //扫描设备为耗电操作，因此需要设定蓝牙扫描最大时长，到时停止扫描
        val timer = Timer()
        val timerTask = object :TimerTask() {
            override fun run() {
                Log.i("标记","stop!")
                isScanning = false
                mBluetoothLeScanner.stopScan(mScanCallback)
            }
        }
        //设置定时器逻辑，5s后执行停止扫描任务
        timer.schedule(timerTask, 5000)
        //开启扫描，将标志为isScanning设为true
        isScanning = true
        mBluetoothLeScanner.startScan(mScanCallback)
        while (isScanning){
            Log.d(TAG,"等待扫描中")
            Thread.sleep(1000)
        }
        return scanResult
    }

}