package handout

import chisel3._
import chisel3.util._

/** Part 6 — pipeline register payload（不帶方向）。 */
class DecodeExec extends Bundle {
  val rd       = UInt(5.W)
  val rs1Val   = UInt(32.W)
  val rs2Val   = UInt(32.W)
  val imm      = UInt(32.W)
  val aluOp    = UInt(3.W)
  val regWrite = Bool()
}

/**
 * Part 6 — 教學版 3-stage RISC-V pipeline（Wildcat 精神，極簡）。
 * 僅示範 fetch / decode+regread / execute+writeback 與 pipeline register；
 * 不含 forwarding / hazard / branch（見 Part 6 練習）。
 */
class Wildcat(initProgram: Seq[BigInt] = Seq.fill(16)(BigInt(0))) extends Module {
  val io = IO(new Bundle {
    val pc  = Output(UInt(32.W))
    val dbg = Output(UInt(32.W))
  })

  // ---- Stage 1: Fetch ----
  val pc   = RegInit(0.U(32.W))
  val rom  = VecInit(initProgram.map(_.U(32.W)))
  val instr = rom(pc(log2Ceil(initProgram.length) + 1, 2)) // word-indexed
  pc := pc + 4.U
  io.pc := pc

  // ---- Stage 2: Decode + Register Read ----
  val rf = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  val de = Wire(new DecodeExec)
  de.rd       := instr(11, 7)
  de.rs1Val   := Mux(instr(19, 15) === 0.U, 0.U, rf(instr(19, 15)))
  de.rs2Val   := Mux(instr(24, 20) === 0.U, 0.U, rf(instr(24, 20)))
  de.imm      := Cat(Fill(20, instr(31)), instr(31, 20))
  de.aluOp    := instr(14, 12)
  de.regWrite := true.B
  val deReg = RegNext(de, 0.U.asTypeOf(new DecodeExec)) // pipeline register

  // ---- Stage 3: Execute + Writeback ----
  val aluY = WireDefault(0.U(32.W))
  switch(deReg.aluOp) {
    is(0.U) { aluY := deReg.rs1Val + deReg.rs2Val } // ADD
    is(6.U) { aluY := deReg.rs1Val | deReg.rs2Val } // OR
    is(7.U) { aluY := deReg.rs1Val & deReg.rs2Val } // AND
  }
  when(deReg.regWrite && deReg.rd =/= 0.U) { rf(deReg.rd) := aluY }
  io.dbg := aluY
}
