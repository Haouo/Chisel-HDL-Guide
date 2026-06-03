# Part 1 — 核心 RTL 食譜（基礎 → 乾淨 RTL 風格）

對應：書 §2、§4 ｜ 一律 `import chisel3._`、`import chisel3.util._`

> **本章心智模型**：你寫的每一行 Chisel，elaboration 後會變成什麼硬體？
> 是 wire、mux、register、decoder，還是複製 N 份 module？隨時自問這句。

本章是整份講義的地基。Chisel 的抽象很多，但乾淨 RTL 仍然只有幾件事：
明確的型別與寬度、語意清楚的介面、完整的組合預設值、可預期的 reset 行為、
以及不把硬體資料結構寫成一堆散落訊號。

學完本章後，讀者應該能看到一段 Chisel，立刻判斷哪些東西會變成 wire、哪些會變成 register、
哪些只是 Scala 在生成期用來展開電路的集合。這個能力比背 API 更重要，因為後面的 pipeline、
cache、generator、SoC integration 都只是同一套規則在更大尺度上的應用。

**本章路線圖**

- 先學：型別/寬度、Bundle、組合預設值、Reg、Vec/Seq、Module 階層。
- 會踩坑：漏 `.W`、`==`/`===` 混淆、組合邏輯沒 default、payload bundle 帶方向。
- 最後能做：寫出介面語意清楚、reset 可預期、沒有未連線/latch 的小型 RTL。

---

## M1.1 型別與常數

**一句話**：描述線路的 bit 寬與字面值。

型別與寬度是 RTL 的契約。Verilog 可以讓很多寬度隱式延伸或截斷，Chisel 也會做 width inference，
但教學與大型設計中最好養成「重要邊界明確寫寬度」的習慣。尤其 top-level IO、memory data、
bus address、register file 這些跨模組訊號，不應該靠推斷猜出來。

```scala
UInt(8.W)   SInt(10.W)   Bool()         // 型別（width 用 .W）
0.U   3.U(4.W)   -3.S   true.B          // 字面值
"hff".U   "o377".U   "b1111_1111".U     // hex / octal / binary（底線可分組）
'A'.U                                    // 字元 → ASCII
```

| 寫法 | 意義 |
|------|------|
| `n.W` | width |
| `x.U` / `x.U(n.W)` | UInt literal（可帶寬） |
| `x.S` / `x.B` | SInt / Bool literal |

⚠️ **頭號陷阱**：`1.U(32)` **不是** 32-bit 的 1！`32` 被當成 **bit 抽取位置** → 結果 0。
要 32-bit 的 1 寫 **`1.U(32.W)`**。

對應：§2.1。

---

## M1.2 用 Bundle 表達硬體語意（★乾淨 RTL 第一守則）

**一句話**：不要散落 scalar IO，定義語意化 interface。

Bundle 的價值不是少打幾行，而是讓資料在設計中帶著語意移動。當 `addr`、`data`、`mask`、
`isWrite` 被包成 `MemReq`，review 時就能知道它們應該一起穿過 pipeline、queue、arbiter 與 bus。
如果它們散落成四條 wire，後續加欄位、加 assertion、加 trace 都會變得脆弱。

### 不好 vs 好

```scala
// ✗ 散亂
val a  = Input(UInt(32.W)); val b = Input(UInt(32.W))
val op = Input(UInt(4.W));  val y = Output(UInt(32.W))

// ✓ 語意化 Bundle
class AluReq  extends Bundle { val src1, src2 = UInt(32.W); val op = UInt(4.W) }
class AluResp extends Bundle { val result = UInt(32.W) }
class AluIO   extends Bundle { val req = Input(new AluReq); val resp = Output(new AluResp) }
```

### Payload Bundle **不要帶方向**（重要 convention）

```scala
// ✓ payload：純資料，不含方向
class MemReq extends Bundle { val addr = UInt(32.W); val data = UInt(32.W); val mask = UInt(4.W) }
// 方向交給 protocol wrapper：
val req  = Decoupled(new MemReq)          // producer
val sink = Flipped(Decoupled(new MemReq)) // consumer
```

| 層級 | 是否帶方向 |
|------|-----------|
| Payload Bundle（純資料） | **不帶**（`UInt`/`Bool`…） |
| Protocol Bundle | 可含 `Decoupled` / `Flipped` |
| Top-level IO | 才用 `Input` / `Output` |

> CPU 設計很早就該定義語意 bundle：`FetchPacket` / `DecodePacket` / `MicroOp` / `ExeResult` / `MemReq` / `MemResp`，讓 pipeline、scoreboard、bypass network 傳遞有語意的結構。

⚠️ **陷阱**：在 payload bundle 裡寫 `Output(...)` → 之後 `Flipped` 行為混亂。方向只在 protocol/IO 層出現。

對應：§4。

