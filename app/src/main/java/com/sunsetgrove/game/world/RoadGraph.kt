package com.sunsetgrove.game.world

import java.util.PriorityQueue

data class RoadNode(
    val id: Int,
    val pos: Vec2,
    val neighbors: MutableList<Int> = mutableListOf(),
)

/**
 * Connected street intersection graph powering:
 * - Ambient traffic lane navigation
 * - Police A* pursuit routing
 * - Radar minimap street geometry
 */
class RoadGraph {
    val nodes: List<RoadNode>

    init {
        val list = mutableListOf<RoadNode>()
        val xs = listOf(-70f, 0f, 70f)
        val zs = listOf(-140f, -80f, 0f, 80f, 140f)
        val grid = Array(xs.size) { IntArray(zs.size) }

        for (ix in xs.indices) {
            for (iz in zs.indices) {
                val id = list.size
                grid[ix][iz] = id
                list.add(RoadNode(id, Vec2(xs[ix], zs[iz])))
            }
        }
        // Connect grid north-south & east-west
        for (ix in xs.indices) {
            for (iz in zs.indices) {
                val id = grid[ix][iz]
                if (ix > 0) list[id].neighbors.add(grid[ix - 1][iz])
                if (ix < xs.lastIndex) list[id].neighbors.add(grid[ix + 1][iz])
                if (iz > 0) list[id].neighbors.add(grid[ix][iz - 1])
                if (iz < zs.lastIndex) list[id].neighbors.add(grid[ix][iz + 1])
            }
        }
        // Grove Circle cul-de-sac spur east of (70, 0)
        val groveCenterId = list.size
        list.add(RoadNode(groveCenterId, Vec2(118f, 0f)))
        val eastCentralId = grid[2][2] // (70, 0)
        list[eastCentralId].neighbors.add(groveCenterId)
        list[groveCenterId].neighbors.add(eastCentralId)

        nodes = list
    }

    fun nearestNode(pos: Vec2): RoadNode {
        var best = nodes[0]
        var bestDistSq = Float.MAX_VALUE
        for (n in nodes) {
            val d = (n.pos - pos).lengthSq()
            if (d < bestDistSq) {
                bestDistSq = d
                best = n
            }
        }
        return best
    }

    /** A* shortest path between two road node IDs. Returns ordered waypoint positions. */
    fun findPath(startId: Int, goalId: Int): List<Vec2> {
        if (startId == goalId) return listOf(nodes[goalId].pos)

        val open = PriorityQueue<Pair<Int, Float>>(compareBy { it.second })
        val cameFrom = HashMap<Int, Int>()
        val gScore = HashMap<Int, Float>().apply { put(startId, 0f) }

        open.add(startId to nodes[startId].pos.distanceTo(nodes[goalId].pos))

        while (open.isNotEmpty()) {
            val (current, _) = open.poll()!!
            if (current == goalId) {
                val path = ArrayList<Vec2>()
                var cur: Int? = current
                while (cur != null) {
                    path.add(nodes[cur].pos)
                    cur = cameFrom[cur]
                }
                path.reverse()
                return path
            }

            val currentG = gScore[current] ?: Float.MAX_VALUE
            for (next in nodes[current].neighbors) {
                val tentativeG = currentG + nodes[current].pos.distanceTo(nodes[next].pos)
                if (tentativeG < (gScore[next] ?: Float.MAX_VALUE)) {
                    cameFrom[next] = current
                    gScore[next] = tentativeG
                    val f = tentativeG + nodes[next].pos.distanceTo(nodes[goalId].pos)
                    open.add(next to f)
                }
            }
        }
        return listOf(nodes[goalId].pos)
    }
}
