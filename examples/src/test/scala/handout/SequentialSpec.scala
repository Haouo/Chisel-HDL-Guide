package handout

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UpCounterSpec extends AnyFlatSpec with Matchers with ChiselSim {
  "UpCounter" should "count and wrap" in {
    simulate(new UpCounter(3)) { dut =>
      dut.io.en.poke(true.B)
      for (i <- 0 to 3) { dut.io.value.expect(i.U); dut.clock.step() }
      dut.io.value.expect(0.U) // wrapped back to 0
    }
  }
}

class RegisterFileSpec extends AnyFlatSpec with Matchers with ChiselSim {
  "RegisterFile" should "write/read and keep x0 zero" in {
    simulate(new RegisterFile(32, 32)) { dut =>
      // write 0xAB into x5
      dut.io.rd.poke(5.U); dut.io.wdata.poke(0xab.U); dut.io.wen.poke(true.B)
      dut.clock.step()
      dut.io.wen.poke(false.B)
      dut.io.rs1.poke(5.U); dut.io.rd1.expect(0xab.U)
      // x0 stays 0 even if written
      dut.io.rd.poke(0.U); dut.io.wdata.poke(0xff.U); dut.io.wen.poke(true.B)
      dut.clock.step()
      dut.io.rs2.poke(0.U); dut.io.rd2.expect(0.U)
    }
  }
}

class FallDetectSpec extends AnyFlatSpec with Matchers with ChiselSim {
  "FallDetect" should "pulse on falling edge" in {
    simulate(new FallDetect) { dut =>
      dut.io.in.poke(true.B); dut.clock.step() // sLow -> sHigh
      dut.io.in.poke(false.B); dut.clock.step() // sHigh -> sFell
      dut.io.fell.expect(true.B)
      dut.clock.step()
      dut.io.fell.expect(false.B) // sFell -> sLow
    }
  }
}
