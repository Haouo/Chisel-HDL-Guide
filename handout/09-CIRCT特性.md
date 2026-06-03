# Part 9 — CIRCT 時代的 Chisel 特性（Layer / Probe / DataView）

對應：Chisel 5+/7（MFC/CIRCT 後端帶來的新能力）｜ `import chisel3._`

> Chisel 5 起改用 MLIR FIRRTL Compiler（CIRCT/firtool），解鎖了 LTL properties、Probe、Layer 等新功能。
> 這些讓「驗證/除錯邏輯」可以乾淨地與正式硬體分離。

---

## M9.1 Layer：分離 debug / verification 邏輯

**一句話**：把 instrumentation 放進 layer，debug build 保留、release build 移除。

```scala
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

```scala
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

---

## M9.3 DataView：兩個 Bundle 語意相同、欄位名不同

**一句話**：當 `CoreMemReq` 與 `BusReq` 是同一件事但命名不同，用 view 對應而非手抄。

```scala
import chisel3.experimental.dataview._

class CoreMemReq extends Bundle { val addr = UInt(32.W); val data = UInt(32.W); val mask = UInt(4.W); val isWrite = Bool() }
class BusReq     extends Bundle { val address = UInt(32.W); val payload = UInt(32.W); val writeStrobe = UInt(4.W); val write = Bool() }

implicit val view: DataView[CoreMemReq, BusReq] = DataView(_ => new BusReq,
  _.addr -> _.address, _.data -> _.payload, _.mask -> _.writeStrobe, _.isWrite -> _.write)

bus := core.viewAs[BusReq]
```
| 規模 | 建議 |
|------|------|
| 小 project / 少量 mapping | explicit adapter（手寫一次更直白） |
| 大量重複 mapping | `DataView`（少 boilerplate、型別安全） |

---

## M9.4 Annotation：很多 API 背後是它

**一句話**：多數時候不手寫 annotation，但要知道這些 API 背後是 annotation 機制。

```text
dontTouch / markDebug
memory transform（loadMemoryFromFile…）
blackbox resource（addResource）
firtool options
```
> 理解這點有助於讀 Rocket/Chipyard 程式碼（它們大量用 annotation 傳遞 metadata 給後端）。

**練習**
- ★ 把 Part 8 的 assertion 包進一個 `Verification` layer。
- ★★ 用 Probe 暴露 Wildcat 的 `pc`，在測試端 `read` 出來比對。
- ★★ 用 `DataView` 把 `CoreMemReq` 接到 `BusReq`。
