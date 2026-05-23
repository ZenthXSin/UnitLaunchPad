package example.world

import arc.Core
import arc.Events
import arc.audio.Sound
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.input.KeyCode
import arc.math.Angles
import arc.math.Interp
import arc.math.Mathf
import arc.scene.ui.layout.Table
import arc.struct.Seq
import arc.util.Log
import arc.util.Time
import arc.util.Tmp
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.Vars
import mindustry.content.Fx
import mindustry.entities.Effect
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Icon
import mindustry.gen.Player
import mindustry.gen.Sounds
import mindustry.gen.Tex
import mindustry.graphics.Drawf
import mindustry.graphics.Layer
import mindustry.graphics.Pal
import mindustry.type.ItemStack
import mindustry.type.LiquidStack
import mindustry.type.Sector
import mindustry.ui.Bar
import mindustry.ui.Styles
import mindustry.world.Tile
import mindustry.world.blocks.payloads.Payload
import mindustry.world.blocks.payloads.UnitPayload
import mindustry.world.blocks.units.UnitBlock
import mindustry.world.meta.BuildVisibility
import mindustry.world.meta.Stat
import mindustry.world.meta.StatCat
import kotlin.math.max
import kotlin.math.min

open class UnitLunchPad(name: String) : UnitBlock(name) {
    var readySound: Sound = Sounds.loopMachine
    var loadSound: Sound = Sounds.loopBuild
    var inputItems: Seq<ItemStack> = Seq()
    var inputLiquids: LiquidStack? = null
    var inputPower: Float = 0f
    var timeLaunch: Float = 300f  // tick
    var shootTime: Float = 180f   // tick
    var cooldown: Float = 120f
    var waitCooldown: Float = 60f

    var defSector: Sector? = null

    init {
        super.init()
        configurable = true
        saveConfig = true
        canOverdrive = false
        update = true
        sync = true
        buildVisibility = BuildVisibility.shown
        solid = true
        outputsPayload = false
        acceptsPayload = true
        rotate = false
        hasConsumers = true

        config(Sector::class.java) { build: UnitLunchPadBuild, sector: Sector ->
            build.nowShootSector = sector
        }
        configClear { build: UnitLunchPadBuild ->
            build.nowShootSector = defSector ?: Vars.state.sector
        }

        if (!inputItems.isEmpty) {
            hasItems = true
            itemCapacity = 100
            consumeItems(*inputItems.toArray(ItemStack::class.java))
        }
        if (inputLiquids != null) {
            hasLiquids = true
            liquidCapacity = 100f
            consumeLiquid(inputLiquids?.liquid, inputLiquids!!.amount)
        }
        if (inputPower > 0f) {
            hasPower = true
            consumesPower = true
            consumePower(inputPower)
        }

        Events.on(EventType.ClientLoadEvent::class.java) {
            if (Core.settings.getString("[BY-ZXS][单位发射台][默认发射区块]") == null) return@on
            val tag = Core.settings.getString("[BY-ZXS][单位发射台][默认发射区块]").split("=")
            defSector = Vars.content.planets().find { it.name == tag[0] }.sectors.find { it.name() == tag[1] }
        }

    }

