package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmployeeMapper {

    /**
     * 根据用户名查询员工
     * @param username
     */
    // 告诉 MyBatis，这个方法执行括号中的查询语句
    // sql比较简短用逐渐，复杂用yaml
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);

    /**
     * 插入员工数据
     * @param employee
     */
    // id_number 因为mybatis 开启了驼峰命名配置 会映射成idNumber
    @Insert("insert into employee (name, phone, sex, id_number, username, password,create_time,update_time,create_user,update_user)"
            + "values(#{name},#{phone},#{sex},#{idNumber},#{username},#{password},#{createTime},#{updateTime},#{createUser},#{updateUser})")
    // #{name} 表示从 employee 对象中获取 name 字段的值
    void insert(Employee employee);

    /**
     * 员工分页查询
     * @param employeePageQueryDTO
     */
    // SQL写在了映射文件
    Page<Employee> pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 根据主键动态修改属性
     * @param employee
     */
    void update(Employee employee);

    /**
     * 根据id查询
     * @param id
     * @return
     */
    @Select("select * from employee where id = #{id}")
    Employee getById(long id);
}
