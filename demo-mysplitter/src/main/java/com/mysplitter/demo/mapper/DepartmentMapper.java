package com.mysplitter.demo.mapper;

import com.mysplitter.demo.domain.Department;
import org.apache.ibatis.annotations.Insert;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
public interface DepartmentMapper {

    @Insert("INSERT INTO dept(name) VALUES(#{name})")
    int save(Department department);

}
