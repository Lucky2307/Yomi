package com.yomi.yomi

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class FloatingActivity: Service() {
    private lateinit var floatButtonView: ViewGroup
    private lateinit var selectionOverlayView: ViewGroup
    private lateinit var floatWindowLayoutParams: WindowManager.LayoutParams
    private lateinit var selectionOverlayLayoutParams: WindowManager.LayoutParams
    private lateinit var floatingBubble: ImageButton
    private var LAYOUT_TYPE: Int? = null
    private lateinit var windowManager: WindowManager
    private lateinit var removeButton: Button
    private val CLICK_DRAG_TOLERANCE = 10f
    private var downRawX = 0f
    private var downRawY = 0f
    private var dX = 0f
    private var dY = 0f

    private lateinit var recognizer: TextRecognizer
    private var mResultCode: Int = 0
    private lateinit var mResultData: Intent
    private lateinit var mMediaProjection: MediaProjection
    private lateinit var mMediaProjectionManager: MediaProjectionManager
    private lateinit var mImageReader: ImageReader


    private var isOverlayActive = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent == null){
            return START_NOT_STICKY
        }
        if (intent.action == "startBubble"){
            windowManager.addView(floatButtonView, floatWindowLayoutParams)
            mResultCode = intent.getIntExtra("code", 0)
            mResultData = intent.getParcelableExtra("data")!!

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                val notificationChannel = NotificationChannel("YOMI", "YOMI", NotificationManager.IMPORTANCE_NONE)
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(notificationChannel)
                val notification: Notification = Notification.Builder(applicationContext, "YOMI")
                    .setOngoing(true)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                    startForeground(
                        1,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                }
                else {
                    startForeground(
                        1,
                        notification
                    )
                }

            }
            initMedia()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()

        val metrics = this.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = baseContext.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        Log.e("DEBUGPOS", (height-height/3).toString())
        floatButtonView = inflater.inflate(R.layout.overlay_bubble, null) as ViewGroup
        floatingBubble = floatButtonView.findViewById(R.id.floatingButton)
        floatingBubble.setOnTouchListener { v, event ->
            val action = event.action
            val oldX = floatWindowLayoutParams.x
            val oldY = floatWindowLayoutParams.y
            val disX = event.rawX.roundToInt() - oldX
            val disY = event.rawY.roundToInt() - oldY
            if (action == MotionEvent.ACTION_DOWN) {
                downRawX = event.rawX
                downRawY = event.rawY
                dX = v.x - downRawX
                dY = v.y - downRawY
                true
            } else if (action == MotionEvent.ACTION_MOVE) {
                val newY = event.rawY.roundToInt() - dpToPixels(30.toDouble())
                Log.e("DEBUGPOT", newY.toString())
                floatWindowLayoutParams.x = event.rawX.roundToInt() - dpToPixels(30.toDouble())
                floatWindowLayoutParams.y = event.rawY.roundToInt() - dpToPixels(30.toDouble())
                windowManager.updateViewLayout(floatButtonView, floatWindowLayoutParams)
                true // Consumed
            } else if (action == MotionEvent.ACTION_UP) {
                val upRawX: Float = event.rawX
                val upRawY: Float = event.rawY
                val upDX: Float = upRawX - downRawX
                val upDY: Float = upRawY - downRawY
                if (abs(upDX) < CLICK_DRAG_TOLERANCE && abs(upDY) < CLICK_DRAG_TOLERANCE) { // A click
                    bubbleClick()
                    true
                } else if (floatWindowLayoutParams.y >= height-height/4){
                    stopSelf()
                    true
                } else { // A drag
                    true // Consumed
                }
            } else {
                true
            }
        }

        LAYOUT_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        floatWindowLayoutParams = WindowManager.LayoutParams(
            dpToPixels(60.toDouble()),
            dpToPixels(60.toDouble()),
            LAYOUT_TYPE!!,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        floatWindowLayoutParams.gravity = Gravity.START or Gravity.TOP
        floatWindowLayoutParams.x = 0
        floatWindowLayoutParams.y = 0

        selectionOverlayView = inflater.inflate(R.layout.overlay_selection, null) as ViewGroup
        selectionOverlayLayoutParams = WindowManager.LayoutParams(
            width,
            height,
            LAYOUT_TYPE!!,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        selectionOverlayLayoutParams.gravity = Gravity.CENTER

        recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

        mMediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private fun bubbleClick(){
        if (isOverlayActive) {
            windowManager.removeView(selectionOverlayView)
            isOverlayActive = false
        } else {
            windowManager.addView(selectionOverlayView, selectionOverlayLayoutParams)
            windowManager.removeView(floatButtonView)
            windowManager.addView(floatButtonView, floatWindowLayoutParams)

            analyzeImage(getScreenshot())

            isOverlayActive = true
        }
    }

    private fun dpToPixels(dp: Double): Int {
        val displayMetrics = resources.displayMetrics
        return (dp * (displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
    }


    private fun getScreenshot(): Bitmap {
        val image: Image = mImageReader.acquireLatestImage()
        val metrics = resources.displayMetrics
        val planes = image.planes
        val buffer = planes[0].buffer
        val offset = 0
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding: Int = rowStride - pixelStride * metrics.widthPixels
        val bitmap = Bitmap.createBitmap(metrics.widthPixels + rowPadding / pixelStride, metrics.heightPixels, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()
        val testImg = selectionOverlayView.findViewById<ImageView>(R.id.imgTest)
        testImg.setImageBitmap(bitmap)
        return bitmap
    }

    private fun analyzeImage(bitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { text ->
                processResult(text)
            }
    }

    private fun processResult(text: Text){
        for (block in text.textBlocks){
            val boundingBox = block.boundingBox.toString()
            selectionOverlayView.
        }
    }
    @SuppressLint("WrongConstant")
    private fun initMedia() {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData)
        val metrics = resources.displayMetrics
        mImageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )
        mMediaProjection.createVirtualDisplay(
            "ScreenCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mImageReader.surface,
            null,
            null
        )
    }

    override fun onDestroy() {
        windowManager.removeView(floatButtonView)
        if (isOverlayActive){
            windowManager.removeView(selectionOverlayView)
        }
        super.onDestroy()
    }
}
