/*
 * Copyright (C) 2026 Ammar Siddiqui
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock.challenges.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.challenges.ChallengeTuning
import com.android.deskclock.challenges.PhotoChallenge
import com.android.deskclock.challenges.engine.Detection
import com.android.deskclock.challenges.engine.PhotoMatcher
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Point the camera at a named object to dismiss the alarm.
 *
 * Detection runs continuously on the preview rather than behind a shutter button, so there
 * is nothing to press while holding a phone at arm's length. [PhotoMatcher] decides when
 * enough consecutive frames have matched; this fragment only supplies frames and draws.
 *
 * Without camera permission the challenge reports itself unavailable and is skipped. A
 * permission dialog is not reliably reachable above the lock screen, and an alarm nobody
 * can turn off is worse than one challenge going unenforced.
 */
class PhotoChallengeFragment : ChallengeFragment() {

    /** Targets already photographed. A set, because each photo must be a different one. */
    private class State {
        val captured = mutableSetOf<String>()
    }

    private val state: State by lazy { runner.engine(engineKey) { State() } }

    private lateinit var previewView: PreviewView
    private lateinit var statusView: TextView
    private lateinit var targetView: TextView

    private var detector: ObjectDetector? = null
    private var analysisExecutor: ExecutorService? = null
    private var matcher: PhotoMatcher? = null

    /** Set while a successful capture is being acknowledged, so frames stop counting. */
    private var isPaused = false

    override val contentLayoutRes: Int get() = R.layout.challenge_content_photo

    override val promptText: String get() = getString(R.string.challenge_photo_prompt)

    override fun onContentCreated(view: View) {
        previewView = view.findViewById(R.id.photo_preview)
        statusView = view.findViewById(R.id.photo_status)

        val photo = config as PhotoChallenge
        targetView = view.findViewById(R.id.photo_target)
        targetView.text = describeTargets(remainingTargets())

        if (!hasCameraPermission()) {
            unavailable(getString(R.string.challenge_photo_no_camera_permission))
            return
        }

        // Built from what is left rather than every target, so a configuration change
        // part way through does not re-offer one that has already been photographed.
        matcher = PhotoMatcher(remainingTargets(), photo.difficulty)
        updateStatus()
        startCamera()
    }

    override fun onDestroyView() {
        analysisExecutor?.shutdown()
        analysisExecutor = null
        detector?.close()
        detector = null
        super.onDestroyView()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    /** Targets still outstanding, in the order they were configured. */
    private fun remainingTargets(): List<String> =
        (config as PhotoChallenge).targets.filterNot { it in state.captured }

    /**
     * "Cup and sink" when every one of them still has to be photographed, "Cup or sink"
     * when there are more targets left than photos left and so any of them will do.
     */
    private fun describeTargets(targets: List<String>): String {
        val photo = config as PhotoChallenge
        val stillNeeded = photo.photos - state.captured.size
        val separator = if (targets.size <= stillNeeded) {
            R.string.challenge_photo_target_separator_all
        } else {
            R.string.challenge_photo_target_separator
        }
        return targets.joinToString(getString(separator)) {
            it.replaceFirstChar(Char::uppercase)
        }
    }

    private fun startCamera() {
        val executor = Executors.newSingleThreadExecutor()
        analysisExecutor = executor

        try {
            detector = buildDetector()
        } catch (e: Exception) {
            // A missing or unreadable model must not strand the user on a dead screen.
            LOGGER.e("Could not open the object detector", e)
            unavailable(getString(R.string.challenge_photo_no_detector))
            return
        }

        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            if (!isAdded) return@addListener
            try {
                bindUseCases(providerFuture.get(), executor)
            } catch (e: Exception) {
                LOGGER.e("Could not start the camera", e)
                unavailable(getString(R.string.challenge_photo_no_camera))
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun buildDetector(): ObjectDetector {
        val photo = config as PhotoChallenge
        val tuning = ChallengeTuning.photo(photo.difficulty)
        val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET)
                        .build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMaxResults(MAX_RESULTS)
                // Kept below the tuned threshold so PhotoMatcher owns the decision, and the
                // streak rule stays the only thing that can pass the challenge.
                .setScoreThreshold(tuning.scoreThreshold / 2f)
                .setResultListener { result, _ -> onDetections(result) }
                .setErrorListener { error -> LOGGER.e("Detection failed: %s", error.message) }
                .build()
        return ObjectDetector.createFromOptions(requireContext(), options)
    }

    private fun bindUseCases(provider: ProcessCameraProvider, executor: ExecutorService) {
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
        analysis.setAnalyzer(executor, ::analyze)

        provider.unbindAll()
        provider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                preview, analysis)
    }

    private fun analyze(image: ImageProxy) {
        image.use {
            if (isPaused || !isAdded) return
            val detector = detector ?: return
            val bitmap = try {
                it.toBitmap()
            } catch (e: Exception) {
                LOGGER.e("Could not read a camera frame", e)
                return
            }
            val options = ImageProcessingOptions.builder()
                    .setRotationDegrees(it.imageInfo.rotationDegrees)
                    .build()
            detector.detectAsync(BitmapImageBuilder(bitmap).build(), options,
                    it.imageInfo.timestamp / 1_000_000)
        }
    }

    /** Called on the analysis thread, so anything touching views hops to the main thread. */
    private fun onDetections(
        result: com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult,
    ) {
        val detections = result.detections().flatMap { detection ->
            detection.categories().map { Detection(it.categoryName(), it.score()) }
        }
        val matcher = matcher ?: return
        val found = if (matcher.onFrame(detections)) matcher.matchedTarget else null

        view?.post {
            if (!isAdded || isPaused) return@post
            if (found != null) onTargetFound(found) else updateStatus()
        }
    }

    private fun onTargetFound(target: String) {
        val photo = config as PhotoChallenge
        isPaused = true
        state.captured += target
        statusView.text = getString(R.string.challenge_photo_found)

        if (state.captured.size >= photo.photos) {
            pass()
            return
        }

        // Ask for the next one after a beat, so the confirmation is readable. The matcher
        // is rebuilt on what is left rather than reset, so holding the camera on the object
        // just photographed cannot satisfy the next photo too.
        view?.postDelayed({
            if (!isAdded) return@postDelayed
            val remaining = remainingTargets()
            matcher = PhotoMatcher(remaining, photo.difficulty)
            targetView.text = describeTargets(remaining)
            isPaused = false
            updateStatus()
        }, FOUND_PAUSE_MILLIS)
    }

    private fun updateStatus() {
        val photo = config as PhotoChallenge
        statusView.text = if (photo.photos > 1) {
            getString(R.string.challenge_photo_step, state.captured.size + 1, photo.photos)
        } else {
            getString(R.string.challenge_photo_searching)
        }
    }

    companion object {
        private const val MODEL_ASSET = "efficientdet_lite0.tflite"
        private const val MAX_RESULTS = 8
        private const val FOUND_PAUSE_MILLIS = 1200L
        private val LOGGER = LogUtils.Logger("PhotoChallenge")
    }
}
