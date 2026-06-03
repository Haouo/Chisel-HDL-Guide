# Part 10 — Chipyard / Rocket Chip / BOOM 風格 SoC 生成器

> ⚠️ **本章 API 多屬 Rocket Chip / Chipyard**（非 core Chisel）。語法以「概念示意」為主，重點在**讀懂 Rocket/BOOM 程式碼的心智模型**，而非背 API。

對應：reference 進階篇 ｜ 心智模型 > 細節

本章的定位是「讀懂大型 SoC generator 的語言」。如果前面 Part 4 是小型 RTL generator，
Chipyard/Rocket/BOOM 則是把 generator 推到 SoC 尺度：core、cache、bus、peripheral、device tree、
simulation harness、FPGA/ASIC flow 都由同一套 config 與 graph 組裝。

不要一開始就模仿所有 API。對教學與個人專案，先理解它們在解什麼問題：參數來源如何管理、
bus width/source ID/address map 如何協商、MMIO peripheral 如何掛載、simulation-only collateral
如何與 target RTL 分離。理解問題後，才知道哪些概念值得借，哪些重量應該暫時避開。

**本章路線圖**

- 先學：Config fragments、Parameters、LazyModule、Diplomacy、TileLink widgets、RegMapper、binders。
- 會踩坑：把 diplomatic node 當普通 wire、追不到 implicit parameter 來源、混淆 target RTL 與 harness。
- 最後能做：讀懂 Rocket/Chipyard 新增 peripheral 或 accelerator 時，參數、graph、metadata、harness 如何串起來。

**本章程式碼標記**：除非特別說明，Scala 片段都屬 **Rocket/Chipyard API｜概念示意**。
它們用來說明資料流與設計模式，不保證可直接貼到任意 Chipyard 版本編譯。

---

## M10.1 三個層級

這三個層級代表抽象半徑不同。普通 Chisel 關心單一模組內的 RTL；進階 Chisel generator
關心一族模組如何由參數產生；Chipyard/Rocket 則關心整個 SoC 的元件如何被組態、協商與整合。
閱讀大型專案時，先判斷目前程式碼位在哪一層，否則很容易把 diplomatic node 誤看成普通 wire。

```text
普通 Chisel:        Module / Bundle / Vec / Decoupled / Queue
進階 Chisel:        parameterized RTL generator（Part 4）
Rocket / Chipyard:  可組裝、可協商、可被 config fragments 改寫的 SoC generator
```
> Chipyard 的本質：**system integration as code**。
> 真正要追求的不是「Can I write less Verilog?」，而是
> **「Can I express a family of microarchitectures / SoCs without losing RTL discipline?」**

---

## M10.2 Config fragments（像 recipe 疊出一顆 SoC）

Config fragment 的價值是讓 design variant 變成可組合的 recipe。你可以用一組 fragment 建小 core，
另一組 fragment 加 BOOM、cache、UART、trace、不同 bus width，而不用改主 SoC 原始碼。
代價是參數來源變得分散，因此讀 code 時要同時追 class hierarchy 與 config 疊加順序。

Rocket/Chipyard 很少直接寫 `class MySoC(nCores: Int, hasUART: Boolean)`，而是疊 config：

標記：**Rocket/Chipyard API｜概念示意**

```scala
class MyConfig extends Config(
  new chipyard.config.WithNBigCores(1) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig
)
```
fragments 像料理步驟：`WithNBigCores` / `WithNBoomCores` / `WithInclusiveCache` / `WithUART` / `WithSystemBusWidth` / `WithTraceIO`。
> 用途：design variants / design-space exploration——換 config 不改主程式。

---

## M10.3 `Parameters` / `Field` / `Key`

`Parameters` 是全系統組態環境。它讓深層 generator 可以查到自己需要的 key，而不用一路從 top constructor
傳參數。這對大型 SoC 很有用，但也會降低 local reasoning：看到 `p(MyKey)` 時，你必須回頭追哪個
config fragment 設了它。教學專案不必一開始採用這種重量。

