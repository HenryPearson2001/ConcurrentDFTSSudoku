import io.threadcso._
import scala.collection.immutable.List
import scala.collection.mutable.Queue

// a concurrent stack which terminates if all  the worker threads are attempting to pop off the stack
// and the stack is empty
class TerminatingConcStack[T](numWorkers: p) {

    // channels for enqueueing and dequeueing - when dequeue send a reply chan for a reply
    private val enqueueChan = ManyOne[T]

    private type ReplyChan = Chan[T]
    private val dequeueChan = ManyOne[ReplyChan]

    // channel to shutdown the stack
    private val shutdownChan = ManyOne[Unit]

    // send the value to be enqueued
    def enqueue(x: T): Unit = enqueueChan!x

    // send the request to dequeue and the reply channel, receive the reply
    def dequeue: T = {
        val reply = OneOne[A]
        dequeueChan!reply
        reply?()
    }

    // shutdown the stack
    def shutdown = attempt{ shutdownChan!(()) }{}

    private def server = proc {
        // held values
        var stack = new List[T]()
        // queue of channels waiting to dequeue
        val waiters = new Queue[ReplyChan]()

        // function to close all the channels in the server
        def close = {
            for (w <- waiters) w.close
            enqueueChan.close; dequeueChan.close; shutdownChan.close
        }

        serve (
            // if enqueueing, either send directly to a waiting process or push to the stack if no waiters
            enqueueChan =?=> { x =>
                if (waiters.nonEmpty) {
                    assert(stack.isEmpty); waiters.dequeue!x
                }
                else stack = x::stack
            }
            // if dequeueing, either pop item from the stack if stack has items or add to list of waiting processes
            | dequeueChan =?=> { reply =>
                if (stack.nonEmpty) {
                    (head::stack) = stack
                    reply!head
                }
                else {
                    waiters.enqueue(reply)
                    if (waiters.length == numWorkers) close
                }
            }
            // if shutdown requested, close all channels
            | shutdownChan =?=> { _ => close }
        )
    }

    server.fork

}