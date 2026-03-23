package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("accommodation")
@Schema(description = "住宿实体类")
public class Accommodation {
    
    @TableId(type = IdType.AUTO)
    @Schema(description = "住宿ID")
    private Long id;
    
    @Schema(description = "住宿名称")
    private String name;
    
    @Schema(description = "住宿类型")
    private String type;
    
    @Schema(description = "住宿地址")
    private String address;
    
    @Schema(description = "关联景点ID")
    private Long scenicId;
    
    @Schema(description = "描述")
    private String description;
    
    @Schema(description = "联系电话")
    private String contactPhone;
    
    @Schema(description = "价格区间")
    private String priceRange;
    
    @Schema(description = "评分")
    private BigDecimal starLevel;
    
    @Schema(description = "图片URL")
    private String imageUrl;
    
    @Schema(description = "图片列表，JSON格式")
    private String imageList;
    
    @Schema(description = "特色服务")
    private String features;
    
    @Schema(description = "距景点距离")
    private String distance;
    
    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") //此注解用来接收字符串类型的参数封装成LocalDateTime类型
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8", shape = JsonFormat.Shape.STRING) //此注解将date类型数据转成字符串响应出去
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)		// 反序列化
    @JsonSerialize(using = LocalDateTimeSerializer.class)		// 序列化
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
      @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") //此注解用来接收字符串类型的参数封装成LocalDateTime类型
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8", shape = JsonFormat.Shape.STRING) //此注解将date类型数据转成字符串响应出去
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)		// 反序列化
    @JsonSerialize(using = LocalDateTimeSerializer.class)		// 序列化
    private LocalDateTime updateTime;
    
    @TableField(exist = false)
    @Schema(description = "关联景点名称")
    private String scenicName;
    
    @TableField(exist = false)
    @Schema(description = "评价数量")
    private Long reviewCount;
    
    @TableField(exist = false)
    @Schema(description = "平均评分")
    private BigDecimal averageRating;
} 