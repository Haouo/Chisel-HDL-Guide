# Chisel Handout 規劃藍圖（基礎 → 進階 → 處理器）

> **形式**：速查表（cheat-sheet）＋ 程式碼食譜（cookbook）
> **目標讀者**：已修過數位邏輯（懂組合/時序邏輯、Boolean algebra、二進位），但**不會 Scala**
> **終點**：處理器設計 + RISC-V pipeline
> **語言**：繁體中文，technical terms 與 code 保留英文
> **產生日期**：2026-06-03

---

## 0. 研究來源與對應（Source Map）

本規劃綜整兩個主要來源，並交叉比對版本落差：

| 來源 | 內容 | 角色 |
|------|------|------|
| **《Digital Design with Chisel》6th Ed. (Martin Schoeberl, 2025)** — `chisel-book.pdf` | 17 章，從 install → 基礎 → FSM → 生成器 → FIFO/UART → 互連 → 處理器(Leros) → RISC-V(Wildcat) | **教學骨幹**：章節順序、範例設計、習題 |
| **官方文件 chisel-lang.org/docs** | Introduction / Getting Started / **Cookbooks** / **Explanations** / Appendix / Developer / Resources | **API 現況與食譜**：最新語法、現行測試框架、cookbook 寫法 |

### ⚠️ 關鍵版本落差（Currency Gap，務必在 Handout 開頭標注）

| 主題 | 書本 (6th Ed.) | 現況 (Chisel 7.0, 2025-09 起) | Handout 採用 |
|------|----------------|------------------------------|--------------|
| 測試框架 | `ChiselTest`（`peek/poke/expect/step`） | **ChiselSim**（`simulate { ... }`），ChiselTest 已 deprecated | **以 ChiselSim 為主**，ChiselTest 標為 legacy |
| Scala 版本 | Scala 2.13 | Scala 2.13 **與 Scala 3** 皆支援 | 教 Scala 2.13 語法為主，註記 3 的差異 |
| 編譯後端 | MFC (CIRCT) | 同（MFC/CIRCT） | 一致 |
| 安裝 | sbt + JDK 8–21 | 同；另推薦 **Scala CLI** 快速試 | 兩者並列，新手先用 Scala CLI |

> **食譜原則**：每個範例「以 Chisel 7 / ChiselSim 可直接跑」為驗收標準；書中 ChiselTest 寫法放在「legacy 對照」小框。

---

## 1. 整體架構（7 大 Part，可獨立查閱）

```
Part 0  起步      ── 環境 + 給硬體人的 Scala 速成 + Hello Chisel
Part 1  核心 RTL  ── 型別 / 組合 / 時序 / Bundle&Vec / Module
Part 2  建構區塊  ── 組合&時序 library / 記憶體 / 輸入處理
Part 3  控制&通訊 ── FSM / FSMD / Decoupled / 測試&除錯
Part 4  生成器    ── 用 Scala 生硬體 / 參數化 / 函數式生成   ← Chisel 真正威力
Part 5  範例&互連 ── FIFO / UART / Bus(AXI/Wishbone/OCP)
Part 6  處理器    ── Leros(單週期) → Wildcat(RISC-V 3-stage pipeline)
附錄    速查      ── Verilog 對照 / 術語表 / 陷阱總表 / 資源
```

**設計理念（針對速查+食譜形式）**：每個 module 用統一版型，讀者能「跳著查」：

> **每節版型（Recipe Template）**
> 1. **一句話**：這節解決什麼問題
> 2. **最小骨架**：可直接複製貼上、能跑的 code skeleton
> 3. **語法速查表**：相關 API / operator 一覽
> 4. **常見陷阱 ⚠️**：pitfalls（例：`1.U(32)` 的 bug）
> 5. **練習**：1–2 題（附難度 ★）
> 6. **對應**：書 §x.x ／ docs 連結

---

## 2. 逐章規劃（Module-by-Module）

### Part 0 — 起步

#### M0.1 環境與工具鏈
- **一句話**：把 Chisel 7 跑起來、產生 Verilog、看波形。
- **食譜**：`build.sbt`（Chisel 7 依賴）骨架；`Scala CLI` 單檔快速試；`sbt run` 產生 Verilog；GTKWave 開 VCD。
- **速查**：JDK 8–21、sbt、GTKWave、IntelliJ/VSCode(Metals)。
- 對應：書 §1.1、docs Installation / Getting Started。

#### M0.2 給硬體人的 Scala 速成（**新手關鍵章**）
- **一句話**：只學「描述硬體會用到」的 Scala，其餘略過。
- **食譜清單（精選，不教完整 Scala）**：
  - `val` / `var` / `def`、type inference
  - `class` / `object` / `extends`（對應 Module）
  - `Int`、`Boolean`、字串；`for` 迴圈、`if` 表達式
  - 集合：`Seq` / `Vector` / `Range`、`.map` / `.reduce` / `.zipWithIndex`
  - **`case class`**（之後參數化 config 用）
  - **anonymous function** `(x) => ...`、高階函數
  - `Option` / `Some` / `None`（optional ports 用）
  - implicit / given 的「最小認知」（只需知道為何 clock/reset 是隱含的）
