package com.yadea.smartmoto.ui

import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.castiel.common.base.BaseActivity
import com.castiel.common.base.BaseViewModel
import com.castiel.common.recycler.decoration.VerticalItemDecoration
import com.yadea.smartmoto.R
import com.yadea.smartmoto.adaper.SettingAdapter
import com.yadea.smartmoto.databinding.ActivitySettingBinding
import com.yadea.smartmoto.databinding.ItemSettingBinding
import com.yadea.smartmoto.model.SettingMsgModel
import com.yadea.smartmoto.utils.MMKVUtils
import com.yadea.smartmoto.widget.InputLocationVibrationDialog

/**
 * @author jiayu.liu
 */
class SettingActivity : BaseActivity<ActivitySettingBinding, BaseViewModel>() {
    private var mAdapter: SettingAdapter? = null
    private val mTitle = arrayOf(
        "模拟定位震动功能",
        "模拟导航经纬度绑路功能",
    )
    private val mMsg = arrayOf(
        "定位开启后，自动微调参数(方向、经纬度等)，当前随机半径范围为:",
        "模拟导航计算增加绑路功能，提高精度。注意：性能有所降低！",
    )

    override fun initViewModel(): Class<BaseViewModel> {
        return BaseViewModel::class.java
    }

    override fun getLayout(): Int {
        return R.layout.activity_setting
    }

    override fun initView() {
        findViewById<android.view.View>(R.id.btn_root_engine).setOnClickListener {
            startActivity(android.content.Intent(this, RootEngineActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btn_map_provider).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("切换地图服务")
                .setMessage("切换将停止当前模拟并清空本次路线；地点历史保留。是否继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("停止并切换") { _, _ ->
                    startActivity(android.content.Intent(this, MapEntryActivity::class.java).putExtra("switch_map", true))
                }.show()
        }
        findViewById<android.view.View>(R.id.btn_privacy_reset).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("撤回同意并清除地点历史")
                .setMessage("将停止模拟、清除地点历史和地图偏好并关闭应用。已保存的轨迹文件不会删除。下次打开需重新同意。")
                .setNegativeButton("取消", null).setPositiveButton("撤回并退出") { _, _ ->
                    stopService(android.content.Intent(this, com.yadea.smartmoto.server.GpsService::class.java))
                    com.yadea.smartmoto.manager.SearchManager.INSTANCE.cancel()
                    MMKVUtils.clearDataList(MMKVUtils.LOCATION_LIST_KEY)
                    MMKVUtils.clearDataList(MMKVUtils.MULTIPLE_NAVI_LIST_KEY)
                    com.yadea.smartmoto.maps.MapPreferences.prefs(this).edit().clear().commit()
                    finishAffinity()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 300)
                }.show()
        }

        dataBinding.recycler.layoutManager = LinearLayoutManager(this)
        mAdapter = SettingAdapter()
        dataBinding.recycler.adapter = mAdapter
        dataBinding.recycler.addItemDecoration(
            VerticalItemDecoration(
                this,
                R.drawable.shape_item_line_verticall,
                10f,
                0f
            )
        )

        mAdapter?.listener = object : SettingAdapter.OnItemListener {
            override fun onItemSwitch(
                dataBinding: ItemSettingBinding?,
                model: SettingMsgModel,
                isChecked: Boolean
            ) {
                when (model.title) {
                    mTitle[0] -> {
                        MMKVUtils.saveSettingConfig(
                            MMKVUtils.KEY_LOCATION_VIBRATION,
                            isChecked
                        )
                        dataBinding?.isShowSetting = isChecked
                    }

                    mTitle[1] -> MMKVUtils.saveSettingConfig(
                        MMKVUtils.KEY_NAVI_ROUTE_BINDING,
                        isChecked
                    )

                    else -> {}
                }
            }

            override fun onSettingClick(model: SettingMsgModel) {
                when (model.title) {
                    mTitle[0] -> {
                        InputLocationVibrationDialog(
                            this@SettingActivity,
                            MMKVUtils.getLocationVibrationValue(),
                            MMKVUtils.getLocationFrequencyValue()
                        ).apply {
                            mDismissListener = object :
                                InputLocationVibrationDialog.LocationVibrationDismissListener {
                                override fun onDismiss() {
                                    Toast.makeText(
                                        this@SettingActivity,
                                        "频率设置需要等待上次频率触发后生效",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    updateData()
                                }
                            }
                            show()
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    override fun initData() {
        updateData()
    }

    private fun updateData() {
        val settingModel = MMKVUtils.getSettingModel()
        mAdapter?.submitList(
            mutableListOf(
                SettingMsgModel(
                    title = mTitle[0],
                    msg = "${mMsg[0]}${MMKVUtils.getLocationVibrationValue()}m，当前震动频率:${MMKVUtils.getLocationFrequencyValue()}s",
                    isSwitch = settingModel.isLocationQuiver
                ),
                SettingMsgModel(
                    title = mTitle[1],
                    msg = mMsg[1],
                    isSwitch = settingModel.isNaviRouteBinding
                )
            )
        )
    }

    override fun initObserver() {
    }


}