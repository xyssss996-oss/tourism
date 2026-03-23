package org.example.springboot.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import jakarta.annotation.Resource;
import org.example.springboot.config.AlipayConfig;
import org.example.springboot.entity.TicketOrder;
import org.example.springboot.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AlipayService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlipayService.class);

    @Resource
    private AlipayConfig alipayConfig;
    
    @Resource
    private TicketOrderService ticketOrderService;
    
    /**
     * 生成支付宝支付表单
     */
    public String createAlipayForm(Long orderId) {
        // 获取订单详情
        TicketOrder order = ticketOrderService.getOrderDetail(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        
        // 创建支付宝客户端
        AlipayClient alipayClient = new DefaultAlipayClient(
                alipayConfig.getGateway(),
                alipayConfig.getAppId(),
                alipayConfig.getPrivateKey(),
                alipayConfig.getFormat(),
                alipayConfig.getCharset(),
                alipayConfig.getPublicKey(),
                alipayConfig.getSignType());
        
        // 创建支付请求
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        // 设置回调地址
        request.setReturnUrl(alipayConfig.getReturnUrl());
        request.setNotifyUrl(alipayConfig.getNotifyUrl());
        
        // 创建支付模型
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(order.getOrderNo());
        model.setTotalAmount(order.getTotalAmount().toString());
        model.setSubject("门票预订-" + order.getTicketName());
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        
        request.setBizModel(model);
        
        try {
            // 生成表单
            return alipayClient.pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            LOGGER.error("生成支付宝支付表单失败: {}", e.getMessage());
            throw new ServiceException("生成支付宝支付表单失败");
        }
    }
    
    /**
     * 处理支付宝同步回调
     */
    public void handleAlipayReturn(Map<String, String> params) {
        try {
            // 验证签名
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params, 
                    alipayConfig.getPublicKey(), 
                    alipayConfig.getCharset(), 
                    alipayConfig.getSignType());
            
            if (!signVerified) {
                LOGGER.error("支付宝同步回调签名验证失败");
                throw new ServiceException("支付宝同步回调签名验证失败");
            }
            
            // 获取交易状态
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String tradeStatus = params.get("trade_status");
            
            LOGGER.info("支付宝同步回调 - 订单号: {}, 交易号: {}, 交易状态: {}", outTradeNo, tradeNo, tradeStatus);
            
            // 查询订单
            TicketOrder order = ticketOrderService.getOrderByOrderNo(outTradeNo);
            if (order == null) {
                throw new ServiceException("订单不存在");
            }
            
            // 订单已支付，则不再处理
            if (order.getStatus() == 1) {
                return;
            }
            
            // 更新订单状态为已支付
            ticketOrderService.payOrder(order.getId(), "ALIPAY");
        } catch (AlipayApiException e) {
            LOGGER.error("支付宝同步回调处理失败: {}", e.getMessage());
            throw new ServiceException("支付宝同步回调处理失败");
        }
    }
    
    /**
     * 处理支付宝异步通知
     */
    public String handleAlipayNotify(Map<String, String> params) {
        try {
            // 验证签名
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params, 
                    alipayConfig.getPublicKey(), 
                    alipayConfig.getCharset(), 
                    alipayConfig.getSignType());
            
            if (!signVerified) {
                LOGGER.error("支付宝异步通知签名验证失败");
                return "fail";
            }
            
            // 获取交易状态
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String tradeStatus = params.get("trade_status");
            
            LOGGER.info("支付宝异步通知 - 订单号: {}, 交易号: {}, 交易状态: {}", outTradeNo, tradeNo, tradeStatus);
            
            // 验证交易状态
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                // 查询订单
                TicketOrder order = ticketOrderService.getOrderByOrderNo(outTradeNo);
                if (order == null) {
                    return "fail";
                }
                
                // 订单已支付，则不再处理
                if (order.getStatus() == 1) {
                    return "success";
                }
                
                // 更新订单状态为已支付
                ticketOrderService.payOrder(order.getId(), "ALIPAY");
                return "success";
            }
            return "fail";
        } catch (Exception e) {
            LOGGER.error("处理支付宝异步通知失败: {}", e.getMessage());
            return "fail";
        }
    }
} 