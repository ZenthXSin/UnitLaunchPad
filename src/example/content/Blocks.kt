package example.content

import arc.Core
import arc.graphics.g2d.Draw
import arc.struct.Seq
import example.world.UnitLunchPad
import mindustry.Vars
import mindustry.content.Items
import mindustry.content.Liquids
import mindustry.content.TechTree
import mindustry.gen.Building
import mindustry.type.Category
import mindustry.type.ItemStack
import mindustry.type.LiquidStack
import mindustry.world.draw.DrawDefault
import mindustry.world.draw.DrawFade
import mindustry.world.draw.DrawMulti
import mindustry.world.meta.BuildVisibility

object Blocks {
    val unitLaunchPad = UnitLunchPad("单位发射台").apply {
        size = 16
        health = 50000
        timeLaunch = 3600f
        itemCapacity = 1000
        liquidCapacity = 2500f
        buildVisibility = BuildVisibility.shown
        requirements(Category.effect, ItemStack.with(Items.metaglass, 8000, Items.graphite, 6000, Items.plastanium, 4000, Items.titanium, 6000, Items.silicon, 4000, Items.phaseFabric, 3000))
        inputPower = 1250f
        inputItems = Seq(ItemStack.with(Items.copper, 400,Items.metaglass,400,Items.titanium,400))
        inputLiquids = LiquidStack(Liquids.cryofluid,20f)
        shootTime = 180f
        consumeItems(*ItemStack.with(Items.copper, 400,Items.metaglass,400,Items.titanium,400))
        consumeLiquid(Liquids.cryofluid,20f)
        consumePower(1250f)
        buildTime = 60 * 30f
    }
    val eUnitLaunchPad = UnitLunchPad("e-单位发射机").apply {
        size = 16
        health = 50000
        timeLaunch = 3600f
        itemCapacity = 1000
        liquidCapacity = 2500f
        buildVisibility = BuildVisibility.shown
        requirements(Category.effect, ItemStack.with(Items.silicon,3000,Items.phaseFabric,3000, Items.tungsten,5000,
            Items.carbide,2000))
        inputPower = 1250f
        consumePower(1250f)
        inputLiquids = LiquidStack(Liquids.water,20f)
        consumeLiquid(Liquids.water,20f)
        inputItems = Seq(ItemStack.with(Items.thorium, 500,Items.tungsten,200,Items.oxide,200))
    }
}
