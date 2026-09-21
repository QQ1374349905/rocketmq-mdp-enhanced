package com.gaoji.common.mdp.enhanced.idempotent;

/**
 * 幂等性服务接口
 *
 * 提供消息去重和幂等性保证
 * 支持多种实现：内存、Redis、数据库
 */
public interface IdempotentService {

    /**
     * 尝试处理消息（幂等性检查）
     *
     * @param businessKey 业务唯一键（如订单ID）
     * @param messageId RocketMQ消息ID
     * @param timeout 去重记录过期时间（秒）
     * @param lockTimeout 分布式锁超时时间（秒）
     * @return true=首次处理，false=重复消息
     */
    boolean tryProcess(String businessKey, String messageId, long timeout, long lockTimeout);

    /**
     * 标记消息处理成功
     *
     * @param businessKey 业务唯一键
     * @param messageId RocketMQ消息ID
     */
    void markSuccess(String businessKey, String messageId);

    /**
     * 标记消息处理失败
     *
     * @param businessKey 业务唯一键
     * @param messageId RocketMQ消息ID
     */
    void markFailed(String businessKey, String messageId);

    /**
     * 检查消息是否已处理
     *
     * @param businessKey 业务唯一键
     * @return true=已处理，false=未处理
     */
    boolean isProcessed(String businessKey);

    /**
     * 清理过期的去重记录
     */
    void cleanup();
}
