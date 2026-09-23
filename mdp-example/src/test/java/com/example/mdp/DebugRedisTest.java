package com.example.mdp;

import com.rocketmq.mdp.enhanced.idempotent.RedisIdempotentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Set;

/**
 * Redis幂等性记录诊断工具
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = MdpExampleApplication.class)
public class DebugRedisTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private RedisIdempotentService redisIdempotentService;

    /**
     * 查看所有幂等性记录
     */
    @Test
    public void testListAllIdempotentRecords() {
        System.out.println("=== 查询所有幂等性记录 ===");

        // 查询所有幂等性key
        Set<String> idempotentKeys = stringRedisTemplate.keys("mdp:idempotent:*");
        System.out.println("幂等性记录总数: " + (idempotentKeys != null ? idempotentKeys.size() : 0));
        System.out.println();

        if (idempotentKeys != null && !idempotentKeys.isEmpty()) {
            System.out.println("详细记录:");
            for (String key : idempotentKeys) {
                String value = stringRedisTemplate.opsForValue().get(key);
                Long ttl = stringRedisTemplate.getExpire(key);
                String businessKey = key.replace("mdp:idempotent:", "");

                System.out.println(String.format("  - businessKey: %s", businessKey));
                System.out.println(String.format("    messageId: %s", value));
                System.out.println(String.format("    剩余TTL: %d秒 (%.2f小时)", ttl, ttl / 3600.0));
                System.out.println();
            }
        } else {
            System.out.println("✅ Redis中没有幂等性记录");
        }

        // 查询所有锁key
        Set<String> lockKeys = stringRedisTemplate.keys("mdp:lock:*");
        System.out.println("分布式锁数量: " + (lockKeys != null ? lockKeys.size() : 0));
        if (lockKeys != null && !lockKeys.isEmpty()) {
            System.out.println("⚠️  发现未释放的锁:");
            for (String key : lockKeys) {
                String value = stringRedisTemplate.opsForValue().get(key);
                Long ttl = stringRedisTemplate.getExpire(key);
                System.out.println(String.format("  - %s: %s (TTL: %ds)", key, value, ttl));
            }
        }
    }

    /**
     * 清空所有幂等性记录
     */
    @Test
    public void testClearAllIdempotentRecords() {
        System.out.println("=== 清空所有幂等性记录 ===");

        if (redisIdempotentService != null) {
            redisIdempotentService.clearAll();
            System.out.println("✅ 已通过 RedisIdempotentService 清空所有记录");
        } else {
            // 手动清空
            Set<String> idempotentKeys = stringRedisTemplate.keys("mdp:idempotent:*");
            Set<String> lockKeys = stringRedisTemplate.keys("mdp:lock:*");

            long deletedCount = 0;
            if (idempotentKeys != null && !idempotentKeys.isEmpty()) {
                deletedCount += stringRedisTemplate.delete(idempotentKeys);
            }
            if (lockKeys != null && !lockKeys.isEmpty()) {
                deletedCount += stringRedisTemplate.delete(lockKeys);
            }

            System.out.println("✅ 已清空 " + deletedCount + " 条记录");
        }

        // 验证清空结果
        Set<String> remaining = stringRedisTemplate.keys("mdp:*");
        System.out.println("剩余记录数: " + (remaining != null ? remaining.size() : 0));
    }

    /**
     * 查询特定订单的幂等性记录
     */
    @Test
    public void testCheckSpecificOrder() {
        String[] orderIds = {
            "ORDER_DIFFERENT_001",
            "ORDER_DIFFERENT_002",
            "ORDER001",
            "ORDER002",
            "VIP_ORDER001"
        };

        System.out.println("=== 检查特定订单的幂等性记录 ===");
        for (String orderId : orderIds) {
            String key = "mdp:idempotent:" + orderId;
            String value = stringRedisTemplate.opsForValue().get(key);
            Long ttl = stringRedisTemplate.getExpire(key);

            if (value != null) {
                System.out.println(String.format("✅ %s: 存在", orderId));
                System.out.println(String.format("   messageId: %s", value));
                System.out.println(String.format("   TTL: %d秒 (%.2f小时)", ttl, ttl / 3600.0));
            } else {
                System.out.println(String.format("❌ %s: 不存在", orderId));
            }
            System.out.println();
        }
    }
}
