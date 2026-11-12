package com.omarea.vtools.activities

import android.Manifest
import android.app.Activity
import android.app.UiModeManager
import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.PermissionChecker
import android.util.Log
import com.google.android.material.color.DynamicColors
import com.omarea.common.ui.ThemeMode
import com.omarea.store.SpfConfig
import com.omarea.vtools.R

object ThemeSwitch {
    private val themeMap = arrayListOf(
            // 彩色 + 白
            R.style.AppThemeBlue,
            R.style.AppThemeCyan,
            R.style.AppThemeGreen,
            R.style.AppThemeOrange,
            R.style.AppThemeRed,
            R.style.AppThemePink,
            R.style.AppThemePretty,
            R.style.AppThemeViolet
    )

    private var globalSPF: SharedPreferences? = null

    private fun checkPermission(context: Context, permission: String): Boolean = PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

    /**
     * 在 setTheme 之后为当前 Activity 应用 Material You 动态取色。
     *
     * 为什么不能只在 Application 里调 applyToActivitiesIfAvailable：
     * 本项目的每个 Activity 都会在 onCreate 里调用 setTheme(themeId) 切换 8 套主题，
     * setTheme 会重置窗口主题并丢弃此前套上的动态色 overlay，导致动态取色形同虚设。
     * 因此必须在 setTheme 之后重新应用。
     *
     * 用户在设置里关闭「跟随壁纸取色」时不应用，M3 语义色回落到 AppTheme 的静态色板。
     */
    private fun applyDynamicColorIfEnabled(activity: Activity) {
        val enabled = globalSPF?.getBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_COLOR, true) ?: true
        if (!enabled) return

        // 1) 让 DynamicColors 把 M3 色板换成壁纸派生色（Android 12 以下内部自动跳过）
        DynamicColors.applyToActivityIfAvailable(activity)