```scala
case object MyKey extends Field[Int](default = 8)        // 定義 key（含 default）

class MyModule(implicit p: Parameters) extends Module {
  val n = p(MyKey)                                        // 查詢 key
}
```
| 優點 | 缺點 |
|------|------|
| 全系統組態環境；各 generator 查自己要的 key；fragment 可改寫 | 參數來源不明顯；implicit `p` 讓資料流隱形；grep/閱讀困難 |

---

## M10.4 `site` / `here` / `up`（config fragment 解析）

`site/here/up` 是 Rocket config 系統中最容易讓人困惑的部分。可以把它想成三種查詢視角：
看最終整體、看目前 fragment、看被覆蓋前的舊值。它們讓 fragment 能基於其他參數推導新參數，
也讓 fragment 順序變得非常重要。

```scala
class WithThing extends Config((site, here, up, p) => {
  case X => site(Y)   // 查「最終整體 config」
  case A => here(B)   // 查「目前 fragment」
  case C => up(D)     // 查「被目前 fragment 覆蓋前的值」
})
```
> fragment **順序很重要**（後疊的覆蓋前面）。`up` 常用於「在舊值基礎上加東西」。

---

## M10.5 `LazyModule`：two-phase elaboration

LazyModule 的核心是「先建立連接圖並協商參數，再產生 RTL」。這對 bus protocol 很重要，
因為 source ID、beatBytes、address set、transfer size 往往不能由單一 module 自己決定。
讀 `LazyModule` 時，先看 body 中的 node 與 graph connection，再進 `LazyModuleImp` 看真正的 Chisel RTL。

```text
phase 1: diplomatic graph
  clients/managers/widgets declare capability
  nodes connect and negotiate parameters
            │
            ▼
phase 2: LazyModuleImp
  known widths / IDs / address sets
  generate ordinary Chisel RTL channels
```

標記：**Rocket/Chipyard API｜概念示意**

```scala
class Foo(implicit p: Parameters) extends LazyModule {
  val node = TLClientNode(/* ... */)        // phase 1：建 diplomatic graph
  lazy val module = new LazyModuleImp(this) {
    // phase 2：真正的 Chisel RTL（Reg/Wire/when/switch）
  }
}
```
| LazyModule body | LazyModuleImp |
|-----------------|---------------|
| 建 nodes、連 protocol graph、**參數協商** | 真正 RTL |

> 心智模型：先「談好怎麼接」（diplomacy），再「長出硬體」（imp）。讀 Rocket/BOOM/TileLink 必備。

---

## M10.6 Diplomacy：parameter negotiation framework

Diplomacy 解的是 SoC integration 問題，不是單一 RTL 問題。當 client、manager、crossbar、
buffer、width widget、fragmenter 都會影響協定能力時，手動傳參數很容易錯。
Diplomacy 用 graph 讓能力沿邊協商，最後在 phase 2 產生已知寬度與型別的硬體介面。

解決「SoC 級參數不能單點決定」的問題：
```text
bus width 不一定單點決定
source ID range 要協商
manager/client capability 要協商
address map 要整合
transfer size 要匹配
ordering / cacheability / executability 要描述
```
> Diplomacy = two-phase elaboration：phase 1 沿 graph 協商參數，phase 2 才生 RTL。

---

## M10.7 TileLink nodes 與 connection operators

TileLink node connection 看起來像 assignment，但其實是在接 graph。這一點非常重要：
`nodeA := nodeB` 不是把某條 wire 接起來，而是宣告兩個 diplomatic node 的關係。
真正的 ready/valid channel 會在協商後於 `LazyModuleImp` 中生成。

常見 node：`TLClientNode` / `TLManagerNode` / `TLIdentityNode` / `TLXbar` / `TLBuffer` / `TLFragmenter` / `TLWidthWidget` / `TLToAXI4` / `AXI4ToTL`。

