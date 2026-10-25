package com.yadea.smartmoto

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import com.blankj.utilcode.util.Utils
import com.castiel.common.BaseApp


/**
 * @author jiayu.liu
 */
class MyApp : BaseApp() {
    private lateinit var mMockReceiver: MockReceiver
    override fun onCreate() {
        super.onCreate()

        Utils.init(this)
        initReceiver()
    }


    private fun initReceiver() {
        mMockReceiver = MockReceiver()
        val intentFilter = IntentFilter()
        intentFilter.addAction(MockReceiver.MOCK_ACTION)
        val permission = "$packageName.permission.CONTROL_MOCK_LOCATION"
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(mMockReceiver, intentFilter, permission, null, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(mMockReceiver, intentFilter, permission, null)
        }
    }
}