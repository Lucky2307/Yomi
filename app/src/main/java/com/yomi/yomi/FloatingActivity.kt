package com.yomi.yomi

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.Button


class FloatingActivity: Service() {
    private lateinit var floatButtonView: ViewGroup
    private lateinit var floatWindowLayoutParams: WindowManager.LayoutParams
    private lateinit var floatingBubble: Button
    private var LAYOUT_TYPE: Int? = null
    private lateinit var windowManager: WindowManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent == null){
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        val metrics = this.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = baseContext.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        floatButtonView = inflater.inflate(R.layout.overlay_bubble, null) as ViewGroup

        floatingBubble = floatButtonView.findViewById(R.id.floatingButton)

        LAYOUT_TYPE = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        floatWindowLayoutParams = WindowManager.LayoutParams(
            width,
            height,
            LAYOUT_TYPE!!,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        floatWindowLayoutParams.gravity = Gravity.CENTER
        floatWindowLayoutParams.x = 0
        floatWindowLayoutParams.y = 0

        windowManager.addView(floatButtonView, floatWindowLayoutParams)
    }


}
