package com.example.mdp.domain;

import java.io.Serializable;

/**
 * 订单信息示例领域对象
 */
public class OrderInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderId;
    private String userId;
    private String productName;
    private Double amount;
    private String status;
    private Long createTime;

    public OrderInfo() {
    }

    public OrderInfo(String orderId, String userId, String productName, Double amount) {
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
        return "OrderInfo{" +
                "orderId='" + orderId + '\'' +
                ", userId='" + userId + '\'' +
                ", productName='" + productName + '\'' +
                ", amount=" + amount +
                ", status='" + status + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
