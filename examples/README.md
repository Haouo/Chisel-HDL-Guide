# Chisel Handout — 可跑範例專案

這是 [`../handout/`](../handout/) 的配套**可執行專案**：把 Handout 裡 Part 0–6 的
core-Chisel 食譜實作成真正的 module + **ChiselSim** 測試，並用 CI 驗證。

- **基準版本**：Chisel **7.13.0** + Scala 2.13.18 + ChiselSim（firtool 1.149.0 自動下載）
- **測試框架**：ChiselSim（`chisel3.simulator.scalatest.ChiselSim`）— **不是** 已 deprecated 的 ChiselTest

---

## 驗證狀態（誠實標示）

| 項目 | 狀態 | 如何驗證 |
|------|------|----------|
| 所有 module 與 ChiselSim 測試**編譯通過** | ✅ 已驗證 | `scala-cli`/`sbt compile` |
| `handout.Main` 中 10 個 top-level module **elaboration → SystemVerilog** | ✅ 已驗證（firtool 1.149.0） | `sbt "runMain handout.Main"` |
| ChiselSim 測試**實際模擬通過** | ✅ 已驗證（Verilator 5.048，9/9 passed） | `sbt test` |

> 全部 9 個 ChiselSim 測試已用 Verilator 5.048 實跑通過（`Tests: succeeded 9, failed 0`）。
> CI（`.github/workflows/ci.yml`）會自行安裝 Verilator 後重跑。

---

## 怎麼跑

```bash
# 需要 JDK 8–21、sbt；模擬另需 Verilator（apt-get install verilator）
sbt compile                 # 編譯
sbt test                    # 跑 ChiselSim 測試（需 Verilator）
sbt "runMain handout.Main"  # 產生所有 module 的 SystemVerilog
```

不想裝 sbt？用 scala-cli 單獨編譯：
```bash
scala-cli compile src/main/scala src/test/scala \
  --scala 2.13.18 \
  --dep org.chipsalliance::chisel:7.13.0 \
  --compiler-plugin org.chipsalliance:::chisel-plugin:7.13.0 \
  --dep org.scalatest::scalatest:3.2.19
```

---

## 內容對應 Handout

| 檔案 | module | Handout |
|------|--------|---------|
| `Blinky.scala` | `Blinky` | Part 0 |
| `Combinational.scala` | `Alu`（語意化 Bundle）、`Mux4`（MuxLookup） | Part 1/2 |
| `Sequential.scala` | `UpCounter`、`RegisterFile`（x0=0）、`Pwm` | Part 1/6 |
| `Fsm.scala` | `FallDetect`（ChiselEnum Moore FSM） | Part 3 |
| `FlowControl.scala` | `IncStage`、`OneStage`、`SkidBuffer`、`QueueFifo` | Part 3/5 |
| `Generators.scala` | `Mux2[T]`、`AdderTree`（`Vec.reduceTree(_ +& _)`）、`CacheParams`、`AddressSplitter` | Part 4 |
| `Wildcat.scala` | `Wildcat`（教學版 3-stage pipeline） | Part 6 |
| `Main.scala` | 產生 10 個 top-level module 的 SystemVerilog（目前不含 `Pwm`） | — |
| `src/test/scala/handout/*Spec.scala` | ChiselSim 測試 | Part 3 測試章 |

---

## 從本專案發現、並已回修進 Handout 的真實 API 細節

1. `reduceTree` 是 **`Vec` 的方法**，不是 `Seq`；且用 **`+&`**（width-expanding）避免溢位。
   非 `Data` 型別（如仲裁樹的 tuple）需自寫 `Generators.treeReduce`。
2. **top-level port 不可用 inferred width**（`Output(UInt())` 會被 firtool 拒絕），要給明確 `.W`。
3. 目前 Chisel 最新穩定版是 **7.13.0**（非 7.2.0）。
