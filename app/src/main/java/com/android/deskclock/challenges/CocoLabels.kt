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

package com.android.deskclock.challenges

/**
 * The object classes the bundled detector can recognise, used as the photo challenge's
 * target vocabulary.
 *
 * These are the 80 COCO detection classes, which is what the bundled EfficientDet-Lite0
 * model was trained on. The strings must match the category names the detector reports,
 * so comparisons are case insensitive but the spellings are otherwise exact.
 */
object CocoLabels {

    /** Chosen because brushing your teeth is hard to do from bed. */
    const val DEFAULT_TARGET = "toothbrush"

    /** All 80 classes, in the model's own label order. */
    val ALL: List<String> = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane",
        "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird",
        "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack",
        "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat",
        "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
        "wine glass", "cup", "fork", "knife", "spoon",
        "bowl", "banana", "apple", "sandwich", "orange",
        "broccoli", "carrot", "hot dog", "pizza", "donut",
        "cake", "chair", "couch", "potted plant", "bed",
        "dining table", "toilet", "tv", "laptop", "mouse",
        "remote", "keyboard", "cell phone", "microwave", "oven",
        "toaster", "sink", "refrigerator", "book", "clock",
        "vase", "scissors", "teddy bear", "hair drier", "toothbrush",
    )

    /**
     * Offered first in the target picker. These are things that are normally somewhere
     * other than the bedroom, which is the whole point of the challenge, and that the
     * model detects reliably.
     */
    val SUGGESTED: List<String> = listOf(
        "toothbrush", "sink", "toilet", "cup", "bottle",
        "refrigerator", "microwave", "oven", "book", "laptop",
        "clock", "potted plant", "teddy bear", "tv", "keyboard",
    )

    private val lookup: Set<String> = ALL.toSet()

    /** True when [label] is a class the bundled model can actually detect. */
    fun isKnown(label: String): Boolean = label.lowercase() in lookup

    /** True when [detected] satisfies a photo challenge looking for any of [targets]. */
    fun matches(detected: String, targets: Collection<String>): Boolean {
        val normalized = detected.lowercase()
        return targets.any { it.lowercase() == normalized }
    }
}
