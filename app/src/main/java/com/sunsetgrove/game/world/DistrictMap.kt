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
    val hasDriverInside: Boolean = false,
)

/**
 * Extended 640m x 640m Los Santos / San Andreas-inspired open world:
 * - Grove Street Cul-de-Sac (East home block)
 * - Idlewood / Ballas Turf (South-East neighborhood patrolled by armed Purple Gang)
 * - Vinewood Strip (Central 4-lane palm boulevard + stunt ramps)
 * - Downtown Los Santos (North & South skyscrapers with flat Jetpack rooftops)
 * - Verona Beach & Swimmable Pacific Ocean (West coastline x <= -132f with wooden pier!)
 */
object DistrictMap {
    const val WORLD_HALF_EXTENT = 300f
    const val OCEAN_SHORELINE_X = -132f
    const val WATER_SURFACE_Y = 0.08f
    val PLAYER_SPAWN = Vec3(84f, 0.25f, 8f)

    val roadSegments: List<RoadSegmentSpec> = buildList {
        // 4 Major North-South Avenues: Beach Blvd (-70), Vinewood Strip (0), Grove Ave (70), Idlewood Blvd (140)
        for (x in listOf(-70f, 0f, 70f, 140f)) {
            for (z in -160..160 step 20) {
                add(RoadSegmentSpec(x, z.toFloat(), 20f, 14f, 0f))
            }
        }
        // 5 Major East-West Boulevards: Far South (-160), South (-80), Central (0), North (80), Far North (160)
        for (z in listOf(-160f, -80f, 0f, 80f, 160f)) {
            for (x in -80..150 step 20) {
                add(RoadSegmentSpec(x.toFloat(), z, 20f, 14f, 90f))
            }
        }
        // Grove Street cul-de-sac spur east of Grove Ave
        add(RoadSegmentSpec(105f, 0f, 22f, 22f, 0f))
        add(RoadSegmentSpec(120f, 0f, 18f, 24f, 0f))
    }

    val buildings: List<BuildingSpec> = listOf(
        // Grove Street residential bungalows
        BuildingSpec("grove_house_1", "world_bungalow", 96f, -24f, 0f, 14f, 11f, 6.4f, false),
        BuildingSpec("grove_house_2", "world_bungalow", 118f, -24f, -12f, 14f, 11f, 6.4f, false),
        BuildingSpec("grove_house_4", "world_bungalow", 118f, 24f, 192f, 14f, 11f, 6.4f, false),
        BuildingSpec("grove_house_5", "world_bungalow", 96f, 24f, 180f, 14f, 11f, 6.4f, false),
        BuildingSpec("grove_house_6", "world_bungalow", 104f, -54f, 90f, 14f, 11f, 6.4f, false),
        BuildingSpec("grove_house_7", "world_bungalow", 104f, 54f, 90f, 14f, 11f, 6.4f, false),

        // Idlewood / East Los Santos Ballas Turf blocks (South-East & Far-East)
        BuildingSpec("idle_bungalow_1", "world_bungalow", 168f, -110f, -90f, 14f, 11f, 6.4f, false),
        BuildingSpec("idle_bungalow_2", "world_bungalow", 168f, -50f, -90f, 14f, 11f, 6.4f, false),
        BuildingSpec("idle_store_1", "world_storefront", 106f, -120f, 0f, 18f, 14f, 9.0f, true),
        BuildingSpec("idle_store_2", "world_storefront", 168f, 28f, -90f, 18f, 14f, 9.0f, true),
        BuildingSpec("idle_tower_1", "world_tower_med", 172f, 115f, 0f, 20f, 20f, 18.0f, true),

        // Vinewood Strip storefronts (Central blocks)
        BuildingSpec("vine_shop_1", "world_storefront", 35f, -38f, 0f, 18f, 14f, 9.0f, true),
        BuildingSpec("vine_shop_2", "world_storefront", 35f, 38f, 180f, 18f, 14f, 9.0f, true),
        BuildingSpec("vine_shop_3", "world_storefront", -35f, -38f, 0f, 18f, 14f, 9.0f, true),
        BuildingSpec("vine_shop_4", "world_storefront", -35f, 38f, 180f, 18f, 14f, 9.0f, true),

        // Downtown Los Santos multi-story towers with landable flat rooftops (North & South)
        BuildingSpec("dt_tower_n1", "world_tower_tall", 35f, 118f, 0f, 22f, 22f, 26.0f, true),
        BuildingSpec("dt_tower_n2", "world_tower_med", -35f, 118f, 0f, 20f, 20f, 18.0f, true),
        BuildingSpec("dt_tower_n3", "world_tower_med", 104f, 118f, 0f, 20f, 20f, 18.0f, true),
        BuildingSpec("dt_tower_n4", "world_tower_tall", 35f, 195f, 0f, 22f, 22f, 26.0f, true),
        BuildingSpec("dt_tower_s1", "world_tower_tall", -35f, -118f, 0f, 22f, 22f, 26.0f, true),
        BuildingSpec("dt_tower_s2", "world_tower_med", 35f, -118f, 0f, 20f, 20f, 18.0f, true),
        BuildingSpec("dt_tower_s3", "world_tower_tall", -35f, -195f, 0f, 22f, 22f, 26.0f, true),
    )

