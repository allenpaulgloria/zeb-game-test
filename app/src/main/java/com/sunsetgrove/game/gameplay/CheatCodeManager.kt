package com.sunsetgrove.game.gameplay

enum class CheatCategory(val label: String) {
    PLAYER("PLAYER"),
    VEHICLES("VEHICLES"),
    POLICE("POLICE"),
    WORLD("WORLD"),
}

enum class CheatMode { INSTANT, TOGGLE }

data class CheatDefinition(
    val code: String,
    val alias: String?,
    val title: String,
    val description: String,
    val category: CheatCategory,
    val mode: CheatMode,
)

data class CheatActivationResult(
    val code: String,
    val activated: Boolean,
    val bannerMessage: String,
    val spawnVehicle: VehicleArchetype? = null,
    val equipJetpack: Boolean = false,
    val healAndCash: Boolean = false,
    val clearWanted: Boolean = false,
    val addWantedStars: Int = 0,
    val setMaxWanted: Boolean = false,
    val blowUpNearbyCars: Boolean = false,
    val weatherPreset: WeatherPreset? = null,
)

/**
 * Registry and live state manager for 22 authentic GTA: San Andreas PC cheat codes,
 * including Jetpack (YECGAA / ROCKETMAN), Rhino Army Tank (AIWPRTON), and
 * Monster Truck (AGBDLCID / MONSTERMASH).
 */
class CheatCodeManager {

