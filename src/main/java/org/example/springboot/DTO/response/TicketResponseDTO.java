package org.example.springboot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门票响应DTO
 * 用于前端展示，包含景点名称、销售数量等扩展信息
 */
@Data
@Schema(description = "门票响应DTO")
public class TicketResponseDTO {
    
    @Schema(description = "门票ID")
    private Long id;
    
    @Schema(description = "景点ID")
    private Long scenicId;
    
    @Schema(description = "景点名称")
    private String scenicName;
    
    @Schema(description = "门票名称")
    private String ticketName;
    
    @Schema(description = "门票价格")
    private BigDecimal price;
    
    @Schema(description = "门票类型")
    private String ticketType;
    
    @Schema(description = "有效期")
    private String validPeriod;
    
    @Schema(description = "描述")
    private String description;
    
    @Schema(description = "库存（余票）")
    private Integer stock;
    
    @Schema(description = "销售数量（购买人数）")
    private Integer salesCount;
    
    @Schema(description = "封面图片")
    private String coverImage;
    
    @Schema(description = "是否热门")
    private Boolean isHot;
    
    @Schema(description = "状态: 1-可预订, 0-不可预订")
    private Integer status;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
