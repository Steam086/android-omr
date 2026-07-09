package com.answercard.grader.template

/**
 * Dependency-free fiducial used by both Android and desktop renderers.
 *
 * The outer ring of the 6x6 marker is always black. The inner 4x4 payloads were
 * selected so every marker and all of its rotations are at least six bits apart.
 */
object CodedCornerMarker {
    const val VERSION = 1
    const val GRID_SIZE = 6
    const val PAYLOAD_SIZE = 4

    private val payloads = mapOf(
        CornerMarkerId.LU to 0x76A6,
        CornerMarkerId.RU to 0xB0E5,
        CornerMarkerId.LD to 0x1247,
        CornerMarkerId.RD to 0x8323,
    )

    fun payload(id: CornerMarkerId): Int = payloads.getValue(id)

    fun isDark(id: CornerMarkerId, row: Int, column: Int): Boolean {
        require(row in 0 until GRID_SIZE && column in 0 until GRID_SIZE) { "cell must be inside marker grid" }
        if (row == 0 || column == 0 || row == GRID_SIZE - 1 || column == GRID_SIZE - 1) return true
        return payloadBit(payload(id), row - 1, column - 1)
    }

    fun payloadBit(payload: Int, row: Int, column: Int): Boolean {
        require(row in 0 until PAYLOAD_SIZE && column in 0 until PAYLOAD_SIZE) { "cell must be inside payload" }
        val shift = PAYLOAD_SIZE * PAYLOAD_SIZE - 1 - (row * PAYLOAD_SIZE + column)
        return payload and (1 shl shift) != 0
    }

    fun rotateClockwise(payload: Int): Int {
        var rotated = 0
        for (row in 0 until PAYLOAD_SIZE) {
            for (column in 0 until PAYLOAD_SIZE) {
                if (payloadBit(payload, PAYLOAD_SIZE - 1 - column, row)) {
                    val shift = PAYLOAD_SIZE * PAYLOAD_SIZE - 1 - (row * PAYLOAD_SIZE + column)
                    rotated = rotated or (1 shl shift)
                }
            }
        }
        return rotated
    }

    fun rotateClockwise(payload: Int, quarterTurns: Int): Int {
        var rotated = payload
        val normalizedTurns = ((quarterTurns % 4) + 4) % 4
        repeat(normalizedTurns) { rotated = rotateClockwise(rotated) }
        return rotated
    }

    fun hammingDistance(first: Int, second: Int): Int = Integer.bitCount(first xor second)
}
