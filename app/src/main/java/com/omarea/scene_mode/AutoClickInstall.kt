package com.omarea.scene_mode

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.omarea.store.SpfConfig
import java.util.*

/**
 * Created by Hello on 2017/4/8.
 */
class AutoClickInstall : AutoClickBase() {
    internal var autoClickKeyWords: ArrayList<String> = object : ArrayList<String>() {
        init {
            add("继续安装")
            add("继续安装")
            add("下一步")
            add("下一步")
            add("Next")
            add("Next")
        }
    }
    internal var autoClickKeyWords2: ArrayList<String> = object : ArrayList<String>() {
        init {
            add("安装")
            add("Install")
            add("安裝")
            add("完成")
            add("Done")
        }
    }

    fun packageinstallerAutoClick(service: AccessibilityService, event: AccessibilityEvent) {
        // event.source 是带自定义 getter 的属性，每次访问都会重新取值，
        // 因此必须先存入局部变量才能安全地复用非空判断（Kotlin 1.7 起强制）
        val source = event.source
        if (!service.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
                        .getBoolean(SpfConfig.GLOBAL_SPF_AUTO_INSTALL, false) || source == null)
            return

        try {
            for (ki in autoClickKeyWords.indices) {
                val nextNodes = source.findAccessibilityNodeInfosByText(autoClickKeyWords[ki])
                if (nextNodes != null && !nextNodes.isEmpty()) {
                    var node: AccessibilityNodeInfo
                    for (i in nextNodes.indices) {
                        node = nextNodes[i]
                        if (node.className.toString().toLowerCase(Locale.getDefault()).contains("button") && node.isEnabled) {
                            if (node.text.trim().toString() != autoClickKeyWords[ki])
                                continue
                            super.clickNode(node) || super.tryTouchNodeRect(node, service)
                            try {
                                Thread.sleep(300)
                            } catch (ex: Exception) {
                            }

                        }
                    }
                }
            }


            for (ki in autoClickKeyWords.indices) {

                val nodes = source.findAccessibilityNodeInfosByText(autoClickKeyWords2[ki])
                if (nodes != null && !nodes.isEmpty()) {
                    var node: AccessibilityNodeInfo
                    for (i in nodes.indices) {
                        node = nodes[i]
                        if (node.className.toString().toLowerCase(Locale.getDefault()).contains("button")) {
                            if (!node.isEnabled) {
                                node.isEnabled = true
                            }
                            if (node.text.trim().toString() != autoClickKeyWords2[ki])
                                continue
                            super.clickNode(node) || super.tryTouchNodeRect(node, service)
                        }
                    }
                }
            }
        } catch (ex: java.lang.Exception) {

        }
    }

    fun miuiUsbInstallAutoClick(service: AccessibilityService, event: AccessibilityEvent) {
        val source = event.source
        if (!service.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
                        .getBoolean(SpfConfig.GLOBAL_SPF_AUTO_INSTALL, false) || source == null)
            return

        try {
            val next2Nodes = source.findAccessibilityNodeInfosByText("继续安装")
            if (next2Nodes != null && !next2Nodes.isEmpty()) {
                var node: AccessibilityNodeInfo
                for (i in next2Nodes.indices) {
                    node = next2Nodes[i]
                    if (node.className.toString().toLowerCase(Locale.getDefault()).contains("button")) {
                        if (!node.isEnabled) {
                            node.isEnabled = true
                        }
                        super.clickNode(node) || super.tryTouchNodeRect(node, service)
                    }
                }
            }
        } catch (ex: java.lang.Exception) {
        }
    }
}