 package com.sunnyweather.signal.logic

import android.bluetooth.*
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import com.sunnyweather.signal.MyApplication
import java.util.*

object BluetoothHelper {

    private const val TARGET_DEVICE = "Mi Band 3" //目标设备 就决定是你了
    private const val STATE_CONNECT_FAILED = 0//蓝牙未开启
    private const val STATE_SCAN_FAILED = 1    //扫描失败
    private const val STATE_LINK_OK = 2
    private lateinit var mGatt: BluetoothGatt  //用于与目标设备通信蓝牙通信的Gatt
    private var isScanning = false //是否正在扫描标志，实际上蓝牙扫描时会发出系统广播 ACTION_DISCOVERY_FINISHED  可以尝试使用BroadcastReceiver修改其值
    private val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()     //获取系统默认蓝牙适配器
    var mState = -1


    fun checkBTSwitch():Boolean{
        return mBluetoothAdapter.isEnabled
    }

    //开始连接设备
    fun startLinkToDevice():Int{

        Log.d("标记","开始扫描")

        Log.d("标记","第一步")
        //第一步，扫描周围可供链接的BLE设备
        val scanResult = startLeScanTargetDevice()
        if (scanResult == null) {
            mState = STATE_SCAN_FAILED
            return mState
        }
        Log.d("标记","第二步")
        //第二步,开始与目标设备进行Gatt连接
        mGatt = startConnectGattToTargetDevice(scanResult)
        return STATE_LINK_OK
    }

    //断开连接设备
    fun stopLinkToDevice(){

        /**
         * 手机连接蓝牙设备数量有上限，所以BLE设备断开连接时
         * 应当立即解除与手机匹配
         */

        mGatt.disconnect()
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
         * 获取设备认证的server的UUID="0000fee1-0000-1000-8000-00805f9b34fb"
         * 获取设备认证的characteristic的UUID="00000009-0000-3512-2118-0009af100700"
         *
         */
        val verificationOfServerUUID = "0000fee1-0000-1000-8000-00805f9b34fb"
        val verificationOfCharacteristicUUID = "00000009-0000-3512-2118-0009af100700"
        val verificationOfDescriptorUUID = "00002902-0000-1000-8000-00805f9b34fb"
        //创建callback实例
        val mBluetoothGattCallback = object: BluetoothGattCallback() {

            /**第一回调
             * 连接状态发生改变调用该回调函数
             * 回调完成通过调用gatt.discoverServices（）
             * 回调onServicesDiscovered（）
             * */
            override fun onConnectionStateChange(
                gatt: BluetoothGatt?, //用于通讯的Gatt对象
                status: Int,          //连接结果
                newState: Int         //新的连接状态
            ) {
                super.onConnectionStateChange(gatt, status, newState)
                Log.d("标记","onConnectionStateChange:status: $status, newState: $newState")
                if (status == 0 ){
                    val servers = gatt?.discoverServices()
                    Log.d("标记","servers is not null？$servers")
                }
            }
            /**第二回调
             * 搜索到目标设备Server列表回调此函数
             * 该回调中可完成对目标characteristic的查找
             * 回调完成通过调用gatt.readCharacteristic()
             * 回调onCharacteristicRead()
             * */
            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                //目标设备所有的服务存储再serverList中
                val serverList = gatt?.services

                //获取认证设备的service
                val verificationService = gatt?.getService(UUID.fromString(verificationOfServerUUID))
                //获取认证设备的characteristic
                val verificationCharacteristic = verificationService?.getCharacteristic(UUID.fromString(verificationOfCharacteristicUUID))
                Log.d("标记","verificationCharacteristic:${verificationCharacteristic?.uuid}")
                //从该characteristic中读取特征值
                if (gatt != null && verificationCharacteristic != null) {

                    /**
                     * 开始尝试与小米手环建立连接通讯
                     * 根据资料第一步以要求response的方式向descriptor写入0x01,0x00
                     * 不知道怎么要求response 直接写入数据康康会发生什么
                     * */

                    verificationCharacteristic.writeType
                    //获取认证Characteristic的Descriptor
                    val descriptor = verificationCharacteristic.getDescriptor(UUID.fromString(verificationOfDescriptorUUID))
                    //尝试直接向其中写入0x01，0x00
                    descriptor.value = byteArrayOf(0x01,0x00)
                    gatt.writeDescriptor(descriptor)
                    //调用该函数，即可回调onCharacteristicRead
                   // val readable = gatt.readCharacteristic(verificationCharacteristic)
                }

            }


            /**
             * 当读取到目标characteristic，回调该函数
             * 搜索认证设备characteristic时发现
             * 该特征中共有：1个描述符,descriptor:00002902-0000-1000-8000-00805f9b34fb
             * 通过readDescriptor()回调至 onDescriptorWrite
             * */
            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicRead(gatt, characteristic, status)
                if (characteristic != null) {
                    //可以读取该characteristic中所有descriptor
                    val descriptors = characteristic.descriptors
                    //认证设备时的uuid已经找到，则
                    val descriptor = characteristic.getDescriptor(UUID.fromString(verificationOfDescriptorUUID))
                    Log.d("标记","已读取数据：${descriptor.value}")

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


            /** 测试中
             * setCharacteristicNotification()回调至
             * onCharacteristicChanged()中，若成功回调则读写成功
             */
            override fun onDescriptorWrite(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor?,
                status: Int
            ) {
                super.onDescriptorWrite(gatt, descriptor, status)
                if (status == 0 && descriptor!= null && gatt != null) {

                    //以不要求response的方式写入 0x02,0x00数据
                    val command2 = byteArrayOf(0x01,0x00,0x11,0x12,0x13,0x04,0x05,0x16,0x17,0x08,0x11,0x12,0x13,0x04,0x05,0x16,0x17,0x08)

                    val command = byteArrayOf(0x02,0x00)
                    val characteristic = descriptor.characteristic
                    characteristic.writeType = WRITE_TYPE_NO_RESPONSE
                    characteristic.value = command2
                    gatt.writeCharacteristic(characteristic)
                    gatt.setCharacteristicNotification(characteristic,true)

                    //需要用Gatt.notification
                    gatt.setCharacteristicNotification(characteristic,true)

                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                if (gatt != null && characteristic != null) {
                    Log.d("标记",Arrays.toString(characteristic.value))
                }
            }

        }


        return targetDevice.connectGatt(MyApplication.mContext,false, mBluetoothGattCallback)

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
            Log.d("标记","等待扫描中")
            Thread.sleep(1000)
        }
        return scanResult
    }

}