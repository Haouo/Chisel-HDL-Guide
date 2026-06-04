# Part 9 — CIRCT 時代的 Chisel 特性（Layer / Probe / DataView）

對應：Chisel 5+/7（MFC/CIRCT 後端帶來的新能力）｜ `import chisel3._`

> Chisel 5 起改用 MLIR FIRRTL Compiler（CIRCT/firtool），解鎖了 LTL properties、Probe、Layer 等新功能。
> 這些讓「驗證/除錯邏輯」可以乾淨地與正式硬體分離。

本章的重點不是追新 API，而是理解 Chisel/CIRCT 後端開始提供更清楚的 instrumentation 邊界。
傳統做法常把 debug IO、assertion、coverage、trace signal 直接混進正式 RTL；專案變大後，
這會讓合成產物、測試產物與除錯產物難以分開。Layer、Probe、DataView 這類機制的價值，
就在於讓設計意圖與後端 metadata 更明確。

這些特性適合在 Part 8 的驗證基礎上再學。先知道自己要觀測什麼、要檢查什麼、哪些邏輯不該進 release build，
再選 Layer 或 Probe；不要為了使用新功能而增加抽象。

**本章路線圖**

- 先學：Layer、Probe、DataView、annotation 的角色。
- 會踩坑：把測試用 debug IO 變成正式介面，或為了新 API 增加不必要抽象。
- 最後能做：把 debug/verification instrumentation 與正式 RTL 分層，知道哪些程式碼只是後端 metadata。

**本章程式碼標記**：以下範例多屬 **概念示意**。Layer/Probe/DataView API 受 Chisel/CIRCT 版本影響；
實作時以專案鎖定的 Chisel 版本文件與 Scaladoc 為準。

選 instrumentation 手段時，先用這張表判斷 ownership：

| 需求 | 優先選擇 |
|------|----------|
| 產品或 ISS 長期依賴 | 正式 IO / `CommitTrace` |
| 測試想看內部狀態 | `Probe` |
| assertion / cover / shadow state 可移除 | `Layer` |
| 臨時保留波形訊號 | `dontTouch`，用完移除 |

---

## M9.1 Layer：分離 debug / verification 邏輯

**一句話**：把 instrumentation 放進 layer，debug build 保留、release build 移除。

Layer 可以把「只為驗證或除錯存在」的邏輯集中標記。這比到處用參數 `if (debug)` 更乾淨，
因為它把 instrumentation 當成後端可理解的結構，而不是普通 generator branch。
適合放 assertion、coverage、shadow state、或昂貴 debug counter。

```scala
// 標記：概念示意。Layer API 以專案鎖定版本文件為準。
import chisel3._
import chisel3.layer.{Layer, LayerConfig, block}

object Verification extends Layer(LayerConfig.Extract())

class MyModule extends Module {
  // ... 正式硬體 ...
  block(Verification) {            // 只在啟用該 layer 時存在
    val shadow = RegNext(io.in)
    assert(shadow === expected)
  }
}
```
適合：debug logic、verification logic、coverage、probe、optional instrumentation。
目標：**debug/sim build 保留、release/synthesis build 移除**——同一份原始碼，兩種產物。

---

## M9.2 Probe：看內部狀態又不污染正式 IO

**一句話**：verification 想觀測內部狀態，但不想把它變成正式 hardware IO。

Probe 解決的是「測試想看內部，但正式介面不該多一堆 debug port」的矛盾。
它尤其適合 CPU 或 cache 中的內部狀態：PC、ROB head、scoreboard、predictor state、cache metadata。
設計上仍要節制；如果某個訊號是產品規格的一部分，就應該是正式 IO 或 trace，而不是 probe。

```scala
// 標記：概念示意。Probe 是 verification view，不是一般產品 IO。
import chisel3.probe.{Probe, ProbeValue, define, read}

class Core extends Module {
  val robHead = RegInit(0.U(6.W))
  val probe   = IO(Output(Probe(UInt(6.W))))
  define(probe, ProbeValue(robHead))     // 對外暴露為 probe，不是一般 wire
}
// 上層：read(core.probe) 取值（驗證用）
```
適合觀測：ROB head、rename map、scoreboard、branch predictor state、cache metadata。
> 比「拉一大包 debug IO」乾淨：probe 在最終合成可被移除/不佔正式介面。

### Before/After：從髒 debug IO 改成 Probe + Layer

傳統教學範例常為了測試方便，把內部狀態直接拉成正式 IO：

```scala
// 標記：反例。debug 訊號被放進正式 IO，短期方便但會污染產品介面。
class Core extends Module {
  val io = IO(new Bundle {
    val imem = new ImemPort
    val commit = Valid(new CommitTrace)
    val dbgPc = Output(UInt(32.W))
    val dbgScoreboard = Output(UInt(32.W))
  })

  val pc = RegInit(0.U(32.W))
  val scoreboard = RegInit(0.U(32.W))
  io.dbgPc := pc
  io.dbgScoreboard := scoreboard
}
```

這樣短期很方便，但問題是 `dbgPc` 與 `dbgScoreboard` 變成 core 的正式介面。
上層 SoC、ChipTop、FPGA pin planning、generated RTL 都會看到它們；等 debug 結束後還要手動清理。

較乾淨的做法是：正式產品需要的觀測資料用 `commit trace`，測試專用的內部觀測用 `Probe`，
昂貴或可移除的 checker 放進 `Layer`。

