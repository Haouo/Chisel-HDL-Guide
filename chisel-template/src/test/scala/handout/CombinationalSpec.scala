package handout

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AluSpec extends AnyFlatSpec with Matchers with ChiselSim {
  "Alu" should "compute add/sub/and/or" in {
    simulate(new Alu) { dut =>
      def check(op: Int, a: Int, b: Int, exp: Int): Unit = {
        dut.io.req.op.poke(op.U); dut.io.req.src1.poke(a.U); dut.io.req.src2.poke(b.U)
        dut.io.resp.result.expect(exp.U)
      }
      check(0, 7, 5, 12)        // add
      check(1, 7, 5, 2)         // sub
      check(2, 0xF0, 0x0F, 0x00) // and
      check(3, 0xF0, 0x0F, 0xFF) // or
    }
  }
}

class Mux4Spec extends AnyFlatSpec with Matchers with ChiselSim {
  "Mux4" should "select the right input" in {
    simulate(new Mux4) { dut =>
      for (i <- 0 until 4) dut.io.in(i).poke((10 + i).U)
      for (i <- 0 until 4) { dut.io.sel.poke(i.U); dut.io.out.expect((10 + i).U) }
    }
  }
}

class AdderTreeSpec extends AnyFlatSpec with Matchers with ChiselSim {
  "AdderTree" should "sum all inputs" in {
    simulate(new AdderTree(8, 16)) { dut =>
      for (i <- 0 until 8) dut.io.in(i).poke((i + 1).U) // 1..8 → sum=36
      dut.io.sum.expect(36.U)
    }
  }
}
