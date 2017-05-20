package fs2

import scala.concurrent.ExecutionContext

import cats.effect.Effect
import cats.implicits._

import fs2.async.mutable.Queue

object Pipe {

  // nb: methods are in alphabetical order

  // /** Behaves like the identity stream, but emits no output until the source is exhausted. */
  // def bufferAll[F[_],I]: Pipe[F,I,I] = bufferBy(_ => true)
  //
  // /**
  //  * Behaves like the identity stream, but requests elements from its
  //  * input in blocks that end whenever the predicate switches from true to false.
  //  */
  // def bufferBy[F[_],I](f: I => Boolean): Pipe[F,I,I] = {
  //   def go(buffer: Vector[Chunk[I]], last: Boolean): Handle[F,I] => Pull[F,I,Unit] = {
  //     _.uncons.flatMap {
  //       case Some((chunk, h)) =>
  //         val (out, buf, last) = {
  //           chunk.foldLeft((Vector.empty[Chunk[I]], Vector.empty[I], false)) { case ((out, buf, last), i) =>
  //             val cur = f(i)
  //             if (!f(i) && last) (out :+ Chunk.indexedSeq(buf :+ i), Vector.empty, cur)
  //             else (out, buf :+ i, cur)
  //           }
  //         }
  //         if (out.isEmpty) {
  //           go(buffer :+ Chunk.indexedSeq(buf), last)(h)
  //         } else {
  //           (buffer ++ out).foldLeft(Pull.pure(()): Pull[F,I,Unit]) { (acc, c) => acc >> Pull.output(c) } >>
  //             go(Vector(Chunk.indexedSeq(buf)), last)(h)
  //         }
  //
  //       case None =>
  //         buffer.foldLeft(Pull.pure(()): Pull[F,I,Unit]) { (acc, c) => acc >> Pull.output(c) }
  //     }
  //   }
  //   _.pull { h => go(Vector.empty, false)(h) }
  // }

  // /** Debounce the stream with a minimum period of `d` between each element */
  // def debounce[F[_], I](d: FiniteDuration)(implicit F: Effect[F], scheduler: Scheduler, ec: ExecutionContext): Pipe[F, I, I] = {
  //   def go(i: I, h1: Handle[F, I]): Pull[F, I, Nothing] = {
  //     time.sleep[F](d).open.flatMap { h2 =>
  //       h2.awaitAsync.flatMap { l =>
  //         h1.awaitAsync.flatMap { r =>
  //           (l race r).pull.flatMap {
  //             case Left(_) => Pull.output1(i) >> r.pull.flatMap(identity).flatMap {
  //               case (hd, tl) => go(hd.last, tl)
  //             }
  //             case Right(r) => r.optional.flatMap {
  //               case Some((hd, tl)) => go(hd.last, tl)
  //               case None => Pull.output1(i) >> Pull.done
  //             }
  //           }
  //         }
  //       }
  //     }
  //   }
  //   _.pull { h => h.await.flatMap { case (hd, tl) => go(hd.last, tl) } }
  // }

