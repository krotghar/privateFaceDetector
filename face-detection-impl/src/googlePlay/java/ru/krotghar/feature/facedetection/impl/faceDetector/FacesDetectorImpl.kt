package ru.krotghar.feature.facedetection.impl.faceDetector

import android.annotation.SuppressLint
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import timber.log.Timber

/**
 * Базовая реализация [FacesDetector]
 *
 * @param faceDetectorOptions настройки детектора лиц
 */
internal class FacesDetectorImpl(faceDetectorOptions: FaceDetectorOptions) : FacesDetector {

    private val detector = FaceDetection.getClient(faceDetectorOptions)

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(
        imageProxy: ImageProxy,
        onFacesDetected: ((Pair<ImageProxy, List<Face>>) -> Unit)?,
        onFailure: ((Throwable) -> Unit)?
    ) {
        val mediaImage = imageProxy.image
        mediaImage ?: return

        detector.process(InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { faces -> onFacesDetected?.invoke(imageProxy to faces) }
            .addOnFailureListener {
                Timber.tag(TAG).e(it)
                onFailure?.invoke(it)
            }
    }

    companion object {

        private const val TAG = "FacesDetector"
    }
}