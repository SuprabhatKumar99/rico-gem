package com.ricozknow.customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CustomerUserRepository extends JpaRepository<CustomerUser,UUID>{
 Optional<CustomerUser> findByTenantIdAndEmailIgnoreCase(UUID tenantId,String email);
}