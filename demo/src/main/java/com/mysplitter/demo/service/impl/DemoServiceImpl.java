package com.mysplitter.demo.service.impl;

import com.mysplitter.demo.domain.Department;
import com.mysplitter.demo.domain.User;
import com.mysplitter.demo.mapper.DepartmentMapper;
import com.mysplitter.demo.mapper.UserMapper;
import com.mysplitter.demo.service.DemoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exception(Integer id) {

        if ("0".equals(id.toString())) {
            throw new IllegalArgumentException("test exception");
        }

        User user = new User();
        user.setName("testException");
        user.setAge(20);
        userMapper.save(user);

        if ("1".equals(id.toString())) {
            throw new IllegalArgumentException("test exception");
        }

        Department department = new Department();
        department.setName("testException");
        departmentMapper.save(department);

        if ("2".equals(id.toString())) {
            throw new IllegalArgumentException("test exception");
        }

    }

    @Override
    public List<User> userList() {
        return userMapper.list();
    }

}
