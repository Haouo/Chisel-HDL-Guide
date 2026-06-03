# Ultimate Chisel Tutorial

> 一份從**乾淨 RTL** 到 **Chipyard 風格 SoC 生成器**的 Chisel HDL 中文講義，搭配**已驗證可跑**的範例專案。
> A hands-on, Traditional-Chinese Chisel HDL handout — from clean RTL to a Chipyard-style SoC generator — with a fully validated runnable example project.

[![CI](https://github.com/Haouo/Chisel-HDL-Guide/actions/workflows/ci.yml/badge.svg)](https://github.com/Haouo/Chisel-HDL-Guide/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Chisel](https://img.shields.io/badge/Chisel-7.13.0-brightgreen)
![Scala](https://img.shields.io/badge/Scala-2.13.18-red)
![Tests](https://img.shields.io/badge/ChiselSim-9%2F9%20passing-success)

---

## 這是什麼

- **講義（[`handout/`](handout/)）**：14 篇、約 4,300 行的繁體中文教學講義，結合完整敘述、速查表與程式碼食譜，技術術語與 code 保留英文。
- **範例專案（[`examples/`](examples/)）**：把講義的 core-Chisel 食譜實作成真正的 module ＋ **ChiselSim** 測試，並用 CI 驗證。
- **基準版本**：**Chisel 7.13.0** + Scala 2.13.18 + **ChiselSim**（測試框架；`ChiselTest` 已 deprecated）。

> 適合對象:**已修過數位邏輯、但不會 Scala** 的讀者；想一路學到處理器/SoC 的人。

---

## ✨ 特色

- **以 ChiselSim 為主**：全程使用官方現行的 `ChiselSim`，並標明 `ChiselTest` 已被官方棄用。
- **統一教學版型**：每節＝設計直覺 → 最小可跑骨架 → 語法速查 → 常見陷阱 ⚠️ → 有完成標準的練習 →（進階加「適合/不適合」表與心智模型）。
- **基礎到架構雙軌**：基礎 track 帶你做出會跑的 CPU；進階 track 一路到 Diplomacy / TileLink / Chipyard 心智模型。
- **真的能跑**：範例專案在 Verilator 5.048 下 **9/9 測試通過**，全部 module 都能產生 SystemVerilog。

---

## 🗂️ 內容導覽

### 講義 `handout/`（[完整索引](handout/README.md)）

| Part | 主題 |
|------|------|
| [0](handout/00-起步.md) | 環境、**給硬體人的 Scala 速成**、Hello Chisel |
| [1](handout/01-核心RTL.md) | 型別、語意化 Bundle、`WireDefault`、Reg、Vec/Seq |
| [2](handout/02-建構區塊.md) | ALU、`Mux1H`/`PopCount`、`BitPat` decoder |
| [3](handout/03-控制通訊測試.md) | FSM、`Valid`/`Decoupled`、**ChiselSim 測試** |
| [4](handout/04-生成器.md) | `case class Params`、ADT、`reduceTree`、DSE sweep |
| [5](handout/05-範例互連.md) | FIFO、skid buffer、Arbiter、UART、Bus |
| [6](handout/06-處理器.md) | Leros → Wildcat pipeline → 微架構資料結構 |
| [7](handout/07-記憶體時脈黑盒.md) | `SyncReadMem`、Clock/Reset、`BlackBox`、CDC |
| [8](handout/08-驗證除錯基礎建設.md) | `assert`/`cover`、commit-trace difftest、harness |
| [9](handout/09-CIRCT特性.md) | Layer、Probe、DataView |
| [10](handout/10-Chipyard-SoC生成器.md) | Config fragments、Diplomacy、TileLink、RoCC |
| [11](handout/11-教學專案與心智模型.md) | Chipyard-lite 專案結構、最終心智模型 |
| [附錄](handout/99-附錄.md) | Verilog 對照、術語表、**陷阱+慣用法總表**、版本注記 |

### 範例專案 `examples/`（[說明](examples/README.md)）

10 個 module（Blinky / Alu / Mux4 / UpCounter / RegisterFile / Pwm / FallDetect / SkidBuffer / QueueFifo / AdderTree / Wildcat …）＋ 9 個 ChiselSim 測試。

---

## 🚀 Quick Start

```bash
git clone https://github.com/Haouo/Chisel-HDL-Guide.git
cd Chisel-HDL-Guide/examples

# 需要 JDK 8–21、sbt；模擬另需 Verilator
sbt compile                  # 編譯
sbt test                     # 跑 ChiselSim 測試（需 Verilator）
sbt "runMain handout.Main"   # 產生所有 module 的 SystemVerilog
```

只想讀講義？直接從 [`handout/README.md`](handout/README.md) 開始。

---

## ✅ 驗證狀態

| 項目 | 狀態 |
|------|------|
| 所有 module 與 ChiselSim 測試**編譯通過** | ✅ |
| 全部 10 個 module **elaboration → SystemVerilog**（firtool 1.149.0） | ✅ |
| ChiselSim 測試**實際模擬通過**（Verilator 5.048） | ✅ 9/9 passed |

---

## 🙏 致謝

- **Martin Schoeberl, _Digital Design with Chisel_ (6th Edition)** — 講義的章節骨架與多數範例構想來源；該書以 [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) 授權（[github.com/schoeberl/chisel-book](https://github.com/schoeberl/chisel-book)）。
- **Chisel 官方文件** [chisel-lang.org/docs](https://www.chisel-lang.org/docs) — 現行 API、ChiselSim 與 cookbook 依據。
- 進階篇的 SoC 生成器觀念參考 **Rocket Chip / Chipyard / BOOM** 的設計模式。

> 本 repo 內容為原創撰寫的衍生教學素材；程式碼以 MIT 授權釋出，文字內容若取材自上述 CC BY-SA 來源者，請一併遵循其授權條款。

---

## 📄 授權

本專案以 [MIT License](LICENSE) 釋出。Copyright (c) 2026 Chun-Hao Chang。
