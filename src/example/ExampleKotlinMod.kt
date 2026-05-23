package example

import arc.Core
import arc.Events
import arc.func.Prov
import example.content.Blocks
import example.world.UnitLaunchPadData
import mindustry.Vars
import mindustry.game.EventType
import mindustry.mod.Mod
import mindustry.world.meta.BuildVisibility
import arc.math.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import mindustry.content.*
import mindustry.entities.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.world.blocks.storage.*
import arc.graphics.g2d.Draw
import arc.util.Log
import arc.util.Time
import mindustry.graphics.Drawf
import mindustry.graphics.Pal
import mindustry.world.Tile
import arc.graphics.g2d.*
import mindustry.content.Fx
import mindustry.content.Planets
import mindustry.gen.Sounds
import mindustry.type.UnitType
import mindustry.world.blocks.campaign.LaunchPad
import kotlin.math.abs
import kotlin.math.max

class ExampleKotlinMod : Mod() {

    override fun loadContent() {
        super.loadContent()

        //UnitTypes.antumbra.weapons.add(WarpWeapon(""))

        //EVars.load()

        // 安全地修改科技树，添加空值检查

        Vars.content.units().each { unit ->
            unit.envDisabled = 0
        }

        example.world.other.TechTree.addToNode(mindustry.content.Blocks.duct) {
            example.world.other.TechTree.node(Blocks.eUnitLaunchPad)
        }

        example.world.other.TechTree.addToNode(mindustry.content.Blocks.router) {
            example.world.other.TechTree.node(mindustry.content.Blocks.launchPad) {
                example.world.other.TechTree.node(Blocks.unitLaunchPad)
            }
        }

        Planets.serpulo.campaignRules.legacyLaunchPads = true
        mindustry.content.Blocks.launchPad.buildVisibility = BuildVisibility.shown
        Planets.serpulo.campaignRuleDefaults.legacyLaunchPads = true
        Planets.serpulo.campaignRuleDefaults.fog = true

        UnitLaunchPadData.init()

        Events.on(EventType.WorldLoadEndEvent::class.java) {
            Core.app.post {
                val sector = Vars.state.rules.sector ?: return@post
                val team = Vars.state.rules.defaultTeam
                val cores = Vars.state.teams.get(team).cores
                if (cores.isEmpty) return@post

                // 清空上一轮的降落点记录
                usedLandingPositions.clear()

                UnitLaunchPadData.get(sector).forEach { unitTypeName ->
                    val unitType = Vars.content.unit(unitTypeName) ?: return@forEach

                    var noFind = true

                    cores.forEach { core ->
                        val spawnPos = findLandingPosition(unitType, core.tileX(), core.tileY())
                        if (spawnPos != null && noFind) {
                            // 记录使用的降落点
                            usedLandingPositions.add(spawnPos)

                            createLandingEffect(unitType, spawnPos.x, spawnPos.y)

                            Sounds.padLand.at(spawnPos)

                            Time.run(180f) {
                                val unit = unitType.create(team)
                                unit.set(spawnPos.x, spawnPos.y)
                                unit.rotation = 90f
                                unit.add()
                                Effect(60f) { e ->  // 延长持续时间让效果更持久
                                    val baseRadius = 4f
                                    val maxRadius = 140f  // 144px - 4px = 140f，总范围18格(144px)

                                    // 1. 主波纹圈 - 加粗加宽
                                    Draw.color(Pal.engine)
                                    e.scaled(30f) { f ->
                                        Lines.stroke(f.fout() * 4f)  // 线条更粗(原来2f)
                                        Lines.circle(e.x, e.y, baseRadius + f.finpow() * maxRadius)
                                    }

                                    // 2. 第二层脉冲圈（向外扩散的装饰圈）
                                    e.scaled(20f) { f ->
                                        Draw.alpha(f.fout() * 0.6f)
                                        Lines.stroke(f.fout() * 6f)  // 更宽的发光边缘
                                        Lines.circle(e.x, e.y, f.finpow() * 144f)
                                    }

                                    // 3. 主射线束 - 数量增加50%，长度18格
                                    Draw.color(Pal.engine)
                                    Lines.stroke(e.fout() * 3f)  // 射线更粗
                                    Angles.randLenVectors(e.id.toLong(), 36, e.finpow() * 144f) { x, y ->  // 36条(原来24)，长度144px
                                        val ang = Mathf.angle(x, y)
                                        // 射线末端加长加粗
                                        Lines.lineAngle(e.x + x, e.y + y, ang, e.fout() * 6f + 2f)
                                    }

                                    // 4. 中心爆闪（增强视觉冲击力）
                                    Draw.color(Pal.engine)
                                    Draw.alpha(e.fout() * 0.9f)
                                    Fill.circle(e.x, e.y, e.finpow() * 20f)  // 中心强光

                                    // 5. 地面冲击波（扁平椭圆，增加层次感）
                                    Draw.z(110f)  // 确保在地面上方
                                    Draw.color(Pal.engine)
                                    Draw.alpha(e.fout() * 0.4f)
                                    val expand = e.finpow() * 144f
                                    Fill.circle(e.x, e.y, expand * 0.8f)  // 扁平椭圆
                                }.at(unit)
                                Sounds.explosion.at(unit)
                            }

                            noFind = false

                        } else {
                            Log.warn("无法为单位 ${unitType.name} 找到合适的降落点")
                        }

                    }
                }

                UnitLaunchPadData.remove(sector)
            }
        }
    }
}