    override fun canPlaceOn(tile: Tile?, team: Team?, rotation: Int): Boolean {
        return super.canPlaceOn(tile, team, rotation) && Vars.state.isCampaign
    }
    override fun setStats() {
        super.setStats()
        stats.add(Stat("默认发射区块", StatCat("发射区块"))) { table ->
            fun update() {
                table.clear()

                table.left().top().defaults().fillX().height(40f).pad(2f)

                table.add("默认发射区块: " + if (defSector != null) "[accent]${defSector!!.planet.localizedName} - ${if (defSector!!.hasBase()) defSector!!.name() else "未探索"}[]" else "未选择")

                table.row()

                table.background(Styles.grayPanelDark)

                table.table { t ->
                    t.center().defaults().pad(4f).size(100f, 40f)

                    Vars.content.planets()
                        .select { it.startSector != 0 && it.accessible }
                        .forEach { planet ->
                            t.button(planet.localizedName, Styles.flatt) {
                                val startSector = planet.sectors.get(planet.startSector)
                                if (startSector != null) {
                                    Vars.ui.planet.showSelect(startSector) { selected: Sector? ->
                                        selected?.let {
                                            defSector = it
                                            Vars.ui.showInfoToast("目标: ${planet.localizedName} - ${it.name()}", 2f)
                                            Core.settings.put("[BY-ZXS][单位发射台][默认发射区块]", "${planet.name}=${it.name()}")
                                            update()
                                        }
                                    }
                                } else {
                                    Vars.ui.showInfo("该星球暂无可用扇区")
                                }
                            }
                        }
                }.center().pad(4f).row()
            }
            update()
        }
    }

    override fun drawPlace(x: Int, y: Int, rotation: Int, valid: Boolean) {
        if (!Vars.state.isCampaign) {
            drawPlaceText("[red]只能在战役中使用", x, y, valid)
        }
    }

    override fun setBars() {
        super.setBars()
        addBar("progress") { e: UnitLunchPadBuild? ->
            Bar("bar.progress", Pal.ammo) { e?.progress ?: 0f }
        }
    }

