package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.example.springboot.dto.response.TicketResponseDTO;
import org.example.springboot.entity.ScenicSpot;
import org.example.springboot.entity.Ticket;
import org.example.springboot.exception.ServiceException;
import org.example.springboot.mapper.ScenicSpotMapper;
import org.example.springboot.mapper.TicketMapper;
import org.example.springboot.mapper.TicketOrderMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TicketService {

    @Resource
    private TicketMapper ticketMapper;
    
    @Resource
    private ScenicSpotMapper scenicSpotMapper;
    
    @Resource
    private TicketOrderMapper ticketOrderMapper;

    /**
     * 分页查询门票
     */
    public Page<Ticket> getTicketsByPage(String ticketName, String ticketType, Long scenicId, Integer currentPage, Integer size) {
        LambdaQueryWrapper<Ticket> queryWrapper = new LambdaQueryWrapper<>();
        
        // 添加查询条件
        if (StringUtils.isNotBlank(ticketName)) {
            queryWrapper.like(Ticket::getTicketName, ticketName);
        }
        if (StringUtils.isNotBlank(ticketType)) {
            queryWrapper.eq(Ticket::getTicketType, ticketType);
        }
        if (scenicId != null) {
            queryWrapper.eq(Ticket::getScenicId, scenicId);
        }
        
        // 只查询可预订的门票
        queryWrapper.eq(Ticket::getStatus, 1);
        
        // 按创建时间降序排序
        queryWrapper.orderByDesc(Ticket::getCreateTime);
        
        return ticketMapper.selectPage(new Page<>(currentPage, size), queryWrapper);
    }
    
    /**
     * 根据ID获取门票详情
     */
    public Ticket getTicketById(Long id) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new ServiceException("门票不存在");
        }
        return ticket;
    }
    
    /**
     * 新增门票
     */
    @Transactional
    public void addTicket(Ticket ticket) {
        // 检查景点是否存在
        ScenicSpot scenicSpot = scenicSpotMapper.selectById(ticket.getScenicId());
        if (scenicSpot == null) {
            throw new ServiceException("关联的景点不存在");
        }
        
        // 设置默认状态为可预订
        if (ticket.getStatus() == null) {
            ticket.setStatus(1);
        }
        
        ticketMapper.insert(ticket);
    }
    
    /**
     * 更新门票信息
     */
    @Transactional
    public void updateTicket(Ticket ticket) {
        Ticket existTicket = ticketMapper.selectById(ticket.getId());
        if (existTicket == null) {
            throw new ServiceException("门票不存在");
        }
        
        // 如果景点ID有变更，需要检查新景点是否存在
        if (ticket.getScenicId() != null && !ticket.getScenicId().equals(existTicket.getScenicId())) {
            ScenicSpot scenicSpot = scenicSpotMapper.selectById(ticket.getScenicId());
            if (scenicSpot == null) {
                throw new ServiceException("关联的景点不存在");
            }
        }
        
        ticketMapper.updateById(ticket);
    }
    
    /**
     * 删除门票
     */
    @Transactional
    public void deleteTicket(Long id) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new ServiceException("门票不存在");
        }
        
        ticketMapper.deleteById(id);
    }
    
    /**
     * 获取某个景点的所有可预订门票
     */
    public Page<Ticket> getTicketsByScenicId(Long scenicId, Integer currentPage, Integer size) {
        LambdaQueryWrapper<Ticket> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Ticket::getScenicId, scenicId);
        queryWrapper.eq(Ticket::getStatus, 1); // 只查询可预订的门票
        queryWrapper.orderByAsc(Ticket::getPrice); // 按价格升序
        
        return ticketMapper.selectPage(new Page<>(currentPage, size), queryWrapper);
    }
    
    /**
     * 更新门票库存
     */
    @Transactional
    public void updateTicketStock(Long ticketId, Integer quantity) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new ServiceException("门票不存在");
        }
        
        // 检查库存是否充足
        if (ticket.getStock() < quantity) {
            throw new ServiceException("门票库存不足");
        }
        
        // 更新库存
        ticket.setStock(ticket.getStock() - quantity);
        ticketMapper.updateById(ticket);
    }
    
    /**
     * 恢复门票库存（订单取消或退款时）
     */
    @Transactional
    public void restoreTicketStock(Long ticketId, Integer quantity) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new ServiceException("门票不存在");
        }
        
        // 恢复库存
        ticket.setStock(ticket.getStock() + quantity);
        ticketMapper.updateById(ticket);
    }
    
    /**
     * 获取热门推荐门票
     * 根据销量排序，返回热门门票列表
     */
    public List<TicketResponseDTO> getHotTickets(Integer size) {
        // 查询所有可预订的门票
        LambdaQueryWrapper<Ticket> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Ticket::getStatus, 1);
        List<Ticket> tickets = ticketMapper.selectList(queryWrapper);
        
        // 转换为DTO并计算销售数量
        List<TicketResponseDTO> ticketDTOs = tickets.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        // 按销售数量降序排序，取前N个
        return ticketDTOs.stream()
                .sorted((t1, t2) -> {
                    int sales1 = t1.getSalesCount() != null ? t1.getSalesCount() : 0;
                    int sales2 = t2.getSalesCount() != null ? t2.getSalesCount() : 0;
                    return Integer.compare(sales2, sales1);
                })
                .limit(size)
                .collect(Collectors.toList());
    }
    
    /**
     * 增强的分页查询门票（包含景点名称、销售数量等扩展信息）
     */
    public Page<TicketResponseDTO> getTicketsPageWithDetails(
            String ticketName, 
            String ticketType, 
            String priceRange, 
            String sortType,
            Long scenicId, 
            Integer currentPage, 
            Integer size) {
        
        LambdaQueryWrapper<Ticket> queryWrapper = new LambdaQueryWrapper<>();
        
        // 基本查询条件
        if (StringUtils.isNotBlank(ticketName)) {
            queryWrapper.like(Ticket::getTicketName, ticketName);
        }
        if (StringUtils.isNotBlank(ticketType)) {
            queryWrapper.eq(Ticket::getTicketType, ticketType);
        }
        if (scenicId != null) {
            queryWrapper.eq(Ticket::getScenicId, scenicId);
        }
        
        // 价格区间筛选
        if (StringUtils.isNotBlank(priceRange)) {
            String[] range = priceRange.split("-");
            if (range.length == 2) {
                try {
                    BigDecimal minPrice = new BigDecimal(range[0]);
                    BigDecimal maxPrice = new BigDecimal(range[1]);
                    queryWrapper.between(Ticket::getPrice, minPrice, maxPrice);
                } catch (NumberFormatException e) {
                    // 忽略格式错误
                }
            } else if (priceRange.endsWith("+")) {
                try {
                    BigDecimal minPrice = new BigDecimal(priceRange.replace("+", ""));
                    queryWrapper.ge(Ticket::getPrice, minPrice);
                } catch (NumberFormatException e) {
                    // 忽略格式错误
                }
            }
        }
        
        // 只查询可预订的门票
        queryWrapper.eq(Ticket::getStatus, 1);
        
        // 基础排序（会被后续排序覆盖）
        queryWrapper.orderByDesc(Ticket::getCreateTime);
        
        // 查询门票
        Page<Ticket> ticketPage = ticketMapper.selectPage(new Page<>(currentPage, size), queryWrapper);
        
        // 转换为DTO
        List<TicketResponseDTO> dtoList = ticketPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        // 应用排序
        if (StringUtils.isNotBlank(sortType)) {
            switch (sortType) {
                case "price-asc":
                    dtoList.sort((t1, t2) -> t1.getPrice().compareTo(t2.getPrice()));
                    break;
                case "price-desc":
                    dtoList.sort((t1, t2) -> t2.getPrice().compareTo(t1.getPrice()));
                    break;
                case "sales":
                    dtoList.sort((t1, t2) -> {
                        int sales1 = t1.getSalesCount() != null ? t1.getSalesCount() : 0;
                        int sales2 = t2.getSalesCount() != null ? t2.getSalesCount() : 0;
                        return Integer.compare(sales2, sales1);
                    });
                    break;
                default:
                    // 综合推荐，保持原有顺序
                    break;
            }
        }
        
        // 构建结果Page
        Page<TicketResponseDTO> resultPage = new Page<>(currentPage, size);
        resultPage.setRecords(dtoList);
        resultPage.setTotal(ticketPage.getTotal());
        resultPage.setPages(ticketPage.getPages());
        
        return resultPage;
    }
    
    /**
     * 将Ticket实体转换为TicketResponseDTO
     * 包含景点名称、销售数量、封面图片等扩展信息
     */
    private TicketResponseDTO convertToDTO(Ticket ticket) {
        TicketResponseDTO dto = new TicketResponseDTO();
        BeanUtils.copyProperties(ticket, dto);
        
        // 获取景点信息
        ScenicSpot scenicSpot = scenicSpotMapper.selectById(ticket.getScenicId());
        if (scenicSpot != null) {
            dto.setScenicName(scenicSpot.getName());
            // 使用景点的封面图片作为门票封面
            dto.setCoverImage(scenicSpot.getImageUrl());
        }
        
        // 计算销售数量（统计已支付和已完成的订单）
        Integer salesCount = ticketOrderMapper.selectSalesCountByTicketId(ticket.getId());
        dto.setSalesCount(salesCount != null ? salesCount : 0);
        
        // 判断是否热门（销量大于100认为是热门）
        dto.setIsHot(salesCount != null && salesCount > 100);
        
        return dto;
    }
} 