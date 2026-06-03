package handout

import chisel3._
import chisel3.util._

/** Part 1/2 — 語意化 Bundle 介面的 ALU。 */
class AluReq  extends Bundle { val src1, src2 = UInt(16.W); val op = UInt(2.W) }
class AluResp extends Bundle { val result = UInt(16.W) }

class Alu extends Module {
  val io = IO(new Bundle {
    val req  = Input(new AluReq)
    val resp = Output(new AluResp)
  })
  val y = WireDefault(0.U(16.W))
  switch(io.req.op) {
    is(0.U) { y := io.req.src1 + io.req.src2 }
    is(1.U) { y := io.req.src1 - io.req.src2 }
    is(2.U) { y := io.req.src1 & io.req.src2 }
    is(3.U) { y := io.req.src1 | io.req.src2 }
  }
  io.resp.result := y
}

/** Part 1 — 4 選 1 多工器（MuxLookup 慣用法）。 */
class Mux4 extends Module {
  val io = IO(new Bundle {
    val in  = Input(Vec(4, UInt(8.W)))
    val sel = Input(UInt(2.W))
    val out = Output(UInt(8.W))
  })
  io.out := MuxLookup(io.sel, 0.U)(Seq(
    0.U -> io.in(0), 1.U -> io.in(1), 2.U -> io.in(2), 3.U -> io.in(3),
  ))
}
