package com.capstone.komunitas.tracking

import android.content.Context
import android.graphics.*
import android.text.TextUtils
import android.util.Log
import android.util.Pair
import android.util.TypedValue
import com.capstone.komunitas.tflite.Classifier
import com.capstone.komunitas.utils.Utils
import java.util.*

class MultiBoxTracker(context: Context) {

    internal val screenRects: MutableList<Pair<Float, RectF>> = LinkedList()
    private val availableColors = LinkedList<Int>()
    private val trackedObjects = LinkedList<TrackedRecognition>()
    private val boxPaint = Paint()
    private val textSizePx: Float
    private val borderedText: BorderedText
    private var frameToCanvasMatrix: Matrix? = null
    private var frameWidth: Int = 0
    private var frameHeight: Int = 0
    private var sensorOrientation: Int = 0

    private class TrackedRecognition {
        internal var location: RectF? = null
        internal var detectionConfidence: Float = 0.toFloat()
        internal var color: Int = 0
        internal var title: String? = null
    }

    init {
        for (color in COLORS) {
            availableColors.add(color)
        }
        boxPaint.strokeCap = Paint.Cap.SQUARE
        boxPaint.strokeJoin = Paint.Join.ROUND
        boxPaint.color = Color.WHITE
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeMiter = 100f
        boxPaint.strokeWidth = 1.0f
        textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.resources.displayMetrics)
        borderedText = BorderedText(textSizePx)
    }

    @Synchronized
    fun setFrameConfiguration(
        width: Int, height: Int, sensorOrientation: Int) {
        frameWidth = width
        frameHeight = height
        this.sensorOrientation = sensorOrientation
    }

    @Synchronized
    fun drawDebug(canvas: Canvas) {
        val textPaint = Paint()
        textPaint.color = Color.WHITE
        textPaint.textSize = 60.0f

        val boxPaint = Paint()
        boxPaint.color = Color.RED
        boxPaint.alpha = 200
        boxPaint.style = Paint.Style.STROKE

        for (detection in screenRects) {
            val rect = detection.second
            canvas.drawRect(rect, boxPaint)
            canvas.drawText("" + detection.first, rect.left, rect.top, textPaint)
            borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first)
        }
    }

    @Synchronized
    fun trackResults(results: List<Classifier.Recognition>, timestamp: Long) {
        Log.i(TAG, "\"Processing $timestamp results from ${ results.size} ")
        processResults(results)
    }

    @Synchronized
    fun draw(canvas: Canvas) {
        val rotated = sensorOrientation % 180 == 90
        val multiplier = Math.min(
            canvas.height / (if (rotated) frameWidth else frameHeight).toFloat(),
            canvas.width / (if (rotated) frameHeight else frameWidth).toFloat())
        frameToCanvasMatrix = Utils.getTransformationMatrix(
            frameWidth,
            frameHeight,
            (multiplier * if (rotated) frameHeight else frameWidth).toInt(),
            (multiplier * if (rotated) frameWidth else frameHeight).toInt(),
            sensorOrientation,
            false)
        for (recognition in trackedObjects) {
            val trackedPos = RectF(recognition.location)

            frameToCanvasMatrix!!.mapRect(trackedPos)
            boxPaint.color = recognition.color

            val cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f
            canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint)

            val labelString = if (!TextUtils.isEmpty(recognition.title))
                String.format("%s %.2f", recognition.title, 100 * recognition.detectionConfidence)
            else
                String.format("%.2f", 100 * recognition.detectionConfidence)
            //            borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.top,
            // labelString);
            borderedText.drawText(
                canvas, trackedPos.left + cornerSize, trackedPos.top, "$labelString%", boxPaint)
        }
    }

    private fun processResults(results: List<Classifier.Recognition>) {
        val rectsToTrack = LinkedList<Pair<Float, Classifier.Recognition>>()

        screenRects.clear()
        val rgbFrameToScreen = Matrix(frameToCanvasMatrix)

        for (result in results) {
            if (result.location == null) {
                continue
            }
            val detectionFrameRect = RectF(result.location)

            val detectionScreenRect = RectF()
            rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect)

            Log.d(TAG, "Result! Frame: " + result.location + " mapped to screen:" + detectionScreenRect)

            screenRects.add(Pair(result.confidence, detectionScreenRect))

            if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
                Log.d(TAG,"Degenerate rectangle! $detectionFrameRect")
                continue
            }

            rectsToTrack.add(Pair(result.confidence, result))
        }

        if (rectsToTrack.isEmpty()) {
            Log.d(TAG, "processResults: Nothing to track, aborting.")
            return
        }

        trackedObjects.clear()
        for (potential in rectsToTrack) {
            val trackedRecognition = TrackedRecognition()
            trackedRecognition.detectionConfidence = potential.first
            trackedRecognition.location = RectF(potential.second.location)
            trackedRecognition.title = potential.second.title
            trackedRecognition.color = COLORS[trackedObjects.size]
            trackedObjects.add(trackedRecognition)

            if (trackedObjects.size >= COLORS.size) {
                break
            }
        }
    }

    companion object {
        private val TEXT_SIZE_DIP = 18f
        private val MIN_SIZE = 16.0f
        private val COLORS = intArrayOf(
            Color.BLUE, Color.RED, Color.GREEN,
            Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE, Color.parseColor("#55FF55"),
            Color.parseColor("#FFA500"), Color.parseColor("#FF8888"),
            Color.parseColor("#AAAAFF"), Color.parseColor("#FFFFAA"),
            Color.parseColor("#55AAAA"), Color.parseColor("#AA33AA"),
            Color.parseColor("#0D0068"))

        private const val TAG = "MultiBoxTracker"
    }
}