package com.sunnyweather.signal.UI

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.sunnyweather.signal.R
import com.sunnyweather.signal.logic.BluetoothHelper
import kotlinx.android.synthetic.main.activity_band.*

class BandActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_band)
        vibrate.setOnClickListener{
            BluetoothHelper.vibrateSmartBand()
        }
    }
}