```scala
// 這不是 signal assignment，而是 graph-level connection
someBus.node := TLBuffer() := myDevice.node
```

對照圖：

```text
普通 Chisel:
  producer.io.out ───── wires ─────► consumer.io.in
  width/protocol 已在 Bundle 型別裡固定

Diplomacy:
  client node ── TLBuffer ── bus node ── manager node
      │             │           │              │
      └──── phase 1 graph negotiation ─────────┘
                    ▼
          phase 2 產生真正 TileLink channels
```

| 普通 Chisel | Diplomacy |
|-------------|-----------|
| `io.a := io.b`（接線） | `nodeA := nodeB`（接 diplomatic graph，硬體 IO 之後才生） |

---

## M10.8 TileLink widgets（互連零件）

TileLink widgets 是 SoC integration 的標準轉接零件。`TLBuffer` 解 timing，`TLFragmenter` 解 transaction size，
`TLWidthWidget` 解 bus width，`TLXbar` 解多 client/manager 互連。它們的精神和 Part 5 的 Queue、
skid buffer、arbiter 類似，只是提升到 protocol-aware 的 SoC 層級。

| widget | 作用 |
|--------|------|
| `TLBuffer` | channel queue / timing isolation（切 ready path、改 timing） |
| `TLFragmenter` | 大 transaction 拆成小的（client 發大、manager 只支援小） |
| `TLWidthWidget` | bus width adaptation（128-bit sbus ↔ 32-bit pbus） |
| `TLXbar` | parameterized crossbar（依 address sets / source IDs / sizes 協商） |

### Protocol bridges
`TLToAXI4` / `AXI4ToTL` / `AXI4Buffer` / `AXI4Fragmenter` / `AXI4IdIndexer`
> 因為內部用 TileLink，外接 DRAM/IP 常是 AXI4/APB/AHB。

### TileLink 重要參數
`beatBytes`（beat byte 數）、`sourceId`（outstanding txn ID range）、`AddressSet`（manager 範圍）、`visibility`、`requestFifo`（ordering）、`RegionType`（cacheability / device / side-effect）。

### Manager capability（SoC-level metadata，非 signal）
`address` / `regionType` / `executable` / `supportsGet` / `supportsPutFull` / `supportsPutPartial` / `supportsArithmetic` / `supportsLogical` / `fifoId`。

---

## M10.9 Bus wrappers

Bus wrapper 把「這條 bus 的角色、寬度、clock domain、可見範圍、掛載點」集中管理。
大型 SoC 不會只有一條 bus；system bus、memory bus、peripheral bus、control bus 的效能需求和
可見 address range 都不同。分層清楚，peripheral 與 accelerator 才能掛在合理的位置。

| bus | 角色 |
|-----|------|
| `sbus` | system bus |
| `mbus` | memory bus |
| `pbus` | peripheral bus |
| `cbus` | control bus |
| `fbus` | front bus |

各 bus 可有不同 `beatBytes` / clock domain / visibility / 掛載 device。

---

## M10.10 MMIO peripheral：`TLRegisterRouter` / `regmap` / `RegField`

MMIO peripheral 的本質是把 bus transaction 轉成 register read/write 與 side effect。
`TLRegisterRouter`/`regmap` 幫你處理 TileLink slave 的細節，讓 designer 專注在 register semantics：
哪些欄位 read-only、write-one-to-clear、寫入會觸發命令、讀取是否有 side effect。

```scala
regmap(
  0x00 -> Seq(RegField(32, myReg)),         // 讀寫
  0x04 -> Seq(RegField.r(32, status)),      // read-only
  0x08 -> Seq(RegField.w(32, command)),     // write-only（可觸發 side effect）
)
```
不需手刻完整 TileLink slave。適合：UART-like control regs、accelerator command/status、interrupt enable/status、perf counter MMIO。

`RegField` 進階語意：read-only / write-only / write-one-to-clear / side-effect read/write / ready-valid backed field（例：寫 command register 觸發 accelerator transaction）。