---

## M1.3 組合邏輯

**一句話**：純由輸入決定輸出（無 register）。

寫組合邏輯時，先問輸出在所有輸入組合下是否都有定義。`WireDefault`、`otherwise`、
以及一開始給 `io.y := 0.U` 都是在回答這個問題。Chisel 的 last-connect-wins 很適合描述
「先給預設，再由特殊條件覆蓋」的控制邏輯，但前提是你知道覆蓋順序就是硬體 mux 的優先序。

```scala
io.y := (io.a & io.b) | io.c                 // (a) 直接運算
io.y := Mux(io.sel, io.a, io.b)              // (b) 二選一
when(io.op === 0.U) { io.y := io.a + io.b }  // (c) 多路
.elsewhen(io.op === 1.U) { io.y := io.a - io.b }
.otherwise { io.y := 0.U }                   // ★ 一定要 default
switch(io.op) { is(0.U){ io.y := io.a + io.b }; is(1.U){ io.y := io.a - io.b } } // (d)
```

| 類別 | 運算子 / 函數 |
|------|---------------|
| 算術 | `+ - * / %` |
| 位元 | `& | ^ ~` |
| 比較 | `=== =/= > >= < <=`（**用 `===`** 不是 `==`） |
| 移位 | `<< >>` |
| 接合 | `Cat(hi, lo)`、`a ## b`、`Fill(n, x)` |
| 抽位 | `x(7, 0)`、`x(3)` |
| 縮減 | `x.andR` `x.orR` `x.xorR` |

⚠️ **陷阱**：`:=` 是 **last-connect-wins**；`when` 外漏給值 → latch。

對應：§2.2、§5。

---

## M1.4 用 `WireDefault` 避免未連接（★乾淨 RTL）

**一句話**：先給安全 default，再條件覆蓋，杜絕 latch 與「忘了連」。

這是控制邏輯最重要的寫法之一。先建立一個合法、保守的 default，再讓 decode、hazard、
exception 或分支條件局部覆蓋欄位。這種寫法讓新增控制訊號時比較不會漏接，也讓 code review
可以集中檢查「哪些情況改變 default」，而不是追每條 wire 的所有分支。

```scala
// ✗ 危險：cond 不成立時 x 未定義
val x = Wire(UInt(32.W)); when(cond) { x := a }

// ✓ 安全
val x = WireDefault(0.U(32.W)); when(cond) { x := a }

// control bundle 一次全給 default
val ctrl = WireDefault(0.U.asTypeOf(new CtrlSignals))
when(special) { ctrl.regWrite := true.B }     // 只覆蓋需要的欄位
```

> **慣用法**：複雜 control 用「`WireDefault` + 局部 override」，比把每條線都 `when/otherwise` 寫滿乾淨得多。

對應：§2.2。

---

## M1.5 暫存器與時序

**一句話**：flip-flop；clock/reset 隱含自動連接。

`Reg` 與 `Wire` 的差異就是 cycle 邊界。凡是需要跨 cycle 保存的資料，必須進 register；
凡是只在同一拍內組合計算的中繼值，不應該誤放進 register。初學者常把修 bug 的直覺寫成
「加一個 Reg」，但這會改變 pipeline latency。每加一個 register，都要能說清楚它切在哪兩個階段之間。

```scala
val reg = RegInit(0.U(8.W)); reg := reg + 1.U   // 有 reset 初值的 counter
val d   = RegNext(io.in, init = 0.U)            // 延遲一拍 + 初值
val acc = RegEnable(io.in, 0.U, io.en)          // enable 才更新（next, init, en）
val (count, wrap) = Counter(io.tick, 10)        // util：0..9 循環
```

| 寫法 | 行為 |
|------|------|
| `Reg(T)` | 無 reset 值 |
| `RegInit(init)` | 有同步 reset 初值 |
| `RegNext(x[, init])` | 延遲一拍 |
| `RegEnable(next, init, en)` | en 為真才載入（pipeline reg 最愛） |
| `Counter(cond, n)` | `(value, wrap)` |
| `ShiftRegister(in, n)` | 固定延遲 n 拍 |

### `RegEnable` 寫 pipeline register 很乾淨
```scala
val id_exe = RegEnable(nextUop, MicroOp.bubble, !stall)   // 比 Reg + when(!stall) 更直白
```

### `ShiftRegister` / `Pipe` 適合 **fixed-latency** pipeline
```scala
val respValid = ShiftRegister(reqFire, 2)   // 兩拍後 response 有效
```
| 適合 | 不適合 |
|------|--------|
| fixed-latency multiplier、BRAM read 對齊、debug 延遲 | 會 stall / flush / replay 的 pipeline |

⚠️ **陷阱**：期望可重現行為卻用 `Reg`（無 init）→ reset 後值未定。要初值用 `RegInit`。

