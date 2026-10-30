package com.vialoyed.nodescatch;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class BatchRunner {
    public interface Job {void run(int index)throws Exception;}
    private BatchRunner() {}
    public static void run(int count,int concurrency,BooleanSupplier cancelled,Job job)throws Exception{
        if(count==0)return;if(concurrency<1)throw new IllegalArgumentException("并发数至少为 1");
        ExecutorService pool=Executors.newFixedThreadPool(Math.min(count,concurrency));
        CompletionService<Void> completion=new ExecutorCompletionService<>(pool);List<Future<Void>> pending=new ArrayList<>();
        try{
            for(int i=0;i<count&&!cancelled.getAsBoolean();i++){final int index=i;pending.add(completion.submit(()->{if(!cancelled.getAsBoolean())job.run(index);return null;}));}
            for(int done=0;done<pending.size()&&!cancelled.getAsBoolean();){Future<Void> ready=completion.poll(100,TimeUnit.MILLISECONDS);if(ready!=null){ready.get();done++;}}
        }finally{for(Future<?> future:pending)if(!future.isDone())future.cancel(true);pool.shutdownNow();pool.awaitTermination(2,TimeUnit.SECONDS);}
    }
}
