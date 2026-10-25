package com.huolala.mockgps.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.castiel.common.base.BaseViewModel
import com.huolala.mockgps.model.AppUpdateModel

/** Original-author update service is deliberately not called by this differently signed app. */
class HomeViewModel : BaseViewModel() {
    private val _updateApp = MutableLiveData<AppUpdateModel>()
    val updateApp: LiveData<AppUpdateModel> = _updateApp
}