- ⚠️ **陷阱**：Scala 的 `==` vs Chisel 的 `===`；`if` 是 Scala 控制流（生成時決定）vs `when` 是硬體 mux。
- 對應：書 §10.1「A Little Bit of Scala」（**前移**到此）、Odersky 教材、docs Explanations。

#### M0.3 Hello Chisel
- **一句話**：第一個 Module、blinking LED、看到生成的 Verilog。
- **食譜**：`class Hello extends Module { val io = IO(...) }`（書 Listing 1.1）；`emitVerilog`；最小 ChiselSim 測試跑一次。
- 對應：書 §1.2–1.7。

---

### Part 1 — 核心 RTL 食譜（基礎）

| Module | 一句話 | 核心食譜 / 速查 | 主要陷阱 ⚠️ | 對應 |
|--------|--------|------------------|-------------|------|
| **M1.1 型別與常數** | 描述線路的 bit 寬與字面值 | `UInt/SInt/Bool/Bits`、`n.W`、`3.U`/`-3.S`/`true.B`、進位 `"hff".U` `"b1010".U` | `1.U(32)` 被當成 bit extract（要 `1.U(32.W)`）；`Bits` 少運算少用 | §2.1 |
| **M1.2 組合邏輯** | 不含 register 的邏輯 | operators(`&` `|` `+` `===` `=/=`)、`Mux`、`when/.elsewhen/.otherwise`、`switch/is`、`Cat/Fill/x(7,0)` | `:=` 是 last-connect-wins；`when` 外未賦值→latch 警告，用 default 或 `DontCare` | §2.2, §5 |
| **M1.3 暫存器與時序** | flip-flop 與時脈 | `RegInit(0.U(n.W))`、`RegNext`、`RegEnable`、counter pattern、隱含 clock/reset | 忘了 init → 無 reset 值；counter wrap 寬度 | §2.3, §6.1 |
| **M1.4 Bundle 與 Vec** | struct 與 array | `new Bundle { val a = ... }`、`Vec(n, type)`、`VecInit`、`Reg(Vec(...))` | Vec wrapped in Wire = mux；Vec of Reg ≠ Reg of Vec | §2.4 |
| **M1.5 Module 與階層** | 模組化與連線 | `Module(new Sub)`、`io <> sub.io`(bulk)、`:=`、`Flipped`、`DontCare` | bundle 方向；`<>` 需方向相符 | §4 |
| **M1.6 Wire/Reg/IO** | 三種「可賦值」物件的差別 | 何時用 `Wire` / `Reg` / `IO` 速查表 | 把組合中繼當 Reg | §2.5 |

---

### Part 2 — 建構區塊食譜（Building Blocks Library）

| Module | 食譜內容（可複製的標準寫法） | 對應 |
|--------|------------------------------|------|
| **M2.1 組合建構區塊** | decoder、encoder、priority encoder、arbiter、comparator、**ALU** | §4.3, §5 |
| **M2.2 時序建構區塊** | up/down counter、timer/one-shot、**PWM**、shift register（parallel in/out）、LFSR | §6.2–6.3 |
| **M2.3 記憶體** | `Mem` / `SyncReadMem`、read-during-write 行為、ROM via `VecInit`、multi-clock memory | §6.4, §11.4 |
| **M2.4 輸入處理** | 2-FF **synchronizer**、**debouncing**、edge detection、majority-vote filtering、同步 reset | §7 |

---

### Part 3 — 控制、通訊與測試

#### M3.1 有限狀態機（FSM）
- **食譜**：`object State extends ChiselEnum`（或 `Enum`）、`switch(state){ is(...){} }`、Moore vs Mealy 範本各一。
- ⚠️ Mealy 的 output 是組合，注意 glitch / timing。
- 對應：§8。

#### M3.2 FSMD 與通訊狀態機
- **食譜**：state machine + datapath（popcount 範例）、light flasher（多 FSM 協作）、**ready/valid handshake**、`Decoupled` / `Queue`。
- 對應：§9。

#### M3.3 測試與除錯食譜（**版本重點章**）
- **主**：**ChiselSim** — `simulate(new Dut){ dut => poke / peek / expect / step }`、VCD 波形、`printf` 除錯、`assert`、formal verification 入門。
- **Legacy 對照框**：書中 `ChiselTest` 寫法（`test(new X){ c => c.io.a.poke(...); c.io.out.expect(...) }`）→ 標明已 deprecated，並給遷移對照表。
- **速查**：peek/poke/expect/step ↔ ChiselSim API 對照。
- 對應：§3.2, §13；docs「Migrating from ChiselTest to ChiselSim」。

---

### Part 4 — 硬體生成器（Chisel 的核心價值，進階關鍵）

