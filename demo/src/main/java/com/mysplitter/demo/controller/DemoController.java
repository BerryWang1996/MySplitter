package com.mysplitter.demo.controller;

import com.mysplitter.MySplitterDataSource;
import com.mysplitter.demo.domain.Department;
import com.mysplitter.demo.domain.User;
import com.mysplitter.demo.service.DemoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
@RestController
public class DemoController {

    @Autowired
    private DemoService demoService;

    @Autowired
    private MySplitterDataSource dataSource;

    @GetMapping("/status")
    public Object status() {
        return dataSource.getStatus();
    }

    @GetMapping("/user/save")
    public Object saveUser(User user) {
        demoService.saveUser(user);
        return "success";
    }

    @GetMapping("/user/list")
    public List<User> userList() {
        return demoService.userList();
    }

    @GetMapping("/dept/save")
    public Object saveDepartment(Department department) {
        demoService.saveDepartment(department);
        return "success";
    }

    @GetMapping("/exception")
    public Object exception(@RequestParam(required = false) Integer id) {
        demoService.exception(id);
        return "success";
    }

}
