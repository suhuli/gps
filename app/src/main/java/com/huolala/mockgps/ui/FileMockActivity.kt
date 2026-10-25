package com.huolala.mockgps.ui

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.huolala.mockgps.utils.TrackParser
import java.io.File
import android.text.TextUtils
import android.view.View
import com.baidu.mapapi.model.LatLng
import com.blankj.utilcode.util.ClickUtils
import com.blankj.utilcode.util.FileIOUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.castiel.common.base.BaseActivity
import com.castiel.common.base.BaseViewModel
import com.huolala.mockgps.R
import com.huolala.mockgps.databinding.ActivityFileBinding
import com.huolala.mockgps.manager.SearchManager
import com.huolala.mockgps.model.MockMessageModel
import com.huolala.mockgps.model.NaviType
import com.huolala.mockgps.utils.WarnDialogUtils
import com.huolala.mockgps.utils.LocationUtils
import com.huolala.mockgps.utils.MMKVUtils
import com.huolala.mockgps.utils.Utils
import com.huolala.mockgps.widget.HintDialog
import com.huolala.mockgps.widget.NaviPathDialog
import com.huolala.mockgps.widget.NaviPopupWindow
import com.huolala.mockgps.widget.PointTypeDialog

/**
 * @author jiayu.liu
 */
class FileMockActivity : BaseActivity<ActivityFileBinding, BaseViewModel>(), View.OnClickListener {
    private var importing = false
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            viewModel.loading.value = true
            try {
                val file = withContext(Dispatchers.IO) {
                    val destination = File(getExternalFilesDir("nav_path"), "import-${System.currentTimeMillis()}.txt")
                    try {
                        contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { "无法读取所选文件" }
                            destination.outputStream().use { output ->
                                var size = 0
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    size += count
                                    require(size <= TrackParser.MAX_BYTES) { "文件超过 5 MB" }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        TrackParser.parse(destination.readText())
                        destination
                    } catch (e: Exception) { destination.delete(); throw e }
                }
                dataBinding.edFile.setText(file.absolutePath)
                ToastUtils.showShort("轨迹已导入，请确认原始坐标类型后启动")
            } catch (e: Exception) { ToastUtils.showLong(e.message ?: "文件导入失败") }
            finally { viewModel.loading.value = false }
        }
    }
    private var mPointType = LocationUtils.gcj02
    override fun initViewModel(): Class<BaseViewModel> {
        return BaseViewModel::class.java
    }

    override fun getLayout(): Int {
        return R.layout.activity_file
    }

    override fun initView() {
        KeyboardUtils.clickBlankArea2HideSoftInput()


        ClickUtils.applySingleDebouncing(dataBinding.ivNaviSetting, this)
        ClickUtils.applySingleDebouncing(dataBinding.ivWarning, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnFile, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnStartNavi, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnCreatePath, this)
        ClickUtils.applySingleDebouncing(dataBinding.btnPointType, this)

        dataBinding.pointType = "经纬度类型：$mPointType"
    }

    override fun initData() {

    }

    override fun initObserver() {

    }

    override fun onClick(v: View?) {
        when (v) {
            dataBinding.ivWarning -> {
                HintDialog(
                    this@FileMockActivity,
                    "数据格式要求",
                    getString(R.string.file_navi_hint)
                ).show()
            }

            dataBinding.btnCreatePath -> {
                //生成路径文件
                startActivity(Intent(this, CalculateRouteActivity::class.java))
            }

            dataBinding.ivNaviSetting -> {
                NaviPopupWindow(this).apply {
                    show(dataBinding.ivNaviSetting)
                }
            }

            dataBinding.btnFile -> {
                androidx.appcompat.app.AlertDialog.Builder(this).setTitle("选择轨迹文件")
                    .setItems(arrayOf("系统文件选择器", "应用内轨迹列表")) { _, which ->
                        if (which == 0) openDocument.launch(arrayOf("*/*"))
                        else NaviPathDialog(this).apply {
                            listener = object : NaviPathDialog.NaviPathListener {
                                override fun onItemClick(path: String) { dataBinding.edFile.setText(path) }
                            }; show()
                        }
                    }.show()
            }
            dataBinding.btnStartNavi -> {
                if (importing) return
                val path = dataBinding.edFile.text?.toString()?.trim().orEmpty()
                if (path.isEmpty()) { ToastUtils.showShort("请先选择轨迹文件"); return }
                if (!Utils.checkFloatWindow(this)) { WarnDialogUtils.setFloatWindowDialog(this); return }
                val coordinateType = mPointType
                importing = true
                lifecycleScope.launch {
                    viewModel.loading.value = true
                    try {
                        val route = withContext(Dispatchers.IO) {
                            val file = File(path)
                            require(file.isFile && file.length() <= TrackParser.MAX_BYTES) { "文件不存在、不可读或超过 5 MB" }
                            ArrayList(TrackParser.parse(file.readText()).map { point ->
                                val converted = when (coordinateType) {
                                    LocationUtils.gps84 -> LocationUtils.wgs84ToGcj02(point.longitude, point.latitude)
                                    LocationUtils.bd09 -> LocationUtils.bd09ToGcj02(point.longitude, point.latitude)
                                    else -> doubleArrayOf(point.longitude, point.latitude)
                                }
                                LatLng(converted[1], converted[0])
                            })
                        }
                        SearchManager.INSTANCE.polylineList = route
                        val model = MockMessageModel(naviType = NaviType.NAVI_FILE, speed = MMKVUtils.getSpeed(), pointType = LocationUtils.gcj02)
                        startActivity(Intent(this@FileMockActivity, MockLocationActivity::class.java).putExtra("model", model))
                    } catch (e: Exception) { ToastUtils.showLong(e.message ?: "轨迹解析失败") }
                    finally { importing = false; viewModel.loading.value = false }
                }
            }

            dataBinding.btnPointType -> {
                PointTypeDialog(this).apply {
                    listener = object : PointTypeDialog.PointTypeDialogListener {
                        override fun onDismiss(type: String) {
                            mPointType = type
                            dataBinding.pointType = "经纬度类型：$mPointType"
                        }
                    }
                }.show(mPointType)
            }

            else -> {
            }
        }
    }

}