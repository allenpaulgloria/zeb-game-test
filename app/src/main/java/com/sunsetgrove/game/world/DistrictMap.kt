package com.sunsetgrove.game.world

data class BuildingSpec(
    val id: String,
    val modelKey: String,
    val x: Float,
    val z: Float,
    val yawDeg: Float,
    val width: Float,
    val depth: Float,
    val height: Float,
    val roofLandable: Boolean,
)

data class PropSpec(
    val modelKey: String,
    val x: Float,
    val z: Float,
    val yawDeg: Float = 0f,
    val colliderRadius: Float = 0.35f,
)

data class RoadSegmentSpec(
    val x: Float,
    val z: Float,
    val length: Float,
    val width: Float,
    val yawDeg: Float,
)

data class StuntRampSpec(
    val x: Float,
    val z: Float,
    val yawDeg: Float,
    val width: Float = 6f,
    val length: Float = 11f,
    val height: Float = 2.6f,
)

data class ParkedCarSpec(
    val archetype: String,
    val x: Float,
    val z: Float,
    val yawDeg: Float,
)

/**
 * 420m x 420m sun-soaked 90s West Coast district inspired by GTA: San Andreas:
 * - Grove Circle (East cul-de-sac home block, spawn point)
 * - Vinewood Strip (Center 4-lane palm avenue & commercial strip + stunt ramp)
 * - Verona Beachfront (West sandy coastline & boardwalk)
 * - Downtown Crossings (North/South multi-story blocks with flat Jetpack rooftops)
 */
object DistrictMap {
    const val WORLD_HALF_EXTENT = 200f
    val PLAYER_SPAWN = Vec3(84f, 0.25f, 8f)

    val roadSegments: List<RoadSegmentSpec> = buildList {
        // Main North-South Avenues: x = -70 (Beach Blvd), x = 0 (Vinewood Strip), x = 70 (Grove Ave)
        for (x in listOf(-70f, 0f, 70f)) {
            for (z in -140..140 step 20) {
                add(RoadSegmentSpec(x, z.toFloat(), 20f, 14f, 0f))
            }
        }
        // Main East-West Boulevards: z = -80 (South Blvd), z = 0 (Central Ave), z = 80 (Downtown Ave)
        for (z in listOf(-80f, 0f, 80f)) {
            for (x in -100..120 step 20) {
                add(RoadSegmentSpec(x.toFloat(), z, 20f, 14f, 90f))
            }
        }
        // Grove Circle cul-de-sac loop at East end (x = 95..135, z = -10..10)
        add(RoadSegmentSpec(110f, 0f, 22f, 22f, 0f))
        add(RoadSegmentSpec(126f, 0f, 20f, 24f, 0f))
    }

    val buildings: List<BuildingSpec> = listOf(
        // Grove Circle residential bungalows wrapping the cul-de-sac (East)
        BuildingSpec("grove_house_1", "world_bungalow", 96f, -24f, 0f, 14f, 11f, 6.4f, false),
        BuildingSpec("grove_house_2", "world_bungalow", 122f, -24f, -15f, 14f, 11f, 6.4f, false),
        BuildingSpec("grove_house_3", "world_bungalow", 146f, 0f, -90f, 14f, 11f, 6.4f, false),
        BuildingSpec("grove_house_4", "world_bungalow", 122f, 24f, 195f, 14f, 11f, 6.4f, false),
        BuildingSpec("grove_house_5", "world_bungalow", 96f, 24f, 180f, 14f, 11f, 6.4f, false),
        BuildingSpec("grove_house_6", "world_bungalow", 98f, -54f, 90f, 14f, 11f, 6.4f, false),
        BuildingSpec("grove_house_7", "world_bungalow", 98f, 54f, 90f, 14f, 11f, 6.4f, false),

        // Vinewood Strip storefronts (Center-East & Center-West blocks)
        BuildingSpec("vine_shop_1", "world_storefront", 35f, -38f, 0f, 18f, 14f, 9.0f, true),
        BuildingSpec("vine_shop_2", "world_storefront", 35f, 38f, 180f, 18f, 14f, 9.0f, true),
        BuildingSpec("vine_shop_3", "world_storefront", -35f, -38f, 0f, 18f, 14f, 9.0f, true),
        BuildingSpec("vine_shop_4", "world_storefront", -35f, 38f, 180f, 18f, 14f, 9.0f, true),

        // Downtown Crossings multi-story towers with landable flat rooftops (North & South)
        BuildingSpec("dt_tower_n1", "world_tower_tall", 35f, 115f, 0f, 22f, 22f, 26.0f, true),
        BuildingSpec("dt_tower_n2", "world_tower_med", -35f, 115f, 0f, 20f, 20f, 18.0f, true),
        BuildingSpec("dt_tower_n3", "world_tower_med", 104f, 115f, 0f, 20f, 20f, 18.0f, true),
        BuildingSpec("dt_tower_s1", "world_tower_tall", -35f, -115f, 0f, 22f, 22f, 26.0f, true),
        BuildingSpec("dt_tower_s2", "world_tower_med", 35f, -115f, 0f, 20f, 20f, 18.0f, true),
        BuildingSpec("dt_tower_s3", "world_storefront", 104f, -115f, 0f, 18f, 14f, 9.0f, true),
    )

