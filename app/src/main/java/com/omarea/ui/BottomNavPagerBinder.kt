package com.omarea.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * 底部导航 + ViewPager 绑定器。
 *
 * 取代原来的 [TabIconHelper2]（顶部 TabLayout）。保留 ViewPager 作为内容容器，
 * 因此各 Fragment（FragmentNav / FragmentHome / FragmentDonate）无需改动。
 *
 * @param menuIdToIndex 菜单项 id -> ViewPager 页面下标，顺序必须与实际 addPage 顺序一致
 */
class BottomNavPagerBinder(
    private val navigation: BottomNavigationView,
    private val viewPager: ViewPager,
    private val supportFragmentManager: FragmentManager,
    private val menuIdToIndex: List<Int>
) {
    private val fragments = ArrayList<Fragment>()

    val adapter = object : FragmentPagerAdapter(supportFragmentManager) {
        override fun getCount(): Int = fragments.size

        override fun getItem(position: Int): Fragment = fragments[position]
    }

    fun addPage(fragment: Fragment) {
        fragments.add(fragment)
        adapter.notifyDataSetChanged()
    }

    /** 选中指定页面（同时同步底部导航高亮） */
    fun select(index: Int) {
        if (index < 0 || index >= fragments.size) return
        viewPager.currentItem = index
        navigation.selectedItemId = menuIdToIndex[index]
    }

    init {
        viewPager.adapter = adapter

        // 底部导航 -> ViewPager
        navigation.setOnItemSelectedListener { item ->
            val index = menuIdToIndex.indexOf(item.itemId)
            if (index >= 0 && viewPager.currentItem != index) {
                viewPager.currentItem = index
            }
            true
        }

        // ViewPager 滑动 -> 底部导航（只在用户滑动/代码切换时同步，避免回环）
        viewPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                val itemId = menuIdToIndex.getOrNull(position) ?: return
                if (navigation.selectedItemId != itemId) {
                    navigation.selectedItemId = itemId
                }
            }
        })
    }
}
