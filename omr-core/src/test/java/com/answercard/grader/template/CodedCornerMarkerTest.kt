package com.answercard.grader.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodedCornerMarkerTest {
    @Test
    fun payloadsAndRotationsHaveAtLeastSixBitsDistance() {
        val variants = buildList {
            CornerMarkerId.entries.forEach { id ->
                repeat(4) { rotation ->
                    add(id to CodedCornerMarker.rotateClockwise(CodedCornerMarker.payload(id), rotation))
                }
            }
        }

        variants.forEachIndexed { index, first ->
            variants.drop(index + 1).forEach { second ->
                assertTrue(
                    "${first.first} and ${second.first} are too close",
                    CodedCornerMarker.hammingDistance(first.second, second.second) >= 6,
                )
            }
        }
    }

    @Test
    fun markerHasBlackOuterRingAndExpectedPayload() {
        CornerMarkerId.entries.forEach { id ->
            for (index in 0 until CodedCornerMarker.GRID_SIZE) {
                assertTrue(CodedCornerMarker.isDark(id, 0, index))
                assertTrue(CodedCornerMarker.isDark(id, CodedCornerMarker.GRID_SIZE - 1, index))
                assertTrue(CodedCornerMarker.isDark(id, index, 0))
                assertTrue(CodedCornerMarker.isDark(id, index, CodedCornerMarker.GRID_SIZE - 1))
            }
            for (row in 0 until CodedCornerMarker.PAYLOAD_SIZE) {
                for (column in 0 until CodedCornerMarker.PAYLOAD_SIZE) {
                    assertEquals(
                        CodedCornerMarker.payloadBit(CodedCornerMarker.payload(id), row, column),
                        CodedCornerMarker.isDark(id, row + 1, column + 1),
                    )
                }
            }
        }
    }
}
