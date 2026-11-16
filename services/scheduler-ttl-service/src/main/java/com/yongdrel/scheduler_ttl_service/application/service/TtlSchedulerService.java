package com.yongdrel.scheduler_ttl_service.application.service;
import com.yongdrel.scheduler_ttl_service.adapter.out.persistence.*;import com.yongdrel.scheduler_ttl_service.application.port.ExpiryPorts;import org.springframework.beans.factory.annotation.Value;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.time.*;import java.util.*;
@Service public class TtlSchedulerService{
 private final LeaseService leases;private final ExecutionRepository executions;private final ExpiryPorts.Orders orders;private final ExpiryPorts.Inventory inventory;private final ExpiryPorts.Events events;private final long leaseSeconds;
 public TtlSchedulerService(LeaseService l,ExecutionRepository e,ExpiryPorts.Orders o,ExpiryPorts.Inventory i,ExpiryPorts.Events v,@Value("${app.scheduler.lease-seconds:60}")long s){leases=l;executions=e;orders=o;inventory=i;events=v;leaseSeconds=s;}
 @Scheduled(fixedDelayString="${app.scheduler.poll-ms:30000}")public void run(){runJob("unpaid-orders",()->{for(var x:orders.expired(Instant.now()))orders.expire(x.id(),x.tenantId());});runJob("ticket-reservations",()->{for(var x:inventory.expired(Instant.now()))inventory.release(x.id(),x.tenantId());});runJob("lottery-events",()->{for(var x:events.closing(Instant.now()))events.close(x.id());});}
 void runJob(String name,Runnable action){if(!leases.acquire(name,leaseSeconds))return;JobExecutionEntity j=executions.save(new JobExecutionEntity(name));try{action.run();j.finish("SUCCESS",null);}catch(RuntimeException ex){j.finish("FAILED",ex.getMessage());}executions.save(j);}
 interface Action{void run();}
}
