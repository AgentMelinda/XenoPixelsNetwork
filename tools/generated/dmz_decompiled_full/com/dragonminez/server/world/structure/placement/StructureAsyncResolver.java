package com.dragonminez.server.world.structure.placement;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class StructureAsyncResolver {
   private static final ExecutorService COORDINATOR = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "DMZ-StructurePlanner");
      thread.setDaemon(true);
      thread.setPriority(1);
      return thread;
   });
   private static final ForkJoinPool SEARCH_POOL = new ForkJoinPool(
      Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1)), new StructureAsyncResolver.SearchThreadFactory(), null, false
   );

   private StructureAsyncResolver() {
   }

   static void buildPlan(StructureSpawnPlanner.PlanHolder holder) {
      COORDINATOR.submit(() -> {
         try {
            StructureSpawnPlanner.runBuild(holder, SEARCH_POOL);
         } catch (Throwable var2) {
            System.err.println("[DMZ] StructureAsyncResolver build failed: " + var2.getMessage());
            holder.publish(Collections.emptyMap());
         }
      });
   }

   static void buildPlanSync(StructureSpawnPlanner.PlanHolder holder) {
      try {
         StructureSpawnPlanner.runBuild(holder, SEARCH_POOL);
      } catch (Throwable var2) {
         System.err.println("[DMZ] StructureAsyncResolver sync build failed: " + var2.getMessage());
         holder.publish(Collections.emptyMap());
      }
   }

   private static final class SearchThreadFactory implements ForkJoinWorkerThreadFactory {
      private final AtomicInteger counter = new AtomicInteger();

      @Override
      public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
         ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
         thread.setName("DMZ-StructureSearch-" + this.counter.incrementAndGet());
         thread.setDaemon(true);
         thread.setPriority(1);
         return thread;
      }
   }
}
