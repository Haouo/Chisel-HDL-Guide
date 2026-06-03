package handout

import chisel3._
import circt.stage.ChiselStage

/** 產生 SystemVerilog：`sbt run` 或 `sbt "runMain handout.Main"`。 */
object Main extends App {
  val opts = Array("-disable-all-randomization", "-strip-debug-info")
  def emit(name: String, gen: => chisel3.RawModule): Unit = {
    println(s"// ===== $name =====")
    println(ChiselStage.emitSystemVerilog(gen, firtoolOpts = opts))
  }
  emit("Blinky", new Blinky(1000))
  emit("Alu", new Alu)
  emit("Mux4", new Mux4)
  emit("UpCounter", new UpCounter(9))
  emit("RegisterFile", new RegisterFile(32, 32))
  emit("FallDetect", new FallDetect)
  emit("SkidBuffer", new SkidBuffer(UInt(8.W)))
  emit("QueueFifo", new QueueFifo(8, 4))
  emit("AdderTree", new AdderTree(8, 16))
  emit("Wildcat", new Wildcat(Seq.fill(16)(BigInt(0))))
}