    val stuntRamps: List<StuntRampSpec> = listOf(
        StuntRampSpec(x = 0f, z = 40f, yawDeg = 0f),
        StuntRampSpec(x = -70f, z = -35f, yawDeg = 0f),
    )

    val props: List<PropSpec> = buildList {
        // Signature California palm trees lining Vinewood Strip & Verona Beachfront
        for (z in -130..130 step 26) {
            add(PropSpec("prop_palm_tree", -11f, z.toFloat(), (z * 17 % 360).toFloat(), 0.45f))
            add(PropSpec("prop_palm_tree", 11f, (z + 12).toFloat(), (z * 23 % 360).toFloat(), 0.45f))
            add(PropSpec("prop_palm_tree", -86f, z.toFloat(), (z * 31 % 360).toFloat(), 0.45f))
            add(PropSpec("prop_palm_tree", -104f, (z + 10).toFloat(), (z * 13 % 360).toFloat(), 0.45f))
        }
        // Palm trees around Grove Circle cul-de-sac
        add(PropSpec("prop_palm_tree", 88f, -14f, 20f, 0.45f))
        add(PropSpec("prop_palm_tree", 136f, -16f, 110f, 0.45f))
        add(PropSpec("prop_palm_tree", 136f, 16f, 240f, 0.45f))
        add(PropSpec("prop_palm_tree", 88f, 14f, 300f, 0.45f))

        // Poly Haven CC0 street lamps along major intersections
        for (x in listOf(-60f, 10f, 60f)) {
            for (z in listOf(-70f, -12f, 12f, 70f)) {
                add(PropSpec("prop_street_lamp", x, z, if (x > 0) -90f else 90f, 0.25f))
            }
        }
        // Poly Haven CC0 fire hydrants at corners
        add(PropSpec("prop_fire_hydrant", 79f, -10f, 0f, 0.3f))
        add(PropSpec("prop_fire_hydrant", 9f, 10f, 90f, 0.3f))
        add(PropSpec("prop_fire_hydrant", -61f, -10f, 180f, 0.3f))

        // Poly Haven CC0 concrete barriers framing coastal boardwalk edge
        for (z in -100..100 step 40) {
            add(PropSpec("prop_concrete_barrier", -82f, z.toFloat(), 90f, 0.7f))
        }
    }

    val initialParkedCars: List<ParkedCarSpec> = listOf(
        // Grove green muscle car parked right in your Grove Circle home cul-de-sac!
        ParkedCarSpec("muscle", 104f, 6f, -90f),
        // Red sedan parked across Grove Circle
        ParkedCarSpec("sedan", 118f, -9f, 90f),
        // Police cruiser idling near Vinewood Strip intersection
        ParkedCarSpec("police", 10f, -26f, 0f),
        // Beachfront muscle car
        ParkedCarSpec("muscle", -78f, 22f, 0f),
        // Downtown parked sedan
        ParkedCarSpec("sedan", 22f, 88f, 90f),
    )

    fun districtNameAt(x: Float, z: Float): String = when {
        x <= -82f -> "Verona Beachfront"
        x >= 76f && z in -45f..45f -> "Grove Circle"
        z >= 68f || z <= -68f -> "Downtown Crossings"
        else -> "Vinewood Strip"
    }
}
