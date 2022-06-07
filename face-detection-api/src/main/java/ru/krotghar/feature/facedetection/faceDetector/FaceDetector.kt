package ru.krotghar.feature.facedetection.faceDetector

import android.graphics.Rect
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import ru.krotghar.feature.facedetection.faceDetector.FaceDetector.FaceDetectorSettings.ContourMode.ALL
import java.io.File

/**
 * Внешнее API для работы с детектором лица
 */
interface FaceDetector {

    /**
     * Старт процесса распознавания лиц в live режиме
     *
     * @param lifecycleOwner владелец жизненного цикла для отслеживания
     * @param options набор опций для процесса распознавания
     * @param onFacesDetected коллбэк для возврата результата распознавания
     * @param onFailure коллбэк для возврата ошибки
     * @param faceDetectorSettings настройки для детектора лиц. Можно настроить необходимость распознавания тех или
     * иных параметров. Если передать `null`, то будут использоваться настройки по-умолчанию
     * @param previewView [PreviewView] для настройки отображения пользователю потока с камеры в реальном времени
     */
    fun startDetection(
        lifecycleOwner: LifecycleOwner,
        options: Options,
        onFacesDetected: OnFacesDetected? = null,
        onFailure: ((Throwable) -> (Unit))? = null,
        faceDetectorSettings: FaceDetectorSettings? = null,
        previewView: PreviewView? = null,
    )

    /**
     * Остановка процесса распознавания лиц
     */
    fun stopDetection()

    /**
     * Обработчик результатов распознавания
     */
    interface OnFacesDetected {

        /**
         * Коллбэк для возврата результатов распознавания
         *
         * @param helper помощник для работы с результатами распознавания
         */
        fun onDetected(helper: FaceDetectionHelper)
    }

    /**
     * Настройки процесса распознавания лица
     *
     * @property cameraSelector камера, которую необходимо использовать
     * @property analyzerRotation целевой угол поворота картинки, предоставляемой распознавателю для анализа
     * @property analyzerResolution целевое разрешение картинки, предоставляемой распознавателю для анализа
     * @property isImageCaptureEnabled флаг включенности возможности сохранения картинки из потока
     * @property captureTargetResolution целевое разрешение фотографии при снятии скриншота
     * @see CameraSelector
     */
    data class Options(
        val cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
        val analyzerRotation: Int? = null,
        val analyzerResolution: Size? = null,
        val isImageCaptureEnabled: Boolean = false,
        val captureTargetResolution: Size? = null
    )

    /**
     * Помощник при обработке распознавания лица
     */
    interface FaceDetectionHelper {

        /**
         * @return картинка, которая использовалась для распознавания
         */
        fun getImage(): ImageProxy

        /**
         * @return список найденных лиц
         */
        fun getFaces(): List<Face>

        /**
         * Сохраняет фото по указанному пути
         *
         * @param file файл для сохранения изображения
         */
        fun savePhoto(file: File)
    }

    /**
     * Настройки распознавателя лиц
     *
     * @property contourMode настройка распознавания контуров лица
     * @property classificationMode настройка распознавания различных атрибутов (вероятность улыбки или закрытых глаз)
     */
    data class FaceDetectorSettings(
        val contourMode: ContourMode = ALL,
        val classificationMode: ClassificationMode = ClassificationMode.ALL
    ) {
        /* Режим распознавания контуров лица */
        enum class ContourMode { ALL }
        /* Режим распознавания различных атрибутов (вероятность улыбки или закрытых глаз) */
        enum class ClassificationMode { ALL }
    }

    /**
     * Обертка параметров распознанного лица
     *
     * @property boundingBox прямоугольник контура лица
     * @property headEulerAngleX угол поворота головы по оси X
     * @property headEulerAngleY угол поворота головы по оси Y
     * @property headEulerAngleZ угол поворота головы по оси Z
     */
    data class Face(
        val boundingBox: Rect,
        val headEulerAngleX: Float,
        val headEulerAngleY: Float,
        val headEulerAngleZ: Float
    )
}