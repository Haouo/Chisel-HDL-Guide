package handout

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class QueueFifoSpec extends AnyFlatSpec with Matchers with ChiselSim {
  "QueueFifo" should "enqueue then dequeue in order" in {
    simulate(new QueueFifo(8, 4)) { dut =>
      dut.io.deq.ready.poke(false.B)
      // push 1,2,3
      for (v <- Seq(1, 2, 3)) {
        dut.io.enq.valid.poke(true.B); dut.io.enq.bits.poke(v.U)
        dut.clock.step()
      }
      dut.io.enq.valid.poke(false.B)
      // pop and check order
      dut.io.deq.ready.poke(true.B)
      for (v <- Seq(1, 2, 3)) {
        dut.io.deq.valid.expect(true.B)
        dut.io.deq.bits.expect(v.U)
        dut.clock.step()
      }
    }
  }
}

class OneStageSpec extends AnyFlatSpec with Matchers with ChiselSim {
  "OneStage" should "hold one element" in {
    simulate(new OneStage(8)) { dut =>
      dut.io.deq.ready.poke(false.B)
      dut.io.enq.valid.poke(true.B); dut.io.enq.bits.poke(42.U)
      dut.io.enq.ready.expect(true.B)
      dut.clock.step()
      dut.io.enq.valid.poke(false.B)
      dut.io.deq.valid.expect(true.B)
      dut.io.deq.bits.expect(42.U)
      dut.io.enq.ready.expect(false.B)   // full now
    }
  }
}

class SkidBufferSpec extends AnyFlatSpec with Matchers with ChiselSim {
  "SkidBuffer" should "pass data through when not stalled" in {
    simulate(new SkidBuffer(UInt(8.W))) { dut =>
      dut.io.out.ready.poke(true.B)
      dut.io.in.valid.poke(true.B); dut.io.in.bits.poke(99.U)
      dut.io.out.valid.expect(true.B)
      dut.io.out.bits.expect(99.U)
    }
  }
}
