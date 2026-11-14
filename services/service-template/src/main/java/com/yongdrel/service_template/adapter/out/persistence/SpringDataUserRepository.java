
package com.yongdrel.servicetemplate.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserRepository
        extends JpaRepository<UserEntity, Long> {
}
