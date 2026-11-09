
package com.yongdrel.servicetemplate.application.port.out;

import com.yongdrel.servicetemplate.domain.model.User;
import java.util.List;
import java.util.Optional;

public interface UserPersistencePort {

    User save(User user);

    List<User> findAll();

    Optional<User> findById(Long id);
}