    open inner class UnitLunchPadBuild : UnitBuild() {
        var shootProgress = 0f
        var totalProgress = 1f / timeLaunch
        var totalShootProgress = 1f / shootTime
        var draw = 0      // 0:未准备 1:准备中 2:发射
        var nowShootSector: Sector = defSector ?: Vars.state.sector
        var engineTime = 0f
        var start = 0     // 0=未开始, 1=等待爆炸, 2=升空
        var startCooldown = false

        var visualFade = 0f

        override fun drawSelect() = Unit

        // 状态判定1：是否应该消耗资源（在准备阶段且资源充足时消耗）
        override fun shouldConsume(): Boolean {
            return payload != null && progress < 1f && canReady()
        }

        override fun updateTile() {
            super.updateTile()

            val targetFade = if (canReady()) 1f else 0f
            visualFade = Mathf.lerpDelta(visualFade, targetFade, 0.1f)

            // 使用canReady()检查资源，使用progress检查进度
            if (canReady() && progress <= 1f && payload != null) {
                Vars.control.sound.loop(readySound, this, 1f)
                draw = 1
                progress += totalProgress * Time.delta
                if (progress >= 1f && canShoot()) {
                    shoot()
                    Sounds.padLaunch.at(this)
                }
            } else if (canReady()) {
                Vars.control.sound.loop(loadSound, this, 1f)
            }

            if (startCooldown) {
                shootProgress -= Time.delta / cooldown
                if (shootProgress <= 0f) {
                    shootProgress = 0f
                    startCooldown = false
                    progress = 0f
                    payload = null
                }
            }
        }

        override fun display(table: Table) {
            super.display(table)
            if (Vars.state.isCampaign && !Vars.net.client() && this.team === Vars.player.team()) {
                table.row()
                table.label {
                    val dest = nowShootSector
                    Core.bundle.format(
                        "launch.destination",
                        if (dest.hasBase()) "[accent]" + dest.name() else Core.bundle.get("sectors.nonelaunch")
                    )
                }.pad(4.0f).wrap().width(200.0f).left()
            }
        }

        // 状态判定2：生产是否有效（资源是否充足）
        override fun productionValid(): Boolean = canReady()

        override fun shouldShowConfigure(player: Player?): Boolean {
            return Vars.state.isCampaign
        }

        override fun buildConfiguration(table: Table) {
            if (Vars.state.isCampaign && !Vars.net.client()) {

                table.table { t ->
                    t.left().top().defaults().fillX().height(40f).pad(2f)

                    Vars.content.planets()
                        .select { it.startSector != 0 && it.accessible }
                        .forEach { planet ->
                            t.button(planet.localizedName, Styles.flatt) {
                                val startSector = planet.sectors.get(planet.startSector)
                                if (startSector != null) {
                                    Vars.ui.planet.showSelect(startSector) { selected: Sector? ->
                                        selected?.let {
                                            nowShootSector = it
                                            Vars.ui.showInfoToast("目标: ${planet.localizedName} - ${it.name()}", 2f)
                                        }
                                    }
                                } else {
                                    Vars.ui.showInfo("该星球暂无可用扇区")
                                }
                                deselect()
                            }.width(200f).maxHeight(200f).pad(4f).left().row()
                        }
                }.width(200f).maxHeight(200f).pad(4f).left().row()

                table.table { info ->
                    info.left().defaults().left()
                    info.label { "当前目标:" }.padRight(4f)
                    info.label {
                        val dest = nowShootSector
                        "[accent]${dest.planet.localizedName} - ${if (dest.hasBase()) dest.name() else "未探索"}[]"
                    }.wrap().width(160f)
                }.pad(4f).left().row()

            } else {
                deselect()
            }
        }

        override fun acceptPayload(source: Building?, payloa: Payload?): Boolean {
            return payloa is UnitPayload && payload == null && payloa.unit.type.fullIcon.width <= Core.atlas.find(block.name + "-pod").width && payloa.unit.type.fullIcon.height <= Core.atlas.find(
                block.name + "-pod"
            ).height
        }

        override fun acceptUnitPayload(unit: mindustry.gen.Unit): Boolean {
            return payload == null && unit.type.fullIcon.width <= Core.atlas.find(block.name + "-pod").width && unit.type.fullIcon.height <= Core.atlas.find(
                block.name + "-pod"
            ).height
        }

        override fun draw() {
            super.draw()

            try {
                if (draw == 0) {
                    // 空闲状态
                }
                if (draw == 1 || (progress >= 0f && shootProgress <= 0f)) {
                    val unit = payload?.unit?.type
                    if (unit != null) {
                        Draw.draw(Layer.block) {
                            // 基础动画淡入淡出 * 资源状态淡入淡出
                            val baseFade = when {
                                progress < 0.2f -> progress / 0.2f
                                progress > 0.8f -> (1f - progress) / 0.2f
                                else -> 1f
                            }
                            val fade = baseFade * visualFade

                            Drawf.construct(this, unit, 0f, 1 - progress, fade, Time.time)
                            Drawf.construct(
                                this,
                                Core.atlas.find(block.name + "-pod"),
                                0f,
                                progress + 0.01f,
                                fade,
                                Time.time
                            )
                        }
                    }
                }
                if (draw == 2) {
                    val unit = payload?.unit?.type
                    if (unit != null) {
                        if (start == 0) {
                            start = 1
                            engineTime = 0f
                            Time.run(30f) {
                                start = 2
                                Effect(60f) { e ->
                                    val baseRadius = 4f
                                    val maxRadius = 140f
                                    Draw.color(Pal.engine)
                                    e.scaled(30f) { f ->
                                        Lines.stroke(f.fout() * 4f)
                                        Lines.circle(e.x, e.y, baseRadius + f.finpow() * maxRadius)
                                    }
                                    e.scaled(20f) { f ->
                                        Draw.alpha(f.fout() * 0.6f)
                                        Lines.stroke(f.fout() * 6f)
                                        Lines.circle(e.x, e.y, f.finpow() * 144f)
                                    }
                                    Draw.color(Pal.engine)
                                    Lines.stroke(e.fout() * 3f)
                                    Angles.randLenVectors(e.id.toLong(), 36, e.finpow() * 144f) { x, y ->
                                        val ang = Mathf.angle(x, y)
                                        Lines.lineAngle(e.x + x, e.y + y, ang, e.fout() * 6f + 2f)
                                    }
                                    Draw.color(Pal.engine)
                                    Draw.alpha(e.fout() * 0.9f)
                                    Fill.circle(e.x, e.y, e.finpow() * 20f)
                                    Draw.z(110f)
                                    Draw.color(Pal.engine)
                                    Draw.alpha(e.fout() * 0.4f)
                                    val expand = e.finpow() * 144f
                                    Fill.circle(e.x, e.y, expand * 0.8f)
                                }.at(this)
                            }
                        }

                        val region = Core.atlas.find(block.name + "-pod")

                        if (start == 1 && progress >= 1f) {
                            Draw.rect(region, x, y)
                        }

                        if (start == 2) {
                            shootProgress += totalShootProgress * Time.delta
                            engineTime = (engineTime + 1f).coerceAtMost(45f)
                            val engineWarmup = (engineTime / 45f).coerceIn(0f, 1f)

                            val fin = shootProgress.coerceIn(0f, 1f)
                            val fout = Interp.pow5Out.apply(1f - fin)

                            val seed = id().toLong()
                            val randX = Mathf.randomSeedRange(seed + 3, 4f)
                            val randY = Mathf.randomSeedRange(seed + 2, 30f)
                            val randRot = Mathf.randomSeedRange(seed, 50f)

                            val unitWidth = region.width * region.scl()
                            val unitHeight = region.height * region.scl()
                            val unitSize = max(unitWidth, unitHeight)
                            val sizeRatio = unitSize / 32f
                            val effectScale = max(1f, sizeRatio * 0.9f)

                            val noRotRatio = 0.2f
                            val isRotating = fin >= noRotRatio

                            val rotTotal = 175f + randRot
                            val rotProgress = if (isRotating) {
                                val t = ((fin - noRotRatio) / (1f - noRotRatio)).coerceIn(0f, 1f)
                                t * t
                            } else 0f

                            val cx = x + Interp.pow3In.apply(fin) * (12f + randX)
                            val cy = y + Interp.pow4In.apply(fin) * (100f + randY + unitSize * 0.5f)
                            val rotation = rotProgress * rotTotal * 0.3f
                            val scale = (1f - fout) * 1.3f + 1f
                            val alpha = fout

                            Draw.z(110.001f)
                            Draw.color(Pal.engine)

                            val baseLightRadius = 25f * effectScale
                            val lightRadius = baseLightRadius * engineWarmup * (scale * 0.3f + 0.7f)
                            val segments = max(10, (10 * effectScale).toInt())

                            Fill.light(
                                cx, cy, segments, lightRadius,
                                Tmp.c2.set(Pal.engine).a(alpha * engineWarmup),
                                Tmp.c1.set(Pal.engine).a(0f)
                            )

                            Draw.alpha(alpha)
                            val triLen = 40f * effectScale * engineWarmup
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
                            Draw.rect(region, cx, cy, rw, rh, rotation)

                            Tmp.v1.trns(225f, Interp.pow3In.apply(fin) * (250f + unitSize * 2f))
                            Draw.z(116f)
                            Draw.color(0f, 0f, 0f, 0.22f * alpha)
                            Draw.rect(region, cx + Tmp.v1.x, cy + Tmp.v1.y, rw, rh, rotation)

                            Draw.reset()

                            if (Mathf.chanceDelta((0.3f * (1f - fin * 0.5f)).toDouble())) {
                                val smokeRange = 3f * effectScale
                                Fx.rocketSmoke.at(
                                    cx + Mathf.range(smokeRange),
                                    cy + Mathf.range(smokeRange),
                                    fin
                                )
                            }
                        }
                    }

                    if (shootProgress >= 1f && payload != null) {
                        start = 0
                        engineTime = 0f
                        draw = 0
                        Time.run(waitCooldown) {
                            startCooldown = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.err("[单位发射台]动画处理错误：$e")
            }

            if (Core.atlas.find(block.name + "-top") != null){
                Draw.z(Layer.block)
                Draw.rect(Core.atlas.find(block.name + "-top"), x, y)
                Draw.reset()
            }

            if (draw == 2 || shootProgress != 0f) {
                Draw.z(Layer.block)
                Draw.color(Color.red)
                Draw.alpha(min(0.3f, shootProgress))
                Draw.rect(Core.atlas.find(block.name + "-glow"), x, y)
                Draw.reset()
            }
        }

        fun shoot() {
            draw = 2
            payload?.unit?.type?.let { unitType ->
                UnitLaunchPadData.add(nowShootSector, unitType.name)
            }
            consume()
        }

        fun canShoot(): Boolean = progress >= 1f && payload != null

        // 状态判定3：资源检查（恢复完整逻辑）
        fun canReady(): Boolean {
            // 检查物品
            if (hasItems) {
                for (i in 0 until inputItems.size) {
                    val stack = inputItems[i]
                    if (items.get(stack.item) < stack.amount) {
                        return false
                    }
                }
            }
            // 检查液体
            if (hasLiquids && inputLiquids != null) {
                if (liquids.get(inputLiquids!!.liquid) < inputLiquids!!.amount) {
                    return false
                }
            }
            // 检查电力（使用power.status检查电网饱和度）
            if (hasPower && inputPower > 0f) {
                if (power.status < 0.99f) return false
            }
            return true
        }

        override fun write(write: Writes) {
            super.write(write)

            // 进度相关
            write.f(progress)
            write.f(shootProgress)

            // 状态相关
            write.i(draw)
            write.i(start)
            write.bool(startCooldown)
            write.f(engineTime)

            // 目标扇区（星球ID + 扇区ID）
            write.i(nowShootSector.planet.id.toInt())
            write.i(nowShootSector.id)
        }

        override fun read(read: Reads, revision: Byte) {
            super.read(read, revision)

            // 进度相关
            progress = read.f()
            shootProgress = read.f()

            // 状态相关
            draw = read.i()
            start = read.i()
            startCooldown = read.bool()
            engineTime = read.f()

            //手动开启冷却
            if (progress >= 1f && shootProgress >= 0f) startCooldown = true

            // 目标扇区
            val planetId = read.i()
            val sectorId = read.i()
            val planet = Vars.content.planets().find { it.id.toInt() == planetId }
            nowShootSector = if (planet != null && sectorId >= 0 && sectorId < planet.sectors.size) {
                planet.sectors[sectorId]
            } else {
                Vars.state.rules.sector ?: Vars.state.sector
            }
        }

    }
}

object UnitLaunchPadData {
    val data = HashMap<Sector, MutableList<String>>()
    const val TAG = "[BY-ZXS][单位发射台][单位缓存]"

    fun init() {
        fromString(Core.settings.getString(TAG) ?: "")
    }

    fun fromString(str: String) {
        data.clear()
        if (str.isBlank()) return

        str.split("$").forEach { entry ->
            if (entry.isEmpty()) return@forEach

            val parts = entry.split("=", limit = 2)
            if (parts.size != 2) return@forEach

            val keyParts = parts[0].split("@")
            if (keyParts.size != 2) return@forEach

            val planetName = keyParts[0]
            val sectorId = keyParts[1].toIntOrNull() ?: return@forEach

            val planet = Vars.content.planets().find { it.name == planetName } ?: return@forEach
            val sector = planet.sectors.get(sectorId) ?: return@forEach

            val valueList = if (parts[1].isEmpty()) {
                mutableListOf()
            } else {
                parts[1].split(":").toMutableList()
            }

            data[sector] = valueList
        }
    }

    fun add(sector: Sector, unitName: String) {
        data.getOrPut(sector) { mutableListOf() }.add(unitName)
        save()
    }

    fun remove(sector: Sector) {
        data.remove(sector)
        save()
    }

    fun get(sector: Sector): MutableList<String> {
        return data[sector] ?: mutableListOf()
    }

    fun save() {
        Core.settings.put(TAG, toString())
    }

    override fun toString(): String {
        if (data.isEmpty()) return ""
        return buildString {
            data.forEach { (sector, list) ->
                if (isNotEmpty()) append("$")
                append("${sector.planet.name}@${sector.id}=")
                append(list.joinToString(":"))
            }
        }
    }
}