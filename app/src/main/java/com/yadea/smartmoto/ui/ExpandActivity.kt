package com.yadea.smartmoto.ui

import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.castiel.common.base.BaseActivity
import com.castiel.common.base.BaseListAdapter
import com.castiel.common.base.BaseViewModel
import com.castiel.common.recycler.decoration.VerticalItemDecoration
import com.yadea.smartmoto.R
import com.yadea.smartmoto.adaper.ExpandAdapter
import com.yadea.smartmoto.databinding.ActivityExpandBinding
import com.yadea.smartmoto.model.ExpandModel
import com.yadea.smartmoto.widget.HintDialog

/** @author jiayu.liu */
class ExpandActivity : BaseActivity<ActivityExpandBinding, BaseViewModel>() {
    private val adapter = ExpandAdapter()
    private val items = listOf(
        ExpandModel("外部app启动模拟导航", "仅限同签名且声明 CONTROL_MOCK_LOCATION 权限的应用；受系统后台启动限制"),
        ExpandModel("模拟功能设置", "模拟功能、地图选择及隐私设置"),
        ExpandModel("模拟导航数据导入", "路径：/storage/emulated/0/Android/data/${com.yadea.smartmoto.BuildConfig.APPLICATION_ID}/files/nav_path"),
        ExpandModel("免责声明", "此应用仅限开发学习和开发使用，软件的发布和使用均不收取任何费用。拒绝任何人或任何实体进行出售、重新修改后分发，严禁用于商业谋利用途。项目维护者对软件的滥用不承担任何责任。")
    )
    override fun initViewModel() = BaseViewModel::class.java
    override fun getLayout() = R.layout.activity_expand
    override fun initView() {
        dataBinding.recycler.layoutManager = LinearLayoutManager(this)
        dataBinding.recycler.adapter = adapter
        dataBinding.recycler.addItemDecoration(VerticalItemDecoration(this, R.drawable.shape_item_line_verticall, 10f, 0f))
        adapter.clickListener = object : BaseListAdapter.OnItemClickListener<ExpandModel> {
            override fun onItemClick(view: View?, t: ExpandModel, position: Int) {
                when (position) {
                    0 -> HintDialog(this@ExpandActivity, "外部广播", getString(R.string.receiver_hint)).show()
                    1 -> startActivity(Intent(this@ExpandActivity, SettingActivity::class.java))
                    2 -> startActivity(Intent(this@ExpandActivity, FileMockActivity::class.java))
                }
            }
        }
    }
    override fun initData() { adapter.submitList(items.toMutableList()) }
    override fun initObserver() {}
}