    companion object {
        const val CODE_JETPACK = "YECGAA"
        const val CODE_HESOYAM = "HESOYAM"
        const val CODE_BAGUVIX = "BAGUVIX"
        const val CODE_KANGAROO = "KANGAROO"

        const val CODE_RHINO_TANK = "AIWPRTON"
        const val CODE_MONSTER_TRUCK = "AGBDLCID"
        const val CODE_HOTRING = "PDNEJOH"
        const val CODE_BLOODRING = "CQZIJMB"
        const val CODE_NITRO = "COXEFGU"
        const val CODE_PERFECT_HANDLING = "PGGOMOY"
        const val CODE_FLYING_CARS = "RIPAZHA"
        const val CODE_SMASH_N_BOOM = "JCNRUAD"
        const val CODE_BLOW_UP_CARS = "CPKTNWT"
        const val CODE_AGGRESSIVE_DRIVERS = "YLTEICZ"

        const val CODE_NEVER_WANTED = "AEZAKMI"
        const val CODE_CLEAR_WANTED = "ASNAEB"
        const val CODE_ADD_WANTED = "OSRBLHH"
        const val CODE_MAX_WANTED = "LJSPQK"

        const val CODE_FAST_MOTION = "PPGWJHT"
        const val CODE_SLOW_MOTION = "LIYOAAY"
        const val CODE_SUNNY_DAY = "AFZLLQLL"
        const val CODE_ORANGE_SKY = "OFVIAC"

        val catalog: List<CheatDefinition> = listOf(
            // Player
            CheatDefinition(
                CODE_JETPACK, "ROCKETMAN", "Spawn Jetpack",
                "Equips a twin-thruster Jetpack to fly over rooftops and skyscrapers",
                CheatCategory.PLAYER, CheatMode.INSTANT,
            ),
            CheatDefinition(
                CODE_HESOYAM, null, "Health, Armor & \$250,000",
                "Restores full Health & Armor, adds \$250,000 cash, and repairs current vehicle",
                CheatCategory.PLAYER, CheatMode.INSTANT,
            ),
            CheatDefinition(
                CODE_BAGUVIX, null, "Semi-Infinite Health",
                "Protects player from collisions and police impact damage",
                CheatCategory.PLAYER, CheatMode.TOGGLE,
            ),
            CheatDefinition(
                CODE_KANGAROO, null, "Mega Super Jump",
                "Triples on-foot jump height to leap onto porches and walls",
                CheatCategory.PLAYER, CheatMode.TOGGLE,
            ),

            // Vehicles
            CheatDefinition(
                CODE_RHINO_TANK, "WPRTON", "Spawn Rhino Army Tank",
                "Drops an armored Rhino Tank with rotating turret & explosive main cannon",
                CheatCategory.VEHICLES, CheatMode.INSTANT,
            ),
            CheatDefinition(
                CODE_MONSTER_TRUCK, "MONSTERMASH", "Spawn Monster Truck",
                "Spawns a giant lifted 4x4 Monster Truck that crushes regular cars",
                CheatCategory.VEHICLES, CheatMode.INSTANT,
            ),
            CheatDefinition(
                CODE_HOTRING, "VROCKPOKEY", "Spawn Hotring Racer #1",
                "Spawns a high-speed stock racing muscle car right in front of you",
                CheatCategory.VEHICLES, CheatMode.INSTANT,
            ),
            CheatDefinition(
                CODE_BLOODRING, "OLDSPEEDDEMON", "Spawn Bloodring Banger",
                "Spawns an armored demolition derby muscle car ahead of you",
                CheatCategory.VEHICLES, CheatMode.INSTANT,
            ),
            CheatDefinition(
                CODE_NITRO, "SPEEDFREAK", "All Cars Have Nitro",
                "Unlocks turbo Nitro boost button in any vehicle",
                CheatCategory.VEHICLES, CheatMode.TOGGLE,
            ),
            CheatDefinition(
                CODE_PERFECT_HANDLING, "STICKLIKEGLUE", "Perfect Handling",
                "Razor-sharp cornering grip plus arcade hop on handbrake",
                CheatCategory.VEHICLES, CheatMode.TOGGLE,
            ),
            CheatDefinition(
                CODE_FLYING_CARS, "CHITTYCHITTYBANGBANG", "Flying Cars",
                "Vehicles lift off and fly through the sky at high speed",
                CheatCategory.VEHICLES, CheatMode.TOGGLE,
            ),
            CheatDefinition(
                CODE_SMASH_N_BOOM, "TOUCHMYCARYOUDIE", "Smash N' Boom",
                "Your vehicle becomes invincible and blows up any car it touches",
                CheatCategory.VEHICLES, CheatMode.TOGGLE,
            ),
            CheatDefinition(
                CODE_BLOW_UP_CARS, null, "Blow Up All Nearby Cars",
                "Instantly detonates and launches all surrounding traffic & cop cars",
                CheatCategory.VEHICLES, CheatMode.INSTANT,
            ),
            CheatDefinition(
                CODE_AGGRESSIVE_DRIVERS, "ALLDRIVERSARECRIMINALS", "Aggressive Drivers",
                "Civilian traffic speeds up and drives recklessly through intersections",
                CheatCategory.VEHICLES, CheatMode.TOGGLE,
            ),

            // Police & Wanted
            CheatDefinition(
                CODE_NEVER_WANTED, null, "Never Wanted",
                "Clears all stars and disables police wanted escalation",
                CheatCategory.POLICE, CheatMode.TOGGLE,
            ),
            CheatDefinition(
                CODE_CLEAR_WANTED, "TURNDOWNTHEHEAT", "Clear Wanted Level",
                "Instantly removes all wanted stars and calls off police pursuit",
                CheatCategory.POLICE, CheatMode.INSTANT,
            ),
            CheatDefinition(
                CODE_ADD_WANTED, "TURNUPTHEHEAT", "Increase Wanted (+2 Stars)",
                "Adds 2 wanted stars immediately to spark a chase",
                CheatCategory.POLICE, CheatMode.INSTANT,
            ),
            CheatDefinition(
                CODE_MAX_WANTED, "BRINGITON", "Maximum 6-Star Wanted",
                "Instantly jumps to full 6-star maximum police chaos",
                CheatCategory.POLICE, CheatMode.INSTANT,
            ),

            // World & Time
            CheatDefinition(
                CODE_FAST_MOTION, "SPEEDITUP", "Faster Gameplay",
                "Accelerates world simulation speed to 1.5x",
                CheatCategory.WORLD, CheatMode.TOGGLE,
            ),
            CheatDefinition(
                CODE_SLOW_MOTION, "SLOWITDOWN", "Slower Gameplay",
                "Slows down action to cinematic 0.5x slow motion",
                CheatCategory.WORLD, CheatMode.TOGGLE,
            ),
            CheatDefinition(
                CODE_SUNNY_DAY, "PLEASANTLYWARM", "Sunny California Day",
                "Switches lighting to bright midday California sun & blue skies",
                CheatCategory.WORLD, CheatMode.INSTANT,
            ),
            CheatDefinition(
                CODE_ORANGE_SKY, null, "21:00 Orange Sky",
                "Locks the iconic deep amber-orange San Andreas evening sky",
                CheatCategory.WORLD, CheatMode.INSTANT,
            ),
        )
    }

