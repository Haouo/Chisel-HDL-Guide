package handout

import chisel3._
import chisel3.util._

/** Part 1 — 數到 max 後 wrap 的計數器。 */
class UpCounter(max: Int) extends Module {
  val io = IO(new Bundle {
    val en    = Input(Bool())
    val value = Output(UInt(log2Ceil(max + 1).W))
    val wrap  = Output(Bool())
  })
  val cnt  = RegInit(0.U(log2Ceil(max + 1).W))
  val wrap = io.en && cnt === max.U
  when(io.en) { cnt := Mux(wrap, 0.U, cnt + 1.U) }
  io.value := cnt
  io.wrap  := wrap
}

/** Part 1/6 — 2R1W register file，x0 恆 0。 */
class RegisterFile(n: Int, width: Int) extends Module {
  val io = IO(new Bundle {
    val rs1   = Input(UInt(log2Ceil(n).W))
    val rs2   = Input(UInt(log2Ceil(n).W))
    val rd    = Input(UInt(log2Ceil(n).W))
    val wen   = Input(Bool())
    val wdata = Input(UInt(width.W))
    val rd1   = Output(UInt(width.W))
    val rd2   = Output(UInt(width.W))
  })
  val regs = RegInit(VecInit(Seq.fill(n)(0.U(width.W))))
  when(io.wen && io.rd =/= 0.U) { regs(io.rd) := io.wdata }
  io.rd1 := Mux(io.rs1 === 0.U, 0.U, regs(io.rs1))
  io.rd2 := Mux(io.rs2 === 0.U, 0.U, regs(io.rs2))
}

/** Part 2 — PWM。 */
class Pwm(period: Int) extends Module {
  val io = IO(new Bundle {
    val duty = Input(UInt(log2Ceil(period + 1).W))
    val out  = Output(Bool())
  })
  val (cnt, _) = Counter(true.B, period)
  io.out := cnt < io.duty
}
