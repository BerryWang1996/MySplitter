package com.mysplitter.demo.service;

import com.mysplitter.demo.domain.Department;
import com.mysplitter.demo.domain.User;

import java.util.List;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
public interface DemoService {

    int saveUser(User user);

    int saveDepartment(Department user);

    void exception(Integer id);

    List<User> userList();
}
