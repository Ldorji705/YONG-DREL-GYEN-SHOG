
package com.yongdrel.servicetemplate.application.port.in;

import com.yongdrel.servicetemplate.domain.model.User;
import java.util.List;

public interface UserUseCase {

    User registerUser(User user);

    List<User> getAllUsers();

    User getUserById(Long id);
}
