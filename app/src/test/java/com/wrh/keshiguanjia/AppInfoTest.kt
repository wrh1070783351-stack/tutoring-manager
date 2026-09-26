package com.wrh.keshiguanjia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M0 骨架冒烟测试：验证构建配置正确接入测试框架与 BuildConfig。
 */
class AppInfoTest {

    @Test
    fun versionName_followsSemanticFormat() {
        assertTrue(
            "versionName 应符合 x.y.z 格式，实际为 ${AppInfo.VERSION_NAME}",
            AppInfo.VERSION_NAME.matches(Regex("""\d+\.\d+\.\d+"""))
        )
    }

    @Test
    fun initialVersion_is_0_1_0() {
        assertEquals("0.1.0", AppInfo.VERSION_NAME)
    }
}
