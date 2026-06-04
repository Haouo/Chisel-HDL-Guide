# Chisel Handout — 從乾淨 RTL 到 Chipyard 風格 SoC 生成器

> **Subtitle**：From Clean RTL Generator to Chipyard-style SoC Generator
> **形式**：速查表（cheat-sheet）＋ 程式碼食譜（cookbook）＋ 慣用法/心智模型
> **讀者**：已修過數位邏輯、**不會 Scala**（基礎track）；想進到架構層的人（進階track）
> **基準版本**：**Chisel 7.13.0** + Scala 2.13.18 + **ChiselSim**

這份講義不是要把 Chisel 當成「比較短的 Verilog」來教，而是把它當成
**硬體生成語言**來學。讀者應該先掌握乾淨 RTL 的寫法，再理解如何用 Scala
把同一套 RTL discipline 擴展成可參數化、可測試、可整合的設計家族。

每一章都分成兩層：第一層是可以快速查找的語法與慣用法；第二層是背後的設計理由。
如果你只是照抄程式碼，會很快卡在 width、reset、handshake、memory latency、pipeline hazard
這些硬體問題上。正確的讀法是：先問「這段 elaboration 後會長成什麼硬體」，
再看 Chisel 提供了哪個抽象讓這件事更容易維護。

本講義可以有三種讀法：

| 讀法 | 怎麼讀 | 目標 |
|------|--------|------|
| 自學路線 | 依照 Part 0 → 11 前進，每章跑對應 examples 或練習 | 建立最小 RTL / pipeline / SoC generator 心智模型 |
| 查表路線 | 直接查單一小節與附錄 | 快速確認 API、慣用法、陷阱 |
| Capstone 路線 | 先讀 Part 6、8、11，再回補 Part 5、7、10 | 把 Wildcat 重組成可驗證的 Chipyard-lite 專案 |

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

### 程式碼標記

講義中的程式碼依用途分成四種，閱讀時先看標記再決定能不能直接複製。若某段 code 沒標明「可跑範例」，
就應先把它當成教學片段，而不是可以直接貼進專案的完整實作。

| 標記 | 意義 | 讀法 |
|------|------|------|
| **可跑範例** | 對應 [`../examples/`](../examples/) 中已驗證 module/test | 可直接跑、改、測 |
| **教學骨架** | 語法與結構接近實作，但省略周邊 glue code | 適合照著補成完整 module |
| **概念示意** | 用短碼說明設計模式，未保證可獨立編譯 | 看硬體意圖，不要直接貼上 |
| **Rocket/Chipyard API** | 屬 rocket-chip/chipyard 生態，版本可能變動 | 讀心智模型，實作時查目標版本文件 |

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

每節盡量遵守同一個敘事順序：① 問題場景 → ② 常見壞直覺或反例 → ③ Chisel 寫法 → ④ elaboration 後的硬體長相 → ⑤ 陷阱 ⚠️ → ⑥ 測試/驗收方式 → 書/docs 對應。

授課或自學時，建議把每節再補成三個動作：

1. 先用 Verilog/硬體直覺說清楚要產生的電路。
2. 再看 Chisel 寫法，指出哪些是生成期 Scala，哪些是硬體 runtime。
3. 最後到 [`../examples/`](../examples/) 找對應 module 或 test，改一個小需求並重新跑測試。

這樣讀會比「一次背完 API」穩。Chisel 的難點通常不在語法，而在你是否能持續分清：
Scala 值、Chisel Data、wire、reg、IO、protocol、harness 各自屬於哪一層。

---

## 建議學習順序

```
新手：     0 → 1 → 3(測試) → 2 → 4 → 5 → 6
           目標：能寫乾淨 RTL，理解最小 pipeline CPU 的核心路徑、hazard 與修法。

進階：     7 → 8 → 9
           目標：會處理 memory/clock/IP 邊界，並建立 assertion/trace/harness 的驗證基礎。

架構視野： 10 → 11
           目標：讀懂 Rocket/BOOM 的 SoC generator 語言，並能設計 Chipyard-lite 專案結構。

查閱：     直接看 99-附錄.md 的「陷阱+慣用法總表」與「術語表」
```

---

## Quick Start

```bash
git clone https://github.com/Haouo/Chisel-HDL-Guide.git
cd Chisel-HDL-Guide/examples
sbt compile
sbt test                     # 需要 Verilator
sbt "runMain handout.Main"   # 產生本講義範例的 SystemVerilog
```

若你想從空白專案開始，而不是使用本 repo 的配套範例，可再參考官方
[`chipsalliance/chisel-template`](https://github.com/chipsalliance/chisel-template)。
