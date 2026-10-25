package com.yadea.smartmoto.widget

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.ConvertUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ToastUtils
import com.yadea.smartmoto.R
import com.yadea.smartmoto.databinding.DialogInputSpeedBinding
import com.yadea.smartmoto.utils.MMKVUtils

/**
 * @author jiayu.liu
 */
class InputSpeed(context: Context) : Dialog(context) {
    init {
        DataBindingUtil.bind<DialogInputSpeedBinding>(
            LayoutInflater.from(context)
                .inflate(R.layout.dialog_input_speed, null, false)
        )?.let { it ->
            setContentView(it.root)
            window?.run {
                setBackgroundDrawableResource(R.color.transparent);
                val lp = attributes;
                lp.width = ScreenUtils.getScreenWidth() - ConvertUtils.dp2px(20f)
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                attributes = lp
            }
            ClickUtils.applySingleDebouncing(it.btnConfirm) { view ->
                try {
                    val speed = it.editSpeed.text.toString().toInt()
                    if (speed !in 1..300) {
                        ToastUtils.showShort("请输入 1–300 km/h")
                        return@applySingleDebouncing
                    }
                    MMKVUtils.setSpeed(speed)
                } catch (e: Exception) {
                    e.printStackTrace()
                    ToastUtils.showShort("存储异常，设置失败")
                }
                dismiss()
            }
        }
    }
}