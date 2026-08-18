package com.eesara.drive.apikey;
import com.eesara.drive.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
@Component
public class ApiKeyRateLimiter {
 private record Window(long minute, AtomicInteger count){}
 private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
 @Value("${service.api-key.requests-per-minute:300}") private int limit;
 public void check(long keyId,String ip){long minute=Instant.now().getEpochSecond()/60; String id=keyId+":"+ip;
  Window window=windows.compute(id,(ignored,current)->current==null||current.minute()!=minute?new Window(minute,new AtomicInteger(1)):
   new Window(current.minute(),new AtomicInteger(current.count().incrementAndGet())));
  if(window.count().get()>limit) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMIT_EXCEEDED","API-key request limit exceeded.");
 }
}