### 貫穿案例：新增一個 MMIO Timer peripheral

以下是概念流程，不是可直接貼到任意 Chipyard 版本的完整程式。目標是看懂一個 peripheral
從 generator 到軟體 metadata、interrupt、harness 的資料流。

第一步，定義參數與 Config fragment。Config fragment 不直接接線，只描述「這個 SoC variant 要有 Timer」：

標記：**Rocket/Chipyard API｜概念示意**

```scala
case class TimerParams(address: BigInt = 0x10010000L, width: Int = 64)
case object TimerKey extends Field[Option[TimerParams]](None)

class WithTimer(address: BigInt = 0x10010000L) extends Config((site, here, up) => {
  case TimerKey => Some(TimerParams(address = address))
})
```

第二步，寫 MMIO register map。`mtime` 每拍遞增，`mtimecmp` 可由軟體寫入；當 `mtime >= mtimecmp`
時產生 interrupt。

```scala
class Timer(params: TimerParams)(implicit p: Parameters) extends LazyModule {
  val device = new SimpleDevice("timer", Seq("example,timer0"))
  val node = TLRegisterNode(
    address = Seq(AddressSet(params.address, 0xfff)),
    device = device,
    beatBytes = 4
  )
  val intnode = IntSourceNode(IntSourcePortSimple(num = 1, resources = device.int))

  lazy val module = new LazyModuleImp(this) {
    val mtime    = RegInit(0.U(params.width.W))
    val mtimecmp = RegInit(~0.U(params.width.W))
    mtime := mtime + 1.U

    val irq = mtime >= mtimecmp
    intnode.out.foreach { case (ints, _) => ints(0) := irq }

    node.regmap(
      0x00 -> Seq(RegField.r(32, mtime(31, 0))),
      0x04 -> Seq(RegField.r(32, mtime(63, 32))),
      0x08 -> Seq(RegField(32, mtimecmp(31, 0))),
      0x0c -> Seq(RegField(32, mtimecmp(63, 32)))
    )
  }
}
```

第三步，在 subsystem trait 中依照 key 決定是否 instantiate，並把 TileLink node 掛到 peripheral bus：

```scala
trait CanHavePeripheryTimer { this: BaseSubsystem =>
  private val timerOpt = p(TimerKey).map { params =>
    val timer = LazyModule(new Timer(params))
    pbus.toVariableWidthSlave(Some("timer")) { timer.node }
    ibus.fromSync := timer.intnode
    timer
  }
}
```

第四步，確認軟體 metadata。`SimpleDevice` 與 address set 會影響 device tree；interrupt node 會影響
interrupt mapping。軟體端應能看到類似：

```dts
timer@10010000 {
  compatible = "example,timer0";
  reg = <0x0 0x10010000 0x0 0x1000>;
  interrupts = <...>;
};
```

第五步，決定 harness 是否需要額外 collateral。Timer 本身通常不需要 bridge；但測試可能需要 plusarg
控制 timeout，或 harness 監看 interrupt 發生：

```scala
class WithTimerTimeout(cycles: Long) extends HarnessBinder({
  // 概念示意：在 harness 裡加 timeout checker，不放進 target SoC RTL
})
```

閱讀檢查表：

| 問題 | 要追哪裡 |
|------|----------|
| Timer 是否存在 | `TimerKey` 與 `WithTimer` config |
| MMIO address 是多少 | `TimerParams.address` / `AddressSet` |
| CPU 如何讀寫 | `TLRegisterNode` / `regmap` |
| interrupt 如何接 | `IntSourceNode` 到 PLIC/interrupt bus |
| Linux/軟體如何知道 | `SimpleDevice` / generated device tree |
| simulation-only timeout 在哪 | HarnessBinder / TestHarness，不在 Timer RTL |

這條線串起來後，Chipyard 的抽象會比較清楚：Config 決定 variant，LazyModule/Diplomacy 接 graph，
RegMapper 描述 MMIO semantics，device tree/interrupt 是軟體契約，HarnessBinder 處理模擬環境。

