package com.kdh.carculator.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.kdh.carculator.R
import com.kdh.carculator.databinding.ActivityBaseDrawerBinding
import com.kdh.carculator.repo.CarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

abstract class BaseDrawerActivity : AppCompatActivity() {
    protected lateinit var baseBinding: ActivityBaseDrawerBinding
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        baseBinding = ActivityBaseDrawerBinding.inflate(layoutInflater)
        super.setContentView(baseBinding.root)
        setSupportActionBar(baseBinding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toggle = ActionBarDrawerToggle(this, baseBinding.drawerLayout, baseBinding.toolbar, R.string.app_name, R.string.app_name)
        toggle.isDrawerIndicatorEnabled = true
        baseBinding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        baseBinding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    baseBinding.drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    true
                }
                R.id.nav_settings -> {
                    baseBinding.drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> {
                    val carId = item.intent?.getStringExtra("carId")
                    if (carId != null) {
                        baseBinding.drawerLayout.closeDrawer(GravityCompat.START)
                        val i = Intent(this, CarDetailActivity::class.java)
                        i.putExtra("carId", carId)
                        startActivity(i)
                        true
                    } else false
                }
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            val repo = CarRepository(this@BaseDrawerActivity)
            repo.observeCars().collectLatest { cars ->
                val menu = baseBinding.navigationView.menu
                menu.removeGroup(R.id.group_cars)
                val groupId = R.id.group_cars
                for (car in cars) {
                    val item = menu.add(groupId, View.generateViewId(), 0, car.registrationNumber)
                    item.intent = Intent().putExtra("carId", car.id)
                }
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        toggle.syncState()
    }

    override fun setContentView(layoutResID: Int) {
        val child: View = LayoutInflater.from(this).inflate(layoutResID, baseBinding.contentContainer, false)
        baseBinding.contentContainer.removeAllViews()
        baseBinding.contentContainer.addView(child)
    }

    override fun setContentView(view: View?) {
        if (view != null) {
            baseBinding.contentContainer.removeAllViews()
            baseBinding.contentContainer.addView(view)
        }
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        if (view != null) {
            baseBinding.contentContainer.removeAllViews()
            if (params != null) {
                baseBinding.contentContainer.addView(view, params)
            } else {
                baseBinding.contentContainer.addView(view)
            }
        }
    }
}
