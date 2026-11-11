package com.omarea.vtools.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.omarea.Scene
import com.omarea.common.shared.MagiskExtend
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.KernelProrp
import com.omarea.common.shell.RootFile
import com.omarea.common.ui.DialogHelper
import com.omarea.permissions.CheckRootStatus
import com.omarea.store.SpfConfig
import com.omarea.ui.BottomNavPagerBinder
import com.omarea.utils.ElectricityUnit
import com.omarea.utils.Update
import com.omarea.vtools.R
import com.omarea.vtools.dialogs.DialogMonitor
import com.omarea.vtools.dialogs.DialogPower
import com.omarea.vtools.fragments.FragmentDonate
import com.omarea.vtools.fragments.FragmentHome
import com.omarea.vtools.fragments.FragmentNav
import com.omarea.vtools.fragments.FragmentNotRoot
import kotlinx.android.synthetic.main.activity_main.*

class ActivityMain : ActivityBase() {
    private lateinit var globalSPF: SharedPreferences

    private class ThermalCheckThread(private var context: Activity) : Thread() {
        private fun deleteThermalCopyWarn(onYes: Runnable) {
            Scene.post {
                if (!context.isFinishing) {
                    val view = LayoutInflater.from(context).inflate(R.layout.dialog_delete_thermal, null)
                    val dialog = DialogHelper.customDialog(context, view)
                    view.findViewById<View>(R.id.btn_no).setOnClickListener {
                        dialog.dismiss()
                    }
                    view.findViewById<View>(R.id.btn_yes).setOnClickListener {
                        dialog.dismiss()
                        onYes.run()
                    }
                    dialog.setCancelable(false)
                }
            }
        }

        override fun run() {
            sleep(500)
            if (
                    MagiskExtend.magiskSupported() &&
                    KernelProrp.getProp("${MagiskExtend.MAGISK_PATH}system/vendor/etc/thermal.current.ini") != ""
            ) {
                when {
                    RootFile.list("/data/thermal/config").size > 0 -> {
                        deleteThermalCopyWarn {
                            KeepShellPublic.doCmdSync(
                                    "chattr -R -i /data/thermal 2> /dev/null\n" +
                                            "rm -rf /data/thermal 2> /dev/null\n" +
                                            "sync;svc power reboot || reboot;"
                            )
                        }
                    }
                    RootFile.list("/data/vendor/thermal/config").size > 0 -> {
                        if (
                                RootFile.fileEquals(
                                        "/data/vendor/thermal/config/thermal-normal.conf",
                                        MagiskExtend.getMagiskReplaceFilePath("/system/vendor/etc/thermal-normal.conf")
                                )
                        ) {
                            // Scene.toast("文件相同，跳过温控清理", Toast.LENGTH_SHORT)
                            return
                        } else {
                            deleteThermalCopyWarn {
                                KeepShellPublic.doCmdSync(
                                        "chattr -R -i /data/vendor/thermal 2> /dev/null\n" +
                                                "rm -rf /data/vendor/thermal 2> /dev/null\n" +
                                                "sync;svc power reboot || reboot;"
                                )
                            }
                        }
                    }
                    else -> return
                }
            }
        }
    }

    @SuppressLint("ResourceAsColor")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ActivityStartSplash.finished) {
            val intent = Intent(this.applicationContext, ActivityStartSplash::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            // intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            startActivity(intent)
            finish()
            return
        }

        /*
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()   // or .detectAll() for all detectable problems
                .penaltyLog()
                .build());
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyDeath()
                .detectAll()
                .build());
        */

        globalSPF = getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
        if (!globalSPF.contains(SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT)) {
            globalSPF.edit().putInt(SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT, ElectricityUnit().getDefaultElectricityUnit(this)).apply()
        }

        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // 底部导航 + ViewPager（原为顶部 TabLayout，由 TabIconHelper2 装配）
        val navBinder = BottomNavPagerBinder(
                tab_list,
                tab_content,
                supportFragmentManager,
                listOf(R.id.nav_action, R.id.nav_home, R.id.nav_donate)
        )
        navBinder.addPage(FragmentNav.createPage(themeMode))
        navBinder.addPage(if (CheckRootStatus.lastCheckResult) {
            FragmentHome()
        } else {
            FragmentNotRoot()
        })
        navBinder.addPage(FragmentDonate())
        navBinder.select(1) // 默认选中第二页（概览）

        if (CheckRootStatus.lastCheckResult) {
            try {
                if (MagiskExtend.magiskSupported() &&
                        !(MagiskExtend.moduleInstalled() || globalSPF.getBoolean("magisk_dot_show", false))
                ) {
                    DialogHelper.confirm(this,
                            getString(R.string.magisk_install_title),
                            getString(R.string.magisk_install_desc),
                            {
                                MagiskExtend.magiskModuleInstall(this)
                            })
                    // 不再提示 globalSPF.edit().putBoolean("magisk_dot_show", true).apply()
                }
            } catch (ex: Exception) {
                DialogHelper.alert(
                        this,
                        getString(R.string.sorry),
                        "启动应用失败\n" + ex.message
                ) {
                    recreate()
                }
            }
            ThermalCheckThread(this).start()
        }

    }

    override fun onResume() {
        super.onResume()

        // 如果距离上次检查更新超过 24 小时
        if (globalSPF.getLong(SpfConfig.GLOBAL_SPF_LAST_UPDATE, 0) + (3600 * 24 * 1000) < System.currentTimeMillis()) {
            Update().checkUpdate(this)
            globalSPF.edit().putLong(SpfConfig.GLOBAL_SPF_LAST_UPDATE, System.currentTimeMillis()).apply()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    }

    //返回键事件
    override fun onBackPressed() {
        try {
            when {
                supportFragmentManager.backStackEntryCount > 0 -> {
                    supportFragmentManager.popBackStack()
                }
                else -> {
                    excludeFromRecent()
                    super.onBackPressed()
                    this.finishActivity(0)
                }
            }
        } catch (ex: Exception) {
            ex.stackTrace
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    //右上角菜单
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> startActivity(Intent(this.applicationContext, ActivityOtherSettings::class.java))
            R.id.action_power -> DialogPower(this).showPowerMenu()
            R.id.action_graph -> {
                if (!CheckRootStatus.lastCheckResult) {
                    Toast.makeText(this, "没有获得ROOT权限，不能使用本功能", Toast.LENGTH_SHORT).show()
                    return false
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    if (Settings.canDrawOverlays(this)) {
                        DialogMonitor(this).show()
                    } else {
                        //若没有权限，提示获取
                        //val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        //startActivity(intent);
                        val intent = Intent()
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                        intent.data = Uri.fromParts("package", this.packageName, null)
                        Toast.makeText(applicationContext, getString(R.string.permission_float), Toast.LENGTH_LONG).show()
                    }
                } else {
                    DialogMonitor(this).show()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    public override fun onPause() {
        super.onPause()
        if (!CheckRootStatus.lastCheckResult) {
            finish()
        }
    }

    override fun onDestroy() {
        val fragmentManager = supportFragmentManager
        fragmentManager.fragments.clear()
        super.onDestroy()
    }
}
