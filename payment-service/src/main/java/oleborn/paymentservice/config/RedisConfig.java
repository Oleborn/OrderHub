package oleborn.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Конфигурация Redis для payment-service.
 * Настраивает RedisTemplate для удобной работы с Redis.
 */
@Configuration
public class RedisConfig {

    /**
     * Создаёт кастомный RedisTemplate с человекочитаемой сериализацией.
     *
     * @param connectionFactory – фабрика подключений к Redis (автоматически создаётся Spring Boot
     *                            на основе свойств spring.data.redis.*)
     * @return настроенный RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        // Создаём экземпляр шаблона
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        // Устанавливаем фабрику соединений
        template.setConnectionFactory(connectionFactory);

        // -------- НАСТРОЙКА СЕРИАЛИЗАЦИИ КЛЮЧЕЙ --------
        // Используем StringRedisSerializer для ключей – это гарантирует,
        // что ключи будут храниться как обычные строки (без лишних байтовых префиксов).
        // Это позволяет легко искать ключи через redis-cli и видеть их в человекочитаемом виде.
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);         // для основных ключей
        template.setHashKeySerializer(stringSerializer);     // для ключей внутри хешей

        // -------- НАСТРОЙКА СЕРИАЛИЗАЦИИ ЗНАЧЕНИЙ --------
        // Используем GenericJackson2JsonRedisSerializer – он сохраняет объекты в формате JSON.
        // Преимущества:
        //   - JSON-представление читаемо для человека (удобно при отладке).
        //   - В JSON добавляется поле @class, хранящее полное имя класса.
        //     Это позволяет десериализовать объект в исходный класс даже без явного указания типа.
        // Недостаток: поле @class увеличивает размер данных, но для большинства приложений это некритично.
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);          // для обычных значений
        template.setHashValueSerializer(jsonSerializer);      // для значений внутри хешей

        // Если вы хотите хранить значения как строки (например, "processing") – можно вместо
        // GenericJackson2JsonRedisSerializer использовать StringRedisSerializer и для значений.
        // Тогда значение будет сохранено как простая строка без кавычек и мета-информации.
        // template.setValueSerializer(stringSerializer);
        // template.setHashValueSerializer(stringSerializer);

        // Инициализирует внутренние компоненты шаблона (обязательный вызов)
        template.afterPropertiesSet();

        return template;
    }

    /**
     * Альтернативный бин: StringRedisTemplate – если вы храните только строки.
     * Spring Boot автоматически создаёт его, поэтому его не нужно объявлять вручную.
     * Его можно внедрить напрямую:
     * @Autowired private StringRedisTemplate stringRedisTemplate;
     *
     * StringRedisTemplate уже настроен со строковыми сериализаторами для ключей и значений.
     */

    //Настройка TTL по умолчанию (если используете Redis как кэш через @Cacheable)
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}