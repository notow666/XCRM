package cn.cordys.crm.blacklist.mapper;

import cn.cordys.crm.blacklist.domain.Blacklist;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface BlacklistMapper {
    @Select("<script>select * from customer_blacklist where mobile in " +
            "<foreach collection='mobiles' item='mobile' open='(' separator=',' close=')'>#{mobile}</foreach></script>")
    List<Blacklist> find(@Param("mobiles") List<String> mobiles);

    @Select("<script>select * from customer_blacklist where id in " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Blacklist> findIds(@Param("ids") List<String> ids);

    @Select("select * from customer_blacklist where (#{keyword} = '' or mobile like concat('%', #{keyword}, '%') " +
            "or customer_name like concat('%', #{keyword}, '%')) order by update_time desc, id desc limit #{offset}, #{size}")
    List<Blacklist> page(@Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("select count(*) from customer_blacklist where (#{keyword} = '' or mobile like concat('%', #{keyword}, '%') " +
            "or customer_name like concat('%', #{keyword}, '%'))")
    long count(@Param("keyword") String keyword);

    @Select("select id from customer_blacklist where (#{keyword} = '' or mobile like concat('%', #{keyword}, '%') " +
            "or customer_name like concat('%', #{keyword}, '%')) order by id")
    List<String> findIdsByKeyword(@Param("keyword") String keyword);

    @Insert("insert into customer_blacklist(id,mobile,customer_name,create_time,create_user,update_time,update_user) " +
            "values(#{id},#{mobile},#{customerName},#{createTime},#{createUser},#{updateTime},#{updateUser}) " +
            "on duplicate key update customer_name=values(customer_name),update_time=values(update_time),update_user=values(update_user)")
    int upsert(Blacklist row);

    @Insert("<script>insert into customer_blacklist(id,mobile,customer_name,create_time,create_user,update_time,update_user) values " +
            "<foreach collection='rows' item='row' separator=','>" +
            "(#{row.id},#{row.mobile},#{row.customerName},#{row.createTime},#{row.createUser},#{row.updateTime},#{row.updateUser})" +
            "</foreach> on duplicate key update customer_name=values(customer_name),update_time=values(update_time),update_user=values(update_user)</script>")
    int upsertRows(@Param("rows") List<Blacklist> rows);

    @Delete("<script>delete from customer_blacklist where id in " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int delete(@Param("ids") List<String> ids);
}