  // /**
  //  * Partitions the input into a stream of chunks according to a discriminator function.
  //  * Each chunk is annotated with the value of the discriminator function applied to
  //  * any of the chunk's elements.
  //  */
  // def groupBy[F[_], K, V](f: V => K)(implicit eq: Eq[K]): Pipe[F, V, (K, Vector[V])] = {
  //
  //   def go(current: Option[(K, Vector[V])]):
  //       Handle[F, V] => Pull[F, (K, Vector[V]), Unit] = h => {
  //
  //     h.uncons.flatMap {
  //       case Some((chunk, h)) =>
  //         val (k1, out) = current.getOrElse((f(chunk(0)), Vector[V]()))
  //         doChunk(chunk, h, k1, out, Vector.empty)
  //       case None =>
  //         val l = current.map { case (k1, out) => Pull.output1((k1, out)) } getOrElse Pull.pure(())
  //         l >> Pull.done
  //     }
  //   }
  //
  //   @annotation.tailrec
  //   def doChunk(chunk: Chunk[V], h: Handle[F, V], k1: K, out: Vector[V], acc: Vector[(K, Vector[V])]):
  //       Pull[F, (K, Vector[V]), Unit] = {
  //
  //     val differsAt = chunk.indexWhere(v => eq.neqv(f(v), k1)).getOrElse(-1)
  //     if (differsAt == -1) {
  //       // whole chunk matches the current key, add this chunk to the accumulated output
  //       val newOut: Vector[V] = out ++ chunk.toVector
  //       if (acc.isEmpty) {
  //         go(Some((k1, newOut)))(h)
  //       } else {
  //         // potentially outputs one additional chunk (by splitting the last one in two)
  //         Pull.output(Chunk.indexedSeq(acc)) >> go(Some((k1, newOut)))(h)
  //       }
  //     } else {
  //       // at least part of this chunk does not match the current key, need to group and retain chunkiness
  //       var startIndex = 0
  //       var endIndex = differsAt
  //
  //       // split the chunk into the bit where the keys match and the bit where they don't
  //       val matching = chunk.take(differsAt)
  //       val newOut: Vector[V] = out ++ matching.toVector
  //       val nonMatching = chunk.drop(differsAt)
  //       // nonMatching is guaranteed to be non-empty here, because we know the last element of the chunk doesn't have
  //       // the same key as the first
  //       val k2 = f(nonMatching(0))
  //       doChunk(nonMatching, h, k2, Vector[V](), acc :+ ((k1, newOut)))
  //     }
  //   }
  //
  //   in => in.pull(go(None))
  // }

  // /**
  //   * Maps a running total according to `S` and the input with the function `f`.
  //   *
  //   * @example {{{
  //   * scala> Stream("Hello", "World")
  //   *      |   .mapAccumulate(0)((l, s) => (l + s.length, s.head)).toVector
  //   * res0: Vector[(Int, Char)] = Vector((5,H), (10,W))
  //   * }}}
  //   */
  // def mapAccumulate[F[_],S,I,O](init: S)(f: (S,I) => (S,O)): Pipe[F,I,(S,O)] =
  //   _.pull { _.receive { case (chunk, h) =>
  //     val f2 = (s: S, i: I) => {
  //       val (newS, newO) = f(s, i)
  //       (newS, (newS, newO))
  //     }
  //     val (s, o) = chunk.mapAccumulate(init)(f2)
  //     Pull.output(o) >> _mapAccumulate0(s)(f2)(h)
  //   }}
  // private def _mapAccumulate0[F[_],S,I,O](init: S)(f: (S,I) => (S,(S,O))): Handle[F,I] => Pull[F,(S,O),Handle[F,I]] =
  //   _.receive { case (chunk, h) =>
  //     val (s, o) = chunk.mapAccumulate(init)(f)
  //     Pull.output(o) >> _mapAccumulate0(s)(f)(h)
  //   }

  // /**
  //  * Modifies the chunk structure of the underlying stream, emitting potentially unboxed
  //  * chunks of `n` elements. If `allowFewer` is true, the final chunk of the stream
  //  * may be less than `n` elements. Otherwise, if the final chunk is less than `n`
  //  * elements, it is dropped.
  //  */
  // def rechunkN[F[_],I](n: Int, allowFewer: Boolean = true): Pipe[F,I,I] =
  //   in => chunkN(n, allowFewer)(in).flatMap { chunks => Stream.chunk(Chunk.concat(chunks)) }

