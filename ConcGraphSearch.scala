import io.threadcso._
import scala.collection.immutable.List

class ConcGraphSearch[N](g: Graph[N]) extends GraphSearch[N](g){
    /**The number of workers. */
    val numWorkers = 8

    private val stack = TerminatingConcStack[(N, List[N])](numWorkers)

    // channel which sends solution to coordinator
    private val toCoordinator = ManyOne[List[N]]

    // store the solution
    private val result: Option[List[N]] = None

    // a single worker
    private def worker(isTarget: N => Boolean) = proc {
        repeat {
            // get the next node to search
            val (n, path) = stack.pop
            for (n1 <- g.succs(n)) {
                // if solution found, send to coordinator
                if isTarget(n1) toCoordinator!(path :+ n1)
                // otherwise add to the stack
                else stack.push((n1, path :+ n1))
            }
        }
        // close the coordinator
        toCoordinator.close
    }

    // the coordinator
    private def coordinator = proc {
        attempt{ result = toCoordinator?() }{}
        stack.shutdown
        toCoordinator.close
    }

    /** Perform a depth-first search in g, starting from start, for a node that
      * satisfies isTarget. */
    def apply(start: N, isTarget: N => Boolean): Option[N] = {
        val workers = || (for (_ <- 0 until numWorkers) yield worker)
        run(workers || coordinator)
        result
    }
}
