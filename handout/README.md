# Chisel Handout — 從乾淨 RTL 到 Chipyard 風格 SoC 生成器

> **Subtitle**：From Clean RTL Generator to Chipyard-style SoC Generator
> **形式**：速查表（cheat-sheet）＋ 程式碼食譜（cookbook）＋ 慣用法/心智模型
> **讀者**：已修過數位邏輯、**不會 Scala**（基礎track）；想進到架構層的人（進階track）
> **基準版本**：**Chisel 7.13.0** + Scala 2.13.18 + **ChiselSim**

---

## ⚠️ 版本須知（最重要的一件事）

| 主題 | 舊（書本/網路舊教學） | **本 Handout 採用（Chisel 7）** |
|------|----------------------|--------------------------------|
| 測試框架 | `chiseltest`（`test(){ c => }`） | **`ChiselSim`**（`simulate(){ dut => }`） |
| 狀態 | **ChiselTest 已 deprecated** — 官方：*"not used or maintained by the core Chisel development team"* | ChiselSim 為官方維護取代品 |
| 後端 | 舊 SFC | **MFC / CIRCT（firtool）**，解鎖 Layer/Probe/LTL |
| Verilog 產生 | 舊 `Driver` | `circt.stage.ChiselStage.emitSystemVerilog` |

> 全範例以 **Chisel 7 + ChiselSim** 撰寫；`ChiselTest` 僅在「legacy 對照框」出現。

> 📌 **程式碼驗證範圍**：講義內的 code 以**教學示意**為主，並非每段都經編譯。
> 經 CI 完整驗證（編譯 → 產生 SystemVerilog → Verilator 實跑通過）的版本在 [`../examples/`](../examples/)。
> 進階 track 的 Part 10（Chipyard / Diplomacy / TileLink）為**概念示意**，API 屬 rocket-chip / chipyard，非 core Chisel。

---

## 核心觀念（全程關鍵）

> **Chisel 的強項不是讓你少寫 Verilog，而是讓你能以可維護、可驗證、可參數化的方式描述一整個硬體設計家族。**

分清兩個世界，每寫一行都自問「elaboration 後變成什麼硬體？」：

```scala
for (i <- 0 until n) { ... }   // Scala elaboration-time：決定長出什麼電路 / 複製幾份
when (io.valid) { ... }        // Hardware run-time：真正的硬體（mux / reg / decoder）
```

---

## 目錄

### 基礎 track（讀者：懂數位邏輯、不會 Scala）

| Part | 檔案 | 內容 |
|------|------|------|
| **0** | [`00-起步.md`](00-起步.md) | 環境工具鏈、**給硬體人的 Scala 速成**、Hello Chisel |
| **1** | [`01-核心RTL.md`](01-核心RTL.md) | 型別、**語意化 Bundle/payload 無方向**、`WireDefault`、Reg、Vec vs Seq、`DontCare` |
| **2** | [`02-建構區塊.md`](02-建構區塊.md) | ALU、`Mux1H`/`OHToUInt`/`PriorityEncoderOH`/`PopCount`、**`BitPat` decoder** |
| **3** | [`03-控制通訊測試.md`](03-控制通訊測試.md) | FSM、**`Valid`/`Decoupled`/`Irrevocable`**、ready-valid assertion、pipeline reg、**ChiselSim** |
| **4** | [`04-生成器.md`](04-生成器.md) | function 生硬體、**`case class Params`/`require`/`Option`/ADT**、generic `gen:T`、`reduceTree`、DSE sweep |
| **5** | [`05-範例互連.md`](05-範例互連.md) | FIFO/`Queue` 當 timing 工具、**skid buffer**、Arbiter、UART、Bus |
| **6** | [`06-處理器.md`](06-處理器.md) | Leros → Wildcat pipeline → **MicroOp/commit-trace/epoch/branch-mask/perf-counter** |

### 進階 / 架構 track

| Part | 檔案 | 內容 |
|------|------|------|
| **7** | [`07-記憶體時脈黑盒.md`](07-記憶體時脈黑盒.md) | `RawModule`/`withClockAndReset`/CDC、`BlackBox`、`SyncReadMem` 對齊、memory wrapper、RUW |
| **8** | [`08-驗證除錯基礎建設.md`](08-驗證除錯基礎建設.md) | `assert`/`cover`、reusable checkers、測試分層、**commit-trace difftest**、harness 分離 |
| **9** | [`09-CIRCT特性.md`](09-CIRCT特性.md) | **Layer**（分離 debug）、**Probe**（觀測內部）、**DataView**、Annotation |
| **10** | [`10-Chipyard-SoC生成器.md`](10-Chipyard-SoC生成器.md) | Config fragments、`Parameters`/`Field`、`LazyModule`、**Diplomacy/TileLink**、RegMapper、IO/HarnessBinders、RoCC |
| **11** | [`11-教學專案與心智模型.md`](11-教學專案與心智模型.md) | **Chipyard-lite** 專案結構、mini config、core 不知周邊、最終心智模型 |
| **附** | [`99-附錄.md`](99-附錄.md) | Verilog 對照、術語中英表、**陷阱+慣用法總表**、版本注記、資源 |

---

## 食譜版型

每節：① 一句話 → ② 最小可跑骨架 → ③ 語法/慣用法速查 → ④ 陷阱 ⚠️ →（進階加 ⑤「適合/不適合」表 ⑥ 心智模型）→ 練習 → 書/docs 對應。

---

## 建議學習順序

```
新手：     0 → 1 → 3(測試) → 2 → 4 → 5 → 6           （能做出會跑的 CPU）
進階：     7 → 8 → 9                                  （記憶體/驗證/CIRCT 特性）
架構視野： 10 → 11                                     （讀懂 Rocket/BOOM、設計 SoC generator）
查閱：     直接看 99-附錄.md 的「陷阱+慣用法總表」與「術語表」
```

---

## Quick Start

```bash
git clone https://github.com/chipsalliance/chisel-template.git my-chisel
cd my-chisel
sbt test     # ChiselSim
sbt run      # 產生 Verilog
```