    val stuntRamps: List<StuntRampSpec> = listOf(
        StuntRampSpec(x = 0f, z = 40f, yawDeg = 0f),
        StuntRampSpec(x = -70f, z = -35f, yawDeg = 0f),
        StuntRampSpec(x = 140f, z = -30f, yawDeg = 0f),
    )

    val props: List<PropSpec> = buildList {
        // California palm trees lining Vinewood Strip, Beachfront & Idlewood
        for (z in -150..150 step 28) {
            add(PropSpec("prop_palm_tree", -11f, z.toFloat(), (z * 17 % 360).toFloat(), 0.45f))
            add(PropSpec("prop_palm_tree", 11f, (z + 12).toFloat(), (z * 23 % 360).toFloat(), 0.45f))
            add(PropSpec("prop_palm_tree", -86f, z.toFloat(), (z * 31 % 360).toFloat(), 0.45f))
            add(PropSpec("prop_palm_tree", -106f, (z + 14).toFloat(), (z * 13 % 360).toFloat(), 0.45f))
            add(PropSpec("prop_palm_tree", 152f, z.toFloat(), (z * 19 % 360).toFloat(), 0.45f))
        }
        // Poly Haven CC0 street lamps along major intersections
        for (x in listOf(-60f, 10f, 60f, 130f)) {
            for (z in listOf(-88f, -12f, 12f, 88f)) {
                add(PropSpec("prop_street_lamp", x, z, if (x > 0) -90f else 90f, 0.25f))
            }
        }
        // Poly Haven CC0 fire hydrants, benches, barrels, and concrete barriers
        add(PropSpec("prop_fire_hydrant", 79f, -10f, 0f, 0.3f))
        add(PropSpec("prop_fire_hydrant", 9f, 10f, 90f, 0.3f))
        add(PropSpec("prop_fire_hydrant", -61f, -10f, 180f, 0.3f))
        add(PropSpec("prop_fire_hydrant", 131f, -72f, 0f, 0.3f))
        add(PropSpec("prop_bench", -96f, 12f, 90f, 0.65f))
        add(PropSpec("prop_bench", -96f, -24f, 90f, 0.65f))
        add(PropSpec("prop_barrel", 148f, -94f, 0f, 0.45f))
        add(PropSpec("prop_barrel", 149f, -92f, 35f, 0.45f))

        for (z in -120..120 step 48) {
            add(PropSpec("prop_concrete_barrier", -82f, z.toFloat(), 90f, 0.7f))
        }
    }

    val initialParkedCars: List<ParkedCarSpec> = listOf(
        // Grove Street muscle car right in your cul-de-sac
        ParkedCarSpec("muscle", 104f, 6f, -90f, hasDriverInside = false),
        // Occupied idling sedan across Grove Street (carjackable!)
        ParkedCarSpec("sedan", 116f, -9f, 90f, hasDriverInside = true),
        // Beachfront muscle car
        ParkedCarSpec("muscle", -78f, 22f, 0f, hasDriverInside = true),
        // Idlewood parked sedan
        ParkedCarSpec("sedan", 148f, -62f, 0f, hasDriverInside = true),
        // Downtown parked sedan
        ParkedCarSpec("sedan", 22f, 88f, 90f, hasDriverInside = false),
    )

    fun isInSwimmableOcean(x: Float): Boolean = x <= OCEAN_SHORELINE_X

    fun districtNameAt(x: Float, z: Float): String = when {
        x <= OCEAN_SHORELINE_X -> "Pacific Ocean"
        x <= -82f -> "Verona Beach"
        x >= 112f && z <= -35f -> "Idlewood (Ballas Turf)"
        x >= 76f && z in -45f..45f -> "Grove Street"
        z >= 68f || z <= -68f -> "Downtown Los Santos"
        else -> "Vinewood Strip"
    }
}
