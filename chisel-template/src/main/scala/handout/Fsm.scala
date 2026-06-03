package handout

import chisel3._
import chisel3.util._

/** Part 3 — Moore FSM：偵測輸入序列 1 → 0（下降緣 detector 的 FSM 版）。 */
object DetectState extends ChiselEnum {
  val sLow, sHigh, sFell = Value
}

class FallDetect extends Module {
  val io = IO(new Bundle {
    val in   = Input(Bool())
    val fell = Output(Bool())
  })
  val state = RegInit(DetectState.sLow)
  switch(state) {
    is(DetectState.sLow)  { state := Mux(io.in, DetectState.sHigh, DetectState.sLow) }
    is(DetectState.sHigh) { state := Mux(io.in, DetectState.sHigh, DetectState.sFell) }
    is(DetectState.sFell) { state := Mux(io.in, DetectState.sHigh, DetectState.sLow) }
  }
  io.fell := state === DetectState.sFell   // Moore：輸出只看 state
}
