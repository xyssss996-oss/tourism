package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.springboot.common.Result;
import org.example.springboot.service.AlipayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Tag(name="支付宝支付接口")
@RestController
@RequestMapping("/alipay")
public class AlipayController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlipayController.class);
    
    @Resource
    private AlipayService alipayService;

    @Operation(summary = "生成支付宝支付表单")
    @GetMapping("/pay/{orderId}")
    public Result<String> pay(@PathVariable Long orderId) {
        String formHtml = alipayService.createAlipayForm(orderId);
        return Result.success(formHtml);
    }
    
    @Operation(summary = "支付宝同步回调接口")
    @GetMapping("/return")
    public void returnUrl(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LOGGER.info("收到支付宝同步回调请求");
        Map<String, String> params = convertRequestParametersToMap(request);
        
        try {
            alipayService.handleAlipayReturn(params);
            // 跳转到订单列表页
            response.sendRedirect("/orders");
        } catch (Exception e) {
            LOGGER.error("处理支付宝同步回调失败: {}", e.getMessage());
            response.sendRedirect("/payment-failed");
        }
    }
    
    @Operation(summary = "支付宝异步通知接口")
    @PostMapping("/notify")
    public String notifyUrl(HttpServletRequest request) {
        LOGGER.info("收到支付宝异步通知请求");
        Map<String, String> params = convertRequestParametersToMap(request);
        
        return alipayService.handleAlipayNotify(params);
    }
    
    /**
     * 将请求参数转换为Map
     */
    private Map<String, String> convertRequestParametersToMap(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        Map<String, String> params = new HashMap<>();
        
        for (String key : parameterMap.keySet()) {
            String[] values = parameterMap.get(key);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(key, valueStr);
        }
        
        return params;
    }
} 