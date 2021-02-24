import io.threadcso._
import scala.collection.mutable.Stack
import scala.collection.mutable.Queue

// a concurrent stack which terminates if all  the worker threads are attempting to pop off the stack
// and the stack is empty
class TerminatingConcStack[T](numWorkers: Int) {

    // channels for pushing and popping - when popping send a reply chan for a reply
    private val pushChan = ManyOne[T]

    private type ReplyChan = Chan[T]
    private val popChan = ManyOne[ReplyChan]

    // channel to shutdown the stack
    private val shutdownChan = ManyOne[Unit]

    // send the value to be enqueued
    def push(x: T): Unit = pushChan!x

    // send the request to dequeue and the reply channel, receive the reply
    def pop: T = {
        val reply = OneOne[T]
        popChan!reply
        reply?()
    }

    // shutdown the stack
    def shutdown = attempt{ shutdownChan!(()) }{}

    private def server = proc {
        // held values
        var stack = new Stack[T]()
        // queue of channels waiting to pop
        val waiters = new Queue[ReplyChan]()

        // function to close all the channels in the server
        def close = {
            for (w <- waiters) w.close
            pushChan.close; popChan.close; shutdownChan.close
        }

        serve (
            // if pushing, either send directly to a waiting process or push to the stack if no waiters
            pushChan =?=> { x =>
                if (waiters.nonEmpty) {
                    assert(stack.isEmpty); waiters.dequeue!x
                }
                else stack.push(x)
            }
            // if popping, either pop item from the stack if stack has items or add to list of waiting processes
            | popChan =?=> { reply =>
                if (stack.nonEmpty) {
                    reply!(stack.pop)
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