// ==================== 全局状态 ====================

// 存储已使用的降落点（世界坐标），用于防挤压检查
private val usedLandingPositions = ObjectSet<Vec2>()

// 安全距离系数：单位大小(格数) + 2.5格，转换为世界坐标
private fun getSafeDistance(unitType: UnitType): Float {
    val hitSizeInTiles = unitType.hitSize / Vars.tilesize
    val safeDistanceInTiles = hitSizeInTiles + 2.5f
    return safeDistanceInTiles * Vars.tilesize
}

/**
 * 检查位置是否与已使用的降落点保持安全距离
 */
private fun isSafeFromOtherLandings(x: Float, y: Float, unitType: UnitType): Boolean {
    val safeDist = getSafeDistance(unitType)
    val safeDistSq = safeDist * safeDist

    for (usedPos in usedLandingPositions) {
        val dx = x - usedPos.x
        val dy = y - usedPos.y
        val distSq = dx * dx + dy * dy
        if (distSq < safeDistSq) {
            return false
        }
    }
    return true
}

/**
 * 检查位置附近是否有敌方建筑或单位
 */
private fun hasNearbyEnemies(x: Float, y: Float, team: Team, radius: Float): Boolean {
    val radiusSq = radius * radius

    // 检查敌方单位
    val hasEnemyUnits = Groups.unit.any { unit ->
        unit.team != team && unit.team != Team.derelict && Mathf.dst(unit.x, unit.y, x, y) < radiusSq
    }
    if (hasEnemyUnits) return true

    // 检查敌方建筑
    val tileRadius = (radius / Vars.tilesize).toInt() + 1
    val tx = (x / Vars.tilesize).toInt()
    val ty = (y / Vars.tilesize).toInt()

    for (dx in -tileRadius..tileRadius) {
        for (dy in -tileRadius..tileRadius) {
            val checkTile = Vars.world.tile(tx + dx, ty + dy) ?: continue
            val build = checkTile.build ?: continue

            if (build.team != team && build.team != Team.derelict && build.block.solid) {
                if (Mathf.dst(checkTile.worldx(), checkTile.worldy(), x, y) < radiusSq) {
                    return true
                }
            }
        }
    }
    return false
}

/**
 * 检查位置是否适合该单位类型降落（统一的地形检查）
 */
private fun isValidPosition(tile: Tile?, unitType: UnitType): Boolean {
    if (tile == null) return false

    // 海军：必须是水域
    if (unitType.naval) {
        if (!tile.floor().isLiquid) return false
        if (tile.floor().liquidDrop != Liquids.water && !tile.floor().isDeep) return false
        if (tile.build != null && tile.build.block.solid) return false
        return true
    }

    // 空军/有助推的单位：几乎任何位置都可以（除了虚空和地图外）
    if (unitType.flying || unitType.canBoost || unitType.hovering) {
        if (tile.floor().name == "void") return false
        if (tile.build != null && tile.build.block.solid) return false
        return true
    }

    // 陆军：必须是地面且可通行
    if (tile.floor().isLiquid) return false
    if (tile.solid() && !unitType.canBoost) return false

    if (tile.build != null) {
        val block = tile.build.block
        // 不允许降落在核心上（避免完全重叠）
        if (block is CoreBlock && tile.build is CoreBlock.CoreBuild) return false
        if (block.solid) return false
    }

    if (tile.floor().name == "void" || tile.floor().isDeep) return false

    // 检查是否已有其他单位
    val hitSize = unitType.hitSize / 2f
    val anyUnits = Groups.unit.intersect(
        tile.worldx() - hitSize, tile.worldy() - hitSize, hitSize * 2, hitSize * 2
    ).any()

    return !anyUnits
}

/**
 * 寻找降落位置 - 所有单位类型统一使用防挤压机制
 */
