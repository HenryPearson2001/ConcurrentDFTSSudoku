import io.threadcso._
import scala.collection.immutable.List

class ConcGraphSearch[N](g: Graph[N]) extends GraphSearch[N](g){
    /**The number of workers. */
    val numWorkers = 8

    private val stack = TerminatingConcStack[(N, List[N])](numWorkers)

    // a single worker
    private def worker = proc {
        repeat {
            val (n, path) = stack.
        }
    }

    /** Perform a depth-first search in g, starting from start, for a node that
      * satisfies isTarget. */
    def apply(start: N, isTarget: N => Boolean): Option[N] = {

    }
}
