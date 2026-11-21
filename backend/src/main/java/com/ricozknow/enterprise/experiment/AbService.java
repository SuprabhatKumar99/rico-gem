package com.ricozknow.enterprise.experiment;
import com.fasterxml.jackson.databind.*; import com.ricozknow.common.TenantContext; import com.ricozknow.audit.AuditService; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.*;
@Service @RequiredArgsConstructor public class AbService {
 private final AbExperimentRepository repo; private final AuditService audit; private final ObjectMapper mapper;
 @Transactional(readOnly=true) public List<AbExperiment> list(){return repo.findByTenantIdOrderByCreatedAtDesc(TenantContext.get());}
 @Transactional public AbExperiment save(AbExperiment e){e.setTenantId(TenantContext.get());repo.save(e);audit.record("AbExperiment",e.getId(),"AB_EXPERIMENT_SAVED",null,e.getKey());return e;}
 @Transactional public String assign(String key,String subject){var e=repo.findByTenantIdAndKey(TenantContext.get(),key).orElseThrow();if(!e.isEnabled())return first(e.getVariantsJson());if(Math.floorMod(hash(subject+"|"+key),100)>=e.getTrafficPercent())return first(e.getVariantsJson());return pick(e.getVariantsJson(),Math.floorMod(hash(subject+"|"+key),100));}
 private String first(String j){try{var a=mapper.readTree(j);return a.size()==0?"control":a.get(0).path("key").asText("control");}catch(Exception x){return "control";}}
 private String pick(String j,int n){try{var a=mapper.readTree(j);if(a.size()==0)return"control";return a.get(n%a.size()).path("key").asText("control");}catch(Exception x){return"control";}}
 private int hash(String s){try{return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))[0]&255;}catch(Exception e){return s.hashCode();}}
}
