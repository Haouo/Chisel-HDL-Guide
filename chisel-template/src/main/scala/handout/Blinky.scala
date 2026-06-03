package handout

import chisel3._
import chisel3.util.Counter

/** Part 0 — Hello Chisel：blinking LED。 */
class Blinky(freq: Int, startOn: Boolean = false) extends Module {
  val io = IO(new Bundle { val led = Output(Bool()) })

  val led = RegInit(startOn.B)
  val (_, wrap) = Counter(true.B, freq / 2)
  when(wrap) { led := ~led }
  io.led := led
}
