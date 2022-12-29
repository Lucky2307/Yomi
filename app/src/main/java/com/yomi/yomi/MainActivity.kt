package com.yomi.yomi

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.nio.ByteBuffer


class MainActivity : AppCompatActivity() {
    private lateinit var dialog: AlertDialog
    private lateinit var toggleButton: Button
    private lateinit var mMediaProjectionManager: MediaProjectionManager
    private lateinit var mMediaProjection: MediaProjection
    private lateinit var mImageReader: ImageReader
    private var REQUEST_CODE = 1
    private var mResultCode: Int = 0
    private lateinit var mResultData: Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar?>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar?.setTitleTextAppearance(this, R.style.DMSansTextAppearance)

        if (!checkFloatingPermission()) {
            requestFloatingPermission()
        }


        mMediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)

        toggleButton = findViewById<Button>(R.id.ToggleButton)
        toggleButton.setOnClickListener {
            Log.e("DEBUGGING", "Clicked")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode != RESULT_OK) {
                Log.e("ERROR", "User Cancelled")
                return
            }
            mResultCode = resultCode
            if (data != null) {
                mResultData = data
                launchService()
            }
        }
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

    private fun launchService() {
        val intent = Intent(
            this,
            FloatingActivity::class.java
        )
        intent.action = "startBubble"
        intent.putExtra("data", mResultData)
        intent.putExtra("code", mResultCode)

        startService(intent)

    }


}


