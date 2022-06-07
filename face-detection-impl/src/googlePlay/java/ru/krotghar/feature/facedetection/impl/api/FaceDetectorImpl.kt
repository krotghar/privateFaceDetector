package ru.krotghar.feature.facedetection.impl.api

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceDetectorOptions.CLASSIFICATION_MODE_ALL
import com.google.mlkit.vision.face.FaceDetectorOptions.CONTOUR_MODE_ALL
import ru.krotghar.feature.facedetection.faceDetector.FaceDetector
import ru.krotghar.feature.facedetection.faceDetector.FaceDetector.FaceDetectionHelper
import ru.krotghar.feature.facedetection.faceDetector.FaceDetector.FaceDetectorSettings
import ru.krotghar.feature.facedetection.faceDetector.FaceDetector.FaceDetectorSettings.ClassificationMode
import ru.krotghar.feature.facedetection.faceDetector.FaceDetector.FaceDetectorSettings.ContourMode
import ru.krotghar.feature.facedetection.faceDetector.FaceDetector.OnFacesDetected
import ru.krotghar.feature.facedetection.faceDetector.FaceDetector.Options
import ru.krotghar.feature.facedetection.impl.faceDetector.FacesDetector
import ru.krotghar.feature.facedetection.impl.faceDetector.FacesDetectorImpl
import timber.log.Timber
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Реализация апи распознавания лиц
 *
 * @property context контекст
 */
internal class FaceDetectorImpl @Inject constructor(private val context: Context) : FaceDetector {

    private lateinit var faceDetector: FacesDetector
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var onFacesDetected: OnFacesDetected? = null
    private var imageCapture: ImageCapture? = null

    override fun startDetection(
        lifecycleOwner: LifecycleOwner,
        options: Options,
        onFacesDetected: OnFacesDetected?,
        onFailure: ((Throwable) -> Unit)?,
        faceDetectorSettings: FaceDetectorSettings?,
        previewView: PreviewView?
    ) {
        val detectorOptions =
            faceDetectorSettings?.let { buildFaceDetectionOptions(it) } ?: defaultFaceDetectionOptions()
        faceDetector = FacesDetectorImpl(detectorOptions)
        this.onFacesDetected = onFacesDetected
        startProcess(lifecycleOwner, options, onFailure, previewView)
    }

