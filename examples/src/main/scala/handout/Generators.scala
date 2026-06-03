package handout

import chisel3._
import chisel3.util._

/** Part 4 — function 回傳硬體：延遲 n 拍。 */
object Generators {
  def delay(x: UInt, n: Int): UInt = if (n == 0) x else delay(RegNext(x), n - 1)
  def immI(inst: UInt): UInt = Cat(Fill(20, inst(31)), inst(31, 20))

  /** 泛型平衡樹歸約（適用於非 Data 型別，如 (Bool, UInt) tuple 的仲裁樹）。 注意：Chisel 內建的 `reduceTree` 只能用在 `Vec` 上（見 AdderTree）。
    */
  def treeReduce[T](xs: Seq[T])(op: (T, T) => T): T = {
    require(xs.nonEmpty)
    if (xs.length == 1) xs.head
    else { val (l, r) = xs.splitAt((xs.length + 1) / 2); op(treeReduce(l)(op), treeReduce(r)(op)) }
  }
}

/** Part 4 — 泛型 2 選 1（type parameter）。 */
class Mux2[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle {
    val sel = Input(Bool())
    val a = Input(gen)
    val b = Input(gen)
    val y = Output(gen)
  })
  io.y := Mux(io.sel, io.a, io.b)
}

/** Part 4 — 函數式生成：平衡加法樹（reduceTree, O(log n)）。 */
class AdderTree(n: Int, width: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(n, UInt(width.W)))
    val sum = Output(UInt((width + log2Ceil(n)).W)) // top-level port 需明確寬度
  })
  // reduceTree 是 Vec 的方法；用 +&（width-expanding add）避免溢位。
  io.sum := io.in.reduceTree(_ +& _)
}

/** Part 4 — case class Params + require + derived parameter。 */
case class CacheParams(addrBits: Int, nSets: Int, nWays: Int, blockBytes: Int) {
  require(isPow2(nSets), "nSets must be power of two")
  require(isPow2(blockBytes), "blockBytes must be power of two")
  val offsetBits = log2Ceil(blockBytes)
  val indexBits = log2Ceil(nSets)
  val tagBits = addrBits - indexBits - offsetBits
}

/** 用 CacheParams 抽 tag/index/offset 的小範例模組。 */
class AddressSplitter(p: CacheParams) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(p.addrBits.W))
    val tag = Output(UInt(p.tagBits.W))
    val index = Output(UInt(p.indexBits.W))
    val offset = Output(UInt(p.offsetBits.W))
  })
  io.offset := io.addr(p.offsetBits - 1, 0)
  io.index := io.addr(p.offsetBits + p.indexBits - 1, p.offsetBits)
  io.tag := io.addr(p.addrBits - 1, p.offsetBits + p.indexBits)
  override def desiredName = s"AddressSplitter_${p.nSets}s_${p.nWays}w"
}
