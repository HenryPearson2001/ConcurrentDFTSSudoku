import io.threadcso._
import scala.collection.immutable.List

class ConcGraphSearch[N](g: Graph[N]) extends GraphSearch[N](g){
    /**The number of workers. */
    val numWorkers = 8
    private val stack = new TerminatingConcStack[(N)](numWorkers)

    // channel which sends solution to coordinator
    private val toCoordinator = ManyOne[N]

    // store the solution
    private var result: Option[N] = None

    // a single worker
    private def worker(isTarget: N => Boolean) = proc {
        repeat {
            // get the next node to search
            val n = stack.pop
            for (n1 <- g.succs(n)) {
                // if solution found, send to coordinator
                if (isTarget(n1)) toCoordinator!(n1)
                // otherwise add to the stack
                else stack.push(n1)
            }
        }
        // close the coordinator
        toCoordinator.close
    }

    // the coordinator
    private def coordinator = proc {
        attempt{ result = Some(toCoordinator?()) }{}
        stack.shutdown
        toCoordinator.close
    }

    /** Perform a depth-first search in g, starting from start, for a node that
      * satisfies isTarget. */
    def apply(start: N, isTarget: N => Boolean): Option[N] = {
        val workers = || (for (_ <- 0 until numWorkers) yield worker(isTarget))
        stack.push(start)
        run(workers || coordinator)
        result
    }
}