  // /** Emits the given values, then echoes the rest of the input. */
  // def shiftRight[F[_],I](head: I*): Pipe[F,I,I] =
  //   _ pull { h => h.push(Chunk.indexedSeq(Vector(head: _*))).echo }
  //
  // /**
  //  * Groups inputs in fixed size chunks by passing a "sliding window"
  //  * of size `n` over them. If the input contains less than or equal to
  //  * `n` elements, only one chunk of this size will be emitted.
  //  *
  //  * @example {{{
  //  * scala> Stream(1, 2, 3, 4).sliding(2).toList
  //  * res0: List[Vector[Int]] = List(Vector(1, 2), Vector(2, 3), Vector(3, 4))
  //  * }}}
  //  * @throws scala.IllegalArgumentException if `n` <= 0
  //  */
  // def sliding[F[_],I](n: Int): Pipe[F,I,Vector[I]] = {
  //   require(n > 0, "n must be > 0")
  //   def go(window: Vector[I]): Handle[F,I] => Pull[F,Vector[I],Unit] = h => {
  //     h.receive {
  //       case (chunk, h) =>
  //         val out: Vector[Vector[I]] =
  //           chunk.toVector.scanLeft(window)((w, i) => w.tail :+ i).tail
  //         if (out.isEmpty) go(window)(h)
  //         else Pull.output(Chunk.indexedSeq(out)) >> go(out.last)(h)
  //     }
  //   }
  //   _ pull { h => h.awaitN(n, true).flatMap { case (chunks, h) =>
  //     val window = chunks.foldLeft(Vector.empty[I])(_ ++ _.toVector)
  //     Pull.output1(window) >> go(window)(h)
  //   }}
  // }
  //
  // /**
  //  * Breaks the input into chunks where the delimiter matches the predicate.
  //  * The delimiter does not appear in the output. Two adjacent delimiters in the
  //  * input result in an empty chunk in the output.
  //  */
  // def split[F[_],I](f: I => Boolean): Pipe[F,I,Vector[I]] = {
  //   def go(buffer: Vector[I]): Handle[F,I] => Pull[F,Vector[I],Unit] = {
  //     _.uncons.flatMap {
  //       case Some((chunk, h)) =>
  //         chunk.indexWhere(f) match {
  //           case None =>
  //             go(buffer ++ chunk.toVector)(h)
  //           case Some(idx) =>
  //             val out = buffer ++ chunk.take(idx).toVector
  //             val carry = if (idx + 1 < chunk.size) chunk.drop(idx + 1) else Chunk.empty
  //             Pull.output1(out) >> go(Vector.empty)(h.push(carry))
  //         }
  //       case None =>
  //         if (buffer.nonEmpty) Pull.output1(buffer) else Pull.done
  //     }
  //   }
  //   _.pull(go(Vector.empty))
  // }

  // /**
  //  * Groups inputs into separate `Vector` objects of size `n`.
  //  *
  //  * @example {{{
  //  * scala> Stream(1, 2, 3, 4, 5).vectorChunkN(2).toVector
  //  * res0: Vector[Vector[Int]] = Vector(Vector(1, 2), Vector(3, 4), Vector(5))
  //  * }}}
  //  */
  // def vectorChunkN[F[_],I](n: Int, allowFewer: Boolean = true): Pipe[F,I,Vector[I]] =
  //   chunkN(n, allowFewer) andThen (_.map(i => i.foldLeft(Vector.empty[I])((v, c) => v ++ c.iterator)))