        // 2) 本项目的顶栏/状态栏/图表读的是 AppCompat 属性，DynamicColors 不覆盖它们，
        //    因此再叠一层桥接 overlay，把 AppCompat 属性重定向到 M3 语义色，
        //    这样壁纸派生色才能传导到旧界面，做到新旧页面配色一致。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.theme.applyStyle(R.style.ThemeOverlay_Scene_DynamicBridge, true)
        }
    }

    internal fun switchTheme(activity: Activity): ThemeMode {
        val themeMode = ThemeMode()
        if (globalSPF == null) {
            globalSPF = activity.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
        }

        //Rrex: android 5 may have issue with white, set default to 0 (blue)
        val theme = globalSPF!!.getInt(SpfConfig.GLOBAL_SPF_THEME, 0)

        // 设置壁纸作为背景需要读取外置存储权限（如果没权限，就恢复默认主题）
        if (theme == 10 && !(checkPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) && checkPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
            globalSPF!!.edit().remove(SpfConfig.GLOBAL_SPF_THEME).apply()
            return switchTheme(activity)
        }

        if (theme < themeMap.size) {
            val uiModeManager = activity.applicationContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            themeMode.isDarkMode = uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES

            val themeId = when (theme) {
                -1 -> {
                    // AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    if (themeMode.isDarkMode) {
                        themeMode.isLightStatusBar = false
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        R.style.AppThemeNoActionBarNight
                    } else {
                        themeMode.isLightStatusBar = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        R.style.AppThemeWhite
                    }
                }
                -2 -> {
                    themeMode.isDarkMode = true
                    themeMode.isLightStatusBar = false
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    R.style.AppThemeNoActionBarNight
                }
                -3 -> {
                    themeMode.isDarkMode = false
                    themeMode.isLightStatusBar = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    R.style.AppThemeWhite
                }
                else -> {
                    themeMode.isLightStatusBar = false
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    themeMap[theme]
                }
            }
            if (activity is AppCompatActivity) {
                activity.getDelegate().setLocalNightMode(AppCompatDelegate.getDefaultNightMode())
            }
            activity.setTheme(themeId)

            // 动态取色必须紧跟在 setTheme 之后应用：
            // setTheme 会重置窗口主题，把 Application 里 applyToActivitiesIfAvailable
            // 预先套上的动态色 overlay 覆盖掉，导致 Material You 完全不生效。
            // 这里对当前 Activity 重新应用一次，才能让壁纸取色真正覆盖 M3 色板。
            applyDynamicColorIfEnabled(activity)

            if (themeMode.isLightStatusBar) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            }
        } else if (theme == 10) {
            // 壁纸主题：Android 13+ 读取壁纸 Drawable 需要 READ_MEDIA_IMAGES 权限，
            // 而本项目 targetSdk 30 未声明该权限，wallpaper.drawable 会抛 SecurityException。
            // 一旦抛出且主题值已存进 SPF，就会形成「每次启动必崩、用户无法自行恢复」的死循环，
            // 因此这里整体兜底：任何异常都回退到默认蓝色主题并清除该设置。
            try {
                val wallpaper = WallpaperManager.getInstance(activity)
                val wallpaperInfo = wallpaper.wallpaperInfo
                activity.setTheme(R.style.AppThemeWallpaper)
                applyDynamicColorIfEnabled(activity)

                // 动态壁纸
                if (wallpaperInfo != null && wallpaperInfo.packageName != null) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                    themeMode.isDarkMode = true
                } else {
                    val wallpaperDrawable = wallpaper.drawable
                    if (wallpaperDrawable is BitmapDrawable) {
                        // 深色的静态壁纸
                        if (isDarkColor(wallpaperDrawable)) {
                            themeMode.isDarkMode = true
                        } else {
                            // 浅色的静态壁纸
                            themeMode.isDarkMode = false
                            themeMode.isLightStatusBar = true
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            }

                            if (!(activity is ActivityMain)) {
                                activity.window.navigationBarColor = Color.TRANSPARENT
                            }
                        }
                        activity.window.setBackgroundDrawable(wallpaperDrawable)
                    }
                }

                if (themeMode.isDarkMode) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            } catch (ex: Throwable) {
                // 壁纸不可读（权限/格式问题）时回退，避免启动崩溃死循环
                Log.w("SceneDynamicColor", "壁纸主题不可用，回退默认主题: ${ex.javaClass.simpleName}")
                globalSPF!!.edit().putInt(SpfConfig.GLOBAL_SPF_THEME, 0).apply()
                activity.setTheme(themeMap[0])
                applyDynamicColorIfEnabled(activity)
            }
        }
        return themeMode
    }

    private fun isDarkColor(wallPaper: Drawable): Boolean {
        // 根据壁纸色彩设置主题
        val bitmap = (wallPaper as BitmapDrawable).bitmap
        val h = bitmap.height - 1
        val w = bitmap.width - 1

        var darkPoint = 0
        var lightPoint = 0

        // 采样点数
        val pointCount = if (h > 24 && w > 24) 24 else 1

        for (i in 0..pointCount) {
            val y = h / pointCount * i
            val x = w / pointCount * i
            val pixel = bitmap.getPixel(x, y)

            // 获取颜色
            val redValue = Color.red(pixel)
            val blueValue = Color.blue(pixel)
            val greenValue = Color.green(pixel)

            if (redValue > 150 && blueValue > 150 && greenValue > 150) {
                lightPoint += 1
            } else {
                darkPoint += 1
            }
        }
        return darkPoint > lightPoint
    }

    private fun rsBlur(source: Bitmap, radius: Int, context: Context): Bitmap {
        val inputBmp = source
        val renderScript = RenderScript.create(context);

        // Allocate memory for Renderscript to work with
        //(2)
        val input = Allocation.createFromBitmap(renderScript, inputBmp);
        val output = Allocation.createTyped(renderScript, input.getType());
        //(3)
        // Load up an instance of the specific script that we want to use.
        val scriptIntrinsicBlur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
        //(4)
        scriptIntrinsicBlur.setInput(input);
        //(5)
        // Set the blur radius
        scriptIntrinsicBlur.setRadius(radius.toFloat());
        //(6)
        // Start the ScriptIntrinisicBlur
        scriptIntrinsicBlur.forEach(output);
        //(7)
        // Copy the output to the blurred bitmap
        output.copyTo(inputBmp);
        //(8)
        renderScript.destroy();

        return inputBmp;
    }
}
