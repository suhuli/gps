package com.yadea.smartmoto.maps

import org.junit.Assert.*
import org.junit.Test

class BaiduAuthErrorsTest {
    @Test fun serviceDisabledIsNotReportedAsMcodeFailure() {
        val message = BaiduAuthErrors.message(240)
        assertTrue(message.contains("APP 服务被禁用"))
        assertFalse(message.contains("Mcode 校验失败"))
    }
    @Test fun mcodeFailureExplainsInstalledIdentity() {
        val message = BaiduAuthErrors.message(230)
        assertTrue(message.contains("Mcode 校验失败"))
        assertTrue(message.contains("实际安装包名"))
    }
    @Test fun incorrectAppTypeNeedsAndroidKey() {
        assertTrue(BaiduAuthErrors.message(203).contains("Android SDK"))
    }
    @Test fun unknownCodeIsKeptWithoutRawSdkPayload() {
        assertTrue(BaiduAuthErrors.message(-99).contains("-99"))
    }
}