> 這是 Chisel 勝過 Verilog 的地方；對「已會數位邏輯」的讀者，這 Part 是「為什麼要學 Chisel」的答案。

| Module | 食譜內容 | 對應 |
|--------|----------|------|
| **M4.1 用 Scala 生硬體** | function 回傳 hardware、用 `for`/迴圈生重複結構、`println` 在生成期 debug、讀檔生 ROM、型別轉換 | §10.1–10.3 |
| **M4.2 參數化設計** | **`case class` config**、type parameters `[T <: Data]`、parameterized `Bundle`、**Optional ports**（`Option[T]` + `MixedVec`） | §10.4 |
| **M4.3 函數式生成** | `VecInit(...).reduceTree(_+_)`、map/fold 生 mux tree、minimum-search、**arbitration tree** | §10.6 |
| **M4.4 繼承與重用** | trait / abstract Module、用繼承共用 IO 與行為 | §10.5 |

---

### Part 5 — 範例設計與互連

| Module | 食譜內容 | 對應 |
|--------|----------|------|
| **M5.1 FIFO 設計與變體** | bubble FIFO → 參數化 → double-buffer → register/on-chip memory FIFO（同一介面多種實作，示範生成器威力） | §11.1, §11.3 |
| **M5.2 UART / Serial Port** | TX/RX 時序、串列傳輸 FSM、與 FIFO 整合 | §11.2 |
| **M5.3 互連與匯流排** | memory-mapped device、combinational/pipelined handshake bus、**Wishbone / AXI / OCP** 概覽與選用 | §12 |

---

### Part 6 — 處理器設計（進階終點）

#### M6.1 簡單處理器 — Leros（單週期 / FSMD）
- **食譜骨架**：ISA → datapath（含 ALU、register file、instruction memory）→ decode → state machine 實作 → implementation variations。
- **學習目標**：把 Part 1–4 的所有 building block 組成一顆能跑的 CPU。
- 對應：§14。

#### M6.2 RISC-V 3-stage Pipeline — Wildcat
- **食譜骨架**：RISC-V ISA 子集 → pipeline stage 切分 → fetch / decode + register read / execute + memory → top level 串接。
- **重點**：pipeline 暫存、hazard 概念、用 Chisel 參數化描述各 stage。
- 對應：§15。

#### M6.3（選修）發佈與整合
- 發佈 Chisel library、用 `BlackBox` 整合 legacy Verilog。
- 對應：§16, Appendix A.2。

---

## 3. 附錄（速查工具）

| 附錄 | 內容 | 對應 |
|------|------|------|
| **A. Chisel ↔ Verilog/VHDL 對照** | 同一電路三語言並排（component / register / combinational / 進階） | Appendix A |
| **B. 術語中英對照表** | module/wire/register/bundle/decoupled… 中英對照（因 Handout 中文、術語英文） | 全書 |
| **C. 常見陷阱總表** | 把各章 ⚠️ 集中：`1.U(32)`、`==` vs `===`、`if` vs `when`、last-connect、width inference… | 全書 |
| **D. 保留字 / 資源連結** | reserved keywords、Chisel projects、cheat sheet、社群 | Appendix B/C/D |
| **E. 版本注記** | Chisel 7 / Scala 3 / ChiselSim 變更與遷移 | docs Appendix |

---

## 4. 撰寫順序與風險提醒（Devil's Advocate）

**建議撰寫順序**：先寫 Part 0–1（地基）→ Part 3 測試章（之後所有範例都要它）→ Part 2 → Part 4 → Part 5 → Part 6。

**風險與對策**：
1. **「速查+食譜」最大風險＝概念太淺**。讀者已懂數位邏輯，但 Chisel 的「生成 vs 硬體」心智模型（M0.2、C 陷阱表）若略過會全盤卡住 → **此處不可只給 code，要留足概念**。
2. **版本漂移**：書用 ChiselTest，市面教學新舊混雜 → 全 Handout 統一 Chisel 7 + ChiselSim，並維護一份「驗證過可跑」的範例 repo。
3. **Scala 過載**：硬體讀者易被 Scala 嚇退 → M0.2 嚴格「夠用就好」，進階 Scala（implicit/given、type class）延到 Part 4 真正需要時才補。
4. **Part 6 落差大**：從 building block 到 CPU 跳躍大 → 用 M5（FIFO/UART）當橋樑，先讓讀者做過「中型整合設計」再進處理器。

---

## 5. 交付清單（若後續要實際寫出 Handout）

- [ ] 一份「可跑範例 repo」（Chisel 7 + ChiselSim，CI 驗證），對應每個食譜
- [ ] 各 Part 的 Markdown/LaTeX 講義（依本藍圖版型）
- [ ] 術語對照表（附錄 B）與陷阱總表（附錄 C）獨立成可列印速查卡
- [ ] 習題與解答（可改編自書本 exercises 與 schoeberl/chisel-lab）
