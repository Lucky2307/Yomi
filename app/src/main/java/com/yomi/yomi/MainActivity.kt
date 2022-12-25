package com.yomi.yomi

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {
    private lateinit var dialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar?>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar?.setTitleTextAppearance(this, R.style.DMSansTextAppearance)

        if (!checkFloatingPermission()) {
            requestFloatingPermission()
        }

        var toggleButton = findViewById<Button>(R.id.ToggleButton)
        toggleButton.setOnClickListener {
            if (!isServiceRunning()) {
                val intent = Intent(
                    this,
                    FloatingActivity::class.java
                )
                intent.action = "startFloatingBubble"
                startService(intent)
                Log.e("MAIN", "Service started")
            }
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)){
            if (FloatingActivity::class.java.name == service.service.className){
                return true
            }
        }
        return false
    }

    private fun requestFloatingPermission() {
        var builder = AlertDialog.Builder(this)
        builder.setCancelable(true)
        builder.setTitle("Overlay Permission Needed")
        builder.setMessage("Enable 'Display Over Other App'")
        builder.setPositiveButton("Open Settings") { _, _ ->
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, RESULT_OK)
        }
        dialog = builder.create()
        dialog.show()
    }

    private fun checkFloatingPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }
}
