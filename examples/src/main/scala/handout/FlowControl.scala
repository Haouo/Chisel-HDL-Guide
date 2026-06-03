package handout

import chisel3._
import chisel3.util._

/** Part 3 — combinational ready-valid stage（+1）。注意：這不是 pipeline register。 */
class IncStage(width: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(width.W)))
    val out = Decoupled(UInt(width.W))
  })
  io.out.valid := io.in.valid
  io.out.bits := io.in.bits + 1.U
  io.in.ready := io.out.ready
}

/** Part 5 — single-element buffer（bubble FIFO 一格）。 */
class OneStage(width: Int) extends Module {
  val io = IO(new Bundle {
    val enq = Flipped(Decoupled(UInt(width.W)))
    val deq = Decoupled(UInt(width.W))
  })
  val full = RegInit(false.B)
  val data = Reg(UInt(width.W))
  io.enq.ready := !full
  io.deq.valid := full
  io.deq.bits := data
  when(io.enq.fire) { data := io.enq.bits; full := true.B }
  when(io.deq.fire) { full := false.B }
}

/** Part 5 — skid buffer：打斷 ready critical path 又保住吞吐。 */
class SkidBuffer[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(gen))
    val out = Decoupled(gen)
  })
  val full = RegInit(false.B)
  val data = Reg(gen)
  io.in.ready := !full
  io.out.valid := full || io.in.valid
  io.out.bits := Mux(full, data, io.in.bits)
  when(io.in.valid && io.in.ready && !io.out.ready) { full := true.B; data := io.in.bits }
    .elsewhen(io.out.ready) { full := false.B }
}

/** Part 5 — 用內建 Queue 包成 FIFO。 */
class QueueFifo(width: Int, depth: Int) extends Module {
  val io = IO(new Bundle {
    val enq = Flipped(Decoupled(UInt(width.W)))
    val deq = Decoupled(UInt(width.W))
  })
  io.deq <> Queue(io.enq, depth)
}