private fun findLandingPosition(unitType: UnitType, coreX: Int, coreY: Int): Vec2? {
    val team = Vars.state.rules.defaultTeam
    val safeDist = getSafeDistance(unitType)
    val enemyCheckRadius = safeDist * 2

    // ===== 第一阶段：核心附近搜索 =====
    val nearbyResult = findPositionUnified(
        unitType = unitType,
        centerX = coreX,
        centerY = coreY,
        radius = 15,
        attempts = 80,
        checkEnemies = true,
        enemyRadius = enemyCheckRadius,
        team = team
    )

    if (nearbyResult != null) {
        Log.info("单位 ${unitType.name} 在核心附近找到安全降落点")
        return nearbyResult
    }

    // ===== 第二阶段：全图搜索（避开敌人）=====
    Log.info("单位 ${unitType.name} 在核心附近未找到合适降落点，开始全图搜索...")

    val globalResult = findGlobalPositionUnified(
        unitType = unitType, attempts = 250, checkEnemies = true, enemyRadius = enemyCheckRadius, team = team
    )

    if (globalResult != null) {
        Log.info("单位 ${unitType.name} 在全图找到安全降落点")
        return globalResult
    }

    // ===== 第三阶段：紧急回退（仅避开已降落点）=====
    Log.warn("单位 ${unitType.name} 无法找到远离敌人的位置，尝试紧急降落...")

    val emergencyResult = findGlobalPositionUnified(
        unitType = unitType, attempts = 400, checkEnemies = false, enemyRadius = 0f, team = team
    )

    if (emergencyResult != null) {
        Log.info("单位 ${unitType.name} 紧急降落于安全位置")
        return emergencyResult
    }

    // ===== 最后手段：强制寻找（忽略防挤压）=====
    Log.err("单位 ${unitType.name} 防挤压失败，强制寻找任何可用位置...")
    return findForcedPositionUnified(unitType)
}

/**
 * 统一的局部位置搜索 - 所有单位类型使用相同逻辑
 */
private fun findPositionUnified(
    unitType: UnitType,
    centerX: Int,
    centerY: Int,
    radius: Int,
    attempts: Int,
    checkEnemies: Boolean,
    enemyRadius: Float,
    team: Team
): Vec2? {
    val candidates = Seq<Vec2>()
    val coreWorldX = centerX * Vars.tilesize
    val coreWorldY = centerY * Vars.tilesize

    repeat(attempts) { i ->
        // 使用螺旋模式确保均匀分布
        val angle = (i * 137.5f) % 360f + Mathf.random(-30f, 30f)
        val distProgress = i.toFloat() / attempts
        // 距离从近到远分布，但保持一定随机性
        val dist = (radius * Vars.tilesize * (0.2f + 0.8f * distProgress)) + Mathf.random(-30f, 30f)

        val x = coreWorldX + Angles.trnsx(angle, dist)
        val y = coreWorldY + Angles.trnsy(angle, dist)

        // 检查世界边界
        if (!isInWorldBounds(x, y)) return@repeat

        val tx = (x / Vars.tilesize).toInt()
        val ty = (y / Vars.tilesize).toInt()
        val tile = Vars.world.tile(tx, ty)

        // 检查地形有效性
        if (!isValidPosition(tile, unitType)) return@repeat

        // 检查防挤压（核心机制）
        if (!isSafeFromOtherLandings(x, y, unitType)) return@repeat

        // 检查敌人（可选）
        if (checkEnemies && hasNearbyEnemies(x, y, team, enemyRadius)) return@repeat

        candidates.add(Vec2(x, y))
    }

    // 从候选点中随机选择一个（确保分散）
    return if (candidates.isEmpty) {
        null
    } else {
        // 随机选择以避免所有单位聚集在同一半径
        candidates.random()
    }
}

/**
 * 统一的全局位置搜索 - 所有单位类型使用相同逻辑
 */
private fun findGlobalPositionUnified(
    unitType: UnitType, attempts: Int, checkEnemies: Boolean, enemyRadius: Float, team: Team
): Vec2? {
    val worldWidth = Vars.world.width()
    val worldHeight = Vars.world.height()
    val cores = Vars.state.teams.get(team).cores
    val nearestCore = if (cores.isEmpty) null else cores.first()

    var bestPos: Vec2? = null
    var bestScore = Float.MAX_VALUE

    repeat(attempts) {
        // 随机采样，但优先选择距离核心不太远的区域
        val margin = 3
        val tx = Mathf.random(margin, worldWidth - margin)
        val ty = Mathf.random(margin, worldHeight - margin)
        val tile = Vars.world.tile(tx, ty)

        // 统一的地形检查
        if (!isValidPosition(tile, unitType)) return@repeat

        val x = tx * Vars.tilesize + Vars.tilesize / 2f
        val y = ty * Vars.tilesize + Vars.tilesize / 2f

        // 检查防挤压（核心机制，所有单位都必须遵守）
        if (!isSafeFromOtherLandings(x, y, unitType)) return@repeat

        // 检查敌人（可选）
        if (checkEnemies && hasNearbyEnemies(x, y, team, enemyRadius)) return@repeat

        // 评分：距离核心越近越好（避免散落在地图边缘），但有随机因子
        val distToCore = if (nearestCore != null) {
            Mathf.dst(x, y, nearestCore.x, nearestCore.y)
        } else 0f

        val score = distToCore + Mathf.random(150f)

        if (score < bestScore) {
            bestScore = score
            bestPos = Vec2(x, y)
        }
    }

    return bestPos
}

