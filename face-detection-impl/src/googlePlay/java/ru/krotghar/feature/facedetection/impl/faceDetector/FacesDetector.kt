package ru.krotghar.feature.facedetection.impl.faceDetector

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.face.Face

/**
 * Внутренний анализатор фотографий для распознавания лиц
 */
internal interface FacesDetector {

    /**
     * Распознавание лиц на предоставленной картинке с возвратом результата или ошибки в указанные коллбэки
     *
     * @param imageProxy картинка для анализа
     * @param onFacesDetected коллбэк для возврата результата распознавания лиц. Возвращает картнику,
     * на которой производилось распознавание, а также список распознаных лиц
     * @param onFailure коллбэк для возврата ошибки
     */
    fun analyze(
        imageProxy: ImageProxy,
        onFacesDetected: ((Pair<ImageProxy, List<Face>>) -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    )
}