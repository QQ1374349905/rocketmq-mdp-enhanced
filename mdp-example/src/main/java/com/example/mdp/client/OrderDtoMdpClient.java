package com.example.mdp.client;

import com.example.mdp.dto.OrderDTO;
import com.rocketmq.mdp.enhanced.annotation.MdpClient;
import com.rocketmq.mdp.enhanced.annotation.MdpMethod;

/**
 * 订单DTO客户端 - 用于测试跨包参数转换
 * <p>
 * 这个客户端发送 OrderDTO（com.example.mdp.dto.OrderDTO）
 * 但服务端接收的是 OrderInfo（com.example.mdp.domain.OrderInfo）
 * <p>
 * 测试目标：
 * - 验证框架能否自动将 OrderDTO 转换为 OrderInfo
 * - 验证不同包名的同名字段能否正确映射
 */
@MdpClient(
        service = "order.dto.service",
        topic = "order_service"  // 注意：复用 order_service topic，测试同一消费者处理不同包的类
)
public interface OrderDtoMdpClient {

    /**
     * 发送DTO订单（同步）
     * <p>
     * 客户端：发送 OrderDTO (com.example.mdp.dto.OrderDTO)
     * 服务端：接收 OrderInfo (com.example.mdp.domain.OrderInfo)
     * 框架：自动完成跨包类型转换
     */
    @MdpMethod(isSync = true)
    void sendOrderFromDto(OrderDTO orderDTO);

    /**
     * 发送DTO订单（异步）
     */
    @MdpMethod( isSync = false)
    void sendOrderFromDtoAsync(OrderDTO orderDTO);
}