  // /**
  //  * Zips the elements of the input `Handle` with its next element wrapped into `Some`, and returns the new `Handle`.
  //  * The last element is zipped with `None`.
  //  */
  // def zipWithNext[F[_], I]: Pipe[F,I,(I,Option[I])] = {
  //   def go(last: I): Handle[F, I] => Pull[F, (I, Option[I]), Handle[F, I]] =
  //     _.uncons.flatMap {
  //       case None => Pull.output1((last, None)) as Handle.empty
  //       case Some((chunk, h)) =>
  //         val (newLast, zipped) = chunk.mapAccumulate(last) {
  //           case (prev, next) => (next, (prev, Some(next)))
  //         }
  //         Pull.output(zipped) >> go(newLast)(h)
  //     }
  //   _.pull(_.receive1 { case (head, h) => go(head)(h) })
  // }
  //
  // /**
  //  * Zips the elements of the input `Handle` with its previous element wrapped into `Some`, and returns the new `Handle`.
  //  * The first element is zipped with `None`.
  //  */
  // def zipWithPrevious[F[_], I]: Pipe[F,I,(Option[I],I)] = {
  //   mapAccumulate[F, Option[I], I, (Option[I], I)](None) {
  //     case (prev, next) => (Some(next), (prev, next))
  //   } andThen(_.map { case (_, prevNext) => prevNext })
  // }
  //
  // /**
  //  * Zips the elements of the input `Handle` with its previous and next elements wrapped into `Some`, and returns the new `Handle`.
  //  * The first element is zipped with `None` as the previous element,
  //  * the last element is zipped with `None` as the next element.
  //  */
  // def zipWithPreviousAndNext[F[_], I]: Pipe[F,I,(Option[I],I,Option[I])] = {
  //   (zipWithPrevious[F, I] andThen zipWithNext[F, (Option[I], I)]) andThen {
  //     _.map {
  //       case ((prev, that), None) => (prev, that, None)
  //       case ((prev, that), Some((_, next))) => (prev, that, Some(next))
  //     }
  //   }
  // }
  //
  // /**
  //  * Zips the input with a running total according to `S`, up to but not including the current element. Thus the initial
  //  * `z` value is the first emitted to the output:
  //  * {{{
  //  * scala> Stream("uno", "dos", "tres", "cuatro").zipWithScan(0)(_ + _.length).toList
  //  * res0: List[(String,Int)] = List((uno,0), (dos,3), (tres,6), (cuatro,10))
  //  * }}}
  //  *
  //  * @see [[zipWithScan1]]
  //  */
  // def zipWithScan[F[_],I,S](z: S)(f: (S, I) => S): Pipe[F,I,(I,S)] =
  //   _.mapAccumulate(z) { (s,i) => val s2 = f(s,i); (s2, (i,s)) }.map(_._2)
  //
  // /**
  //  * Zips the input with a running total according to `S`, including the current element. Thus the initial
  //  * `z` value is the first emitted to the output:
  //  * {{{
  //  * scala> Stream("uno", "dos", "tres", "cuatro").zipWithScan1(0)(_ + _.length).toList
  //  * res0: List[(String, Int)] = List((uno,3), (dos,6), (tres,10), (cuatro,16))
  //  * }}}
  //  *
  //  * @see [[zipWithScan]]
  //  */
  // def zipWithScan1[F[_],I,S](z: S)(f: (S, I) => S): Pipe[F,I,(I,S)] =
  //   _.mapAccumulate(z) { (s,i) => val s2 = f(s,i); (s2, (i,s2)) }.map(_._2)

