# Mindustry Draw 系统详细文档

## 目录
1. [概述](#概述)
2. [DrawBlock - 方块绘制基类](#drawblock---方块绘制基类)
3. [内置 DrawBlock 实现](#内置-drawblock-实现)
4. [DrawPart - 部件绘制系统](#drawpart---部件绘制系统)
5. [Drawf - 绘制工具类](#drawf---绘制工具类)
6. [Layer - 图层系统](#layer---图层系统)
7. [实际使用示例](#实际使用示例)

---

## 概述

Mindustry 的绘制系统分为几个层次：
- **DrawBlock**: 用于方块的自定义绘制
- **DrawPart**: 用于炮塔/单位部件的复杂绘制
- **Drawf**: 提供各种绘制工具方法
- **Layer**: 定义绘制层级顺序

---

## DrawBlock - 方块绘制基类

`DrawBlock` 是所有方块绘制器的抽象基类，位于 `mindustry.world.draw.DrawBlock`。

### 核心方法

```java
public abstract class DrawBlock {
    // 绘制方块本身
    public void draw(Building build) {}
    
    // 绘制蓝图/计划状态下的方块
    public void drawPlan(Block block, BuildPlan plan, Eachable<BuildPlan> list) {}
    
    // 绘制额外的光照效果
    public void drawLight(Building build) {}
    
    // 加载纹理资源
    public void load(Block block) {}
    
    // 返回方块图标
    public TextureRegion[] icons(Block block) { return new TextureRegion[]{}; }
    
    // 获取需要描边的区域
    public void getRegionsToOutline(Block block, Seq<TextureRegion> out) {}
}
```

### 关键属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `iconOverride` | String[] | 覆盖默认图标的后缀名数组 |
| `rand` | Rand | 静态随机数生成器 |

### 使用方式

在方块定义中设置绘制器：
```kotlin
// Kotlin 示例
val myBlock = object : GenericCrafter("my-block") {
    init {
        drawer = DrawMulti(
            DrawDefault(),
            DrawFlame(Color.orange)
        )
    }
}
```

---

## 内置 DrawBlock 实现

### 1. DrawDefault - 默认绘制

最基础的绘制器，只绘制方块的基础纹理。

```java
public class DrawDefault extends DrawBlock {
    @Override
    public void draw(Building build) {
        Draw.rect(build.block.region, build.x, build.y, build.drawrot());
    }
}
```

**使用场景**: 简单的方块，不需要特殊效果。

---

### 2. DrawMulti - 组合绘制

将多个绘制器组合在一起，按顺序执行。

```java
public class DrawMulti extends DrawBlock {
    public DrawBlock[] drawers = {};
    
    public DrawMulti(DrawBlock... drawers) {
        this.drawers = drawers;
    }
}
```

**使用示例**:
```kotlin
drawer = DrawMulti(
    DrawDefault(),                    // 绘制基础
    DrawRegion("-rotator", 2f, true), // 绘制旋转部件
    DrawRegion("-top")                // 绘制顶部
)
```

---

### 3. DrawRegion - 区域绘制

绘制指定的纹理区域，支持旋转。

```java
public class DrawRegion extends DrawBlock {
    public String suffix = "";           // 纹理后缀
    public @Nullable String name;        // 覆盖纹理名称
    public @Nullable Color color;        // 着色颜色
    public boolean spinSprite = false;   // 是否使用旋转精灵
    public boolean drawPlan = true;      // 是否在蓝图中绘制
    public boolean buildingRotate = false; // 是否随建筑旋转
    public float rotateSpeed = 0;        // 旋转速度
    public float x, y;                   // 偏移位置
    public float rotation;               // 固定旋转角度
    public float layer = -1;             // 图层覆盖
}
```

**构造函数**:
- `DrawRegion()` - 空构造
- `DrawRegion(String suffix)` - 使用后缀
- `DrawRegion(String suffix, float rotateSpeed)` - 带旋转
- `DrawRegion(String suffix, float rotateSpeed, boolean spinSprite)` - 完全配置

**使用示例**:
```kotlin
// 静态顶部
drawer = DrawRegion("-top")

// 旋转的转子（2倍速度，使用spinSprite正确光照）
drawer = DrawRegion("-rotator", 2f, true)

// 带颜色的覆盖层
drawer = DrawRegion("-glow").apply {
    color = Color.red
    layer = Layer.blockAdditive
}
```

---

### 4. DrawTurret - 炮塔绘制

专为炮塔设计的复杂绘制器。

```java
public class DrawTurret extends DrawBlock {
    public Seq<DrawPart> parts = new Seq<>();     // 可动部件
    public String basePrefix = "";                // 底座前缀
    public @Nullable Liquid liquidDraw;           // 覆盖液体
    
    public float turretLayer = Layer.turret;      // 炮塔层
    public float shadowLayer = Layer.turret - 0.5f; // 阴影层
    public float heatLayer = Layer.turretHeat;    // 热量层
    
    public TextureRegion base, liquid, top, heat, preview, outline;
}
```

**特性**:
- 自动绘制底座、炮塔本体、液体层、顶部
- 支持热量效果（红色叠加）
- 支持自定义 DrawPart 部件
- 自动处理后坐力偏移

**使用示例**:
```kotlin
drawer = DrawTurret().apply {
    parts.add(
        RegionPart("-blade").apply {
            mirror = true                    // 对称镜像
            under = true                     // 绘制在炮塔下方
            moveRot = 22.5f                  // 旋转移动
            progress = PartProgress.warmup   // 使用warmup进度
        }
    )
}
```

---

### 5. DrawFlame - 火焰效果

绘制燃烧火焰效果，常用于熔炉类方块。

```java
public class DrawFlame extends DrawBlock {
    public Color flameColor = Color.valueOf("ffc999");
    public TextureRegion top;
    public float lightRadius = 60f;      // 光照半径
    public float lightAlpha = 0.65f;     // 光照透明度
    public float flameRadius = 3f;       // 火焰半径
    public float flameRadiusIn = 1.9f;   // 内部火焰半径
    public float flameX, flameY;         // 火焰位置偏移
}
```

**使用示例**:
```kotlin
drawer = DrawFlame(Color.valueOf("ffaa55"))
```

---

### 6. DrawGlowRegion - 发光区域

绘制发光效果（加法混合）。

```java
public class DrawGlowRegion extends DrawBlock {
    public Blending blending = Blending.additive;
    public String suffix = "-glow";
    public float alpha = 0.9f;           // 基础透明度
    public float glowScale = 10f;        // 闪烁周期
    public float glowIntensity = 0.5f;   // 闪烁强度
    public float rotateSpeed = 0f;       // 旋转速度
    public float layer = Layer.blockAdditive;
    public boolean rotate = false;       // 是否随建筑旋转
    public Color color = Color.red.cpy();
}
```

**使用示例**:
```kotlin
drawer = DrawGlowRegion().apply {
    suffix = "-glow"
    color = Color.cyan
    alpha = 0.8f
    glowScale = 8f
}
```

---

### 7. DrawHeatRegion - 热量区域

绘制热量发光效果。

```java
public class DrawHeatRegion extends DrawBlock {
    public Color color = new Color(1f, 0.22f, 0.22f, 0.8f);
    public float pulse = 0.3f;           // 脉冲幅度
    public float pulseScl = 10f;         // 脉冲周期
    public float layer = Layer.blockAdditive;
    public String suffix = "-glow";
}
```

---

### 8. DrawFade - 淡入淡出

基于warmup的淡入淡出效果。

```java
public class DrawFade extends DrawBlock {
    public String suffix = "-top";
    public float alpha = 0.6f;           // 最大透明度
    public float scale = 3f;             // 正弦波动周期
}
```

---

### 9. DrawLiquidRegion - 液体区域

绘制液体填充效果。

```java
public class DrawLiquidRegion extends DrawBlock {
    public Liquid drawLiquid;            // 指定液体（null使用当前液体）
    public String suffix = "-liquid";
    public float alpha = 1f;
}
```

---

### 10. DrawLiquidTile - 液体平铺

平铺液体帧动画。

```java
public class DrawLiquidTile extends DrawBlock {
    public Liquid drawLiquid;
    public float padding;                // 内边距
    public float alpha = 1f;
}
```

---

### 11. DrawArcSmelt - 电弧冶炼效果

电弧冶炼炉的特效。

```java
public class DrawArcSmelt extends DrawBlock {
    public Color flameColor = Color.valueOf("f58349");
    public Color midColor = Color.valueOf("f2d585");
    public int particles = 25;           // 粒子数量
    public float particleLife = 40f;     // 粒子生命周期
    public Blending blending = Blending.additive;
}
```

---

### 12. DrawBubbles - 气泡效果

绘制上升气泡。

```java
public class DrawBubbles extends DrawBlock {
    public Color color = Color.valueOf("7457ce");
    public int amount = 12;              // 气泡数量
    public float spread = 3f;            // 扩散范围
    public boolean fill = false;         // 是否填充
}
```

---

### 13. DrawCircles - 扩散圆圈

绘制扩散的圆圈效果。

```java
public class DrawCircles extends DrawBlock {
    public Color color = Color.valueOf("7457ce");
    public int amount = 5;               // 圆圈数量
    public float radius = 12f;
    public Interp strokeInterp = Interp.pow3In;
}
```

---

### 14. DrawCells - 细胞效果

绘制类似细胞培养的液体效果。

```java
public class DrawCells extends DrawBlock {
    public Color color = Color.white;
    public Color particleColorFrom = Color.black;
    public Color particleColorTo = Color.black;
    public int particles = 12;
}
```

---

### 15. DrawCultivator - 培养效果

培养皿的特殊效果。

```java
public class DrawCultivator extends DrawBlock {
    public Color plantColor = Color.valueOf("5541b1");
    public Color plantColorLight = Color.valueOf("7457ce");
    public int bubbles = 12;
}
```

---

### 16. DrawFrames - 帧动画

简单的帧动画序列。

```java
public class DrawFrames extends DrawBlock {
    public int frames = 3;               // 帧数
    public float interval = 5f;          // 帧间隔
    public boolean sine = true;          // 是否正弦往复
}
```

**纹理命名**: `block-name-frame0`, `block-name-frame1`...

---

### 17. DrawBlurSpin - 模糊旋转

根据速度切换模糊纹理。

```java
public class DrawBlurSpin extends DrawBlock {
    public String suffix = "";
    public float rotateSpeed = 1f;
    public float blurThresh = 0.7f;      // 切换到模糊纹理的阈值
}
```

**纹理**: `name-suffix` 和 `name-suffix-blur`

---

### 18. DrawMultiWeave - 多重编织

两个反向旋转的编织层。

```java
public class DrawMultiWeave extends DrawBlock {
    public float rotateSpeed = 1f;
    public float rotateSpeed2 = -0.9f;   // 第二层速度
    public Color glowColor = new Color(1f, 0.4f, 0.4f, 0.8f);
}
```

---

### 19. DrawParticles - 粒子效果

通用粒子发射器。

```java
public class DrawParticles extends DrawBlock {
    public Color color = Color.valueOf("f2d585");
    public int particles = 30;
    public float particleLife = 70f;
    public boolean poly = false;         // 使用多边形而非圆形
    public Interp particleInterp = new PowIn(1.5f);
}
```

---

### 20. DrawPistons - 活塞效果

绘制移动的活塞。

```java
public class DrawPistons extends DrawBlock {
    public float sinMag = 4f;            // 移动幅度
    public float sinScl = 6f;            // 周期
    public int sides = 4;                // 活塞数量
    public String suffix = "-piston";
}
```

---

### 21. DrawPlasma - 等离子效果

等离子弧反应堆效果。

```java
public class DrawPlasma extends DrawFlame {
    public int plasmas = 4;
    public Color plasma1 = Color.valueOf("ffd06b");
    public Color plasma2 = Color.valueOf("ff361b");
}
```

---

### 22. DrawPower - 电量显示

显示电力状态。

```java
public class DrawPower extends DrawBlock {
    public String suffix = "-power";
    public boolean mixcol = true;        // 使用颜色混合
    public Color emptyLightColor = Color.valueOf("f8c266");
    public Color fullLightColor = Color.valueOf("fb9567");
}
```

---

### 23. DrawPulseShape - 脉冲形状

绘制脉冲扩散的形状。

```java
public class DrawPulseShape extends DrawBlock {
    public Color color = Pal.accent;
    public float stroke = 2f;
    public float timeScl = 100f;         // 脉冲周期
    public boolean square = true;        // 方形或菱形
}
```

---

### 24. DrawShape - 基础形状

绘制简单几何形状。

```java
public class DrawShape extends DrawBlock {
    public Color color = Pal.accent;
    public int sides = 4;                // 边数
    public float radius = 2f;
    public boolean useWarmupRadius = false;
}
```

---

### 25. DrawSideRegion - 方向性区域

根据旋转方向切换纹理。

```java
public class DrawSideRegion extends DrawBlock {
    // 使用 -top1 和 -top2 纹理
    // rotation > 1 时使用 top2
}
```

---

### 26. DrawSpikes - 尖刺效果

绘制旋转的尖刺。

```java
public class DrawSpikes extends DrawBlock {
    public Color color = Color.valueOf("7457ce");
    public int amount = 10;              // 尖刺数量
    public int layers = 1;               // 层数
    public float rotateSpeed = 0.8f;
}
```

---

### 27. DrawWeave - 编织效果

简单的编织动画。

```java
public class DrawWeave extends DrawBlock {
    // 使用 -weave 纹理
}
```

---

### 28. DrawWarmupRegion - Warmup淡入

基于warmup的区域淡入。

```java
public class DrawWarmupRegion extends DrawBlock {
    public float sinMag = 0.6f;          // 闪烁幅度
    public float sinScl = 8f;            // 闪烁周期
    public Color color = Color.valueOf("ff9b59");
}
```

---

### 29. DrawHeatInput/DrawHeatOutput - 热量输入输出

显示热量传递方向。

```java
public class DrawHeatInput extends DrawBlock {
    public String suffix = "-heat";
    public Color heatColor = new Color(1f, 0.22f, 0.22f, 0.8f);
}

public class DrawHeatOutput extends DrawBlock {
    public int rotOffset = 0;            // 旋转偏移
    public boolean drawGlow = true;
}
```

---

## DrawPart - 部件绘制系统

`DrawPart` 用于炮塔和单位的复杂部件动画，位于 `mindustry.entities.part.DrawPart`。

### 核心概念

```java
public abstract class DrawPart {
    public static final PartParams params = new PartParams();
    
    public boolean turretShading;        // 是否使用炮塔着色
    public boolean under = false;        // 是否绘制在主体下方
    public int weaponIndex = 0;          // 武器索引
    public int recoilIndex = -1;         // 后坐力索引
    
    public abstract void draw(PartParams params);
}
```

### PartParams 参数

```java
public static class PartParams {
    public float warmup, reload, smoothReload, heat, recoil, life, charge;
    public float x, y, rotation;
    public int sideOverride = -1, sideMultiplier = 1;
}
```

### PartProgress 进度函数

| 进度类型 | 说明 |
|---------|------|
| `reload` | 装填进度 (1=刚射击, 0=准备就绪) |
| `smoothReload` | 平滑装填 |
| `warmup` | 预热进度 (0-1) |
| `charge` | 充能进度 |
| `recoil` | 后坐力 |
| `heat` | 热量 |
| `life` | 生命周期 (导弹用) |

### 链式操作

```java
PartProgress.warmup                    // 基础warmup
    .delay(0.5f)                       // 延迟50%开始
    .curve(0, 0.5f)                    // 压缩到前半段
    .slope()                           // 应用斜坡曲线
    .clamp()                           // 限制在0-1
```

---

### RegionPart - 纹理部件

```java
public class RegionPart extends DrawPart {
    public String suffix = "";
    public boolean mirror = false;       // 镜像对称
    public boolean outline = true;       // 绘制描边
    public boolean drawRegion = true;
    
    // 位置动画
    public float x, y;                   // 基础位置
    public float moveX, moveY;           // 移动距离
    public float moveRot;                // 旋转角度
    
    // 缩放动画
    public float growX, growY;           // 增长距离
    public PartProgress growProgress;    // 缩放进度
    
    // 颜色
    public Color color, colorTo;         // 颜色过渡
    public Color mixColor, mixColorTo;   // 混合颜色
    
    // 热量
    public TextureRegion heat;
    public Color heatColor = Pal.turretHeat.cpy();
    public PartProgress heatProgress = PartProgress.heat;
    
    // 子部件
    public Seq<DrawPart> children = new Seq<>();
    public Seq<PartMove> moves = new Seq<>();
}
```

**使用示例**:
```kotlin
RegionPart("-blade").apply {
    mirror = true
    under = false
    x = 3f
    moveX = 2f
    moveRot = 45f
    progress = PartProgress.warmup.delay(0.2f)
    heatColor = Color.red
    
    // 添加子部件
    children.add(
        RegionPart("-blade-tip").apply {
            y = 4f
            moveY = 2f
        }
    )
}
```

---

### ShapePart - 形状部件

```java
public class ShapePart extends DrawPart {
    public boolean circle = false;       // 圆形或正多边形
    public boolean hollow = false;       // 空心
    public int sides = 3;                // 边数
    public float radius = 3f, radiusTo = -1f;  // 半径过渡
    public float stroke = 1f, strokeTo = -1f;  // 描边过渡
    public Color color = Color.white;
    public float rotateSpeed = 0f;
}
```

---

### HaloPart - 光环部件

```java
public class HaloPart extends DrawPart {
    public boolean hollow = false;
    public boolean tri = false;          // 三角形
    public int shapes = 3;               // 形状数量
    public float haloRadius = 10f;       // 光环半径
    public float haloRotateSpeed = 0f;   // 光环旋转速度
}
```

---

### FlarePart - 光晕部件

```java
public class FlarePart extends DrawPart {
    public int sides = 4;
    public float radius = 100f;
    public float innerScl = 0.5f;        // 内圈比例
    public Color color1 = Pal.techBlue;
    public Color color2 = Color.white;
}
```

---

### HoverPart - 悬浮部件

```java
public class HoverPart extends DrawPart {
    public float radius = 4f;
    public float phase = 50f;            // 相位周期
    public int circles = 2;              // 圆圈数量
}
```

---

### EffectSpawnerPart - 效果生成器

```java
public class EffectSpawnerPart extends DrawPart {
    public float x, y, width, height;
    public boolean mirror = false;
    public float effectChance = 0.1f;
    public Effect effect = Fx.sparkShoot;
}
```

---

## Drawf - 绘制工具类

`Drawf` 提供了大量便捷的绘制方法，位于 `mindustry.graphics.Drawf`。

### 火焰效果

```java
// 绘制火焰
Drawf.flame(x, y, divisions, rotation, length, width, pan);
Drawf.flameFront(x, y, divisions, rotation, length, width);
```

### 光照

```java
// 点光源
Drawf.light(x, y, radius, color, opacity);
Drawf.light(pos, radius, color, opacity);

// 纹理光源
Drawf.light(x, y, region, color, opacity);
Drawf.light(x, y, region, rotation, color, opacity);

// 线光源
Drawf.light(x1, y1, x2, y2);
Drawf.light(x1, y1, x2, y2, stroke, color, alpha);
```

### 选择效果

```java
// 选中框
Drawf.selected(building, color);
Drawf.selected(tile, color);

// 目标标记
Drawf.target(x, y, rad, color);
Drawf.target(x, y, rad, alpha, color);

// 虚线框
Drawf.dashRect(color, x, y, width, height);
Drawf.dashSquare(color, x, y, size);
```

### 线条

```java
// 带阴影的线
Drawf.line(color, x1, y1, x2, y2);

// 虚线
Drawf.dashLine(color, x1, y1, x2, y2);
Drawf.dashLineDst(color, x1, y1, x2, y2);

// 限制长度的线
Drawf.limitLine(start, dest, len1, len2, color);
```

### 阴影

```java
Drawf.shadow(x, y, rad);
Drawf.shadow(x, y, rad, alpha);
Drawf.shadow(region, x, y);
Drawf.shadow(region, x, y, rotation);
```

### 液体

```java
Drawf.liquid(region, x, y, alpha, color);
Drawf.liquid(region, x, y, alpha, color, rotation);
```

### 建造效果

```java
// 建造中效果
Drawf.construct(building, content, rotation, progress, alpha, time);
Drawf.construct(x, y, region, rotation, progress, alpha, time);
Drawf.construct(x, y, region, color, rotation, progress, alpha, time);
```

### 激光

```java
Drawf.laser(line, edge, x1, y1, x2, y2);
Drawf.laser(line, start, end, x1, y1, x2, y2, scale);
```

### 几何图形

```java
// 箭头
Drawf.arrow(x1, y1, x2, y2, length, radius);
Drawf.arrow(x1, y1, x2, y2, length, radius, color);

// 三角形
Drawf.tri(x, y, width, length, rotation);

// 选择框
Drawf.select(x, y, radius, color);
Drawf.square(x, y, radius, rotation, color);
Drawf.circles(x, y, rad);
Drawf.circles(x, y, rad, color);

// 十字
Drawf.cross(x, y, radius, color);
```

### 加法混合

```java
Drawf.additive(region, color, x, y);
Drawf.additive(region, color, x, y, rotation);
Drawf.additive(region, color, x, y, rotation, layer);
Drawf.additive(region, color, alpha, x, y, width, height, layer);
```

### 旋转精灵

```java
// 正确光照的旋转精灵
Drawf.spinSprite(region, x, y, rotation);
```

---

## Layer - 图层系统

`Layer` 定义了所有绘制层级，位于 `mindustry.graphics.Layer`。

```java
public class Layer {
    public static final float
        min = -11,
        background = -10,        // 背景
        floor = 0,               // 地板
        scorch = 10,             // 焦痕
        debris = 20,             // 碎片
        blockUnder = 29.5f,      // 方块底部
        block = 30,              // 方块
        blockCracks = 30.1f,     // 裂纹
        blockAfterCracks = 30.2f,// 裂纹后
        blockAdditive = 31,      // 加法混合
        blockProp = 32,          // 道具
        blockOver = 35,          // 方块上方
        blockBuilding = 40,      // 建造中
        turret = 50,             // 炮塔
        turretHeat = 50.1f,      // 炮塔热量
        groundUnit = 60,         // 地面单位
        power = 70,              // 电力线
        legUnit = 75f,           // 多足单位
        darkness = 80,           // 黑暗
        plans = 85,              // 蓝图
        flyingUnitLow = 90,      // 低空飞行单位
        bullet = 100,            // 子弹
        effect = 110,            // 效果
        flyingUnit = 115,        // 飞行单位
        overlayUI = 120,         // UI覆盖
        buildBeam = 122,         // 建造光束
        shields = 125,           // 护盾
        weather = 130,           // 天气
        light = 140,             // 光照
        playerName = 150,        // 玩家名称
        fogOfWar = 155,          // 战争迷雾
        space = 160,             // 空间效果
        end = 200,               // 结束
        endPixeled = 210,        // 像素化后
        max = 220;
}
```

**使用示例**:
```kotlin
// 临时切换图层
val prevZ = Draw.z()
Draw.z(Layer.blockAdditive)
// 绘制发光内容
Draw.z(prevZ)

// 或直接使用 Drawf.additive
Drawf.additive(region, color, x, y, rotation, Layer.turretHeat)
```

---

## 实际使用示例

### 示例1: 简单的工作台

```kotlin
val crafter = object : GenericCrafter("simple-crafter") {
    init {
        size = 2
        drawer = DrawMulti(
            DrawDefault(),
            DrawFlame(),
            DrawGlowRegion("-glow")
        )
    }
}
```

### 示例2: 复杂的炮塔

```kotlin
val turret = object : ItemTurret("complex-turret") {
    init {
        size = 3
        drawer = DrawTurret().apply {
            parts.addAll(
                RegionPart("-side").apply {
                    mirror = true
                    under = true
                    moveX = 2f
                    progress = PartProgress.warmup
                },
                RegionPart("-barrel").apply {
                    moveY = -2f
                    progress = PartProgress.recoil
                    heatColor = Color.red
                    heatProgress = PartProgress.heat
                },
                HaloPart().apply {
                    color = Color.cyan
                    haloRadius = 8f
                    shapes = 3
                    haloRotateSpeed = 1f
                }
            )
        }
    }
}
```

### 示例3: 自定义绘制

```kotlin
class CustomBlock(name: String) : Block(name) {
    init {
        // 完全自定义绘制
    }
    
    inner class CustomBuild : Building() {
        override fun draw() {
            super.draw()
            
            // 保存当前状态
            val prevZ = Draw.z()
            
            // 在特定层绘制
            Draw.z(Layer.blockAdditive)
            Draw.color(Color.cyan)
            Draw.alpha(0.5f)
            Fill.circle(x, y, 8f)
            
            // 恢复状态
            Draw.color()
            Draw.z(prevZ)
        }
        
        override fun drawSelect() {
            // 绘制选中时的额外效果
            Drawf.selected(this, Pal.accent)
        }
    }
}
```

### 示例4: 组合多个效果

```kotlin
drawer = DrawMulti(
    // 1. 基础
    DrawDefault(),
    
    // 2. 液体显示
    DrawLiquidRegion(),
    
    // 3. 旋转部件
    DrawRegion("-rotator", 3f, true),
    
    // 4. 顶部发光
    DrawGlowRegion("-top").apply {
        color = Color.gold
        alpha = 0.7f
    },
    
    // 5. 热量效果
    DrawHeatRegion("-heat").apply {
        color = Color.red
        pulse = 0.4f
    },
    
    // 6. 最终顶部
    DrawRegion("-top")
)
```

---

## 纹理命名约定

| 后缀 | 用途 |
|------|------|
| (无) | 基础纹理 |
| `-top` | 顶部覆盖层 |
| `-glow` | 发光层 |
| `-heat` | 热量层 |
| `-liquid` | 液体层 |
| `-rotator` | 旋转部件 |
| `-weave` | 编织层 |
| `-frame0`, `-frame1`... | 帧动画 |
| `-blur` | 模糊版本（用于高速旋转） |
| `-outline` | 描边 |
| `-r`, `-l` | 右/左（用于镜像部件） |
| `-base` | 底座 |
| `-preview` | 预览图 |

---

## 最佳实践

1. **图层管理**: 始终正确恢复图层 `Draw.z(prevZ)`
2. **颜色恢复**: 绘制后调用 `Draw.color()` 或 `Draw.reset()`
3. **混合模式**: 使用加法混合后调用 `Draw.blend()` 恢复
4. **性能考虑**: 避免每帧创建新对象，使用对象池
5. **纹理加载**: 在 `load()` 中加载纹理，不要在 `draw()` 中加载
6. **进度使用**: 使用 `build.warmup()` 获取工作状态

---

*文档基于 Mindustry 源码生成*
