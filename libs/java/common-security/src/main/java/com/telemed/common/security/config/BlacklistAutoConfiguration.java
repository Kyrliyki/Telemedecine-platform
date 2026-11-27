package com.telemed.common.security.config;

import com.telemed.common.security.blacklist.NoOpTokenBlacklist;
import com.telemed.common.security.blacklist.RedisTokenBlacklist;
import com.telemed.common.security.blacklist.TokenBlacklist;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(
    afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnClass(StringRedisTemplate.class)
public class BlacklistAutoConfiguration {

  @Bean
  @ConditionalOnBean(StringRedisTemplate.class)
  @ConditionalOnMissingBean(TokenBlacklist.class)
  public TokenBlacklist redisTokenBlacklist(StringRedisTemplate redis) {
    return new RedisTokenBlacklist(redis);
  }

  @Bean
  @ConditionalOnMissingBean(TokenBlacklist.class)
  public TokenBlacklist noOpTokenBlacklist() {
    return new NoOpTokenBlacklist();
  }
}