    override fun stopDetection() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            { cameraProviderFuture.get().unbindAll() }, ContextCompat.getMainExecutor(context)
        )
    }

    private fun startProcess(
        lifecycleOwner: LifecycleOwner,
        options: Options,
        onFailure: ((Throwable) -> Unit)?,
        previewView: PreviewView?
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val imageAnalysis = buildImageAnalyzer(options, onFailure)
            val preview: Preview? = previewView?.let { buildPreview(it) }
            imageCapture = buildImageCapture(options)

            try {
                val useCases = mutableListOf<UseCase>().apply {
                    add(imageAnalysis)
                    preview?.let { add(it) }
                    imageCapture?.let { add(it) }
                }
                cameraProvider.apply {
                    unbindAll()
                    bindToLifecycle(
                        lifecycleOwner,
                        options.cameraSelector,
                        *useCases.toTypedArray()
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error on face detection cameraX start")
                onFailure?.invoke(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun buildImageAnalyzer(options: Options, onFailure: ((Throwable) -> Unit)?): ImageAnalysis {
        val imageAnalysisBuilder = ImageAnalysis.Builder()
        val targetResolution: Size
        val targetRotation: Int

        with(options) {
            targetResolution = analyzerResolution ?: Size(DEFAULT_RESOLUTION_WIDTH, DEFAULT_RESOLUTION_HEIGHT)
            targetRotation = analyzerRotation ?: DEFAULT_TARGET_ROTATION
        }

        getCameraFpsRange()?.let {
            Camera2Interop.Extender(imageAnalysisBuilder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
        }

        return imageAnalysisBuilder.setTargetRotation(targetRotation)
            .setTargetResolution(targetResolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().also {
                it.setAnalyzer(
                    cameraExecutor,
                    {
                        faceDetector.analyze(
                            it,
                            this@FaceDetectorImpl::onFacesDetected,
                            onFailure
                        )
                    })
            }
    }

    private fun buildPreview(previewView: PreviewView): Preview = previewView.let { view ->
        Preview.Builder()
            .build()
            .apply {
                setSurfaceProvider(view.createSurfaceProvider())
            }
    }

    private fun buildImageCapture(options: Options): ImageCapture? = options.run {
        if (isImageCaptureEnabled) {
            val imageCaptureBuilder = ImageCapture.Builder()
            captureTargetResolution?.let { imageCaptureBuilder.setTargetResolution(it) }
            imageCaptureBuilder.build()
        } else {
            null
        }
    }

    private fun buildFaceDetectionOptions(settings: FaceDetectorSettings) = FaceDetectorOptions.Builder().apply {
        setContourMode(settings.contourMode.mapIntoLibConstant())
        setClassificationMode(settings.classificationMode.mapIntoLibConstant())
    }.build()

    private fun defaultFaceDetectionOptions() = FaceDetectorOptions.Builder()
        .setContourMode(CONTOUR_MODE_ALL)
        .setClassificationMode(CLASSIFICATION_MODE_ALL)
        .build()

    private fun getCameraFpsRange(): Range<Int>? {
        val manager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            if (manager.cameraIdList.isEmpty()) {
                Timber.tag(TAG).w("cameraId list is empty")
                return null
            }
            val cameraId = manager.findFrontCameraId()
            if (cameraId == null) {
                Timber.tag(TAG).w("back cameraId is null")
                return null
            }

            val chars = manager.getCameraCharacteristics(cameraId)
            val ranges: Array<Range<Int>>? = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            if (ranges == null) {
                Timber.tag(TAG).w("camera ranges is null")
                return null
            }

            for (range in ranges) {
                Timber.tag(TAG).d("Fps: upper: ${range.upper}, range: $range")
            }
            ranges.findMinLowerBound30Fps()?.let {
                Timber.tag(TAG).d("selected camera rate range (min 30 fps): $it")
                return it
            }
            return ranges.findMaxUpperBound()?.also {
                Timber.tag(TAG).d("selected camera rate range (max upper): $it")
            }
        } catch (e: CameraAccessException) {
            Timber.tag(TAG).e(e, "camera access error")
            null
        }
    }

    private fun CameraManager.findFrontCameraId(): String? {
        for (cameraId in cameraIdList) {
            if (getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) return cameraId
        }
        return null
    }

    private fun Array<Range<Int>>.findMinLowerBound30Fps(): Range<Int>? =
        filter { it.lower >= MIN_DESIRED_FPS }.minByOrNull { it.upper }

    private fun Array<Range<Int>>.findMaxUpperBound(): Range<Int>? = maxByOrNull { it.upper }

    @FaceDetectorOptions.ContourMode
    private fun ContourMode.mapIntoLibConstant(): Int = CONTOUR_MODE_ALL

    @FaceDetectorOptions.ClassificationMode
    private fun ClassificationMode.mapIntoLibConstant(): Int = CLASSIFICATION_MODE_ALL

    private fun onFacesDetected(pair: Pair<ImageProxy, List<Face>>) {
        pair.apply {
            onFacesDetected?.onDetected(FaceDetectionHelperImpl(context, first, second, imageCapture))
            first.close()
        }
    }

    /**
     * Базовая реализация [FaceDetectionHelper]
     *
     * @property context контекст
     * @property image картинка, которая использовалась для анализа
     * @property faces список распознанных лиц
     * @property imageCapture объект для снятия скриншота при необходимости
     */
    class FaceDetectionHelperImpl(
        private val context: Context,
        private val image: ImageProxy,
        private val faces: List<Face>,
        private val imageCapture: ImageCapture?
    ) : FaceDetectionHelper {

        override fun getImage(): ImageProxy = image

        override fun getFaces(): List<FaceDetector.Face> = faces.map { it.map() }

        override fun savePhoto(file: File) {
            val meta = ImageCapture.Metadata()
            meta.isReversedHorizontal = true
            // Create output options object which contains file + metadata
            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).setMetadata(meta).build()

            imageCapture?.takePicture(
                outputOptions, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Timber.tag(TAG).e(exc, "Photo capture failed: ${exc.message}")
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Timber.tag(TAG).d("Photo capture succeeded: ${Uri.fromFile(file)}")
                    }
                }) ?: Timber.tag(TAG).w("image capture is null on photo save")
        }

        private fun Face.map() = FaceDetector.Face(boundingBox, headEulerAngleX, headEulerAngleY, headEulerAngleZ)
    }

    companion object {

        private const val TAG = "FaceDetectorApiImpl"
        private const val DEFAULT_RESOLUTION_WIDTH = 640
        private const val DEFAULT_RESOLUTION_HEIGHT = 960
        private const val DEFAULT_TARGET_ROTATION = Surface.ROTATION_0
        private const val MIN_DESIRED_FPS = 30
    }
}