  // // Combinators for working with pipes
  //
  // /** Creates a [[Stepper]], which allows incrementally stepping a pure pipe. */
  // def stepper[I,O](s: Pipe[Pure,I,O]): Stepper[I,O] = {
  //   type Read[+R] = Option[Chunk[I]] => R
  //   def readFunctor: Functor[Read] = new Functor[Read] {
  //     def map[A,B](fa: Read[A])(g: A => B): Read[B]
  //       = fa andThen g
  //   }
  //   def prompts: Stream[Read,I] =
  //     Stream.eval[Read, Option[Chunk[I]]](identity).flatMap {
  //       case None => Stream.empty
  //       case Some(chunk) => Stream.chunk(chunk).append(prompts)
  //     }
  //
  //   def outputs: Stream[Read,O] = covary[Read,I,O](s)(prompts)
  //   def stepf(s: Handle[Read,O]): Free[Read, Option[(Chunk[O],Handle[Read, O])]]
  //   = s.buffer match {
  //       case hd :: tl => Free.pure(Some((hd, new Handle[Read,O](tl, s.stream))))
  //       case List() => s.stream.step.flatMap { s => Pull.output1(s) }
  //        .close.runFoldFree(None: Option[(Chunk[O],Handle[Read, O])])(
  //         (_,s) => Some(s))
  //     }
  //   def go(s: Free[Read, Option[(Chunk[O],Handle[Read, O])]]): Stepper[I,O] =
  //     Stepper.Suspend { () =>
  //       s.unroll[Read](readFunctor, Sub1.sub1[Read]) match {
  //         case Free.Unroll.Fail(err) => Stepper.Fail(err)
  //         case Free.Unroll.Pure(None) => Stepper.Done
  //         case Free.Unroll.Pure(Some((hd, tl))) => Stepper.Emits(hd, go(stepf(tl)))
  //         case Free.Unroll.Eval(recv) => Stepper.Await(chunk => go(recv(chunk)))
  //       }
  //     }
  //   go(stepf(new Handle[Read,O](List(), outputs)))
  // }
  //
  // /**
  //  * Allows stepping of a pure pipe. Each invocation of [[step]] results in
  //  * a value of the [[Stepper.Step]] algebra, indicating that the pipe is either done, it
  //  * failed with an exception, it emitted a chunk of output, or it is awaiting input.
  //  */
  // sealed trait Stepper[-A,+B] {
  //   @annotation.tailrec
  //   final def step: Stepper.Step[A,B] = this match {
  //     case Stepper.Suspend(s) => s().step
  //     case _ => this.asInstanceOf[Stepper.Step[A,B]]
  //   }
  // }
  //
  // object Stepper {
  //   private[fs2] final case class Suspend[A,B](force: () => Stepper[A,B]) extends Stepper[A,B]
  //
  //   /** Algebra describing the result of stepping a pure pipe. */
  //   sealed trait Step[-A,+B] extends Stepper[A,B]
  //   /** Pipe indicated it is done. */
  //   final case object Done extends Step[Any,Nothing]
  //   /** Pipe failed with the specified exception. */
  //   final case class Fail(err: Throwable) extends Step[Any,Nothing]
  //   /** Pipe emitted a chunk of elements. */
  //   final case class Emits[A,B](chunk: Chunk[B], next: Stepper[A,B]) extends Step[A,B]
  //   /** Pipe is awaiting input. */
  //   final case class Await[A,B](receive: Option[Chunk[A]] => Stepper[A,B]) extends Step[A,B]
  // }

  /** Queue based version of [[join]] that uses the specified queue. */
  def joinQueued[F[_],A,B](q: F[Queue[F,Option[Segment[A,Unit]]]])(s: Stream[F,Pipe[F,A,B]])(implicit F: Effect[F], ec: ExecutionContext): Pipe[F,A,B] = in => {
    for {
      done <- Stream.eval(async.signalOf(false))
      q <- Stream.eval(q)
      b <- in.segments.map(Some(_)).evalMap(q.enqueue1)
             .drain
             .onFinalize(q.enqueue1(None))
             .onFinalize(done.set(true)) merge done.interrupt(s).flatMap { f =>
               f(q.dequeue.unNoneTerminate flatMap Stream.segment)
             }
    } yield b
  }

  /** Asynchronous version of [[join]] that queues up to `maxQueued` elements. */
  def joinAsync[F[_]:Effect,A,B](maxQueued: Int)(s: Stream[F,Pipe[F,A,B]])(implicit ec: ExecutionContext): Pipe[F,A,B] =
    joinQueued[F,A,B](async.boundedQueue(maxQueued))(s)

  /**
   * Joins a stream of pipes in to a single pipe.
   * Input is fed to the first pipe until it terminates, at which point input is
   * fed to the second pipe, and so on.
   */
  def join[F[_]:Effect,A,B](s: Stream[F,Pipe[F,A,B]])(implicit ec: ExecutionContext): Pipe[F,A,B] =
    joinQueued[F,A,B](async.synchronousQueue)(s)
}