```scala
// 標記：概念示意。正式 trace、Probe、Layer 分別承擔不同 ownership。
import chisel3.layer.{Layer, LayerConfig, block}
import chisel3.probe.{Probe, ProbeValue, define}

object Verification extends Layer(LayerConfig.Extract())

class Core extends Module {
  val io = IO(new Bundle {
    val imem = new ImemPort
    val commit = Valid(new CommitTrace)          // 正式 trace contract
    val pcProbe = Output(Probe(UInt(32.W)))      // verification-only view
  })

  val pc = RegInit(0.U(32.W))
  val scoreboard = RegInit(0.U(32.W))
  define(io.pcProbe, ProbeValue(pc))

  block(Verification) {
    when(io.commit.valid && io.commit.bits.rdWen) {
      assert(io.commit.bits.rd =/= 0.U, "commit trace must not write x0")
    }
    cover(scoreboard.orR)
  }
}
```

設計判斷：

| 需求 | 建議介面 |
|------|----------|
| 軟體/ISS/difftest 需要長期依賴 | 正式 `CommitTrace` / debug IO |
| 測試想暫時看內部 pipeline state | `Probe` |
| assertion / coverage / shadow state 可在 release 移除 | `Layer` |
| 臨時防止最佳化、短期看波形 | `dontTouch`，用完移除 |

這個 before/after 的重點是 ownership。正式 IO 是硬體產品契約；Probe 是驗證視角；
Layer 是 instrumentation 分層。三者分清楚，RTL 才不會因為測試需求慢慢變髒。

---

## M9.3 DataView：兩個 Bundle 語意相同、欄位名不同

**一句話**：當 `CoreMemReq` 與 `BusReq` 是同一件事但命名不同，用 view 對應而非手抄。

DataView 的適用場景是「兩個型別語意相同，但欄位命名或封裝不同」。它能減少 adapter boilerplate，
也能讓型別轉換集中定義。不過小專案中手寫 adapter 往往更直白；只有 mapping 重複、欄位多、
或多個子系統需要同一套視圖時，DataView 才真正回本。

```scala
// 標記：概念示意。DataView 適合重複 mapping，多數小專案可先手寫 adapter。
import chisel3.experimental.dataview._

class CoreMemReq extends Bundle { val addr = UInt(32.W); val data = UInt(32.W); val mask = UInt(4.W); val isWrite = Bool() }
class BusReq     extends Bundle { val address = UInt(32.W); val payload = UInt(32.W); val writeStrobe = UInt(4.W); val write = Bool() }

implicit val view: DataView[CoreMemReq, BusReq] = DataView(_ => new BusReq,
  _.addr -> _.address, _.data -> _.payload, _.mask -> _.writeStrobe, _.isWrite -> _.write)

bus := core.viewAs[BusReq]
```

### Scala 語法：`implicit val` 也可以放 typeclass instance

Part 10 的 `implicit p: Parameters` 是「自動補一個參數」。這裡的 `implicit val view` 是另一種常見用途：
把一個型別轉換規則放進 implicit scope，讓 `viewAs[BusReq]` 能自動找到它。可以把 `DataView[CoreMemReq, BusReq]`
想成「如何把 CoreMemReq 看成 BusReq」的 typeclass instance。

```scala
implicit val view: DataView[CoreMemReq, BusReq] = ...
bus := core.viewAs[BusReq]    // compiler 在附近找 implicit DataView[CoreMemReq, BusReq]
```

如果沒有這個 implicit value，`viewAs[BusReq]` 就不知道欄位怎麼對應。這和 `implicit p: Parameters`
同樣是 Scala 隱式解析機制，但語意不同：

| 寫法 | 用途 |
|------|------|
| `(implicit p: Parameters)` | 呼叫 constructor/function 時自動補上下文參數 |
| `implicit val view: DataView[A, B]` | 提供某種型別能力/轉換規則，讓 extension API 可使用 |

| 規模 | 建議 |
|------|------|
| 小 project / 少量 mapping | explicit adapter（手寫一次更直白） |
| 大量重複 mapping | `DataView`（少 boilerplate、型別安全） |

---

## M9.4 Annotation：很多 API 背後是它

**一句話**：多數時候不手寫 annotation，但要知道這些 API 背後是 annotation 機制。

Annotation 是前端 Chisel 與後端 compiler/flow 溝通 metadata 的方式。理解 annotation 有助於讀懂
`dontTouch`、memory preload、blackbox resource、firtool option、debug mark 這些功能為什麼能影響產物。
實務上初學者不需要手寫 annotation，但需要知道某些行為不是普通 RTL，而是交給後端 transform 處理。

```text
dontTouch / markDebug
memory transform（loadMemoryFromFile…）
blackbox resource（addResource）
firtool options
```
> 理解這點有助於讀 Rocket/Chipyard 程式碼（它們大量用 annotation 傳遞 metadata 給後端）。

**練習**
- ★ 把 Part 8 的 assertion 包進一個 `Verification` layer。完成標準：debug/sim build 有檢查，release build 可移除 instrumentation。
- ★★ 用 Probe 暴露 Wildcat 的 `pc`，在測試端 `read` 出來比對。完成標準：正式 IO 不新增普通 `dbgPc` port。
- ★★ 用 `DataView` 把 `CoreMemReq` 接到 `BusReq`。完成標準：欄位對應集中在一個 view 定義中。