/**
 * 统一的强制寻找 - 最后手段，仅保证地形有效，忽略防挤压和敌人
 */
private fun findForcedPositionUnified(unitType: UnitType): Vec2? {
    val worldWidth = Vars.world.width()
    val worldHeight = Vars.world.height()

    repeat(600) {
        val margin = 2
        val tx = Mathf.random(margin, worldWidth - margin)
        val ty = Mathf.random(margin, worldHeight - margin)
        val tile = Vars.world.tile(tx, ty)

        if (isValidPosition(tile, unitType)) {
            return Vec2(
                tx * Vars.tilesize + Vars.tilesize / 2f, ty * Vars.tilesize + Vars.tilesize / 2f
            )
        }
    }
    return null
}

// ==================== 辅助函数 ====================

private fun isInWorldBounds(x: Float, y: Float): Boolean {
    val tx = (x / Vars.tilesize).toInt()
    val ty = (y / Vars.tilesize).toInt()
    return tx >= 0 && tx < Vars.world.width() && ty >= 0 && ty < Vars.world.height()
}

// ==================== 特效部分（保持不变）====================

private fun createLandingEffect(unitType: UnitType, x: Float, y: Float) {
    val effect = Effect(180f) { e ->
        val region = (e.data as UnitType).fullIcon
        val fin = (1f - e.fin()).coerceIn(0f, 1f)
        val fout = Interp.pow5Out.apply(1f - fin)

        val seed = e.id.toLong()
        val randX = Mathf.randomSeedRange(seed + 3, 4f)
        val randY = Mathf.randomSeedRange(seed + 2, 30f)
        val randRot = Mathf.randomSeedRange(seed, 50f)

        val unitWidth = region.width * region.scl()
        val unitHeight = region.height * region.scl()
        val unitSize = max(unitWidth, unitHeight)
        val sizeRatio = unitSize / 32f
        val effectScale = max(1f, sizeRatio * 0.9f)

        val cx = e.x + Interp.pow2In.apply(fin) * (12f + randX)
        val startYOffset = 0
        val cy = e.y + startYOffset + Interp.pow5In.apply(fin) * (100f + randY + unitSize * 0.5f)
        val rotation = fin * (130f + randRot)
        val scale = (1f - fout) * 1.3f + 1f
        val alpha = fout

        Draw.z(110.001f)
        Draw.color(Pal.engine)
        val fslope = (0.5f - abs(fin - 0.5f)) * 2f
        val rad = 0.2f + fslope
        val baseLightRadius = 25f * effectScale
        val lightRadius = baseLightRadius * (rad + scale - 1f)
        val segments = max(10, (10 * effectScale).toInt())

        Fill.light(
            cx, cy, segments, lightRadius, Tmp.c2.set(Pal.engine).a(alpha), Tmp.c1.set(Pal.engine).a(0f)
        )

        Draw.alpha(alpha)
        val triLen = 40f * effectScale * (rad + scale - 1f)
        val triWidth = 6f * effectScale
        val pushDist = unitSize * 0.4f * (1f - fin * 0.3f)

        for (i in 0 until 4) {
            val angle = i * 90f + rotation
            val tx = cx + Angles.trnsx(angle, pushDist)
            val ty = cy + Angles.trnsy(angle, pushDist)
            Drawf.tri(tx, ty, triWidth, triLen, angle)
        }

        Draw.color()
        Draw.z(129f)
        val scl = scale * region.scl()
        val rw = region.width * scl
        val rh = region.height * scl

        Draw.alpha(alpha)
        Draw.rect(region, cx, cy - unitHeight * 0.1f, rw, rh, rotation)

        Tmp.v1.trns(225f, Interp.pow3In.apply(fin) * (250f + unitSize * 2f))
        Draw.z(116f)
        Draw.color(0f, 0f, 0f, 0.22f * alpha)
        Draw.rect(region, cx + Tmp.v1.x, cy + Tmp.v1.y, rw, rh, rotation)

        Draw.reset()

        if (Mathf.chanceDelta((0.3f * (1f - fin * 0.5f)).toDouble())) {
            val smokeRange = 3f * effectScale
            Fx.rocketSmoke.at(
                cx + Mathf.range(smokeRange), cy + Mathf.range(smokeRange), fin
            )
        }
    }

    effect.at(x, y, 0f, unitType)
}
