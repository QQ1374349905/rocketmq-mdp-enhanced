package com.example.mdp.dto;

import java.io.Serializable;

/**
 * 订单DTO - 用于测试跨包参数转换
 * <p>
 * 这个类和 com.example.mdp.domain.OrderInfo 字段完全一致，
 * 但属于不同的包，用于测试灵活参数转换功能
 * <p>
 * 场景：
 * - 服务A使用 com.example.mdp.domain.OrderInfo
 * - 服务B使用 com.example.mdp.dto.OrderDTO
 * - MDP框架自动完成跨包类型转换
 */
public class OrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderId;
    private String userId;
    private String productName;
    private Double amount;
    private String status;
    private Long createTime;

    public OrderDTO() {
    }

    public OrderDTO(String orderId, String userId, String productName, Double amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.productName = productName;
        this.amount = amount;
        this.status = "CREATED";
        this.createTime = System.currentTimeMillis();
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "OrderDTO{" +
                "orderId='" + orderId + '\'' +
                ", userId='" + userId + '\'' +
                ", productName='" + productName + '\'' +
                ", amount=" + amount +
                ", status='" + status + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