---

## M10.11 Device tree 與 interrupt nodes

如果 SoC 要跑作業系統，硬體存在還不夠，軟體也必須知道它在哪裡、叫什麼、interrupt 怎麼接。
Device tree 是硬體 metadata 的軟體介面；interrupt node 則把 peripheral interrupt generator
接到 PLIC 與 CPU tile。這些不是一般 RTL signal 命名問題，而是 hardware/software contract。

```scala
val device = new SimpleDevice("my-device", Seq("example,my-device"))
```
Linux-capable SoC 需產生 device tree：peripheral 要描述 `compatible` string、address range、interrupt mapping、device type。

Interrupt 也被 diplomacy 化：`IntSourceNode`（peripheral 產生）→ PLIC → `IntSinkNode`（CPU tile）。

---

## M10.12 Clock/Reset crossing framework

大型 SoC 很少所有東西都跑同一個 clock。Tile、peripheral、memory controller、debug module、
simulation bridge 可能各有不同 clock/reset。Chipyard 的 crossing framework 讓這些 domain
被明確描述與組裝，而不是靠手寫一堆零散 synchronizer。

大型 SoC 區分：Synchronous / Asynchronous / Rational crossing、`ClockGroup`、`ClockSinkNode`、reset crossing。
目的：tile / peripheral / memory bus 各自不同 clock；harness 供應 clock/reset。

---

## M10.13 Cake pattern / traits 與 `CanHavePeripheryXXX`

Cake pattern 讓功能可以透過 trait mix-in 組裝，這對大型可配置 subsystem 很有彈性。
但它也讓硬體來源分散在多個 trait、config key、binder 與 base class 中。閱讀時不要只看目前 class；
要追哪些 trait 被 mix in、哪些 key 啟用功能、哪些 binder 把 IO 接到 harness。

```scala
trait HasPeripheryUART          // 功能可 mix-in
trait CanHavePeripheryGCD       // 「可選擇性擁有」某 peripheral，由 Parameters/Config 決定
```
| 優點 | 缺點 |
|------|------|
| 功能可 mix-in、subsystem 可組不同能力 | trait linearization / 初始化順序難懂、硬體來源分散、local reasoning 變差 |

> 讀 `CanHave...` code 要追四件事：trait 有沒有被 mix in、相關 key 是什麼、Config fragment 有沒有設、IOBinder/HarnessBinder 有沒有接。

---

## M10.14 IOBinders / HarnessBinders（SoC IO 與 harness 解耦）

Binder 的精神和 Part 8 的 harness 分離一致：target SoC RTL 不應該知道自己在模擬、FPGA 還是 ASIC。
IOBinder 決定哪些 SoC IO 被暴露；HarnessBinder 決定測試環境中接什麼模型或 bridge。
同一個 UART 在 simulation、FPGA、ASIC 下可以接不同東西，而 SoC 主體不必改。

| | 決定什麼 |
|---|---------|
| **IOBinders** | ChipTop IO 如何 punch out / expose |
| **HarnessBinders** | TestHarness 裡 instantiate 什麼 simulation collateral |

> 原因：同一個 UART——simulation 接 SimUART、FPGA 接 pin、ASIC 接 pad ring；DRAM——sim 接 SimDRAM、FPGA 接 MIG、ASIC 接 PHY。

### Bridge pattern（sim-only collateral）
`UARTBridge` / `BlockDeviceBridge` / `SerialBridge` / `DromajoBridge` / `TSI bridge`——屬 TestHarness，不是 target SoC RTL。

---

## M10.15 RoCC accelerator 與 TraceIO cosim

Accelerator 整合有不同層級。MMIO accelerator 最適合教學，因為它透過 load/store 控制，debug 容易；
RoCC 則把 accelerator 接進 Rocket pipeline 與 ISA custom instruction，能力更強但耦合更深。
TraceIO/Dromajo cosim 則展示了大型專案如何把 commit trace 從 core 拉到 harness 做 golden-model 比對。

