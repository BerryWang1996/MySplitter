package com.mysplitter.demo.controller;

import com.mysplitter.demo.domain.Department;
import com.mysplitter.demo.domain.User;
import com.mysplitter.demo.service.DemoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
@RestController
public class DemoController {

    @Autowired
    private DemoService demoService;

    @GetMapping("/user/save")
    public Object saveUser(User user) {
        demoService.saveUser(user);
        return "success";
    }

    @GetMapping("/dept/save")
    public Object saveDepartment(Department department) {
        demoService.saveDepartment(department);
        return "success";
    }

}
