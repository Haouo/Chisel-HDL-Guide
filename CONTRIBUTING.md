# 貢獻指南 Contributing

歡迎指正錯誤、補充範例、或翻譯。這是一份教學專案，最有價值的貢獻通常是**勘誤**與**讓更多範例可編譯/可測試**。

## 回報問題（errata / bug）

開 issue 時請附上:

- 出處:哪一篇講義的哪一節（例:`handout/04-生成器.md` M4.5），或哪個 module。
- 問題:文字錯誤、程式碼編不過、或測試失敗。
- 環境（若是程式碼問題）:Chisel / Scala / JDK / Verilator 版本。

## 修改講義（`handout/`）

- 維持既有「食譜版型」:① 一句話 ② 最小骨架 ③ 語法速查 ④ 陷阱 ⚠️ ⑤ 練習 ⑥ 對應章節。
- 繁體中文為主，technical terms 與程式碼保留英文。
- **講義內的程式碼若是新加的「可宣稱能跑」範例，請一併加進 `examples/` 並通過 CI**（見下）。

## 修改範例專案（`examples/`）

```bash
cd examples
sbt compile                  # 編譯
sbt test                     # ChiselSim 測試（需 Verilator 5.x）
sbt "runMain handout.Main"   # 產生 SystemVerilog
```

- 新增 module 請放 `src/main/scala/handout/`，並在 `src/test/scala/handout/` 補對應的 ChiselSim 測試。
- 測試請用 **ChiselSim**（`chisel3.simulator.scalatest.ChiselSim`），不要用已 deprecated 的 ChiselTest。
- 縮排 2 空白（見 `.editorconfig`）。
- 送 PR 前確認 `sbt compile` 與 `sbt test` 皆綠（CI 也會跑）。

## 授權

送出貢獻即同意以本專案的 [MIT License](LICENSE) 釋出。
若內容取材自 CC BY-SA 4.0 來源（如 _Digital Design with Chisel_），請於 PR 中註明並遵循其授權。
