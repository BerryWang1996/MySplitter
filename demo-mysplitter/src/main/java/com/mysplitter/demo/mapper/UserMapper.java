package com.mysplitter.demo.mapper;

import com.mysplitter.demo.domain.User;
import org.apache.ibatis.annotations.Insert;

/**
 * @author berrywang1996
 * @version V1.0.0
 */
public interface UserMapper {

    @Insert("[database-a] INSERT INTO user(name, age) VALUES(#{name}, #{age})")
    int save(User user);

}
