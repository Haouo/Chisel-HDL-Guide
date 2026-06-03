# Part 10 — Chipyard / Rocket Chip / BOOM 風格 SoC 生成器

> ⚠️ **本章 API 多屬 Rocket Chip / Chipyard**（非 core Chisel）。語法以「概念示意」為主，重點在**讀懂 Rocket/BOOM 程式碼的心智模型**，而非背 API。

對應：reference 進階篇 ｜ 心智模型 > 細節

---

## M10.1 三個層級

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

Rocket/Chipyard 很少直接寫 `class MySoC(nCores: Int, hasUART: Boolean)`，而是疊 config：

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

常見 node：`TLClientNode` / `TLManagerNode` / `TLIdentityNode` / `TLXbar` / `TLBuffer` / `TLFragmenter` / `TLWidthWidget` / `TLToAXI4` / `AXI4ToTL`。

```scala
// 這不是 signal assignment，而是 graph-level connection
someBus.node := TLBuffer() := myDevice.node
```
| 普通 Chisel | Diplomacy |
|-------------|-----------|
| `io.a := io.b`（接線） | `nodeA := nodeB`（接 diplomatic graph，硬體 IO 之後才生） |

---

## M10.8 TileLink widgets（互連零件）

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

```scala
regmap(
  0x00 -> Seq(RegField(32, myReg)),         // 讀寫
  0x04 -> Seq(RegField.r(32, status)),      // read-only
  0x08 -> Seq(RegField.w(32, command)),     // write-only（可觸發 side effect）
)
```
不需手刻完整 TileLink slave。適合：UART-like control regs、accelerator command/status、interrupt enable/status、perf counter MMIO。

`RegField` 進階語意：read-only / write-only / write-one-to-clear / side-effect read/write / ready-valid backed field（例：寫 command register 觸發 accelerator transaction）。

---

## M10.11 Device tree 與 interrupt nodes

```scala
val device = new SimpleDevice("my-device", Seq("example,my-device"))
```
Linux-capable SoC 需產生 device tree：peripheral 要描述 `compatible` string、address range、interrupt mapping、device type。

Interrupt 也被 diplomacy 化：`IntSourceNode`（peripheral 產生）→ PLIC → `IntSinkNode`（CPU tile）。

---

## M10.12 Clock/Reset crossing framework

大型 SoC 區分：Synchronous / Asynchronous / Rational crossing、`ClockGroup`、`ClockSinkNode`、reset crossing。
目的：tile / peripheral / memory bus 各自不同 clock；harness 供應 clock/reset。

---

## M10.13 Cake pattern / traits 與 `CanHavePeripheryXXX`

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

| | 決定什麼 |
|---|---------|
| **IOBinders** | ChipTop IO 如何 punch out / expose |
| **HarnessBinders** | TestHarness 裡 instantiate 什麼 simulation collateral |

> 原因：同一個 UART——simulation 接 SimUART、FPGA 接 pin、ASIC 接 pad ring；DRAM——sim 接 SimDRAM、FPGA 接 MIG、ASIC 接 PHY。

### Bridge pattern（sim-only collateral）
`UARTBridge` / `BlockDeviceBridge` / `SerialBridge` / `DromajoBridge` / `TSI bridge`——屬 TestHarness，不是 target SoC RTL。

---

## M10.15 RoCC accelerator 與 TraceIO cosim

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

```text
Tile        = core + L1 I/D cache + PTW + FPU + RoCC + interrupts + debug + clock crossing + TL nodes
Subsystem   = Tiles + Buses + Memory system + Peripherals + Debug + Harness
BaseSubsystem → DigitalTop → ChipTop（chip-level IO/punchthrough）→ TestHarness
```
> TestHarness 不是一般 testbench，而是 **target environment generator**：可 instantiate SimDRAM/SimUART/SimSerial/Dromajo/clock-reset gen/loadmem/plusargs/FireSim bridges。

---

## M10.17 Runtime config 與產物

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