對應：§2.3、§6.1。

---

## M1.6 Bundle 與 Vec；`Vec` vs `Seq`

**一句話**：`Bundle`=struct、`Vec`=硬體陣列、`Seq`=elaboration-time 集合。

`Vec` 與 `Seq` 是 Chisel 初學者最容易混淆的地方。`Seq` 是 Scala 的集合，只存在於生成期；
你可以用它收集一批 module、wire 或參數，再用 `for/map` 展開結構。`Vec` 是硬體型別，
可以用 `UInt` 動態索引，因此會生成 mux 或 register array。當 index 來自硬體訊號時，
你幾乎一定需要 `Vec`。

```scala
val rf = Reg(Vec(32, UInt(32.W)))     // register file（硬體陣列）
rf(io.waddr) := io.wdata
io.rdata := rf(io.raddr)              // 動態 index → 生成 mux
```

### `Vec` vs `Seq` 的本質差異
```scala
val xs: Seq[Bool] = Seq(a, b, c)      // Scala 集合，index 必須是 Scala Int
val ys: Vec[Bool] = VecInit(a, b, c)  // 硬體集合，可用 UInt 動態 index
ys(idx)                               // idx 可為 UInt（會生成 mux）
```

### `Vec[Reg]` vs `Reg[Vec]`（建議後者）
```scala
val regs = RegInit(VecInit(Seq.fill(n)(0.U(width.W))))   // ✓ register 陣列
for (i <- 0 until n) { when(wen(i)) { regs(i) := wdata(i) } }  // per-entry enable
```

### Large array：**不要全部 reset**
```scala
// ✗ 1024 entry 全 reset → reset fanout 爆炸
val data = RegInit(VecInit(Seq.fill(1024)(0.U(64.W))))
// ✓ 只 reset valid bits；payload invalid 時是 don't care
val valid = RegInit(VecInit(Seq.fill(n)(false.B)))
val data  = Reg(Vec(n, UInt(64.W)))
```

> **valid array 與 payload array 分開** 是 issue queue / ROB / MSHR / LSQ / cache metadata 的標準寫法：valid reset 清楚、payload 免 reset、降低 reset fanout、更貼近真實硬體。

⚠️ **陷阱**：`Wire(Vec(...))` 用硬體 index = mux；`Reg(Vec(...))` 才是暫存器陣列。

對應：§2.4。

---

## M1.7 Module 與階層；bulk connect 與 `DontCare`

階層化的目的不是把程式碼切小而已，而是建立清楚的 ownership。子模組應該暴露穩定的 IO 契約；
上層負責實例化與連線，不應該伸手改子模組內部狀態。`<>` 很方便，但它依賴方向正確，
因此 payload bundle 不帶方向、protocol wrapper 決定方向的 convention 會在這裡開始回收價值。

```scala
class Top extends Module {
  val io  = IO(new Bundle { val x = Input(UInt(8.W)); val y = Output(UInt(8.W)) })
  val add = Module(new Adder)
  add.io.a := io.x; add.io.b := 1.U; io.y := add.io.s
}
producer.io <> consumer.io        // bulk 雙向（依方向/Flipped）
sub.io := DontCare                // 明示「不在意」，消除未連線警告
```

### `DontCare` 的正確使用
```scala
val uop = Wire(new MicroOp); uop := DontCare; uop.valid := false.B  // invalid payload 可 don't care
```
| 可以 DontCare | 不要 DontCare |
|---------------|---------------|
| invalid payload（valid=false 時的 bits） | valid payload、external IO |

⚠️ **陷阱**：`<>` 兩端方向需相符；consumer 端常要 `Flipped(...)`。

對應：§4。

---

## M1.8 Wire / Reg / IO — 三者區別

| 物件 | 是什麼 | 何時用 |
|------|--------|--------|
| `IO(...)` | 對外接腳 | 模組邊界 |
| `Wire(T)` / `WireDefault` | 純組合中繼 | 先算再多處用 |
| `Reg`/`RegInit` | 時序元件（有 clock） | 要跨 cycle 保存 |

⚠️ **陷阱**：需保存的值用 `Wire` → 每拍重算；純中繼用 `Reg` → 多一拍延遲。先決定要不要跨 cycle。

對應：§2.5。

**練習**
- ★ 寫 4-bit `Mux4`，用 `Mux`、`MuxLookup`、`switch` 三種寫法。完成標準：三個版本在相同測試下輸出一致。
- ★ 把一組散落 IO 重構成語意化 `AluIO`（req/resp bundle）。完成標準：payload bundle 不含 `Input`/`Output`。
- ★★ 寫 32×32 register file（雙讀單寫、`x0` 恆 0、valid/payload 分離不適用此例），ChiselSim 驗證（見 Part 3）。完成標準：寫入 x0 後讀出仍為 0。
