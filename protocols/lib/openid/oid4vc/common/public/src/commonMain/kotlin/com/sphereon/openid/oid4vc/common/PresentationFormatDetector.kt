/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vc.common

import com.sphereon.openid.oid4vc.common.vcdm.VcdmClassifier

/** Detects the format of a VCDM presentation representation. */
object PresentationFormatDetector {
    /**
     * Classify a compact JWT presentation. Bare JSON-LD presentations require their secured
     * representation metadata and are intentionally not guessed from an arbitrary string.
     */
    fun detect(value: String): PresentationFormat? {
        if (!value.contains('.')) return null
        return VcdmClassifier.classifyCompactJws(value).getOrNull()?.presentationFormat
    }
}

/** Detect a presentation representation. */
fun String.detectPresentationFormat(): PresentationFormat? = PresentationFormatDetector.detect(this)
