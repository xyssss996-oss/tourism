package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.springboot.entity.TicketOrder;

@Mapper
public interface TicketOrderMapper extends BaseMapper<TicketOrder> {
    
    /**
     * 统计某个门票的销售数量（已支付和已完成的订单）
     * @param ticketId 门票ID
     * @return 销售数量
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM ticket_order " +
            "WHERE ticket_id = #{ticketId} AND status IN (1, 4)")
    Integer selectSalesCountByTicketId(Long ticketId);
}