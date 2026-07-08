package com.answercard.grader.miniprogram

import com.answercard.grader.template.CardLayout
import com.answercard.grader.template.CornerAnchorReference
import com.answercard.grader.template.TemplateGeometry

object AnchorReferenceResolver {
    fun isSolidMarker(anchors: MiniProgramAnchors): Boolean =
        listOf(anchors.lu, anchors.ld, anchors.ru, anchors.rd)
            .all { it.source == SolidCornerMarkerDetector.SOURCE }

    fun projectionReference(cardLayout: CardLayout, anchors: MiniProgramAnchors): CornerAnchorReference =
        if (isSolidMarker(anchors)) {
            TemplateGeometry.cornerMarkerCenters(cardLayout)
        } else {
            TemplateGeometry.cornerAnchorReference(cardLayout)
        }
}
