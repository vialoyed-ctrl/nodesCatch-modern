package com.vialoyed.nodescatch;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class BatchRunnerTest {
 @Test public void concurrencyIsBoundedAndJobsActuallyOverlap() throws Exception {
  AtomicInteger active=new AtomicInteger(),maximum=new AtomicInteger(),done=new AtomicInteger();CountDownLatch pair=new CountDownLatch(2);
  BatchRunner.run(8,2,()->false,index->{int n=active.incrementAndGet();maximum.accumulateAndGet(n,Math::max);pair.countDown();assertTrue("Jobs did not overlap",pair.await(2,TimeUnit.SECONDS));Thread.sleep(20);active.decrementAndGet();done.incrementAndGet();});
  assertEquals(2,maximum.get());assertEquals(8,done.get());assertEquals(0,active.get());
 }
 @Test public void cancellationSkipsQueuedJobs() throws Exception {
  AtomicBoolean cancelled=new AtomicBoolean();AtomicInteger started=new AtomicInteger();
  BatchRunner.run(20,1,cancelled::get,index->{started.incrementAndGet();cancelled.set(true);});assertEquals(1,started.get());
 }
 @Test public void finishedWorkerFailureIsReported() throws Exception {
  try{BatchRunner.run(4,2,()->false,index->{throw new java.io.IOException("probe");});fail();}catch(ExecutionException expected){assertTrue(expected.getCause() instanceof java.io.IOException);}
 }
}
