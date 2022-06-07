package ru.krotghar.feature.facedetection.api

import ru.krotghar.feature.facedetection.availabilityChecker.FaceDetectionAvailabilityChecker
import ru.krotghar.feature.facedetection.faceDetector.FaceDetector

/**
 * Внешнее АПИ процесса распознавания лица
 */
interface FaceDetectionApi {

    /**
     * @return детектор лиц
     */
    fun getFaceDetector(): FaceDetector

    /**
     * @return утилита для проверки доступности фичи
     */
    fun getAvailabilityChecker(): FaceDetectionAvailabilityChecker
}