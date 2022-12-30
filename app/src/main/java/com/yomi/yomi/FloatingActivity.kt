package com.yomi.yomi

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
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
import android.widget.*
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlin.math.abs
import kotlin.math.roundToInt


class FloatingActivity : Service() {
    private lateinit var floatButtonView: ViewGroup
    private lateinit var selectionOverlayView: ViewGroup
    private lateinit var detailOverlayView: ViewGroup
    private lateinit var detailOverlayTextButtonLayout: ViewGroup
    private lateinit var flowView: Flow
    private lateinit var floatWindowLayoutParams: WindowManager.LayoutParams
    private lateinit var selectionOverlayLayoutParams: WindowManager.LayoutParams
    private lateinit var detailOverlayLayoutParams: WindowManager.LayoutParams
    private lateinit var floatingBubble: ImageButton
    private lateinit var inflater: LayoutInflater
    private var LAYOUT_TYPE: Int? = null
    private lateinit var windowManager: WindowManager
    private lateinit var removeButton: Button
    private val CLICK_DRAG_TOLERANCE = 10f
    private var downRawX = 0f
    private var downRawY = 0f
    private var dX = 0f
    private var dY = 0f
    private var statusbarHeight: Int = 0
    private var navbarHeight: Int = 0
    private var tempScreenWidth: Float = 1080F
    private var tempScreenHeight: Float = 1605F
    private var width: Int = 0
    private var height: Int = 0

    private lateinit var symbolList: ArrayList<String>

    private lateinit var recognizer: TextRecognizer
    private var mResultCode: Int = 0
    private lateinit var mResultData: Intent
    private lateinit var mMediaProjection: MediaProjection
    private lateinit var mMediaProjectionManager: MediaProjectionManager
    private lateinit var mImageReader: ImageReader


    private var isOverlayActive = false
    private var isDetailOverlayActive = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent == null) {
            return START_NOT_STICKY
        }
        if (intent.action == "startBubble") {
            windowManager.addView(floatButtonView, floatWindowLayoutParams)
            mResultCode = intent.getIntExtra("code", 0)
            mResultData = intent.getParcelableExtra("data")!!

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationChannel =
                    NotificationChannel("YOMI", "YOMI", NotificationManager.IMPORTANCE_NONE)
                val notificationManager =
                    getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(notificationChannel)
                val notification: Notification = Notification.Builder(applicationContext, "YOMI")
                    .setOngoing(true)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        1,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
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
        width = metrics.widthPixels
        height = metrics.heightPixels

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        inflater = baseContext.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        Log.e("DEBUGPOS", (height - height / 3).toString())
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
                } else if (floatWindowLayoutParams.y >= height - height / 4) {
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



        recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

        mMediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        var resourceID = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceID > 0) {
            statusbarHeight = resources.getDimensionPixelSize(resourceID)
        }
        resourceID = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceID > 0) {
            navbarHeight = resources.getDimensionPixelSize(resourceID)
        }
    }

    private fun bubbleClick() {
        if (isOverlayActive) {
            windowManager.removeView(selectionOverlayView)
            isOverlayActive = false
        } else {
            windowManager.removeView(floatButtonView)
            analyzeImage(getScreenshot())
            windowManager.addView(selectionOverlayView, selectionOverlayLayoutParams)
            windowManager.addView(floatButtonView, floatWindowLayoutParams)

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
        val bitmap =
            Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            36,
            statusbarHeight,
            1000,
            metrics.heightPixels - statusbarHeight - navbarHeight
        )
        image.close()
        return croppedBitmap
    }

    private fun analyzeImage(bitmap: Bitmap) {
        selectionOverlayView = inflater.inflate(R.layout.overlay_selection, null) as ViewGroup
        selectionOverlayLayoutParams = WindowManager.LayoutParams(
            width,
            height,
            LAYOUT_TYPE!!,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { text ->
                processResult(text)
            }
    }

    private fun processResult(text: Text) {
        for (block in text.textBlocks) {
            Log.e("DEBUGTEXT", block.text)
            val boundingBox = block.boundingBox
            val newButton: Button = Button(this)
            if (boundingBox != null) {
                val buttonParams: RelativeLayout.LayoutParams = RelativeLayout.LayoutParams(
                    tempScalerX(boundingBox.width() * 1.1.toInt()),
                    tempScalerY(boundingBox.height() * 1.1.toInt())
                )
                newButton.x = tempScalerX(boundingBox.left).toFloat()
                newButton.y = tempScalerY(boundingBox.top).toFloat()
                newButton.setBackgroundResource(R.drawable.button_style)
                newButton.text = block.text
                newButton.textSize = 0F

                newButton.setOnClickListener {
                    processDetailOverlay(block)
                    Log.e("OnScreenButton", block.text)
                }
                selectionOverlayView.addView(newButton, buttonParams)
            }
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

    private fun processDetailOverlay(text: Text.TextBlock) {
        if (isDetailOverlayActive) {
            windowManager.removeView(detailOverlayView)
        }
        initDetailOverlay()
        symbolList = arrayListOf("")
        for (line in text.lines) {
            for (element in line.elements) {
                for (symbol in element.symbols) {
                    Log.e("DEBUGPROCESSDETAIL", symbol.text)
                    val symbolButton: Button = Button(this)
                    symbolButton.id = View.generateViewId()
                    symbolButton.text = symbol.text
                    symbolButton.setBackgroundColor(Color.parseColor("#FBFBFB"))
                    val symbolButtonLayoutParams = WindowManager.LayoutParams(100, 100, LAYOUT_TYPE!!, WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT)
                    detailOverlayTextButtonLayout.addView(symbolButton, symbolButtonLayoutParams)

                    val newReferences = flowView.referencedIds.toMutableList()
                    newReferences.add(symbolButton.id)
                    flowView.referencedIds = newReferences.toIntArray()

                    symbolList.add(symbol.text)
                }

            }
        }
        windowManager.addView(detailOverlayView, detailOverlayLayoutParams)
    }

    private fun initDetailOverlay() {
        detailOverlayView = inflater.inflate(R.layout.overlay_detail, null) as ViewGroup
        detailOverlayLayoutParams = WindowManager.LayoutParams(
            width,
            height,
            LAYOUT_TYPE!!,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        detailOverlayLayoutParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        val frame = detailOverlayView.findViewById<FrameLayout>(R.id.frameLayout)
        frame.setOnTouchListener { view, motionEvent ->
            windowManager.removeView(detailOverlayView)
            true
        }
        val overlayFrame = detailOverlayView.findViewById<ConstraintLayout>(R.id.overlayFrame)
        overlayFrame.setOnTouchListener { view, motionEvent ->
            true
        }
        detailOverlayTextButtonLayout = detailOverlayView.findViewById(R.id.textLayout)
        flowView = detailOverlayView.findViewById(R.id.flowView)
        overlayFrame.findViewById<LinearLayout>(R.id.layoutFrame).setBackgroundResource(R.drawable.detail_overlay_style)
    }

    override fun onDestroy() {
        windowManager.removeView(floatButtonView)
        if (isOverlayActive) {
            windowManager.removeView(selectionOverlayView)
        }
        super.onDestroy()
    }

    private fun tempScalerX(input: Int): Int {
        val scale: Float = 1.08F
        return (input * scale).toInt()
    }

    private fun tempScalerY(input: Int): Int {
        val scale: Float = 1.077F
        return (input * scale).toInt()
    }
}