```scala
class MyRoCC(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new MyRoCCModuleImp(this)
}
```
| accelerator | 整合方式 |
|-------------|----------|
| MMIO accelerator | CPU 用 load/store 控制 command/status（簡單、教學友善） |
| RoCC accelerator | custom instruction 觸發、與 Rocket pipeline 緊耦合、可有 memory client node（較複雜） |

`OpcodeSet` 指定吃哪些 custom opcodes（= ISA extension 整合的 generator 化）。

TraceIO / Dromajo-style cosim 分層：Core 出 commit trace → ChipTop 可選 punchthrough → TestHarness 接 Dromajo/ISS → Config 決定啟用。與你自己 CPU 的 ISS difftest（Part 8）同精神。

---

## M10.16 Tile / Subsystem / ChipTop 分層

這個分層讓大型 SoC 有清楚邊界：Tile 是 core-local 世界，Subsystem 組 bus/memory/peripheral，
ChipTop 處理晶片級 IO，TestHarness 提供 target environment。讀 Chipyard 時先定位自己在哪一層，
再追該層該負責的硬體與 metadata。

```text
Tile        = core + L1 I/D cache + PTW + FPU + RoCC + interrupts + debug + clock crossing + TL nodes
Subsystem   = Tiles + Buses + Memory system + Peripherals + Debug + Harness
BaseSubsystem → DigitalTop → ChipTop（chip-level IO/punchthrough）→ TestHarness
```
> TestHarness 不是一般 testbench，而是 **target environment generator**：可 instantiate SimDRAM/SimUART/SimSerial/Dromajo/clock-reset gen/loadmem/plusargs/FireSim bridges。

---

## M10.17 Runtime config 與產物

大型 generator 的產物不只 Verilog。軟體需要 device tree 與 headers，模擬需要 plusargs 與 harness files，
FPGA/ASIC flow 需要 constraints、blackbox、SRAM replacement 與 annotations。這也是為什麼 SoC generator
不能只看 RTL；它同時在生成硬體、軟體 metadata 與後端 flow collateral。

- **PlusArgs**：同一份 RTL 用 plusargs 改 runtime 行為（loadmem path、max cycles、verbose、trace enable、uart log）。
- **Generated artifacts 不只 Verilog**：DTS/DTB、ROM image、config headers、simulation files、annotations、FIRRTL/CIRCT IR、FireSim/Hammer collateral。
- **Flow integration**：software sim / Verilator-VCS / FPGA / **FireSim**（FPGA 加速 sim）/ **Hammer**（ASIC）。需注意 clock-reset naming、blackbox macro、SRAM 替換、constraints、hierarchy、annotations、產物組織。

---

## M10.18 對 architect 最值得學的 6 件事

```text
1. Config fragments 思想       → design variants / DSE
2. TLRegisterRouter / RegMapper → MMIO accelerator / peripheral 整合
3. IOBinders / HarnessBinders   → sim harness / cosim / framebuffer / UART console
4. LazyModule mental model      → 讀 Rocket/BOOM/TileLink 必備
5. TileLink widgets             → buffer / fragmenter / width adapt / xbar
6. Commit trace / Dromajo cosim → ISS golden-model verification
```

> 下一站見 [Part 11](11-教學專案與心智模型.md)：如何做「Chipyard-lite」——借精神、去重量，適合教學/個人專案。

**練習**
- ★ 閱讀一個 Chipyard config，列出它用了哪些 fragments。完成標準：能說明每個 fragment 影響 core、bus、peripheral、harness 或 trace 哪一層。
- ★★ 用 Part 11 的 Chipyard-lite config 風格重寫一個小 SoC config。完成標準：不用 implicit `Parameters` 也能表達 core/memory/device/harness 選項。
- ★★★ 找一個 TileLink widget，寫出它對應到 Part 5 哪種互連問題。完成標準：能區分 graph-level connection 與普通 Chisel IO connection。
