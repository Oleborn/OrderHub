package oleborn.orderhub_project.order.config;

import org.jooq.conf.RenderNameCase;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Этот класс позволяет кастомизировать поведение jOOQ на уровне всего приложения.
 * Настройки применяются ко всем запросам, выполняемым через jOOQ DSL.
 */
@Configuration
public class JooqConfig {

    /**
     * DefaultConfigurationCustomizer - это функциональный интерфейс Spring Boot,
     * который позволяет модифицировать конфигурацию jOOQ перед ее использованием.
     *
     * @return кастомизатор конфигурации с примененными настройками
     */
    @Bean
    public DefaultConfigurationCustomizer configurationCustomizer() {
        return config -> {
            config.settings()
                    /* Приведение всех имен к нижнему регистру в генерируемом SQL
                     * Пример:
                     * - Было: SELECT "USERS"."NAME" FROM "USERS"
                     * - Стало: SELECT users.name FROM users
                     */
                    .withRenderNameCase(RenderNameCase.LOWER)

                    /* Включение форматирования SQL (для логирования и отладки)
                     * Пример без форматирования: SELECT name FROM users WHERE id = 1
                     * Пример с форматированием:
                     *   SELECT name
                     *   FROM users
                     *   WHERE id = 1
                     */
                    .withRenderFormatted(true);
        };
    }
}