TestCameraPreview
=================

A basic multithreaded decoder/renderer Camera Preview Implementation


Basic Ideas for parallel execution:

A single threaded program will do this work using following three steps:

1) Grab Raw Frame from Camera Preview Callback function.

2) Decode Raw YCrCb-Format Color Buffer into ARGB Format Color Buffer suitable for use in Bitmap object.

3) Render the Bitmap Object into the Surface View.

Note the three steps are strictly ordered steps meaning one must follow another, and in multithreaded environment shared
memory must be properly guarded with mutex, synchronization be enforced with semaphore to make one step follow another step.

So our parallel execution model boils down to reassigning the above three types of roles to three threads such as following:

Thread 1: Doing work of grabing Raw Frame, which mainly done for us by Android Framework, unfortunately it has to use Main Thread.
Thread 2: Doing Decoding work, it must use each intermediate result feeded from Thread1
Thread 3: Doing Rendering work, double buffering is employed here to minimize memory copying, but still need to carefully
synchronize Swap Buffer event to make sure it gets exactly timed result feeded from Thread2

Graphically, execution flow looks like this (Note: time flows downwards, lower means older in timeline) 

Suppose three time units passed, Thread 3 only gets the first Frame (Here "time unit" is an abstraction limited
by the longest execution time of the three threads) .

      Thread 1          Thread 2          Thread3
      Grab Frame2       Decode Frame1     Display Frame0
      
      Grab Frame1       Decode Frame0
      
      Grab Frame0
      
Suppose six time units passed

      Thread 1          Thread 2          Thread3
      Grab Frame5       Decode Frame4     Display Frame3
      
      Grab Frame4       Decode Frame3     Display Frame2
      
      Grab Frame3       Decode Frame2     Display Frame1
      
Note: the pipeline can be fully filled only after 5 time units, also the major shortcomming of this implementation is
Time Unit is an ideal abstraction, there is certainly time difference for each thread to finish its workload, most efficient
Implemenation should find equally time sliced workload for each thread to do in its time unit so there will be no waiting 
among them.

Also in term of implementation of Barrier Synchronization(Here finishing each time unit can be viewed as "Barrier"), Thread.join()
is simple and straight-forward but it can only be used when a thread terminates, thus adding overheads of making new threads in
the run loop. So the approach taken here is using semaphores to do mannual adjustment of execution sequencing, at the begining and end of 
each loop cycle, this is a bit obscure can make code hard-to-read, but should be much more efficient than former approach.
