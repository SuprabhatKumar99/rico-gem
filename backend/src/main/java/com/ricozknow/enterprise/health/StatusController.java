package com.ricozknow.enterprise.health;
import org.springframework.web.bind.annotation.*; import java.time.Instant; import java.util.*;
@RestController @RequestMapping("/api/v1/status") public class StatusController {
 @GetMapping public Map<String,Object> status(){return Map.of("status","operational","timestamp",Instant.now(),"service","ricozknow-api");}
}
