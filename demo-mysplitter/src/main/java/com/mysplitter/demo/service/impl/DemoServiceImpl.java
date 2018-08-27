package com.mysplitter.demo.service.impl;

import com.mysplitter.demo.domain.Department;
import com.mysplitter.demo.domain.User;
import com.mysplitter.demo.mapper.DepartmentMapper;
import com.mysplitter.demo.mapper.UserMapper;
import com.mysplitter.demo.service.DemoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
@Service
public class DemoServiceImpl implements DemoService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Override
    public int saveUser(User user) {
        return userMapper.save(user);
    }

    @Override
    public int saveDepartment(Department department) {
        return departmentMapper.save(department);
    }
}