    private val activeToggles = LinkedHashSet<String>()

    fun isActive(code: String): Boolean = activeToggles.contains(code.uppercase())
    fun activeCodes(): Set<String> = activeToggles.toSet()

    fun clearAllToggles() {
        activeToggles.clear()
    }

    fun timeScale(): Float = when {
        isActive(CODE_FAST_MOTION) -> 1.5f
        isActive(CODE_SLOW_MOTION) -> 0.5f
        else -> 1.0f
    }

    /** Activates or toggles an authentic cheat code (accepts primary code or classic alias). */
    fun trigger(inputCode: String): CheatActivationResult? {
        val norm = inputCode.trim().uppercase()
        val def = catalog.firstOrNull { it.code == norm || it.alias == norm } ?: return null

        val activated = if (def.mode == CheatMode.TOGGLE) {
            if (activeToggles.contains(def.code)) {
                activeToggles.remove(def.code)
                false
            } else {
                // Mutual exclusion between fast-mo and slow-mo
                if (def.code == CODE_FAST_MOTION) activeToggles.remove(CODE_SLOW_MOTION)
                if (def.code == CODE_SLOW_MOTION) activeToggles.remove(CODE_FAST_MOTION)
                activeToggles.add(def.code)
                true
            }
        } else {
            true
        }

        val banner = if (activated) "Cheat activated" else "Cheat deactivated"

        return when (def.code) {
            CODE_JETPACK -> CheatActivationResult(def.code, true, banner, equipJetpack = true)
            CODE_HESOYAM -> CheatActivationResult(def.code, true, banner, healAndCash = true)
            CODE_RHINO_TANK -> CheatActivationResult(def.code, true, banner, spawnVehicle = VehicleArchetype.RHINO_TANK)
            CODE_MONSTER_TRUCK -> CheatActivationResult(def.code, true, banner, spawnVehicle = VehicleArchetype.MONSTER_TRUCK)
            CODE_HOTRING -> CheatActivationResult(def.code, true, banner, spawnVehicle = VehicleArchetype.HOTRING)
            CODE_BLOODRING -> CheatActivationResult(def.code, true, banner, spawnVehicle = VehicleArchetype.BLOODRING)
            CODE_BLOW_UP_CARS -> CheatActivationResult(def.code, true, banner, blowUpNearbyCars = true)
            CODE_NEVER_WANTED -> CheatActivationResult(def.code, activated, banner, clearWanted = activated)
            CODE_CLEAR_WANTED -> CheatActivationResult(def.code, true, banner, clearWanted = true)
            CODE_ADD_WANTED -> CheatActivationResult(def.code, true, banner, addWantedStars = 2)
            CODE_MAX_WANTED -> CheatActivationResult(def.code, true, banner, setMaxWanted = true)
            CODE_SUNNY_DAY -> CheatActivationResult(def.code, true, banner, weatherPreset = WeatherPreset.SUNNY_DAY)
            CODE_ORANGE_SKY -> CheatActivationResult(def.code, true, banner, weatherPreset = WeatherPreset.ORANGE_SKY_2100)
            else -> CheatActivationResult(def.code, activated, banner)
        }
    }
}
